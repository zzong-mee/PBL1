# Third Eye (써드아이)

> 시각장애인의 주변 인지와 보행 보조를 목표로 개발 중인 **Android + XIAO ESP32-S3 Sense + 듀얼 VL53L8CX** 프로젝트입니다.

**현재 기준:** `feature/radar-scan` · Android 앱 **0.11.3-scan (versionCode 21)**  
**상태:** 기능 개발 및 통합 테스트 단계. 완성된 보행 안전 시스템이 아니며, 안전 보조 기기로 의존해서는 안 됩니다.

## 프로젝트 개요

Third Eye는 외부 USB 카메라의 영상과 좌·우 ToF 거리 센서의 데이터를 Android 앱으로 받아, 객체 인식과 거리·방향 기반 음향 실험을 수행합니다. 사용자 화면은 단순하게 유지하고, 개발자 화면에서 센서 데이터·카메라 추론·음향·알고리즘 모니터링을 확인합니다.

```text
XIAO ESP32-S3 Sense ── USB ── Android Third Eye
  ├─ JPEG 카메라 프레임 ── YOLOX-Nano ── 중앙 물체 인식/설명 TTS
  └─ 좌·우 VL53L8CX ── 8×8 거리 배열 ── 유효성 검사/방향 투영
                                              ├─ 컬러 히트맵
                                              ├─ ToF 공간 음향 실험
                                              └─ 좌→우 레이더 청각 스캔
```

**중요:** 위 두 경로는 현재 독립적으로 동작합니다. 카메라 탐지와 ToF를 결합한 통합 위험도 판정, 긴급 경고 정책 및 통합 음성 우선순위 관리는 아직 완성되지 않았습니다.

## 현재 구현된 기능

| 영역 | 구현 내용 | 범위 및 한계 |
| --- | --- | --- |
| 사용자 화면 | 중앙 물체 인식 버튼, 한국어 TTS, 일시정지/재개, 사용 방법 안내 | 수동 객체 인식 중심. 자동 보행 경고는 미완성 |
| 외부 카메라 | XIAO Sense USB JPEG 수신, YOLOX-Nano 로컬 추론, 탐지 미리보기 | 실제 연결·추론 속도는 기기에서 검증 필요 |
| 휴대폰 카메라 | 개발자 모드에서 명시적으로 Camera2 입력 선택 | USB에서 자동 전환하지 않음 |
| ToF 센서 | 좌·우 VL53L8CX의 8×8(각 64존) 거리값 수신, 상태·신선도 검사 | 현재 앱의 격자 파서는 8×8 JSON 형식을 요구 |
| 거리 히트맵 | 좌우 8×8 숫자·거리별 색상 표시, 회전·반전 설정, 화면 폭에 맞춘 가로 배치 | 작은 화면에서 글자 가독성 실기기 확인 필요 |
| 거리 단음 | 실제 USB 거리 기반 좌/우 단음 실험 | 개발자 기능 |
| 공간 음향 | ToF 유효 존의 방향 투영, HRTF 기반 공간 음향 실험 | 장착 방향 보정 및 실제 청취 검증 필요 |
| 레이더 스캔 | 전방 방향 후보를 좌→우 순서로 음향 스캔, 1/3/5/16 방향 제한 | 개발자 실험 기능, 위험도 분류 아님 |
| 전체 테스트 | 알고리즘 그림 위에 수신·처리 중인 단계의 발광 오버레이 | 원본 이미지 asset 필요, 일부 단계만 관찰 가능 |
| 접근성 | 버튼 터치 안내 TTS·햅틱, TalkBack 고려, 상태 설명 | 실제 기기·설정별 검증 필요 |

### 거리 히트맵 색상

단위는 **mm**입니다.

| 거리 | 색상 |
| --- | --- |
| 1–100 | 빨강 |
| 101–200 | 주황 |
| 201–300 | 노랑 |
| 301–500 | 연두 |
| 501–1000 | 초록 |
| 1001–4000 | 파랑 |
| 유효하지 않거나 만료된 측정 | 회색, `—` |

