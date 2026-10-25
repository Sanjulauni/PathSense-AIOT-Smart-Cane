/*
 * SmartWhiteCane - Cane controller (ESP32-S3)
 *
 * Core 1 (loop):  ultrasonic ranging -> haptic pulses, and the UART link to
 *                 the vision node. When the vision node reports a confirmed
 *                 sign, a UDP "track" event is sent to the phone app.
 * Core 0 (task):  receives guidance audio from the app over UDP and plays it
 *                 on the I2S speaker.
 *
 * Obstacle feedback never waits on Wi-Fi: the cane warns the user from the
 * moment it boots, connected or not.
 */

#include <WiFi.h>
#include <WiFiUdp.h>
#include <driver/i2s_std.h>
#include <string.h>
#include <strings.h>

#include "config.h"

#if __has_include("secrets.h")
#include "secrets.h"
#else
#error "Copy secrets.example.h to secrets.h and fill in your Wi-Fi details."
#endif

HardwareSerial &CamLink = Serial1;

WiFiUDP signUdp;
WiFiUDP audioUdp;

i2s_chan_handle_t tx_chan = nullptr;
TaskHandle_t audioTaskHandle = nullptr;

// ==========================================================================
// Obstacle sensing and haptics
// ==========================================================================

enum DistanceZone { ZONE_SAFE, ZONE_AWARE, ZONE_WARNING, ZONE_DANGER, ZONE_IMPACT, ZONE_INVALID };

DistanceZone currentZone = ZONE_INVALID;
unsigned long lastUltrasonicRead = 0;

DistanceZone getDistanceZone(unsigned int cm) {
  if (cm == 0 || cm > ZONE_MAX_CM) return ZONE_INVALID;
  if (cm < ZONE_IMPACT_CM)         return ZONE_IMPACT;
  if (cm < ZONE_DANGER_CM)         return ZONE_DANGER;
  if (cm < ZONE_WARNING_CM)        return ZONE_WARNING;
  if (cm < ZONE_AWARE_CM)          return ZONE_AWARE;
  return ZONE_SAFE;
}

// Returns the distance in cm, or 0 if no echo came back in time.
unsigned int readUltrasonicCM() {
  digitalWrite(TRIG_PIN, LOW);
  delayMicroseconds(2);
  digitalWrite(TRIG_PIN, HIGH);
  delayMicroseconds(10);
  digitalWrite(TRIG_PIN, LOW);

  unsigned long duration = pulseIn(ECHO_PIN, HIGH, ULTRASONIC_TIMEOUT_US);
  return duration / 58;
}

void setHaptics(bool on) {
  digitalWrite(HAPTIC_LEFT,   on ? HIGH : LOW);
  digitalWrite(HAPTIC_RIGHT,  on ? HIGH : LOW);
  digitalWrite(HAPTIC_BOTTOM, on ? HIGH : LOW);
}

void updateHaptics() {
  static unsigned long lastToggle = 0;
  static bool pulseOn = false;
  unsigned long interval;

  switch (currentZone) {
    case ZONE_AWARE:   interval = PULSE_AWARE_MS;   break;
    case ZONE_WARNING: interval = PULSE_WARNING_MS; break;
    case ZONE_DANGER:  interval = PULSE_DANGER_MS;  break;
    case ZONE_IMPACT:  setHaptics(true);  return;
    default:           setHaptics(false); return;  // safe or no reading
  }

  unsigned long now = millis();
  if (now - lastToggle >= interval) {
    lastToggle = now;
    pulseOn = !pulseOn;
    setHaptics(pulseOn);
  }
}

// ==========================================================================
// Vision node link
// ==========================================================================

void notifyAppOfSign() {
  if (WiFi.status() != WL_CONNECTED) {
    Serial.println("[SIGN] Detected, but Wi-Fi is down. Event not sent.");
    return;
  }
  signUdp.beginPacket(APP_IP, APP_SIGN_PORT);
  signUdp.print(APP_SIGN_MESSAGE);
  bool ok = signUdp.endPacket();
  Serial.printf("[SIGN] Detected -> sent '%s' to %s:%d %s\n",
                APP_SIGN_MESSAGE, APP_IP, APP_SIGN_PORT, ok ? "" : "(send failed)");
}

void handleCamLine(char *line) {
  // Trim surrounding whitespace
  while (*line == ' ' || *line == '\t') line++;
  size_t len = strlen(line);
  while (len > 0 && (line[len - 1] == ' ' || line[len - 1] == '\t')) line[--len] = '\0';
  if (len == 0) return;

  if (strcasecmp(line, CAM_DETECT_MESSAGE) == 0) {
    notifyAppOfSign();
    return;
  }

#if LOG_CAM_LINES
  Serial.print("[CAM] ");
  Serial.println(line);
#endif
}

