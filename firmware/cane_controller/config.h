// Cane controller (ESP32-S3) settings. Wi-Fi details live in secrets.h.
#pragma once

// ---- Link to the vision node (ESP32-CAM) -----------------------------------
// S3 RX <- CAM TX (GPIO1), S3 TX -> CAM RX (GPIO3), plus a shared GND.
#define CAM_RX_PIN     8
#define CAM_TX_PIN     9
#define CAM_LINK_BAUD  115200

// The line the vision node sends when it confirms a sign. Must match the CAM firmware.
#define CAM_DETECT_MESSAGE "detected"

// Relay the vision node's status lines to the USB serial monitor.
#define LOG_CAM_LINES 1

// ---- Network ---------------------------------------------------------------
#define APP_SIGN_PORT    4210     // app listens here for sign events
#define AUDIO_RX_PORT    4212     // cane listens here for guidance audio
#define APP_SIGN_MESSAGE "track"  // payload the app's WifiUdpService expects

// ---- Speaker (I2S out to MAX98357A) ----------------------------------------
#define I2S_BCLK  10
#define I2S_LRC   11
#define I2S_DIN   12

// Incoming audio is raw 16-bit mono PCM. If the voice plays too slow and deep
// or too fast and high, match this to the sender (16000, 24000 or 48000).
#define AUDIO_SAMPLE_RATE 24000

// ---- Obstacle sensing ------------------------------------------------------
#define TRIG_PIN       16
#define ECHO_PIN        5
#define HAPTIC_LEFT     6
#define HAPTIC_RIGHT    7
#define HAPTIC_BOTTOM  15

#define ULTRASONIC_PERIOD_MS  60     // time between distance readings
#define ULTRASONIC_TIMEOUT_US 25000  // ~4.3 m round trip

// Distance zones in cm. Closer zones pulse faster.
#define ZONE_IMPACT_CM   15   // below this: all motors on continuously
#define ZONE_DANGER_CM   40   // 150 ms pulse
#define ZONE_WARNING_CM  80   // 400 ms pulse
#define ZONE_AWARE_CM   150   // 1000 ms pulse; beyond this: no vibration
#define ZONE_MAX_CM     450   // readings past this are ignored

#define PULSE_DANGER_MS   150
#define PULSE_WARNING_MS  400
#define PULSE_AWARE_MS   1000
