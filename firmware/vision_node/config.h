// Vision node (ESP32-CAM) settings.
#pragma once

// ---- Camera board ----------------------------------------------------------
// Pick exactly one. The standard ESP32-CAM is the AI-Thinker board.
#define CAMERA_MODEL_AI_THINKER
// #define CAMERA_MODEL_ESP_EYE

// ---- Link to the cane controller (ESP32-S3) --------------------------------
// The ESP32-CAM talks to the S3 over its UART0 pins (U0TXD = GPIO1, U0RXD = GPIO3).
// That is the same port used for flashing, so unplug the S3 link while uploading.
#define LINK_BAUD 115200

// The line sent to the S3 when a sign is confirmed. Must match the S3 firmware.
#define DETECT_MESSAGE "detected"

// ---- Sign detection filter -------------------------------------------------
// Model class to watch. Must match a label in the Edge Impulse model exactly.
#define TARGET_LABEL "Track Sign"

// A frame counts as a hit when the target confidence is above this value.
#define CONFIDENCE_THRESHOLD 0.70f

// Hits needed in a row before a sign is confirmed and sent to the S3.
#define REQUIRED_CONSECUTIVE 3

// After a sign is confirmed, this many frames in a row must fall below the
// threshold before another sign can be confirmed. This stops one sign from
// being counted twice when the user pauses in front of it.
#define REARM_FRAMES 2

// Minimum time between two confirmed signs, as a second safety net.
#define COOLDOWN_MS 3000

// ---- Logging ---------------------------------------------------------------
// Print one status line per frame. The S3 relays these to its USB serial
// monitor, which is the easiest way to watch the vision node work.
#define VERBOSE_LOG 1
