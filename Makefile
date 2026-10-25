# SmartWhiteCane build helpers (needs arduino-cli with the esp32 core installed)
#
#   make firmware                       build both boards
#   make upload-cane   PORT=COM5        flash the ESP32-S3 cane controller
#   make upload-vision PORT=COM6        flash the ESP32-CAM vision node
#   make app                            build the Android debug APK

ARDUINO_CLI ?= arduino-cli
PORT        ?=
CANE_FQBN   ?= esp32:esp32:esp32s3
VISION_FQBN ?= esp32:esp32:esp32cam:PartitionScheme=huge_app
BUILD_DIR   ?= build

.PHONY: firmware cane vision upload-cane upload-vision app clean

firmware: cane vision

cane:
	$(ARDUINO_CLI) compile -b $(CANE_FQBN) --build-path $(BUILD_DIR)/cane firmware/cane_controller

vision:
	$(ARDUINO_CLI) compile -b $(VISION_FQBN) --build-path $(BUILD_DIR)/vision firmware/vision_node

upload-cane: cane
	$(ARDUINO_CLI) upload -b $(CANE_FQBN) -p $(PORT) --input-dir $(BUILD_DIR)/cane firmware/cane_controller

upload-vision: vision
	$(ARDUINO_CLI) upload -b $(VISION_FQBN) -p $(PORT) --input-dir $(BUILD_DIR)/vision firmware/vision_node

app:
	cd android && ./gradlew assembleDebug

clean:
	rm -rf $(BUILD_DIR)
