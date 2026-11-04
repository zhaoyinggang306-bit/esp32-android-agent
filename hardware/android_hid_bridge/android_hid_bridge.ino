// ESP32 Android Agent: USB HID actuator for an Android phone.
// Derived from ESP32 iPhone Agent by Henry Yuan; see ../../NOTICE and LICENSE.
// Flash with ESP32 Arduino core 3.3.x and NimBLE-Arduino 2.5.x on a native-USB ESP32-S3.

#include "USB.h"
#include "USBHIDMouse.h"
#include "USBHIDKeyboard.h"
#include "USBHIDConsumerControl.h"
#include <NimBLEDevice.h>

#define UUID(s) "6e400001-a9e8-4b1b-9c39-6d6f8a3c" s
static const uint16_t ANDROID_HOME = 0x0223; // HID Consumer: AC Home; Android maps this to HOME.
static USBHIDMouse mouse;
static USBHIDKeyboard keyboard;
static USBHIDConsumerControl consumer;
static NimBLECharacteristic *replyChar, *statusChar;
static bool connected = false;

uint8_t keyCode(const String &key) {
  if (key == "enter") return KEY_RETURN;
  if (key == "back") return KEY_ESC; // Android maps Escape to Back on standard keyboards.
  if (key == "tab") return KEY_TAB;
  if (key == "backspace") return KEY_BACKSPACE;
  if (key == "space") return ' ';
  if (key == "up") return KEY_UP_ARROW;
  if (key == "down") return KEY_DOWN_ARROW;
  if (key == "left") return KEY_LEFT_ARROW;
  if (key == "right") return KEY_RIGHT_ARROW;
  if (key == "ctrl") return KEY_LEFT_CTRL;
  if (key == "alt") return KEY_LEFT_ALT;
  if (key == "shift") return KEY_LEFT_SHIFT;
  return key.length() == 1 && key[0] <= 0x7f ? key[0] : 0;
}

bool combo(String keys) {
  keys += '+';
  for (int pos; (pos = keys.indexOf('+')) >= 0; keys = keys.substring(pos + 1)) {
    uint8_t code = keyCode(keys.substring(0, pos));
    if (!code) { keyboard.releaseAll(); return false; }
    keyboard.press(code);
  }
  delay(30); keyboard.releaseAll(); return true;
}

String execute(const String &line) {
  const int sep = line.indexOf(' ');
  const String cmd = sep < 0 ? line : line.substring(0, sep);
  const String arg = sep < 0 ? "" : line.substring(sep + 1);
  long x = 0, y = 0; sscanf(arg.c_str(), "%ld %ld", &x, &y);
  if (cmd == "ping") return "ok";
  if (cmd == "move") { mouse.move(constrain(x, -127, 127), constrain(y, -127, 127)); return "ok"; }
  if (cmd == "press") { mouse.press(MOUSE_LEFT); return "ok"; }
  if (cmd == "release") { mouse.release(MOUSE_LEFT); return "ok"; }
  if (cmd == "click") { mouse.click(MOUSE_LEFT); return "ok"; }
  if (cmd == "scroll") { mouse.move(0, 0, constrain(x, -127, 127), constrain(y, -127, 127)); return "ok"; }
  if (cmd == "type") { for (size_t i = 0; i < arg.length(); ++i) if ((uint8_t)arg[i] > 0x7f) return "err ascii only"; keyboard.print(arg); return "ok"; }
  if (cmd == "key") return combo(arg) ? "ok" : "err unknown key";
  if (cmd == "homescreen") { consumer.press(ANDROID_HOME); delay(40); consumer.release(); return "ok"; }
  return "err unknown command";
}

class ServerCallbacks : public NimBLEServerCallbacks {
  void onConnect(NimBLEServer *, NimBLEConnInfo &) override { connected = true; }
  void onDisconnect(NimBLEServer *, NimBLEConnInfo &, int) override {
    connected = false; mouse.release(MOUSE_LEFT); keyboard.releaseAll(); NimBLEDevice::startAdvertising();
  }
};
class CommandCallbacks : public NimBLECharacteristicCallbacks {
  void onWrite(NimBLECharacteristic *characteristic, NimBLEConnInfo &) override {
    const NimBLEAttValue &raw = characteristic->getValue();
    String received(raw.c_str(), raw.length()); received.trim();
    int sep = received.indexOf(' '); String id = sep < 0 ? received : received.substring(0, sep);
    String action = sep < 0 ? "" : received.substring(sep + 1);
    if (!id.length() || !id.toInt() && id != "0") { replyChar->setValue("0 err missing request id"); }
    else replyChar->setValue((id + " " + execute(action)).c_str());
    replyChar->notify();
  }
};

void setup() {
  mouse.begin(); keyboard.begin(); consumer.begin();
  USB.productName("ESP32 Android Agent HID"); USB.begin();
  NimBLEDevice::init("ESP32 Android Agent"); NimBLEDevice::setMTU(247);
  NimBLEDevice::setSecurityAuth(true, false, true);
  NimBLEServer *server = NimBLEDevice::createServer(); server->setCallbacks(new ServerCallbacks());
  NimBLEService *service = server->createService(UUID("0000"));
  NimBLECharacteristic *command = service->createCharacteristic(UUID("0001"), NIMBLE_PROPERTY::WRITE | NIMBLE_PROPERTY::WRITE_NR);
  replyChar = service->createCharacteristic(UUID("0002"), NIMBLE_PROPERTY::NOTIFY);
  statusChar = service->createCharacteristic(UUID("0003"), NIMBLE_PROPERTY::NOTIFY | NIMBLE_PROPERTY::READ);
  command->setCallbacks(new CommandCallbacks()); service->start();
  NimBLEAdvertising *advertising = NimBLEDevice::getAdvertising(); advertising->addServiceUUID(UUID("0000")); advertising->start();
}

void loop() {
  static unsigned long previous = 0;
  if (connected && millis() - previous >= 1000) {
    previous = millis(); statusChar->setValue((String("{\"up\":") + millis() + ",\"usb\":\"ok\"}").c_str()); statusChar->notify();
  }
  delay(10);
}
