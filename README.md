# Third Eye

시각장애인을 위한 Android AI 보행 보조 앱. 현재 **1단계 개발용 Mock 화면**이다.
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
- 앱 실행용 외부 라이브러리 없음. JUnit 4.13.2는 Mock 로직 단위 테스트에 사용

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
- `docs/usb-protocol.md`: USB Host/Serial 및 JPEG 전송 후속 설계

## Galaxy A34 확인 항목 (실기기 미검증)

1. Android 16에서 앱 실행, 상태 표시와 시스템 바 겹침 여부 확인.
2. 좌우 거리가 2초마다 변하고 정지/시작 버튼이 동작하는지 확인.
3. 음성 엔진 준비 후 버튼을 누르면 한국어 테스트 안내가 들리는지 확인.
4. 한국어 음성 미설치 시 버튼 비활성화와 안내 문구 확인.
5. 홈 이동 후 갱신/음성이 멈추고 복귀 후 갱신이 재개되는지 확인.
6. 회전, 큰 글꼴, TalkBack에서 버튼 및 내용 접근 확인.

## 다음 단계

앱 데이터 전송은 **USB 유선만** 사용한다. ESP32-S3가 I²C 센서 두 개를 수집하고
Android USB Host API를 통해 먼저 USB Serial 거리 수신을 구현한다.
연결/해제/권한 거절/오류 처리를 추가한 뒤 JPEG 프레임 전송을 실제 보드와 검증한다.
YOLO, 센서 융합, 위험도/중복 음성 억제는 후속 단계이며 현재 구현 범위에 포함하지 않는다.

## 클라우드 검증 결과

- `assembleDebug`: 성공, `app/build/outputs/apk/debug/app-debug.apk` 생성
- `testDebugUnitTest`: 1개 실행, 실패/오류/건너뜀 0
- `lintDebug`: 오류/경고 0
- 검증 도구: JDK 21.0.12.1, Gradle 8.13, Android SDK 36
- SDK/JDK/Gradle 캐시는 `/workspace/tooling`에 유지되며, 클라우드에서는
  `. /workspace/tooling/guideeye-env.sh` 후 위 Gradle 명령을 실행한다.
- Gradle의 Android analytics 초기화 경고는 사용자 홈의 읽기 제한 때문이며 빌드·Lint는 정상 완료됐다.
- 실기기 실행·실제 TTS 재생·USB 통신은 아직 검증하지 않았다.
