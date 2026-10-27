# GSMwatch 인수인계 문서

이 문서는 GSMwatch 프로젝트를 이어받는 사람을 위한 안내입니다. 코드만으로는 알기 어려운 구조·설정·주의점을 정리합니다.

---

## 1. 프로젝트 개요

광주소프트웨어마이스터고(GSM) 학생용 앱. **워치 앱**과 **폰 컴패니언 앱**으로 구성됩니다.

| 모듈 | 대상 | 역할 |
|---|---|---|
| **`:core`** | 라이브러리 | NEIS/봇 통신·모델·파싱·시간표 조립 (워치/폰 공용). `com.gsminulx.gsmwatch.core` |
| **`:app`** | Wear OS 워치 | 타이머·시간표·급식·D-Day, Wear 타일. Jetpack Compose for Wear OS |
| **`:gsmhub`** | 휴대폰 | 시간표/급식/D-Day 열람·편집, 설정, DataGSM 로그인. Jetpack Compose Material3 |

> `:app` 과 `:gsmhub` 의 `applicationId` 는 동일(`com.gsminulx.gsmwatch`) — **Wear 컴패니언 표준**(폰 앱 설치 시 워치 앱 자동 배포). 버그 아님, 유지.

---

## 2. 빌드 방법

- **JDK**: Android Studio 번들 JBR(JDK 21) 권장. Gradle 9.8.2 / AGP 9.4.0 / Kotlin 2.4.21.
- **SDK**: compileSdk 37(최신 AndroidX 요구), targetSdk 36. AGP 9 내장 Kotlin 을 쓰며, KGP 버전은 루트
  `build.gradle.kts` 에서 `kotlin-android` 플러그인을 `apply false` 로 올려 고정한다.
- **`local.properties`** (프로젝트 루트, git 에 올라가지 않음 — **직접 만들어야 함**):
  ```properties
  sdk.dir=<Android SDK 경로>
  DATA_GSM_API_KEY="<NEIS 오픈API 키>"        # open.neis.go.kr 발급
  BOT_API_URL="<학급 D-Day 디스코드 봇 주소>"   # 없으면 D-Day 비어있음
  DATAGSM_CLIENT_ID="<DataGSM OAuth Client ID>"       # 폰 로그인용 (아래 4번)
  DATAGSM_CLIENT_SECRET="<DataGSM OAuth Client Secret>"
  DATAGSM_REDIRECT_URI="gsmhub://oauth2redirect"
  ```
  ⚠️ **주의**: NEIS 키 등 값은 **따옴표를 포함**해 저장돼 있고, build.gradle 이 `.replace("\"","")` 로 벗겨 씀.
  키를 추가할 때 이 규칙을 지킬 것.
- **빌드**: `./gradlew :app:assembleDebug :gsmhub:assembleDebug`
- **테스트**: `./gradlew :core:testDebugUnitTest` (일과 상태 계산·D-Day 파싱)
- **스크린샷**: `./gradlew :app:recordRoborazziDebug :gsmhub:recordRoborazziDebug` — Robolectric 으로 워치(원형)·폰(라이트/다크)
  화면을 PNG 로 렌더링(`*/build/outputs/roborazzi/`). UI 를 바꿨으면 이걸로 먼저 눈으로 확인할 것.
- **에뮬레이터**: 폰은 RAM 4GB 이상 권장(2GB 는 저메모리 킬러로 앱이 종료됨). 워치는 별도 Wear OS AVD 필요.

---

## 3. 아키텍처 / 데이터 흐름

```
NEIS OpenAPI ─┐                    ┌─ 워치(:app)  ── Wear 타일
학급 봇(D-Day)─┼─ :core Repository ─┤   (통신 불안정 → 캐시 우선)
              │  (fetch+파싱+캐시)   └─ 폰(:gsmhub) ── DataGSM OAuth 로그인
              │
DataGSM ───────┘ (OAuth 로그인만: 학년/반 자동. 급식/시간표는 NEIS 사용)
```

- **데이터원**: 급식/시간표 = **NEIS**, D-Day = **학급 디스코드 봇**(학생들이 숙제/수행평가 직접 추가).
  DataGSM 은 급식/시간표를 NEIS 프록시로 제공하므로 데이터원으로는 쓰지 않고 **로그인(학년/반 자동)만** 사용.
- **캐시**: `:core` 는 컴팩트 페이로드(`SyncPayload`)로 캐시. 네트워크 실패/에러 응답 시 캐시 유지.
- **폰 → 워치 동기화**(Wear Data Layer, `SyncKeys`):
  - `/gsm_settings`: 학년/반 + 커스텀 과목 스냅샷 → 워치 `SettingsSyncService` 수신.
  - `/gsm_data`: 폰이 받은 시간표/급식 페이로드 → 워치가 최신 것 사용(자체 통신 실패 대비).

