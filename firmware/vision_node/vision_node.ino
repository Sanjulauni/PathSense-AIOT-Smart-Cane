/*
 * SmartWhiteCane - Vision node (ESP32-CAM)
 *
 * Runs the quantized "track_detector" Edge Impulse model on every camera
 * frame. When the "Track Sign" class scores above CONFIDENCE_THRESHOLD for
 * REQUIRED_CONSECUTIVE frames in a row, it sends one line, DETECT_MESSAGE,
 * to the cane controller (ESP32-S3) over UART. Each physical sign is
 * reported once. See config.h for every tunable value.
 *
 * Camera capture and inference are adapted from the Edge Impulse ESP32
 * camera example, whose licence follows.
 */

/* Edge Impulse Arduino examples
 * Copyright (c) 2022 EdgeImpulse Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

#include <track_detector_inferencing.h>
#include "edge-impulse-sdk/dsp/image/image.hpp"
#include "esp_camera.h"
#include <string.h>

#include "config.h"

#if !defined(EI_CLASSIFIER_SENSOR) || EI_CLASSIFIER_SENSOR != EI_CLASSIFIER_SENSOR_CAMERA
#error "Invalid model for current sensor"
#endif

#if EI_CLASSIFIER_OBJECT_DETECTION == 1
#error "This sketch expects an image classification model, not object detection"
#endif

/* Camera pins -------------------------------------------------------------- */
#if defined(CAMERA_MODEL_AI_THINKER)
#define PWDN_GPIO_NUM     32
#define RESET_GPIO_NUM    -1
#define XCLK_GPIO_NUM      0
#define SIOD_GPIO_NUM     26
#define SIOC_GPIO_NUM     27
#define Y9_GPIO_NUM       35
#define Y8_GPIO_NUM       34
#define Y7_GPIO_NUM       39
#define Y6_GPIO_NUM       36
#define Y5_GPIO_NUM       21
#define Y4_GPIO_NUM       19
#define Y3_GPIO_NUM       18
#define Y2_GPIO_NUM        5
#define VSYNC_GPIO_NUM    25
#define HREF_GPIO_NUM     23
#define PCLK_GPIO_NUM     22

#elif defined(CAMERA_MODEL_ESP_EYE)
#define PWDN_GPIO_NUM    -1
#define RESET_GPIO_NUM   -1
#define XCLK_GPIO_NUM     4
#define SIOD_GPIO_NUM    18
#define SIOC_GPIO_NUM    23
#define Y9_GPIO_NUM      36
#define Y8_GPIO_NUM      37
#define Y7_GPIO_NUM      38
#define Y6_GPIO_NUM      39
#define Y5_GPIO_NUM      35
#define Y4_GPIO_NUM      14
#define Y3_GPIO_NUM      13
#define Y2_GPIO_NUM      34
#define VSYNC_GPIO_NUM    5
#define HREF_GPIO_NUM    27
#define PCLK_GPIO_NUM    25

#else
#error "Camera model not selected in config.h"
#endif

/* Constants ---------------------------------------------------------------- */
#define EI_CAMERA_RAW_FRAME_BUFFER_COLS  320
#define EI_CAMERA_RAW_FRAME_BUFFER_ROWS  240
#define EI_CAMERA_FRAME_BYTE_SIZE        3

/* State -------------------------------------------------------------------- */
static bool debug_nn = false;       // true prints the features fed to the model
static bool is_initialised = false;
static uint8_t *snapshot_buf = nullptr;  // RGB888 frame, allocated once
static int target_label_index = -1;     // index of TARGET_LABEL in the model

static camera_config_t camera_config = {
    .pin_pwdn = PWDN_GPIO_NUM,
    .pin_reset = RESET_GPIO_NUM,
    .pin_xclk = XCLK_GPIO_NUM,
    .pin_sscb_sda = SIOD_GPIO_NUM,
    .pin_sscb_scl = SIOC_GPIO_NUM,

    .pin_d7 = Y9_GPIO_NUM,
    .pin_d6 = Y8_GPIO_NUM,
    .pin_d5 = Y7_GPIO_NUM,
    .pin_d4 = Y6_GPIO_NUM,
    .pin_d3 = Y5_GPIO_NUM,
    .pin_d2 = Y4_GPIO_NUM,
    .pin_d1 = Y3_GPIO_NUM,
    .pin_d0 = Y2_GPIO_NUM,
    .pin_vsync = VSYNC_GPIO_NUM,
    .pin_href = HREF_GPIO_NUM,
    .pin_pclk = PCLK_GPIO_NUM,

    .xclk_freq_hz = 20000000,
    .ledc_timer = LEDC_TIMER_0,
    .ledc_channel = LEDC_CHANNEL_0,

    .pixel_format = PIXFORMAT_JPEG,
    .frame_size = FRAMESIZE_QVGA,

    .jpeg_quality = 12,
    .fb_count = 1,
    .fb_location = CAMERA_FB_IN_PSRAM,
    .grab_mode = CAMERA_GRAB_WHEN_EMPTY,
};

