# Third Eye 외부 USB 카메라 개발 (0.6.0)

## 구현 범위와 남은 확인

스마트폰 카메라 개발 계획은 사용자 요청에 따라 **XIAO ESP32-S3 Sense 외부 카메라**로 변경했다.
CameraX·스마트폰 카메라 권한·Wi-Fi·Bluetooth는 추가하지 않았다.

- Android: USB JPEG 수신, CRC/길이 확인, YOLOX-Nano 추론, 중앙 물체 한국어 TTS,
  개발자 화면의 실제 분석 이미지·박스·분류 신뢰도·처리 시간 표시를 구현했다.
- 중앙 영역은 전체 JPEG의 중앙 가로/세로 20%다. 중심점을 포함하는 박스를 우선하고
  이후 중심과의 거리, 신뢰도 순으로 선택한다. 결과는 '…로 보이는 물체'로 안내한다.
- 0.35 미만 신뢰도, 중앙 영역 밖 결과, USB 수신 후 2초가 지난 결과는 안내에 사용하지 않는다.
  탐지 실패는 장애물이 없다는 뜻이 아니다. COCO의 80개 일반 사물 종류만 지원한다.
- JPEG 픽셀 수는 최대 1280x1280, 패킷은 최대 256 KiB로 제한한다.
- 분석 중에 들어온 추가 프레임은 버려 작업 대기열이 늘어나지 않게 한다.
- 일시정지·백그라운드·개발자 화면 이동 시 사용자 화면의 USB 수신과 분석 결과를 무효화한다.
  이미 진행 중인 단일 추론은 끝날 수 있지만 이전 결과는 버린다.
- 기존 테스트 펌웨어의 LF/CRLF 거리 JSON도 계속 지원한다.
- **거리 센서 배선 사진 대기 중**: VL53L8CX 전원/신호 전압, 센서 수, LP 핀과 주소 분리를
  확인하지 못해 실제 센서 초기화 코드는 확정하지 않았다. 거리와 객체를 공간적으로 융합하거나
  위험도/자동 보행 안내를 구현한 상태가 아니다.

## 모델 및 라이브러리

- YOLOX-Nano 공식 ONNX 416x416 (Apache-2.0)
  https://github.com/Megvii-BaseDetection/YOLOX/releases/download/0.1.1rc0/yolox_nano.onnx
- SHA256: `c789161ed43c8269fcd4e67c67eeeb4e80c622da2eb296a20bc6007bd18a0b7d`
- 공식 전처리/후처리 참고:
  https://github.com/Megvii-BaseDetection/YOLOX/tree/main/demo/ONNXRuntime
- 전처리: BGR 0..255, 좌상단 기준 114 패딩, NCHW. 출력: [1,3549,85],
  stride 8/16/32 디코딩, objectness×class confidence, 클래스별 IoU 0.45 NMS.
- ONNX Runtime Android 1.31.0 (MIT): 스마트폰 CPU에서 모델 실행. 내부 추론 스레드 2개.
- Galaxy A34용 ARM64 ABI만 패키징한다. x86/x86_64 에뮬레이터용 빌드는 이 설정에서 지원하지 않는다.
- 라이선스는 `docs/licenses`와 APK의 assets에 포함한다. Ultralytics 모델은 앱에 포함하지 않았다.
- 앱은 모델을 번들로 포함하고 네트워크 추론/이미지 업로드를 하지 않는다.

## 카메라 검증용 펌웨어

`firmware/ThirdEyeCameraUsb/ThirdEyeCameraUsb.ino`는 **카메라만 먼저 검증하는 펌웨어**다.
거리 센서 값을 만들지 않으며 `left_mm:null,right_mm:null`을 전송한다.
이 코드로 거리 센서가 실제 연결·측정됐다고 판단하면 안 된다.

Arduino IDE 설정 후보:

- Espressif esp32 3.x, 보드 `XIAO_ESP32S3`
- USB CDC On Boot Enabled, PSRAM OPI, Core Debug Level None (바이너리 스트림에 디버그 문자열을 섞지 않음)
- Seeed XIAO ESP32-S3 Sense 카메라 핀맵, JPEG QVGA 320x240, quality 15, frame buffer 1개
- 약 500ms 루프 간격. USB 전송·카메라 처리에 따라 실제 속도는 더 낮아질 수 있다.

**검증 한계:** 실제 보드의 카메라 종류·장착 상태·전원 및 핀 충돌을 확인해야 한다.
클라우드의 `downloads.arduino.cc`, `espressif.github.io` 접근 거부로 ESP32 패키지 설치가 막혀
전체 펌웨어를 ESP32 대상으로 컴파일하거나 업로드하지 못했다. 카메라 API/USB CDC 동작도
실기기 검증 전이다. C++ 호스트 테스트로 패킷 인코더의 헤더·payload·CRC만 확인했다.

센서 배선 확인 전에는 이 검증용 코드를 완성된 전체 시스템 펌웨어로 사용하지 않는다.
원래의 `ThirdEyeUsbTest`는 별도 폴더에 보존했다.

## 실제 검증 순서

1. 배선 사진으로 센서/카메라와 전원 구성을 확인한다.
2. Arduino IDE에서 카메라 검증용 펌웨어를 컴파일하고 오류가 없는지 확인한다.
3. 카메라 장착과 핀 충돌이 없는 구성을 확인한 뒤 XIAO에 업로드한다.
4. Mac의 Android Studio에서 최신 main을 Pull하고 앱을 설치한다.
5. Serial USB Terminal을 Disconnect/종료한 뒤 Galaxy와 XIAO를 USB로 연결한다.
6. 사용자 화면에서 USB 접근 권한을 허용한다. 분석 대기 후 중앙 물체 버튼을 누른다.
7. 개발자 모드에서 USB 연결을 누르고 실제 영상의 방향·중앙 영역·탐지 박스·처리 시간을 확인한다.
   두 화면은 USB 연결을 동시에 소유하지 않는다. 화면 이동 시 이전 화면 연결을 닫는다.
8. 일시정지, 다시 시작, 권한 거절, 케이블 분리, 영상 중단, 손상 패킷, 화면 회전과 TalkBack을 확인한다.
9. 그다음 실제 ToF 초기화/유효 상태 확인/센서 주소 분리와 데이터 전달을 추가한다.

## 클라우드 검증

- Android APK 빌드 성공, 기존 테스트와 신규 패킷/중앙 선택/좌표 변환 테스트 총 18개 통과.
- Lint 오류 0, ChromeOS ABI 경고 1: Galaxy A34 ARM64만 패키징하여 x86_64 ChromeOS를 지원하지 않는다는 경고다.
- `tools/validate_yolox.py`로 실제 ONNX 그래프·입출력 크기와 샘플 이미지 추론 확인.
  공식 샘플 `https://raw.githubusercontent.com/ultralytics/assets/main/im/bus.jpg`에서
  class 5(버스), class 0(사람) 탐지. 이미지는 저장소에 재배포하지 않았다.
- Python 호스트의 ONNX Runtime 1.20.1 검증은 Android native 실행을 증명하지 않는다.
- Galaxy A34의 실시간 성능·전송률·지연·배터리·화각 및 센서 정렬은 미검증이다.
