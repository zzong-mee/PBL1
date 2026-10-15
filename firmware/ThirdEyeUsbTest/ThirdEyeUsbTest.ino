// XIAO_ESP32S3; USB CDC On Boot = Enabled.
// Transport test only: these are NOT readings from physical sensors.
void setup() {
  Serial.begin(115200);
}

void loop() {
  Serial.println(R"({"left_mm":500,"right_mm":1200})");
  delay(1000);
}