/* Sign detection filter ------------------------------------------------------
 * A sign is confirmed after REQUIRED_CONSECUTIVE frames in a row score above
 * CONFIDENCE_THRESHOLD. The filter then stays latched until the sign leaves
 * the view (REARM_FRAMES frames below the threshold), so each physical sign
 * produces exactly one message. COOLDOWN_MS adds a minimum gap between signs.
 */
struct SignFilter {
    uint8_t  streak = 0;       // hits in a row
    uint8_t  clear_frames = 0; // misses in a row while latched
    bool     latched = false;  // a sign was confirmed and is still in view
    bool     has_fired = false;
    uint32_t last_fire_ms = 0;

    // Returns true once per confirmed sign.
    bool update(float confidence, uint32_t now_ms) {
        if (confidence > CONFIDENCE_THRESHOLD) {
            clear_frames = 0;
            if (streak < 255) streak++;
        } else {
            streak = 0;
            if (latched && ++clear_frames >= REARM_FRAMES) {
                latched = false;
                clear_frames = 0;
            }
        }

        bool cooled_down = !has_fired || (now_ms - last_fire_ms >= COOLDOWN_MS);
        if (!latched && cooled_down && streak >= REQUIRED_CONSECUTIVE) {
            latched = true;
            has_fired = true;
            last_fire_ms = now_ms;
            streak = 0;
            return true;
        }
        return false;
    }
};

static SignFilter sign_filter;

/* Declarations ------------------------------------------------------------- */
bool ei_camera_init(void);
void ei_camera_deinit(void);
bool ei_camera_capture(uint32_t img_width, uint32_t img_height, uint8_t *out_buf);
static int ei_camera_get_data(size_t offset, size_t length, float *out_ptr);
static int find_target_label(void);

/* Setup -------------------------------------------------------------------- */
void setup()
{
    Serial.begin(LINK_BAUD);
    delay(200);
    Serial.println();
    Serial.println("VISION: booting");

    target_label_index = find_target_label();
    if (target_label_index < 0) {
        Serial.printf("VISION: ERROR label \"%s\" not in model. Labels are:", TARGET_LABEL);
        for (uint16_t i = 0; i < EI_CLASSIFIER_LABEL_COUNT; i++) {
            Serial.printf(" \"%s\"", ei_classifier_inferencing_categories[i]);
        }
        Serial.println();
        Serial.println("VISION: fix TARGET_LABEL in config.h. No signs will be reported.");
    }

    snapshot_buf = (uint8_t *)malloc(EI_CAMERA_RAW_FRAME_BUFFER_COLS *
                                     EI_CAMERA_RAW_FRAME_BUFFER_ROWS *
                                     EI_CAMERA_FRAME_BYTE_SIZE);
    if (snapshot_buf == nullptr) {
        Serial.println("VISION: ERROR could not allocate frame buffer");
    }

    if (ei_camera_init()) {
        Serial.println("VISION: camera ready");
    } else {
        Serial.println("VISION: ERROR camera init failed");
    }

    Serial.printf("VISION: watching \"%s\", threshold %.2f, %d frames in a row\n",
                  TARGET_LABEL, CONFIDENCE_THRESHOLD, REQUIRED_CONSECUTIVE);
}

