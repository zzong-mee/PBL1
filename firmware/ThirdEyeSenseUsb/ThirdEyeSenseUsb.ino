// Third Eye: Sense JPEG + two real VL53L8CX sensors over USB CDC.
// Arduino ESP32 3.x; XIAO_ESP32S3, USB CDC Enabled, USB Mode Hardware CDC/JTAG,
// PSRAM OPI, Core Debug Level None. No Serial text: all application output is framed.
#include <Arduino.h>
#include <Wire.h>
#include <vl53l8cx.h>
#include "esp_camera.h"
#include "GeyePacket.h"
#include "TofGridJson.h"

// Set false only to isolate the camera during diagnosis; distances then remain null.
constexpr bool ENABLE_TOF = true;
// esp32-camera normally owns controller 1. Avoid sharing its controller if a
// board package was configured to use controller 0 instead.
#if defined(CONFIG_SCCB_HARDWARE_I2C_PORT0) && CONFIG_SCCB_HARDWARE_I2C_PORT0
#define TOF_BUS Wire1
#else
#define TOF_BUS Wire
#endif
constexpr int LP_A = D2;
constexpr int LP_B = D3;
// Wire uses 7-bit addresses; ST's sensor API uses 8-bit addresses.
constexpr uint8_t ADDRESS_A = 0x2A;
constexpr uint8_t ADDRESS_B = 0x2B;
constexpr uint32_t FRAME_TIMEOUT_MS = 3000;
VL53L8CX sensorA(&TOF_BUS, LP_A);
VL53L8CX sensorB(&TOF_BUS, LP_B);
VL53L8CX_ResultsData resultsA, resultsB;
bool failed = false;
char tofError[160] = "";
bool freshA = false, freshB = false;
constexpr uint8_t tofResolution = VL53L8CX_RESOLUTION_8X8;
uint32_t lastA = 0, lastB = 0, lastPrint = 0;

bool ack(uint8_t address) {
  TOF_BUS.beginTransmission(address);
  return TOF_BUS.endTransmission() == 0;
}

bool fail(const char *name, const char *step, int status) {
  snprintf(tofError, sizeof(tofError), "%s/%s/status=%d", name, step, status);
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
  if (!check(name, "init", sensor.init())) return false;
  if (!check(name, "set_i2c_address", sensor.set_i2c_address(uint16_t(target) << 1))) return false;
  if (!ack(target) || (detected != target && ack(detected))) return fail(name, "address verification", -1);
  if (!check(name, "set_resolution", sensor.set_resolution(tofResolution))) return false;
  if (!check(name, "set_frequency 5Hz", sensor.set_ranging_frequency_hz(5))) return false;
  sensor.off();
  return true;
}

