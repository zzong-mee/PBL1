# Sense 카메라 + 두 ToF 센서 → Android YOLOX

> 이 브랜치는 8×8 원본 전송을 추가했다. 새 앱·장착 확인·격자 방향 보정은
> [존별 방향 음향 안내](../../docs/tof-grid-direction.md)를 먼저 따른다.
> 아래의0.8.0 앱 기록과 구분한다.

## 구현 범위

`ThirdEyeSenseUsb.ino`는 XIAO ESP32-S3 Sense의 실제 카메라 JPEG와 두 VL53L8CX의 대표 거리를
USB CDC로 Android 앱에 보낸다. 스마트폰 안에서 번들 YOLOX-Nano / ONNX Runtime이 추론한다.
외부 YOLO API 서버, API 키, 인터넷 이미지 업로드는 사용하지 않는다.
기존 `ThirdEyeDualTof` 단독 격자 테스트는 보존한다. 사용자는 그 두 센서 실측 동작을 확인했다.
**새 카메라+센서 통합 펌웨어의 실기기 동작은 아직 확인되지 않았다.**

## 기존 배선 유지

- 3V3/GND/SDA(D4)/SCL(D5)는 Y선으로 두 센서에 공유한다.
- 왼쪽 센서 A LP=D2, 오른쪽 센서 B LP=D3. 각 SPI/I2C 핀은 GND.
- 주소는 7비트 A=0x2A/B=0x2B (ST API 8비트 0x54/0x56).
- Sense 확장보드에 장착된 카메라를 사용한다. 카메라 케이블/보드는 전원을 분리하고 점검한다.
- 카메라 GPIO 핀맵은 Espressif Arduino CameraWebServer의 CAMERA_MODEL_XIAO_ESP32S3와 일치한다.
  SCCB GPIO40/39와 ToF D4/D5(GPIO5/6)는 별도 버스다. 기본 SCCB 컨트롤러1일 때 ToF는 Wire(0),
  SCCB 컨트롤러0으로 빌드된 패키지에서는 ToF를 Wire1로 선택한다.
- 센서와 카메라를 동시에 켠 상태의 전원 안정성/처리 속도는 실측해야 한다.

## Windows: 펌웨어 올리기

1. `feature/sense-camera-yolox` 브랜치 ZIP을 다운로드하고 압축을 푼다.
2. `firmware/ThirdEyeSenseUsb/ThirdEyeSenseUsb.ino`를 연다. **같은 폴더의 GeyePacket.h도 필요**하다.
3. Arduino 보드 패키지는 Espressif esp32 3.x, 라이브러리는 STM32duino VL53L8CX 2.1.0.
4. 보드 XIAO_ESP32S3, USB CDC On Boot Enabled, USB Mode Hardware CDC and JTAG,
   PSRAM OPI PSRAM, Core Debug Level None. 메뉴명은 보드 패키지 버전에 따라 약간 다를 수 있다.
5. 보드의 COM 포트를 선택하고 Upload(→)한다. 컴파일 오류면 전체 오류 문구와 esp32 버전을 기록한다.
6. 완료 후 PC에서 분리한다. 이 펌웨어는 **바이너리 영상 패킷**을 내보내므로 Arduino Serial Monitor에서
   사람이 읽을 수 있는 거리 격자가 보이지 않는 것이 정상이다. PC 터미널 연결은 종료한다.

센서 문제를 제외하고 카메라만 확인할 필요가 있으면 `ENABLE_TOF=false`로 변경해 재업로드할 수 있다.
이때 LP를 둘 다 LOW로 두고 거리값은 null로 보내며 가짜 값을 만들지 않는다. 정상 통합 모드는 true다.

## Android 앱 설치와 실제 확인

1. 같은 브랜치의 프로젝트 루트를 Android Studio로 연다. JDK 17 이상, SDK 36,
   Build Tools 35.0.0을 준비하고 Gradle Sync를 마친다.
2. 휴대폰 USB 디버깅을 켜고 PC 연결을 승인한 후 `app`을 Run한다.
   앱 ID `com.guideeye.app`, 버전 `0.8.0-sense`(12). 빌드된 debug APK를 직접 설치할 수도 있다.
   기존 앱과 서명이 달라 업데이트가 거절되면 기존 실험 앱 제거 후 설치가 필요할 수 있다.
3. 설치 후 휴대폰을 PC에서 분리하고 **USB 데이터/OTG 연결**로 XIAO와 휴대폰을 직접 연결한다.
   휴대폰이 USB Host가 되어 보드를 인식해야 한다. 다른 Serial USB Terminal 앱은 연결 해제한다.
4. Third Eye를 열고 USB 접근 권한을 허용한다. 기본 입력은 Sense USB이며 휴대폰 후면 카메라로
   자동 전환하지 않는다. 초기 센서/카메라 준비에는 시간이 걸릴 수 있다(첫 유효 패킷 대기 최대 15초).
5. **개발자 모드**에서 실제 외부 영상, 중앙 노란 점, 탐지 초록 상자, 추론 시간과 좌우 거리를 확인한다.
   연결이 안 되면 `USB 연결 / 다시 연결`을 누른다.
6. **사용자 화면으로 돌아가기 → 중앙 물체**를 누르면 중앙 탐지 물체 이름을 한국어 TTS로 안내한다.
   화면 전환 시 USB를 닫고 새 화면에서 다시 연결한다. 일시정지/백그라운드에서도 해제한다.
7. Sense 렌즈만 가렸을 때 영상이 가려지는지 확인한다. 휴대폰 카메라는 이 경로에서 사용하지 않는다.
8. 사람이 있는 장면/의자/컵 등 지원 물체로 테스트하고, 영상 방향과 중앙 점 위치를 확인한다.
   COCO 80종과 신뢰도 기준에 한정되며 모든 물체를 식별하지 않는다. 방향 보정은 실측 후 결정한다.