개발자 화면에서는 센서 **A(왼쪽)**, **B(오른쪽)**를 한 줄에 배치하고 각 8×8 격자를 화면 폭에 맞춰 표시합니다. 값이 바뀌면 색상도 갱신됩니다. 숫자와 색상은 측정 거리의 시각화이지 최종 보행 위험 등급이 아닙니다.

### 전체 테스트: 알고리즘 실행 상태

개발자 화면 맨 아래의 **전체 테스트 시작**을 누르면 원본 흐름도를 보여주고, 실제 수신/처리 신호를 확인한 블록에 하늘색 발광 효과를 겹칩니다. 종료 버튼을 누르면 모니터가 닫힙니다.

현재 추적하는 주요 단계:
- USB 데이터 수신 루프: 최근 ToF 수신 시각 기준
- 좌·우 ToF 8×8 배열: 신선한 격자 프레임
- 거리 유효성 검사: 유효 거리 데이터 존재
- 거리·방향 후보: 장착 보정 확인 및 방향 음향 기능 활성화
- 카메라 JPEG 프레임: 최근 USB JPEG 수신
- YOLO 객체 탐지: 최근 분석 결과 콜백
- 한국어 TTS: 개발자 음성 테스트 요청 기준

표시는 **실시간 진단용 추정 상태**입니다. 모든 함수 호출을 추적하는 프로파일러는 아니며, 블록이 빛난다고 최종 위험도 분석이나 사용자 음성 출력까지 성공했다는 의미는 아닙니다.

**이미지 설정:** 현재 원본 이미지 바이너리는 브랜치에 포함되지 않았습니다. 제공된 흐름도 PNG를 다음 위치에 `algorithm_flow.png` 이름으로 추가해야 합니다.

```text
app/src/main/assets/algorithm_flow.png
```

이 파일이 없으면 앱은 이미지 대신 누락 안내를 표시합니다. 발광 영역은 원본 이미지의 블록 좌표에 기반하므로 이미지 크기·배치가 달라지면 좌표 보정이 필요합니다.

## 사용자 화면 사용법

1. Android 앱 **Third Eye**를 실행합니다.
2. 외부 USB 카메라 연결과 한국어 TTS 준비 상태를 확인합니다.
3. **중앙 물체** 버튼으로 카메라 중앙의 객체를 인식하고 한국어 음성 안내를 요청합니다.
4. **일시정지/다시 시작**으로 카메라 동작을 제어합니다.
5. 하단 **개발자 모드** 버튼으로 상세 진단 화면을 엽니다.

인식 결과가 불확실하거나 프레임이 없으면 상태 안내를 제공합니다. 인식하지 못한 사물을 임의로 추측해 알려주지 않습니다.

## 개발자 화면 사용법

1. **USB 연결**로 외부 XIAO 장치에 연결합니다. 별도 선택 시 휴대폰 카메라 또는 Mock 모드를 사용할 수 있습니다.
2. 카메라 미리보기에서 JPEG·객체 탐지 상태를 확인합니다.
3. 좌·우 거리 및 **A/B 8×8 히트맵**을 확인합니다.
4. 센서 장착에 맞게 **회전/좌우 반전**을 조정한 후 장착 확인을 선택합니다.
5. 필요하면 **거리 단음**, **ToF 공간 음향**, **레이더 스캔**, **HRTF 가상 방향 테스트**를 개별 실행합니다. 일부 음향 모드는 서로 배타적으로 동작합니다.
6. 하단 **전체 테스트 시작**으로 흐름도의 실제 실행 단계 표시를 확인합니다.

### ToF 데이터 유효성

현재 `TofGridFrame` 파서는 `grid_size=8`, `grid_order=st_uld_raw`, 각 센서별 64개 거리·상태 값 및 수신 시각 정보를 요구합니다. 거리 단위는 mm이며 1–4000 범위와 허용 상태 코드(5, 6, 9), 센서 데이터 나이 및 수신 후 경과 시간을 검사합니다. 오래된 데이터는 표시에서 무효 처리됩니다. **4×4 입력을 8×8로 자동 변환하는 기능은 제공하지 않습니다.**

