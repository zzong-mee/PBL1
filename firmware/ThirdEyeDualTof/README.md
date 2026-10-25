# Third Eye: VL53L8CX 2개 실측 테스트

`feature/real-sensor-test-camera-base`에서 분기한 `feature/dual-tof-sensors`용이다.
기존 `VL53L8CX_DistanceGrid_XIAO` 단일 센서 스케치는 그대로 유지한다.
이 새 스케치는 부팅 시 각 센서의 주소를 분리하고, **PC 시리얼 모니터에 두 센서의 거리 격자**를 출력한다.
Android JSON 송신, 카메라 입력, 위험도 판단 및 공간음향은 이 스케치에 포함하지 않는다.

## 배선 (기존 Y형 점퍼선 유지)

전원을 분리하고 실물 핀 라벨로 확인한다.

| XIAO ESP32-S3 Sense | 센서 A | 센서 B |
|---|---|---|
| 3V3 (Y선) | VIN | VIN |
| GND (Y선) | GND | GND |
| D4 (Y선) | SDA | SDA |
| D5 (Y선) | SCL | SCL |
| D2 | LP | 연결하지 않음 |
| D3 | 연결하지 않음 | LP |

각 센서의 **SPI/I2C 핀을 자기 보드의 GND에 연결**한다. 두 LP를 Y선으로 묶지 않는다.
A/B는 LP 배선으로 정해지는 이름이며 장착 방향이 확정된 좌/우를 의미하지 않는다.

## Windows Arduino IDE 업로드

1. 이 브랜치 ZIP을 내려받고 압축을 푼다.
2. `firmware/ThirdEyeDualTof/ThirdEyeDualTof.ino`를 연다. 이전 단일 센서 파일과 구분한다.
3. 보드 매니저의 **esp32 by Espressif Systems**와 라이브러리 매니저의
   **STM32duino VL53L8CX 2.1.0**을 준비한다.
4. 보드는 **XIAO_ESP32S3**, **USB CDC On Boot: Enabled**, 포트는 연결된 **COM**으로 선택한다.
5. Upload(→)를 누르고 완료를 확인한다.
6. Serial Monitor를 **115200 baud**로 연다. 부팅 메시지를 놓쳤다면 Reset을 한 번 누른다.
7. 센서 초기화에 잠시 기다린 뒤 아래 메시지와 A/B 두 격자가 나오는지 확인한다.

```text
Initializing sensor A
Sensor A assigned 7-bit 0x2A
Initializing sensor B
Sensor B assigned 7-bit 0x2B
READY: A=0x2A, B=0x2B (7-bit). 4x4, 5Hz each. Send r to toggle 8x8.
Sensor A Distance [mm]
...
Sensor B Distance [mm]
...
```

위는 출력 형식 예시이며 실측 성공 로그가 아니다. 데이터는 mm이고 `-`는 유효 표적이 없다는 뜻이다.
ST 상태 5/6/9이며 표적 수가 1 이상인 1~4000mm만 표시한다. `-`를 안전/장애물 없음으로 해석하지 않는다.
각 센서는 5Hz로 측정하며 두 센서의 새 데이터가 있을 때 약 0.5초 간격으로 표시한다.
두 측정은 하드웨어 동기화된 것이 아니다. `r`을 보내면 **두 센서 모두** 4×4 ↔ 8×8로 바뀐다.

## 주소 분리 방식

- Wire의 7비트 주소: 기본 `0x29`, A `0x2A`, B `0x2B`.
- STM32duino API의 8비트 주소: 기본 `0x52`, A `0x54`, B `0x56`.
- `begin(address)`는 드라이버가 사용할 주소를 선택할 뿐이다. 하드웨어 주소는 `set_i2c_address()`로 쓴다.
- 두 LP를 LOW로 둔 후 A만 켜서 현재 주소를 확인하고 초기화·주소 설정한다. A를 잠시 비활성화하고
  B도 독립적으로 초기화·주소 설정한 뒤 두 LP를 모두 HIGH로 두고 측정한다.
- LP LOW는 I²C 접근을 차단하는 것이며 전원 리셋이 아니다. ESP32 Reset 때 주소가 남는 경우를 위해
  각 센서를 격리한 상태에서 `0x29/0x2A/0x2B`를 탐색한다. LP를 다시 켤 때 주소를 재설정하지 않는다.
- 완전한 전원 재연결과 ESP32 Reset을 각각 실물에서 확인해야 한다.

## 실제 확인 순서

1. A 정면의 평평한 판/벽을 30cm, 50cm, 1m로 옮겨 A 격자의 변화와 실제 자로 잰 거리를 비교한다.
2. B도 별도로 같은 확인을 한다. 서로 다른 방향을 향하게 해 A/B가 각각 반응하는지 확인한다.
3. `r` 전송 후 두 격자가 8×8로 바뀌는지 확인한다.
4. Reset 후 두 주소와 두 격자가 복구되는지 확인한다.
5. USB 전원을 뺐다 다시 연결하고 동일하게 확인한다.

오류면 `ERROR A/B / 단계 / status=...`를 기록한다. 오류 시 두 LP를 LOW로 내려 이후 출력을 중지한다.
3초간 새 프레임이 없으면 오류로 처리하며 이전 값을 계속 표시하지 않는다.
배선 변경은 모든 전원을 끄고 한다. 이 코드가 사용하는 라이브러리의 `IO_Read()`에는 I²C 전송을
성공할 때까지 반복하는 내부 루프가 있어, **통신 자체가 멈추면 위 프레임 타임아웃까지 도달하지
못할 수 있다.** 이때는 전원을 분리해 배선을 확인하고 재연결한다. 실행 중 센서 분리 내성을 보장하지 않는다.

## 자동 검증과 한계

호스트 제어 흐름 테스트:

```sh
g++ -std=c++17 -Wall -Wextra -Werror \
  -Ifirmware/ThirdEyeDualTof/tests/fakes \
  firmware/ThirdEyeDualTof/tests/test_dual.cpp -o /tmp/test-dual-tof
for scenario in cold warm swapped missingB initError timeout readError; do
  /tmp/test-dual-tof "$scenario" || exit 1
done
```

7개 시나리오 통과: 초기 주소, Reset 후 유지 주소, A/B 주소 교환, B 누락, 초기화 오류,
프레임 타임아웃, 읽기 오류. 정상 경로에서 두 격자 출력, 해상도 왕복 전환, 잘못된 표적 상태 제외도 확인한다.
실제 STM32duino VL53L8CX 2.1.0 헤더를 사용한 호스트 문법/API 검사도 통과했다(Arduino/Wire/SPI는 대역).
이 테스트는 센서 응답을 모사하므로 실제 I²C 통신·주소 유지·센서 정확도를 증명하지 않는다.
클라우드에서는 `downloads.arduino.cc` CONNECT 403으로 도구 설치가 차단되어
**ESP32용 전체 컴파일·업로드 및 실기기 측정은 미검증**이다. Windows Arduino IDE의 컴파일 결과와
실제 측정 결과를 별도로 확인해야 한다.