// Reads whatever bytes are waiting without blocking the haptic loop.
void pollCamLink() {
  static char line[128];
  static size_t len = 0;
  static bool overflow = false;

  while (CamLink.available()) {
    char c = (char)CamLink.read();
    if (c == '\r') continue;
    if (c == '\n') {
      line[len] = '\0';
      if (!overflow) handleCamLine(line);
      len = 0;
      overflow = false;
    } else if (len < sizeof(line) - 1) {
      line[len++] = c;
    } else {
      overflow = true;  // too long to be a command; drop the rest of the line
    }
  }
}

// ==========================================================================
// Wi-Fi and audio
// ==========================================================================

void reportWiFiChanges() {
  static wl_status_t lastStatus = WL_IDLE_STATUS;
  wl_status_t status = WiFi.status();
  if (status == lastStatus) return;

  if (status == WL_CONNECTED) {
    Serial.printf("[WIFI] Connected, IP %s\n", WiFi.localIP().toString().c_str());
  } else if (lastStatus == WL_CONNECTED) {
    Serial.println("[WIFI] Connection lost, reconnecting...");
  }
  lastStatus = status;
}

bool setupSpeaker() {
  i2s_chan_config_t chan_cfg = I2S_CHANNEL_DEFAULT_CONFIG(I2S_NUM_AUTO, I2S_ROLE_MASTER);
  chan_cfg.dma_desc_num = 16;
  chan_cfg.dma_frame_num = 1024;
  chan_cfg.auto_clear = true;

  if (i2s_new_channel(&chan_cfg, &tx_chan, NULL) != ESP_OK) return false;

  i2s_std_config_t std_cfg = {
      .clk_cfg  = I2S_STD_CLK_DEFAULT_CONFIG(AUDIO_SAMPLE_RATE),
      .slot_cfg = I2S_STD_PHILIPS_SLOT_DEFAULT_CONFIG(I2S_DATA_BIT_WIDTH_16BIT, I2S_SLOT_MODE_MONO),
      .gpio_cfg = {
          .mclk = I2S_GPIO_UNUSED,
          .bclk = (gpio_num_t)I2S_BCLK,
          .ws   = (gpio_num_t)I2S_LRC,
          .dout = (gpio_num_t)I2S_DIN,
          .din  = I2S_GPIO_UNUSED,
          .invert_flags = {.mclk_inv = false, .bclk_inv = false, .ws_inv = false},
      },
  };

  if (i2s_channel_init_std_mode(tx_chan, &std_cfg) != ESP_OK) return false;
  return i2s_channel_enable(tx_chan) == ESP_OK;
}

// Core 0: play raw PCM packets from the app as they arrive.
void audioTask(void *) {
  static uint8_t buffer[2048];

  while (WiFi.status() != WL_CONNECTED) vTaskDelay(pdMS_TO_TICKS(200));
  audioUdp.begin(AUDIO_RX_PORT);
  Serial.printf("[AUDIO] Listening on UDP %d\n", AUDIO_RX_PORT);

  for (;;) {
    int packetSize = audioUdp.parsePacket();
    if (packetSize > 0) {
      int bytesRead = audioUdp.read(buffer, sizeof(buffer));
      if (bytesRead > 0) {
        size_t written = 0;
        i2s_channel_write(tx_chan, buffer, bytesRead, &written, portMAX_DELAY);
      }
    } else {
      // Only yield when idle, so a steady audio stream is not slowed down
      vTaskDelay(1);
    }
  }
}

// ==========================================================================
// Setup and main loop
// ==========================================================================

void setup() {
  Serial.begin(115200);
  CamLink.begin(CAM_LINK_BAUD, SERIAL_8N1, CAM_RX_PIN, CAM_TX_PIN);

  Serial.println("\n=============================================");
  Serial.println(" SmartWhiteCane - cane controller (ESP32-S3)");
  Serial.println("=============================================");

  pinMode(HAPTIC_LEFT, OUTPUT);
  pinMode(HAPTIC_RIGHT, OUTPUT);
  pinMode(HAPTIC_BOTTOM, OUTPUT);
  setHaptics(false);

  pinMode(TRIG_PIN, OUTPUT);
  pinMode(ECHO_PIN, INPUT);

  WiFi.mode(WIFI_STA);
  WiFi.setAutoReconnect(true);
  WiFi.begin(WIFI_SSID, WIFI_PASSWORD);
  Serial.printf("[WIFI] Connecting to \"%s\" in the background\n", WIFI_SSID);

  if (setupSpeaker()) {
    xTaskCreatePinnedToCore(audioTask, "audio", 10000, NULL, 1, &audioTaskHandle, 0);
  } else {
    Serial.println("[AUDIO] I2S speaker init failed. Audio disabled.");
  }

  Serial.println("[SYS] Obstacle sensing active. Waiting for vision node...");
}

void loop() {
  if (millis() - lastUltrasonicRead >= ULTRASONIC_PERIOD_MS) {
    lastUltrasonicRead = millis();
    currentZone = getDistanceZone(readUltrasonicCM());
  }
  updateHaptics();

  pollCamLink();
  reportWiFiChanges();
}
