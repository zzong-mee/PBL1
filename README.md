# Third Eye

시각장애인을 위한 Android AI 보행 보조 앱. 현재 **Mock 화면과 USB Serial 거리 JSON 수신**을 제공하는 개발 버전이다.
실제 장애물 감지나 위험도 판단을 수행하지 않으며 보행 안전을 보장하는 앱이 아니다.

## 기존 저장소 분석

작업 시작 시 로컬 파일·커밋 및 원격 브랜치가 없는 빈 저장소였다.
기존 Android 프로젝트, 의존성, 테스트, CI, AGENTS.md는 없었으며 Kotlin/XML 프로젝트를 신규 구성했다.

## 구현한 기능

- Third Eye 메인 화면, USB 카메라·실제 좌우 센서 미연결 표시
- 카메라 영상 대기, AI 미구현 및 위험도 미평가 상태
- 2초 주기로 변하는 가상 좌우 거리(미터), 갱신 정지/시작 버튼
- Android TextToSpeech 한국어 지원 확인 후 활성화되는 음성 테스트 버튼
- 음성 엔진 준비·사용 불가·재생 오류·완료 상태와 최근 요청 문구
- 백그라운드 진입 시 Mock 갱신 및 음성 중지, Activity 종료 시 TTS 해제
- 스크롤, 큰 글자/버튼, 시스템 바 여백 처리. TalkBack/확대 글꼴 실기기 확인 필요

## 개발 환경과 실행

- Android Studio의 AGP 8.11 지원 버전 이상 (예: Narwhal 2025.1.1 이상)
- JDK 17 이상 (클라우드 검증은 JDK 21), Gradle 8.13 Wrapper
- Android SDK Platform 36, Build Tools 35.0.0
- Kotlin 2.1.20, XML 기반 Android 기본 View, minSdk 26 / targetSdk 36
- AndroidX Core 1.17.0: Android 버전별 안전한 BroadcastReceiver 등록에 사용. USB 전송은 Android 기본 Host API 사용
- JUnit 4.13.2는 단위 테스트, org.json 20260814는 Android 기본 JSON 파서의 JVM 테스트 대체 구현에만 사용

1. Android Studio에서 이 폴더를 열고 SDK 36을 설치한 뒤 Gradle Sync한다.
2. Galaxy A34의 개발자 옵션에서 USB 디버깅을 활성화하고 PC 연결을 승인한다.
3. `app` 실행 대상을 선택하고 Run한다. 앱 ID: `com.guideeye.app`.
4. 기기 TTS 설정에서 한국어 지원 엔진·한국어 음성 데이터를 준비하고 미디어 음량을 확인한다.
   TTS는 기기에 설치된 엔진을 사용한다. 엔진에 따라 음성 데이터 설치나 네트워크가 필요할 수 있다.

명령줄에서는 Android SDK 위치를 `ANDROID_HOME` 또는 로컬 `local.properties`의 `sdk.dir`로 지정한다.
`local.properties`는 Git에서 제외된다.

