// Third Eye: two Pololu VL53L8CX carriers on XIAO ESP32-S3 Sense.
// Requires STM32duino VL53L8CX 2.1.0. Serial Monitor only; no Android JSON yet.
#include <Wire.h>
#include <vl53l8cx.h>

constexpr int LP_A = D2;
constexpr int LP_B = D3;
// Wire uses 7-bit addresses; ST's sensor API uses 8-bit addresses.
constexpr uint8_t ADDRESS_A = 0x2A;
constexpr uint8_t ADDRESS_B = 0x2B;
constexpr uint32_t FRAME_TIMEOUT_MS = 3000;
VL53L8CX sensorA(&Wire, LP_A);
VL53L8CX sensorB(&Wire, LP_B);
VL53L8CX_ResultsData resultsA, resultsB;
bool failed = false;
bool freshA = false, freshB = false;
uint8_t resolution = VL53L8CX_RESOLUTION_4X4;
uint32_t lastA = 0, lastB = 0, lastPrint = 0;

bool ack(uint8_t address) {
  Wire.beginTransmission(address);
  return Wire.endTransmission() == 0;
}

bool fail(const char *name, const char *step, int status) {
  Serial.print("ERROR "); Serial.print(name); Serial.print(" / ");
  Serial.print(step); Serial.print(" / status="); Serial.println(status);
  Serial.println("Both sensors disabled. Check wiring with USB unplugged, then reconnect USB.");
  digitalWrite(LP_A, LOW);
  digitalWrite(LP_B, LOW);
  failed = true;
  freshA = freshB = false;
  return false;
}

bool check(const char *name, const char *step, int status) {
  return status == 0 || fail(name, step, status);
}

// Call only while the OTHER sensor's LP is LOW. LP gates I2C, but is not
// a power reset: an ESP32-only reset can leave the sensor's old address intact.
bool prepare(VL53L8CX &sensor, const char *name, uint8_t target) {
  if (!check(name, "begin", sensor.begin())) return false;
  delay(20);
  uint8_t detected = 0;
  int count = 0;
  const uint8_t candidates[] = {0x29, ADDRESS_A, ADDRESS_B};
  for (uint8_t address : candidates) {
    if (ack(address)) { detected = address; ++count; }
  }
  if (count != 1) return fail(name, "expected exactly one isolated sensor at 0x29/0x2A/0x2B", count);
  // begin selects the driver's current address; set_i2c_address writes hardware.
  if (!check(name, "select current address", sensor.begin(uint16_t(detected) << 1))) return false;
  delay(20);
  uint8_t alive = 0;
  if (!check(name, "is_alive", sensor.is_alive(&alive))) return false;
  if (!alive) return fail(name, "unexpected sensor identity", alive);
  Serial.print("Initializing sensor "); Serial.println(name);
  if (!check(name, "init", sensor.init())) return false;
  if (!check(name, "set_i2c_address", sensor.set_i2c_address(uint16_t(target) << 1))) return false;
  if (!ack(target) || (detected != target && ack(detected))) return fail(name, "address verification", -1);
  if (!check(name, "set_resolution", sensor.set_resolution(resolution))) return false;
  if (!check(name, "set_frequency 5Hz", sensor.set_ranging_frequency_hz(5))) return false;
  Serial.print("Sensor "); Serial.print(name); Serial.print(" assigned 7-bit 0x"); Serial.println(target, HEX);
  sensor.off();
  return true;
}