## 설치 및 개발 환경

- Android Studio, Kotlin + XML View
- Android SDK 36 / minSdk 26 / targetSdk 36
- JDK 17 이상, Gradle Wrapper 사용
- ONNX Runtime Android 1.31.0 (YOLOX-Nano CPU 추론)
- Android USB Host / CDC 기반 통신
- 한국어 Android TextToSpeech
- Android 패키지: `com.guideeye.app`

```bash
git clone https://github.com/zzong-mee/PBL1.git
cd PBL1
git switch feature/radar-scan
./gradlew assembleDebug
./gradlew testDebugUnitTest
```

기존 로컬 저장소가 있다면:

```bash
git switch feature/radar-scan
git pull origin feature/radar-scan
```

Android Studio에서 프로젝트를 열고 Gradle Sync 후 기기에 설치합니다. USB Host 연결과 XIAO 펌웨어 구성은 [통합 펌웨어 안내](firmware/ThirdEyeSenseUsb/)를 확인하세요. 펌웨어·장치 구성에 따라 실제 USB 수신이 달라질 수 있습니다.

## 주요 코드 위치

```text
app/src/main/java/com/guideeye/app/
├── MainActivity.kt                  # 사용자 화면 및 중앙 물체 음성 안내
├── DeveloperActivity.kt             # 개발자 대시보드·센서·테스트 모니터
├── AlgorithmFlowView.kt             # 원본 알고리즘 이미지/발광 오버레이
├── usb/
│   ├── UsbDistanceController.kt     # USB 수신 및 연결 상태
│   ├── TofGridFrame.kt              # 8×8 거리·상태 JSON 파싱
│   └── TofGridColorPalette.kt       # 거리별 히트맵 색상
├── audio/
│   ├── TofProjection.kt             # 격자 방향 투영
│   ├── TofSpatialController.kt      # 공간 음향
│   ├── RadarScanController.kt       # 좌→우 청각 스캔
│   └── DistanceToneController.kt    # 거리 단음
├── vision/                          # USB/휴대폰 카메라, YOLOX 탐지
├── speech/KoreanSpeechGuide.kt      # 한국어 TTS
└── accessibility/                   # 버튼 터치 피드백

app/src/main/res/layout/
├── activity_main.xml
└── developer_dashboard.xml

firmware/                            # XIAO/ToF 펌웨어
docs/                                # 기능별 상세 설계 및 실험 기록
```

## 아직 구현·검증이 필요한 부분

- YOLO 객체 정보와 ToF 거리/방향을 결합한 **센서 융합**
- 접근 속도·보행 경로를 포함한 **위험도 분석 및 긴급/위험 단계 판정**
- 자동 경고와 수동 객체 설명의 **통합 음성 우선순위 및 중복 억제**
- 사용자 보행 중 안정적인 **Foreground Service 기반 연속 동작**
- 실제 장착 각도, 방향 음향, 위험 경고의 현장 실험
- 원본 알고리즘 이미지 asset 포함 및 발광 영역 정합성 검증
- 최신 UI 변경 이후 Android 빌드·단위 테스트·실기기 검증

이 저장소의 코드에 존재하는 실험 기능과, 안전성을 검증한 완성 기능은 구분해야 합니다.

## 참고 문서

- [레이더 스캔](docs/radar-scan.md)
- [8×8 존별 방향 음향](docs/tof-grid-direction.md)
- [HRTF 방향 테스트](docs/hrtf-test.md)
- [거리 단음 실험](docs/distance-tone.md)
- [외부 카메라](docs/external-camera.md)
- [통합 펌웨어](firmware/ThirdEyeSenseUsb/)

---

**전공종합설계 PBL · Third Eye**  
이 README는 `feature/radar-scan` 브랜치의 현재 코드 기준으로 작성했습니다. 기존 버전별 상세 기록은 `docs/`와 Git 이력에서 확인할 수 있습니다.