### 핵심 파일
- `core/.../SchoolRepository.kt` — fetch/캐시/파싱, `Schedule.kt` — 시간표 조립, `SyncKeys/SyncPayload.kt` — 동기화.
- `core/.../DayStatus.kt` — '지금' 상태 계산(진행 중/다음 일과까지/주말 입소까지 + 진행률), `Dday.kt` — 마감 파싱·정렬·긴급도.
- `core/src/main/res/drawable/ic_*.xml` — Material Symbols 아이콘(워치·폰 공용, `com.gsminulx.gsmwatch.core.R`).
- `app/.../presentation/` — `WatchViewModel`, `SchoolTimer`(표시 상태), `HomeScreen`, `WeeklyScreens`(2주 페이저),
  `EditSubjectScreen`(RemoteInput), `TasksScreen`, `SettingsScreen`(Picker), `Components`(ScreenList 등 공통 골격),
  `GsmTileService`(Material3TileService 타일), `SettingsSyncService`(수신).
- `gsmhub/.../` — `HubViewModel`, `HubApp`(골격·탭), `TimetableTab`/`MealsTab`/`DdayTab`/`SettingsTab`,
  `HubComponents`(연결형 버튼 그룹 등), `WearSync`(push), `DataGsmAuth`(OAuth), `MainActivity`(딥링크).

### 디자인 원칙 (0.8.0~)
- 워치·타일·폰 모두 **Material 3 Expressive**. 하드코딩 색/크기 대신 `MaterialTheme` 토큰을 쓴다.
- 색은 **동적 색상 우선, 브랜드 팔레트 폴백**(워치 `GsmBrandColorScheme`, 타일 `TileBrandColorScheme`, 폰 `ui/theme/Color.kt`).
- 워치 화면은 `ScreenList`(ScreenScaffold + TransformingLazyColumn) 위에 아이템마다 `itemModifier/itemTransformation` 을 적용.
- 뒤로 가기 버튼을 두지 않는다(워치: 스와이프 백, 폰: 시스템/예측형 뒤로가기).

---

## 4. DataGSM OAuth (폰 로그인)

폰에서 GSM 계정 로그인 → 학생 학년/반 자동 설정. **OAuth 2.0 Authorization Code + PKCE**.

- **클라이언트 등록**: [datagsm.kr/clients](https://www.datagsm.kr/clients) 에서 OAuth Client 생성.
  - 리다이렉트 URI: **`gsmhub://oauth2redirect`** (정확히)
  - 권한: **self_read**
  - 발급된 **Client ID / Secret** 을 `local.properties` 에.
- **주의(실측)**: authorize 요청에 `scope=self:read` 를 **명시하면 400** 발생. scope 는 서드파티용이라
  기본 self:read 는 생략해야 함(`DataGsmAuth.buildAuthorizeUrl` 참고).
- 토큰 교환은 표준 form 이 아니라 **JSON body**. confidential 클라이언트라 `client_secret` 도 함께 전송.
- 문서: [docs.datagsm.kr](https://docs.datagsm.kr) (OpenAPI/OAuth).

---

## 5. CI / 릴리스 (배포)

- **CI** (`.github/workflows/ci.yml`): 모든 push/PR 에서 `:core` 단위 테스트 → 워치/폰 디버그 APK 빌드 →
  Roborazzi 스크린샷 기록. 스크린샷은 Actions 실행 결과의 **`screenshots` artifact** 로 내려받아 볼 수 있다.

- `v0.7.0` 형식의 **태그를 push** 하면 GitHub Actions(`.github/workflows/release.yml`)가:
  워치(`GSMwatch_vX.apk`) + 폰(`GSMhub_vX.apk`) 을 빌드·서명해 GitHub Release 에 자동 첨부.
- **필요한 GitHub Secrets** (Settings → Secrets and variables → Actions):
  - `API_KEY` (NEIS), `BOT_API_URL`, `DATAGSM_CLIENT_ID`, `DATAGSM_CLIENT_SECRET`
  - 이 시크릿이 없으면 빌드는 되지만 해당 기능(예: 폰 OAuth)이 동작하지 않음.
- 서명 키스토어: `keystore/release.keystore` (비번 `gsmwatch`).

---

## 6. 알아둘 점 / 주의

- **보안**: 릴리스 키스토어와 비밀번호가 리포/워크플로우에 노출돼 있음. 정식 배포 규모가 커지면
  키 재발급 + GitHub Secrets 로 이전 권장.
- **학교 코드**: NEIS 학교코드는 교육청 개편 등으로 바뀔 수 있음. 데이터가 안 오면
  `open.neis.go.kr/hub/schoolInfo?...&SCHUL_NM=광주소프트웨어마이스터고등학교` 로 현재 코드 확인 후
  `core/.../Constants.kt` 수정.
- **D-Day 봇**: 이 리포에 없는 외부 서버(`BOT_API_URL`). 봇이 꺼져 있으면 D-Day 비어있음(정상).
- **커스텀 과목 동기화**: 폰이 편집 주체(스냅샷). 폰에서 편집 시 워치의 로컬 편집이 덮어써짐.

---

## 7. 문의/기여

이슈·기여는 GitHub 저장소의 Issues / Pull Request 로. 자세한 변경 이력은 [CHANGELOG.md](CHANGELOG.md) 참고.