void setup() {
  pinMode(LP_A, OUTPUT); pinMode(LP_B, OUTPUT);
  digitalWrite(LP_A, LOW); digitalWrite(LP_B, LOW);
  Serial.begin(115200);
  // Bounded wait: the firmware also starts when Serial Monitor is closed.
  const uint32_t started = millis();
  while (!Serial && uint32_t(millis() - started) < 3000) delay(10);
  Serial.println("Third Eye dual ToF: A=D2, B=D3; SDA=D4, SCL=D5");
  Wire.begin(D4, D5);
  Wire.setClock(400000);
  Wire.setTimeOut(100);
  delay(20);
  if (!prepare(sensorA, "A", ADDRESS_A)) return;
  if (!prepare(sensorB, "B", ADDRESS_B)) return;
  sensorA.on(); sensorB.on();
  if (!ack(ADDRESS_A) || !ack(ADDRESS_B) || ack(0x29)) {
    fail("A+B", "final address verification", -1); return;
  }
  if (!check("A", "start_ranging", sensorA.start_ranging())) return;
  if (!check("B", "start_ranging", sensorB.start_ranging())) return;
  lastA = lastB = lastPrint = millis();
  Serial.println("READY: A=0x2A, B=0x2B (7-bit). 4x4, 5Hz each. Send r to toggle 8x8.");
}

bool poll(VL53L8CX &sensor, const char *name, VL53L8CX_ResultsData &result,
          bool &fresh, uint32_t &last) {
  uint8_t ready = 0;
  if (!check(name, "check_data_ready", sensor.check_data_ready(&ready))) return false;
  if (ready) {
    if (!check(name, "get_ranging_data", sensor.get_ranging_data(&result))) return false;
    fresh = true; last = millis();
  } else if (uint32_t(millis() - last) > FRAME_TIMEOUT_MS) {
    return fail(name, "no frame for 3 seconds", -1);
  }
  return true;
}

void printGrid(const char *name, const VL53L8CX_ResultsData &result) {
  const uint8_t side = resolution == VL53L8CX_RESOLUTION_8X8 ? 8 : 4;
  Serial.print("Sensor "); Serial.print(name); Serial.println(" Distance [mm]");
  for (uint8_t row = 0; row < side; ++row) {
    for (uint8_t col = 0; col < side; ++col) {
      const uint8_t zone = row * side + col;
      const uint8_t index = zone * VL53L8CX_NB_TARGET_PER_ZONE;
      const int distance = result.distance_mm[index];
      const uint8_t status = result.target_status[index];
      // ST valid target statuses: 5, 6, 9. Never substitute a fake distance.
      if (result.nb_target_detected[zone] && (status == 5 || status == 6 || status == 9)
          && distance > 0 && distance <= 4000) Serial.print(distance);
      else Serial.print('-');
      Serial.print(col + 1 == side ? '\n' : '\t');
    }
  }
}

void toggleResolution() {
  if (!check("A", "stop_ranging", sensorA.stop_ranging())) return;
  if (!check("B", "stop_ranging", sensorB.stop_ranging())) return;
  resolution = resolution == VL53L8CX_RESOLUTION_4X4 ? VL53L8CX_RESOLUTION_8X8 : VL53L8CX_RESOLUTION_4X4;
  if (!check("A", "set_resolution", sensorA.set_resolution(resolution))) return;
  if (!check("B", "set_resolution", sensorB.set_resolution(resolution))) return;
  if (!check("A", "set_frequency", sensorA.set_ranging_frequency_hz(5))) return;
  if (!check("B", "set_frequency", sensorB.set_ranging_frequency_hz(5))) return;
  if (!check("A", "start_ranging", sensorA.start_ranging())) return;
  if (!check("B", "start_ranging", sensorB.start_ranging())) return;
  freshA = freshB = false;
  lastA = lastB = lastPrint = millis();
  Serial.println(resolution == VL53L8CX_RESOLUTION_8X8 ? "Mode: 8x8" : "Mode: 4x4");
}

void loop() {
  if (failed) { delay(100); return; }
  if (Serial.available() && Serial.read() == 'r') {
    toggleResolution();
    if (failed) return;
  }
  if (!poll(sensorA, "A", resultsA, freshA, lastA)) return;
  if (!poll(sensorB, "B", resultsB, freshB, lastB)) return;
  if (freshA && freshB && uint32_t(millis() - lastPrint) >= 500) {
    printGrid("A", resultsA); printGrid("B", resultsB);
    Serial.println("--- r: toggle 4x4 / 8x8 ---");
    freshA = freshB = false; lastPrint = millis();
  }
  delay(5);
}