9. 케이블 분리, 재연결, USB 권한 거절, 일시정지/재개, 개발자 화면 왕복, 영상 중단을 확인한다.

후면 카메라 비교 테스트는 개발자 화면의 `휴대폰 카메라 보기`를 명시적으로 선택해야 한다.
그 경로에만 Android 카메라 권한이 필요하다. 외부 USB 경로는 USB 접근 권한을 사용한다.

## 전송 및 오류 처리

- GEYE v1: magic 4바이트, 헤더14(버전/종류/길이/시퀀스/장치 millis), payload, CRC32 4바이트.
  다중 바이트 값은 little-endian. 종류1=거리 JSON, 2=JPEG, 3=카메라 상태 JSON.
- QVGA 320×240 JPEG, quality 15, 버퍼1개. 프레임 요청 간격 목표200ms이며 실측 FPS 보장은 아니다.
- 센서는 8×8·5Hz. 각 센서 유효 영역 최솟값을 left_mm/right_mm으로 약500ms마다 보낸다.
  대표 거리는 중앙 물체까지의 거리라는 뜻이 아니며 카메라/센서 좌표 융합은 하지 않는다.
- 센서 오류/유효 표적 없음/1초 이상 지난 값은 null. tof_error가 있으면 앱에 센서 오류를 표시한다.
- 카메라 초기화/캡처 실패는 종류3으로 알린다. 센서의 감지 가능한 오류가 있어도 카메라 사용을 허용한다.
  단, 기존 ST 라이브러리 IO_Read 내부의 무한 재시도 때문에 I²C 자체가 멈추면 전체 루프가 멈출 수 있다.
  실행 중 배선을 바꾸지 않고, 문제 시 전원을 분리해 점검한다.
- JPEG 최대256KiB, CRC 불일치 폐기. Android 디코딩 최대1280×1280, 추론 작업 중 새 프레임은 버린다.
  결과가 2초 이상 지나면 중앙 물체 안내에 쓰지 않는다. 추론 시간은 USB 전송을 포함한 전체 지연이 아니다.
- 전송은 1초 제한으로 실패 반환하지만 부분 패킷 이후의 복원에는 추가 프레임이 필요할 수 있다.
- 거리가 와도 영상이 안 오면 영상 대기/오류를 표시한다. 카메라 오류와 ToF 미측정을 성공으로 표현하지 않는다.
- 보행 위험도·공간음향·안전성 검증은 이 통합의 범위에 포함하지 않는다.

## 개발 검증

```sh
. /workspace/tooling/guideeye-env.sh
./gradlew --no-daemon assembleDebug testDebugUnitTest lintDebug
# C++ encoder produces Android test fixture; no Arduino dependencies needed.
g++ -std=c++17 -Wall -Wextra -Werror firmware/ThirdEyeSenseUsb/tests/packet_test.cpp -o /tmp/sense-packet-test
/tmp/sense-packet-test > /tmp/sense-jpeg-packet.hex
cmp /tmp/sense-jpeg-packet.hex app/src/test/resources/sense-jpeg-packet.hex
```

기존 센서 제어 흐름 테스트는 `firmware/ThirdEyeDualTof/README.md`를 따른다.
ESP32 전체 컴파일과 실물 카메라/USB/Android native 추론은 별도 검증이 필요하다. 클라우드는
`downloads.arduino.cc` CONNECT 403으로 Arduino 도구 설치가 차단되어 전체 펌웨어 빌드를 검증하지 못했다.
실제 ST 2.1.0 헤더와 Arduino/카메라 대역을 이용한 호스트 문법/API 검사는 통과했으며, 이는 ESP32 빌드가 아니다.

### 이번 클라우드 검증 결과

- Android `assembleDebug testDebugUnitTest lintDebug`: 성공.
- 단위 테스트 20개, 실패/오류/건너뜀 0. 펌웨어 C++ 인코더가 만든 fixture를 Android 디코더에서
  모든 USB 분할 경계로 나눠 복원하는 테스트와 거리/카메라 오류/JPEG 혼합 스트림 테스트 포함.
- Lint 오류 0, 경고 7(유지된 휴대폰 Camera2 경로의 framework ExifInterface 관련 6개,
  ARM64 전용 패키징의 ChromeOsAbiSupport 1개).
- C++ 패킷 인코더 테스트: 길이/타입 제한, 쓰기 실패 반환, Android fixture 일치 확인.
- 실제 ST 2.1.0 헤더로 ToF Wire/Wire1 양쪽 분기 문법/API 검사 통과. 카메라/Arduino API는 대역이므로
  ESP32 대상 전체 컴파일이나 연결 동작을 증명하지 않는다.
- Python ONNX Runtime 1.20.1로 번들 모델 그래프/입출력 검사와 실제 샘플 이미지 추론 수행.
  공식 Ultralytics assets의 bus.jpg에서 버스(class 5, 약0.909)와 사람(class 0) 탐지.
  이 이미지는 저장소에 재배포하지 않았다. Android native 실행 또는 Sense 렌즈 영상 테스트는 아니다.

### 카메라 헤더 이름 충돌 수정

Windows ESP32 빌드에서 카메라 `sensor.h`의 전역 배열 `resolution[]`과 센서 변수 이름이
충돌한 것을 확인해 센서 상수를 `tofResolution`으로 변경했다. 실제 Espressif `sensor.h`를
호스트 검사에 포함하면 수정 전 오류가 재현되고 수정 후 Wire/Wire1 양쪽 분기가 통과한다.
앞선 카메라 대역 헤더만의 검사는 이 충돌을 발견하지 못했다. ESP32 전체 빌드 검증은 여전히 별도다.
