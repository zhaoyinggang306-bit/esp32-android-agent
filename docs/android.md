# Android hardware control

This implementation adapts the ESP32 Agent architecture for Android. It does **not** use AirPlay: Android has no compatible AirPlay sender. The phone is both the Android controller app and the USB host.

```
ESP32 Android Agent app ── BLE GATT ──> ESP32-S3 ── USB HID ──> Android
          │                                      mouse / keyboard / Home
          └── MediaProjection (user grants screen capture) ──> agent vision layer
```

## Requirements

- Native-USB ESP32-S3 with a USB-C data cable (XIAO ESP32S3 recommended).
- Android 8+; Android 12+ is recommended for BLE permissions.
- The phone must support USB OTG/host mode. When plugged in, Android should show a physical mouse pointer or keyboard notification. Some phones need OTG enabled in Settings.

## Flash

Install ESP32 Arduino core 3.3.x and `NimBLE-Arduino@2.5.1`, then open `hardware/android_hid_bridge/android_hid_bridge.ino` in Arduino IDE. Select the correct ESP32-S3 board and USB mode **USB-OTG (TinyUSB)**, then upload.

The new firmware is intentionally separate from `hardware/hid_bridge`: the latter includes AirPlay and Wi-Fi functionality needed only for the iPhone product.

## Run the Android app

Open `android/` in Android Studio and install it. Plug the flashed board into the phone, open the app, grant Bluetooth permissions, press **连接 ESP32**, then grant screen capture when prompted. The test controls confirm the complete command chain:

- **测试点击** sends `click` over BLE and causes a physical HID click.
- **返回桌面** sends the consumer-control `AC Home` report.
- **输入文本** supports ASCII, matching the original HID protocol.

`ScreenCaptureService` is a foreground service and therefore shows a persistent Android system notification while screen access is active. The next layer may consume its frames for a visual agent; no screen pixels are sent to ESP32.

## Safety

Only automate a device and accounts you own or are authorized to operate. The OS screen-capture consent and the foreground notification deliberately keep capture visible to the device owner.
