# VL53L8CX Distance Grid

XIAO ESP32-S3 Sense용 단일 센서 테스트 스케치다.

## 배선

- 3V3 → VIN
- GND → GND
- D4 → SDA
- D5 → SCL
- D2 → 센서 A LP
- D3 → 센서 B LP (현재 LOW로 유지해 센서 B 비활성화)
- 센서의 SPI/I2C 핀 → 해당 센서 GND

현재 스케치는 센서 A만 활성화한다. 시작은 4×4이며 Serial Monitor에서 `r`을 입력하면 8×8로 전환한다. Serial Monitor 속도는 115200이다.
