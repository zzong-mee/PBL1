# Third Eye (써드아이)

> 시각장애인의 주변 인지와 보행 보조를 목표로 개발 중인 **Android + XIAO ESP32-S3 Sense + 듀얼 VL53L8CX** 전공종합설계 PBL 프로젝트

**문서 기준 브랜치:** `feature/obstacle-risk-engine` (2026-11-20)  
**개발 상태:** 객체 추적·ToF 독립 접근 위험 감지·단계별 음성 경고 코드 구현. **Android 빌드, 자동 테스트 실행, 실기기 검증은 아직 수행하지 않음.**  
**안전 주의:** 연구용 시제품이며 실제 보행 시 안전을 보장하지 않습니다. 보행 안전 보조 수단으로 의존해서는 안 됩니다.

## 시스템 구성

```text
[안경] XIAO ESP32-S3 Sense + 카메라 ── USB ──┐
[안경] 좌/우 VL53L8CX (각 8×8, 최대 4m) ─────┤
                                              ▼
                                         Android 앱
                  ┌───────────────────────────┴───────────────────────┐
                  ▼                                                   ▼
          JPEG → YOLOX-Nano                              ToF 8×8 × 2
                  │                                                   │
           객체 탐지·추적 ID                            유효성·시계열·인접 셀 확인
                  │                                                   │
        영상상 크기 변화 기반 TTC                         3m 이내 상대 접근 감지
           (진단용 추정값)                                           │
                  │                                        주의 / 위험 분류
          중앙 객체 수동 설명                                        │
                  └─────────────── 독립 처리 ──────────── 자동 TTS 경고
```

**현재 자동 경고는 ToF 데이터만으로 동작합니다.** 카메라 객체와 특정 ToF 셀을 실제 물체 단위로 연결하는 캘리브레이션은 완료되지 않았습니다. 기존 `ObstacleFusion` 등 융합 실험 코드가 존재하더라도 메인 화면의 자동 경고에는 사용하지 않습니다.

## 구현 현황

| 구분 | 현재 코드 상태 | 검증 및 한계 |
| --- | --- | --- |
| 사용자 화면 | 중앙 물체 수동 인식·한국어 TTS·일시정지/재개·사용법·개발자 모드 | 스마트폰 실기기 확인 필요 |
| 외부 카메라 | XIAO USB JPEG 입력, YOLOX-Nano 로컬 객체 인식 | 연결 안정성·추론 성능 미검증 |
| 객체 추적 | 2단계 IoU 매칭 기반 지속적인 track ID | 겹침·가림·ID 변경 실험 필요 |
| 영상 TTC | 동일 추적 객체의 연속 바운딩 박스 확대율로 TTC 추정 | **실제 거리·충돌 시간 아님**, 개발자 진단용 |
| ToF 데이터 | 좌우 VL53L8CX 8×8(총 128셀), 거리·상태·나이 검증 | 펌웨어 데이터 형식·실측 확인 필요 |
| ToF 접근 감지 | 셀별 거리 감소율, 최근 3프레임 방향 확인, 인접 셀 및 연속 후보 확인 | 움직이는 물체와 사용자 이동 구별 불가 |
| 자동 위험 경고 | ToF만으로 CAUTION/DANGER 판단, 한국어 TTS, 재경고 제한 | 실환경 오경보·미탐·경고 지연 미측정 |
| 객체별 거리·TTC | 칼만 필터·연관기·추정기 코드 존재 | **캘리브레이션 미완료**, 객체별 실측 거리 표시 안 함 |
| 개발자 화면 | 카메라/객체 인식, 연결·모드, ToF 격자, 전체 테스트 관련 진단 | 실제 화면 구성·작동 확인 필요 |
| 휴대폰 카메라 | 개발자 화면에서 수동 선택하는 Camera2 입력 | USB 카메라 자동 대체 아님 |
| 접근성 | 한국어 TTS, 버튼 피드백, TalkBack 고려 | 실제 접근성 설정별 테스트 필요 |

## ToF 독립 장애물 경고 알고리즘