void setupTof() {
  pinMode(LP_A, OUTPUT); pinMode(LP_B, OUTPUT);
  digitalWrite(LP_A, LOW); digitalWrite(LP_B, LOW);
  TOF_BUS.begin(D4, D5);
  TOF_BUS.setClock(400000);
  TOF_BUS.setTimeOut(100);
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


uint32_t sequence = 0;
bool cameraReady = false;
const char *cameraError = "initialization_failed";
uint32_t lastFrameAt = 0, lastStatusAt = 0;

bool writeAll(const uint8_t *bytes, size_t size) {
  const uint32_t started = millis();
  while (size && Serial) {
    const int room = Serial.availableForWrite();
    if (room > 0) {
      const size_t requested = size < size_t(room) ? size : size_t(room);
      const size_t sent = Serial.write(bytes, requested);
      bytes += sent; size -= sent;
    }
    if (uint32_t(millis() - started) >= 1000) return false;
    if (size) delay(1);
  }
  return size == 0;
}
bool packet(uint8_t type, const uint8_t *data, size_t size) {
  return Serial && Geye::packet(type, data, size, sequence++, millis(), writeAll);
}
void jsonPacket(uint8_t type, const char *text) {
  packet(type, reinterpret_cast<const uint8_t *>(text), strlen(text));
}

void setupCamera() {
  // Official Arduino ESP32 CameraWebServer CAMERA_MODEL_XIAO_ESP32S3 mapping.
  // Camera SCCB: GPIO40/39; ToF Wire: D4/D5 (GPIO5/6), separate buses.
  camera_config_t c = {};
  c.ledc_channel=LEDC_CHANNEL_0; c.ledc_timer=LEDC_TIMER_0;
  c.pin_d0=15; c.pin_d1=17; c.pin_d2=18; c.pin_d3=16;
  c.pin_d4=14; c.pin_d5=12; c.pin_d6=11; c.pin_d7=48;
  c.pin_xclk=10; c.pin_pclk=13; c.pin_vsync=38; c.pin_href=47;
  c.pin_sccb_sda=40; c.pin_sccb_scl=39; c.pin_pwdn=-1; c.pin_reset=-1;
  c.xclk_freq_hz=20000000; c.pixel_format=PIXFORMAT_JPEG;
  c.frame_size=FRAMESIZE_QVGA; c.jpeg_quality=15; c.fb_count=1;
  c.fb_location=psramFound() ? CAMERA_FB_IN_PSRAM : CAMERA_FB_IN_DRAM;
  c.grab_mode=CAMERA_GRAB_WHEN_EMPTY;
  cameraReady = esp_camera_init(&c) == ESP_OK;
}

int minimum(const VL53L8CX_ResultsData &result) {
  int nearest = 4001;
  for (uint8_t zone = 0; zone < tofResolution; ++zone) {
    const uint8_t index = zone * VL53L8CX_NB_TARGET_PER_ZONE;
    const int distance = result.distance_mm[index];
    const uint8_t status = result.target_status[index];
    if (result.nb_target_detected[zone] && (status == 5 || status == 6 || status == 9)
        && distance > 0 && distance <= 4000 && distance < nearest) nearest = distance;
  }
  return nearest <= 4000 ? nearest : -1;
}
void sendDistances() {
  char left[12] = "null", right[12] = "null", json[4096];
  // Never turn a missing/stale/error sample into a real distance.
  if (ENABLE_TOF && !failed && freshA && uint32_t(millis()-lastA) <= 1000) {
    const int value = minimum(resultsA);
    if (value > 0) snprintf(left, sizeof(left), "%d", value);
  }
  if (ENABLE_TOF && !failed && freshB && uint32_t(millis()-lastB) <= 1000) {
    const int value = minimum(resultsB);
    if (value > 0) snprintf(right, sizeof(right), "%d", value);
  }
  const uint32_t now=millis();
  const bool validA=ENABLE_TOF && !failed && freshA && uint32_t(now-lastA)<=1000;
  const bool validB=ENABLE_TOF && !failed && freshB && uint32_t(now-lastB)<=1000;
  TofGridJson writer(json,sizeof(json));
  writer.append("{\"left_mm\":%s,\"right_mm\":%s,\"tof_error\":\"%s\",\"grid_size\":8,\"grid_order\":\"st_uld_raw\"",left,right,tofError);
  writer.append(",\"left_age_ms\":%lu,\"right_age_ms\":%lu",(unsigned long)(now-lastA),(unsigned long)(now-lastB));
  writer.grid("left",resultsA,validA,VL53L8CX_NB_TARGET_PER_ZONE);
  writer.grid("right",resultsB,validB,VL53L8CX_NB_TARGET_PER_ZONE);
  writer.append("}");
  if (writer.ok()) jsonPacket(1,json);
  else jsonPacket(3,"{\"tof\":\"grid_packet_too_large\"}");
}
void setup() {
  Serial.begin(115200);
  // Initialize camera first so sensor errors do not prevent camera use.
  setupCamera();
  if (ENABLE_TOF) setupTof();
  else {
    pinMode(LP_A, OUTPUT); pinMode(LP_B, OUTPUT);
    digitalWrite(LP_A, LOW); digitalWrite(LP_B, LOW);
  }
}
void loop() {
  if (ENABLE_TOF && !failed) {
    // Existing driver has internal retry loops; see README for I2C failure limits.
    if (poll(sensorA, "A", resultsA, freshA, lastA))
      poll(sensorB, "B", resultsB, freshB, lastB);
  }
  if (!Serial) { delay(10); return; }
  if (uint32_t(millis()-lastStatusAt) >= 500) {
    sendDistances();
    if (!cameraReady) {
      char json[80];
      snprintf(json, sizeof(json), "{\"camera\":\"%s\"}", cameraError);
      jsonPacket(3, json);
    }
    lastStatusAt = millis();
  }
  // 5 frame requests/second is a target, not a measured end-to-end rate.
  if (cameraReady && uint32_t(millis()-lastFrameAt) >= 200) {
    camera_fb_t *frame = esp_camera_fb_get();
    if (frame) {
      if (frame->format == PIXFORMAT_JPEG) packet(2, frame->buf, frame->len);
      else jsonPacket(3, "{\"camera\":\"capture_failed\"}");
      esp_camera_fb_return(frame);
    } else jsonPacket(3, "{\"camera\":\"capture_failed\"}");
    lastFrameAt = millis();
  }
  delay(5);
}
