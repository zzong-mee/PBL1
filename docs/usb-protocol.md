# Third Eye USB 연결 설계 (후속 단계, 아직 구현되지 않음)

## 확정된 구성

Galaxy A34(Android 16)가 USB Host, XIAO ESP32-S3 Sense가 USB Device이다.
ESP32-S3가 카메라 영상과 I²C 연결된 VL53L8CX 두 개의 측정값을 수집하고,
스마트폰이 YOLO 추론, 위험도 분석 및 한국어 TTS를 처리한다. Wi-Fi/Bluetooth는 사용하지 않는다.
현재 앱은 Mock만 사용하며 USB 권한 요청이나 실제 수신 기능을 제공하지 않는다.

## 단계별 구현

1. 펌웨어의 USB VID/PID, interface/endpoint descriptor와 CDC ACM 지원 여부를 실제 장치에서 확인한다.
   USB Serial/JTAG 디버깅 포트와 TinyUSB CDC 데이터 포트는 같다고 가정하지 않는다.
   Android `UsbManager`, `UsbDeviceConnection`을 통해 열거·권한 요청·interface claim·endpoint I/O를 구현한다.
   CDC ACM으로 확인되면 검증된 USB serial 드라이버 사용을 검토한다. 라이브러리 채택 시 이유와 버전을 기록한다.
2. 연결 이벤트는 foreground에서 다시 열거하고 권한을 확인한다. `requestPermission` 응답의 장치와 허용 결과를
   확인한다. Android 16 대상 PendingIntent/receiver 정책을 준수하고, 앱 범위 permission action을 사용한다.
   거절은 상태 UI와 사용자 재시도로 처리하고 무한 재요청하지 않는다.
3. 읽기는 작업 스레드에서 수행하고 종료·분리 시 read 취소, interface release, connection close한다.
   시간 초과, 손상 프레임, 장치 해제는 서로 다른 상태로 보고한다. 마지막 수신 시각을 관리하여
   오래된 측정값은 '수신 중단'으로 표시한다. 오류 시 Mock으로 몰래 전환하지 않는다.
4. 먼저 거리 패킷 수신을 실제 두 센서와 검증한다. 다음으로 JPEG 프레임을 제한된 속도로 전송하여
   메모리, 전송률, 추론 지연, 센서 패킷 지연을 측정한 뒤 카메라 수신을 구현한다.

## 통신 프로토콜 v1 제안

다음은 펌웨어와 함께 확정해야 하는 제안이며, 이미 지원되는 규격이라는 의미가 아니다.
USB read의 경계는 패킷 경계가 아니므로 분할·병합된 바이트를 누적 파싱한다.

| 필드 | 크기 | 의미 |
|---|---:|---|
| Magic | 4 | ASCII `GEYE` |
| Version | 1 | 1 |
| Type | 1 | 1=거리, 2=JPEG, 3=상태 |
| Payload length | 4 | unsigned little-endian, payload 바이트 수 |
| Sequence | 4 | unsigned little-endian, 타입별 증가, wrap 허용 |
| Capture timestamp | 4 | ESP32 uptime ms, little-endian, wrap 허용 |
| Payload | 가변 | 타입별 내용 |
| CRC32 | 4 | Version부터 Payload 끝까지 CRC-32/ISO-HDLC, little-endian |

길이는 메모리 할당 전에 검사한다. JPEG 최대 256 KiB, 거리 최대 4 KiB,
상태 최대 1 KiB를 초기 제한으로 두고 실제 영상 해상도에 맞춰 협의한다.
CRC 오류·지원하지 않는 버전·허용 범위 밖 길이는 폐기하고 다음 Magic으로 재동기화한다.
Magic이 JPEG 내부에도 나타날 수 있으므로 정상 프레임은 길이와 CRC로 읽는다.
미완성 프레임 시간 제한 및 전체 버퍼 상한도 둔다.

거리 payload는 UTF-8 JSON을 제안한다:

```json
{"left":{"mm":500,"valid":true},"right":{"mm":1200,"valid":true}}
```

이는 초기 단일 대표 거리용이다. VL53L8CX의 다중 zone 데이터는 별도 버전/스키마 확장으로 전달한다.
펌웨어는 유효한 전방 zone의 최소 거리 등 대표값 선정 규칙을 명시해야 한다.
잘못된 거리나 측정 실패는 `{"mm":null,"valid":false}`로 전달하며 0m로 바꾸지 않는다.
JPEG payload는 인코딩된 JPEG 한 장이다. 센서 데이터를 우선 전송하고 대기 중인 오래된 영상은 폐기하여
무한 큐를 만들지 않는다. 타임스탬프는 스마트폰 시계와 바로 비교하지 않는다.

## 하드웨어 검증 필요 항목

- ESP32-S3 native USB/보드 커넥터/펌웨어 설정에서 CDC와 카메라의 동시 사용 가능 여부.
  ESP32-S3 Sense를 기본 UVC 웹캠으로 간주하지 않는다. JPEG over CDC는 커스텀 펌웨어 검증 대상이다.
- 전원 및 데이터 전송을 지원하는 USB-C 케이블, Galaxy의 Host 인식과 전원 공급 용량.
- 두 VL53L8CX의 동일 기본 I²C 주소 충돌 해결(센서 enable/address 설정 또는 multiplexer),
  보드 핀 중복, 전압 및 전체 전류 예산. 전원을 별도로 넣을 경우 역급전 방지 설계.
- USB 장치가 연결된 상태와 PC USB 디버깅이 동시에 필요하면 무선 ADB 등 별도 개발 경로 검토.
  이는 앱 데이터 전송이 Wi-Fi라는 의미가 아니다.

CameraX는 추후 스마트폰 내장 카메라를 사용할 때의 별도 입력 경로다.
USB로 받은 JPEG는 직접 디코딩하여 공통 추론 입력으로 연결하며 CameraX 입력으로 가정하지 않는다.