1. 좌·우 센서에서 8×8 거리값을 수신합니다. 센서별 64개 셀의 원래 인덱스를 유지합니다.
2. 거리 1–4,000mm, 상태 코드 `5/6/9`, 데이터 나이 최대 1,000ms 등 유효성을 검사합니다.
3. 동일 센서·셀의 최근 측정값을 비교합니다. 연속 프레임 간격은 **80–1,000ms**이며, 최근 3프레임에서 지속적으로 거리가 감소해야 합니다.
4. **3,000mm 이내**이고 상대 접근속도가 **800mm/s 이상**인 셀을 후보로 선정합니다. 인접한 최소 2개 셀의 접근과 이전 후보와의 공간적 연속성을 확인합니다.
5. 확인된 후보의 접근속도 중앙값과 최근 거리를 이용해 근사 TTC를 계산합니다. 이는 두 대상 간 거리 변화 기반 값이며 충돌을 보장하지 않습니다.
6. 조건에 따라 음성 경고를 발생시킵니다.

| 위험 단계 | 현재 실험용 조건 | 음성 안내 |
| --- | --- | --- |
| **주의 (CAUTION)** | 접근 후보가 확인되었으나 위험 단계에는 해당하지 않음 | “주의. 가까워지는 물체가 감지되었습니다.” |
| **위험 (DANGER)** | 거리 **1.5m 이하**이면서 추정 TTC **2초 이하** | “위험. 가까워지는 물체가 감지되었습니다.” |

- 같은 단계의 음성 경고는 기본 **5초 재알림 제한**을 적용합니다.
- 주의에서 위험으로 상승하면 제한 시간 중에도 경고할 수 있습니다.
- 일시정지·연결 상태 변경·화면 종료 시 추적 및 경고 상태를 초기화합니다.
- **카메라 객체 이름이나 좌우 방향을 ToF 경고 문구에 넣지 않습니다.** 장착 방향과 센서-카메라 대응 관계가 보정되지 않았기 때문입니다.
- 접근속도는 물체의 절대 이동속도가 아닌 **센서와 물체 사이의 상대 거리 변화율**입니다.
- 정지 장애물, 측면 이동, 가려진 물체, 센서 사각지대 등은 탐지되지 않을 수 있습니다. 필터링은 오경보를 줄이는 대신 경고 지연·미탐을 늘릴 수 있습니다.

## 캘리브레이션 현황

**현재 미실시.** 카메라와 좌우 ToF 센서를 안경의 최종 위치에 고정한 뒤 다음을 수행할 예정입니다.

1. 센서 장착 방향·시야각·겹침 영역 확인
2. 여러 거리(예: 0.5/1/2/3m)와 방향에서 카메라 영상 좌표 ↔ ToF 셀 대응 측정
3. 객체 겹침·셀 경계·실제 거리 오차 검증
4. 보정값을 객체-거리 연관기에 적용하고 객체별 거리·접근속도·TTC 평가

현재 `TrackedObjectRangeEstimator(null)`은 보정값이 없다는 의미이며, 개발자 화면은 객체별 거리 대신 **“거리 미보정”**을 표시합니다. 영상 TTC가 계산되면 **“영상 TTC …초(추정)”**으로 구별합니다.

## 개발자 화면 및 ToF 격자

USB 연결·카메라 객체 인식·거리센서 8×8 격자·전체 테스트 기능을 중심으로 진단합니다. 거리 격자는 좌우 센서별 원본 64개 존을 유지하며 유효하지 않은 값은 거리로 취급하지 않습니다.

| 거리(mm) | 히트맵 색상 |
| --- | --- |
| 1–100 | 빨강 |
| 101–200 | 주황 |
| 201–300 | 노랑 |
| 301–500 | 연두 |
| 501–1,000 | 초록 |
| 1,001–4,000 | 파랑 |
| 무효·만료 | 회색 / — |

`TofGridFrame` 파서는 `grid_size=8`, `grid_order=st_uld_raw`, 센서별 64개 거리·상태 배열 및 데이터 나이 정보를 요구합니다. 4×4 입력을 8×8로 자동 변환하지 않습니다.

전체 테스트의 흐름도 이미지는 별도 `app/src/main/assets/algorithm_flow.png` 파일이 필요할 수 있습니다. 현재 흐름도 asset 포함 여부 및 실제 발광 표시 동작은 확인이 필요합니다.

## 내려받기 및 첫 검증

**필수:** Android Studio, JDK 17, Android SDK 36, USB Host 지원 Android 스마트폰, XIAO ESP32-S3 Sense 및 듀얼 VL53L8CX. Android 앱은 Kotlin/XML 기반이며 패키지는 `com.guideeye.app`입니다.