/* Main loop ---------------------------------------------------------------- */
void loop()
{
    if (ei_sleep(5) != EI_IMPULSE_OK) {
        return;
    }

    if (snapshot_buf == nullptr || !is_initialised) {
        delay(1000);
        return;
    }

    ei::signal_t signal;
    signal.total_length = EI_CLASSIFIER_INPUT_WIDTH * EI_CLASSIFIER_INPUT_HEIGHT;
    signal.get_data = &ei_camera_get_data;

    if (!ei_camera_capture((size_t)EI_CLASSIFIER_INPUT_WIDTH,
                           (size_t)EI_CLASSIFIER_INPUT_HEIGHT, snapshot_buf)) {
        Serial.println("VISION: capture failed");
        return;
    }

    ei_impulse_result_t result = { 0 };
    EI_IMPULSE_ERROR err = run_classifier(&signal, &result, debug_nn);
    if (err != EI_IMPULSE_OK) {
        Serial.printf("VISION: classifier error %d\n", err);
        return;
    }

    float confidence = 0.0f;
    if (target_label_index >= 0) {
        confidence = result.classification[target_label_index].value;
    }

    bool confirmed = sign_filter.update(confidence, millis());

#if VERBOSE_LOG
    Serial.printf("VISION: conf=%.2f streak=%u/%d %s(%d ms)\n",
                  confidence, sign_filter.streak, REQUIRED_CONSECUTIVE,
                  sign_filter.latched ? "latched " : "",
                  result.timing.dsp + result.timing.classification);
#endif

    if (confirmed) {
        // The S3 only acts on a line that is exactly DETECT_MESSAGE.
        Serial.println(DETECT_MESSAGE);
    }
}

/* Helpers ------------------------------------------------------------------ */
static int find_target_label(void)
{
    for (uint16_t i = 0; i < EI_CLASSIFIER_LABEL_COUNT; i++) {
        if (strcmp(ei_classifier_inferencing_categories[i], TARGET_LABEL) == 0) {
            return i;
        }
    }
    return -1;
}

/**
 * @brief   Set up the image sensor and start streaming
 * @retval  false if initialisation failed
 */
bool ei_camera_init(void)
{
    if (is_initialised) return true;

#if defined(CAMERA_MODEL_ESP_EYE)
    pinMode(13, INPUT_PULLUP);
    pinMode(14, INPUT_PULLUP);
#endif

    esp_err_t err = esp_camera_init(&camera_config);
    if (err != ESP_OK) {
        Serial.printf("VISION: camera init error 0x%x\n", err);
        return false;
    }

    sensor_t *s = esp_camera_sensor_get();
    // Some sensors start flipped vertically with saturated colours
    if (s->id.PID == OV3660_PID) {
        s->set_vflip(s, 1);
        s->set_brightness(s, 1);
        s->set_saturation(s, 0);
    }

#if defined(CAMERA_MODEL_ESP_EYE)
    s->set_vflip(s, 1);
    s->set_hmirror(s, 1);
    s->set_awb_gain(s, 1);
#endif

    is_initialised = true;
    return true;
}

/**
 * @brief   Stop streaming sensor data
 */
void ei_camera_deinit(void)
{
    if (esp_camera_deinit() != ESP_OK) {
        Serial.println("VISION: camera deinit failed");
        return;
    }
    is_initialised = false;
}

/**
 * @brief   Capture a frame, convert it to RGB888, then crop and scale it to
 *          the model input size
 * @retval  false if the camera is not ready or capture/conversion failed
 */
bool ei_camera_capture(uint32_t img_width, uint32_t img_height, uint8_t *out_buf)
{
    if (!is_initialised) {
        Serial.println("VISION: camera not initialised");
        return false;
    }

    camera_fb_t *fb = esp_camera_fb_get();
    if (!fb) {
        return false;
    }

    bool converted = fmt2rgb888(fb->buf, fb->len, PIXFORMAT_JPEG, snapshot_buf);
    esp_camera_fb_return(fb);

    if (!converted) {
        Serial.println("VISION: JPEG conversion failed");
        return false;
    }

    if ((img_width != EI_CAMERA_RAW_FRAME_BUFFER_COLS) ||
        (img_height != EI_CAMERA_RAW_FRAME_BUFFER_ROWS)) {
        ei::image::processing::crop_and_interpolate_rgb888(
            out_buf,
            EI_CAMERA_RAW_FRAME_BUFFER_COLS,
            EI_CAMERA_RAW_FRAME_BUFFER_ROWS,
            out_buf,
            img_width,
            img_height);
    }

    return true;
}

static int ei_camera_get_data(size_t offset, size_t length, float *out_ptr)
{
    // The buffer is RGB888, so turn the offset into a pixel index
    size_t pixel_ix = offset * 3;
    size_t pixels_left = length;
    size_t out_ptr_ix = 0;

    while (pixels_left != 0) {
        // Swap BGR to RGB (https://github.com/espressif/esp32-camera/issues/379)
        out_ptr[out_ptr_ix] = (snapshot_buf[pixel_ix + 2] << 16) +
                              (snapshot_buf[pixel_ix + 1] << 8) +
                              snapshot_buf[pixel_ix];
        out_ptr_ix++;
        pixel_ix += 3;
        pixels_left--;
    }
    return 0;
}