```sh
./gradlew assembleDebug testDebugUnitTest lintDebug
# 기기 연결 후 (PC에 adb 필요)
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## 파일 구조

- `MainActivity.kt`: 화면 바인딩 및 lifecycle 제어
- `data/DistanceSource.kt`: 거리 입력 교체 지점
- `data/MockDistanceSource.kt`, `MockSequence.kt`: Mock 주기와 재현 가능한 거리 샘플
- `speech/KoreanSpeechGuide.kt`: 한국어 TTS 초기화·출력·정리
- `res/layout/activity_main.xml`, `res/values/strings.xml`: 화면과 한국어 문구
- `usb/UsbDistanceController.kt`: USB 권한·CDC 연결·읽기·해제 및 timeout 처리
- `usb/DistanceLineDecoder.kt`: 줄 단위 JSON 검증과 mm → m 변환
- `docs/usb-protocol.md`: 현재 거리 통신과 JPEG 전송 후속 설계

## Galaxy A34 기본 기능 확인 항목

1. Android 16에서 앱 실행, 상태 표시와 시스템 바 겹침 여부 확인.
2. 좌우 거리가 2초마다 변하고 정지/시작 버튼이 동작하는지 확인.
3. 음성 엔진 준비 후 버튼을 누르면 한국어 테스트 안내가 들리는지 확인.
4. 한국어 음성 미설치 시 버튼 비활성화와 안내 문구 확인.
5. 홈 이동 후 갱신/음성이 멈추고 복귀 후 갱신이 재개되는지 확인.
6. 회전, 큰 글꼴, TalkBack에서 버튼 및 내용 접근 확인.

## USB 거리 수신 테스트

앱 데이터 전송은 **USB 유선만** 사용한다. Android USB Host API로 Espressif(VID 0x303A)의
단일 CDC ACM 제어 interface와 단일 CDC data interface를 가진 장치 하나를 지원한다.
USB Serial 터미널 앱에서 통신이 된 사실만으로 모든 descriptor 구성이 지원된다고 보장하지 않는다.
여러 지원 장치를 동시에 연결하거나 다른 USB-UART 칩을 사용하는 장치는 이번 구현 범위 밖이다.

1. Mac에서 GitHub의 최신 `main`을 Pull하고 앱을 Galaxy A34에 다시 설치한다.
2. Serial USB Terminal 앱에서 Disconnect 후 앱을 종료해 USB interface를 해제한다.
3. XIAO에 `firmware/ThirdEyeUsbTest/ThirdEyeUsbTest.ino`를 업로드한다.
   보드 `XIAO_ESP32S3`, `USB CDC On Boot: Enabled`를 사용한다.
4. XIAO를 휴대폰에 USB로 연결하고 Third Eye의 **USB 연결 / 다시 연결**을 누른다.
5. USB 접근 권한을 허용한다. 좌측 **0.50미터**, 우측 **1.20미터**가 표시되어야 한다.
   이 값은 펌웨어의 고정 테스트 값이며 실제 센서 실측이 아니다.
6. 케이블 분리 시 분리 상태가 표시되고 거리 값이 지워지는지 확인한다.
7. 권한 거절 후 연결 버튼 재시도, 재연결, Mock 전환을 확인한다.
8. 앱을 백그라운드로 보내면 연결이 닫힌다. 복귀 후 연결 버튼을 다시 누른다.

115200 / 8N1, DTR·RTS 활성화를 요청한다. 수신은 worker thread, UI는 main thread에서 처리한다.
각 줄은 LF 또는 CRLF로 끝나는 JSON이어야 한다:

```json
{"left_mm":500,"right_mm":1200}
```

각 값은 1~100000mm 범위의 정수여야 하며, 두 값이 모두 유효한 경우만 갱신한다.
USB read 분할/병합을 처리하고 512바이트를 넘는 줄은 줄 끝까지 폐기한다.
잘못된 줄은 무시한다. 4초간 유효한 측정값이 없으면 연결을 닫고 이전 거리를 지운다.
오류/권한 거절 시 Mock으로 자동 전환하지 않는다. Mock 전환은 버튼으로 명시적으로 한다.
USB 연결 성공은 카메라 연결이나 실제 센서 측정 성공을 뜻하지 않는다.

후속 단계에서는 ESP32-S3가 I²C 센서 두 개를 읽도록 펌웨어를 추가하고,
JPEG 프레임 전송을 실제 보드와 검증한다.
YOLO, 센서 융합, 위험도/중복 음성 억제는 후속 단계이며 현재 구현 범위에 포함하지 않는다.

## 클라우드 검증 결과

- `assembleDebug`: 성공, `app/build/outputs/apk/debug/app-debug.apk` 생성
- `testDebugUnitTest`: Mock 1개 + USB JSON decoder 3개, 총 4개 통과 (실패/오류/건너뜀 0)
- `lintDebug`: 오류/경고 0
- 검증 도구: JDK 21.0.12.1, Gradle 8.13, Android SDK 36
- SDK/JDK/Gradle 캐시는 `/workspace/tooling`에 유지되며, 클라우드에서는
  `. /workspace/tooling/guideeye-env.sh` 후 위 Gradle 명령을 실행한다.
- Gradle의 Android analytics 초기화 경고는 사용자 홈의 읽기 제한 때문이며 빌드·Lint는 정상 완료됐다.
- 기존 Mock 앱 실행과 한국어 TTS는 사용자가 실기기에서 확인했다. 이번 USB 수신 구현은 실기기 검증이 필요하다.

## 접근성 UI (0.3.0)

화면을 연결/모드, 좌우 거리, 음성 안내, 카메라 상태 구역으로 나누었다.
큰 글자, 대비가 높은 색상, 최소 64dp 버튼, TalkBack 제목 탐색을 적용했다.

- TalkBack 터치 탐색을 사용하지 않을 때: 활성화된 버튼에 손을 대면 버튼 이름을 한국어 TTS로 안내하고 터치 진동을 준다.
- 버튼 실행은 손을 뗄 때의 Android 기본 클릭 동작을 사용한다. 버튼 밖으로 손을 옮기거나 스크롤하면 취소된다.
- TalkBack 터치 탐색을 사용하면 별도의 이름 읽기/진동을 중복 실행하지 않고 TalkBack 기본 탐색과 두 번 탭 실행을 유지한다.
- 이름 읽기는 한국어 TTS 초기화 완료 후 가능하며, 진동은 기기의 터치 진동 설정을 따른다.
- 음성 테스트는 버튼 이름을 먼저 읽은 뒤 테스트 문구를 이어 읽는다. 새 버튼 터치는 이전 음성을 대체한다.
- UI 변경으로 USB/Mock 거리 수신 및 TTS 초기화 로직의 기능 범위를 확대하지 않았다.

실기기 확인: 손을 댄 채로 유지하면 실행되지 않는지, 손을 떼면 한 번 실행되는지,
버튼 밖 이동/스크롤 시 취소되는지, 이름 읽기와 진동이 동작하는지 확인한다.
TalkBack 켜기/끄기, 큰 글꼴, TTS 미준비/사용 불가 상태도 확인한다.
이 버전의 터치 음성·진동 및 화면 배치는 실기기에서 아직 검증하지 않았다.