```bash
git clone -b feature/obstacle-risk-engine https://github.com/zzong-mee/PBL1.git
cd PBL1
./gradlew assembleDebug
./gradlew testDebugUnitTest
```

Windows에서는 `gradlew.bat assembleDebug`, `gradlew.bat testDebugUnitTest`를 실행합니다. 기존 저장소가 있다면:

```bash
git fetch origin
git switch feature/obstacle-risk-engine
git pull origin feature/obstacle-risk-engine
```

Android Studio에서 프로젝트를 열고 Gradle Sync → Debug 빌드 → 단위 테스트 → 스마트폰 설치 순서로 확인합니다. 위 명령은 **실행 안내이며 이 브랜치의 빌드/테스트 성공을 의미하지 않습니다.**

### 권장 검증 순서

- [ ] Gradle Sync 및 `assembleDebug` 성공
- [ ] `testDebugUnitTest` 통과, 실패 테스트 수정
- [ ] Android 스마트폰에서 앱 실행 및 TTS·수동 객체 인식 확인
- [ ] XIAO USB JPEG 프레임과 YOLO 탐지·track ID 확인
- [ ] 좌우 ToF 8×8 실측 데이터, 유효 상태, 프레임 시간 확인
- [ ] 통제된 환경에서 3m 이내 접근·주의·위험 음성 확인
- [ ] 정지 물체·거리값 튐·측정 누락·센서 셀 경계에서 오경보 및 지연 측정
- [ ] 센서 최종 장착 후 캘리브레이션 및 객체별 거리 연관 검증

실험은 통제된 공간에서 실시하고, 경고 누락이 발생해도 사고로 이어지지 않도록 별도 안전 인력을 배치합니다.

## 주요 코드 위치

```text
app/src/main/java/com/guideeye/app/
├── MainActivity.kt                        # 사용자 화면, ToF 독립 경고 TTS
├── DeveloperActivity.kt                   # 개발자 카메라/센서 진단
├── fusion/
│   ├── TofApproachTracker.kt              # ToF 셀별 접근 추적·시계열 필터
│   ├── IndependentTofRiskEngine.kt        # CAUTION/DANGER 분류
│   ├── IndependentTofWarningGate.kt       # 재경고 제한·위험 상승 처리
│   ├── VisualExpansionTtcEstimator.kt     # 영상 확대 기반 TTC 추정
│   ├── TofObjectAssociator.kt             # 보정값 필요: 객체↔ToF 연관
│   ├── ObjectRangeKalmanFilter.kt         # 거리/속도 상태 추정
│   ├── TrackedObjectRangeEstimator.kt     # 추적 객체별 거리 추정
│   └── ObstacleFusion.kt                  # 이전 실험용 미보정 융합 로직
├── vision/                                # YOLOX, 객체 추적, 카메라 입력
├── usb/TofGridFrame.kt                    # 8×8 JSON 파싱
└── speech/KoreanSpeechGuide.kt            # 한국어 음성 안내

app/src/test/java/com/guideeye/app/fusion/
├── CalibrationFreeRiskTest.kt
├── IndependentTofWarningGateTest.kt
└── TofApproachTemporalTest.kt
```

## 향후 개발 과제

1. **우선:** Android 빌드·JUnit 테스트 실행 및 컴파일 오류 수정
2. 실제 XIAO·듀얼 ToF 연결, USB 데이터·카메라 처리 성능 측정
3. 거리값 노이즈·셀 경계·경고 지연·오경보/미탐 실측 및 임계값 조정
4. 최종 안경 장착 후 센서-카메라 캘리브레이션
5. 보정된 YOLO 객체↔ToF 거리 매칭과 객체별 위험도 평가
6. 음성 우선순위·백그라운드 동작·접근성 및 보행 환경 안전성 검증

## 관련 자료

- [통합 펌웨어](firmware/ThirdEyeSenseUsb/)
- [외부 카메라](docs/external-camera.md)
- [8×8 ToF 방향 음향](docs/tof-grid-direction.md)
- [레이더 스캔](docs/radar-scan.md)
- [HRTF 방향 테스트](docs/hrtf-test.md)

---

**전공종합설계 PBL · Third Eye**  
이 문서는 `feature/obstacle-risk-engine`의 구현 상태를 설명합니다. 코드 구현과 실기기 안전성 검증은 별개의 단계입니다.
