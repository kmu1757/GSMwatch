<h1 align="center">
  ⌚ GSMwatch
</h1>

<p align="center">
  <a href="https://github.com/minulx-10/GSMwatch/blob/master/LICENSE"><img loading="lazy" src="https://img.shields.io/github/license/minulx-10/GSMwatch?style=for-the-badge&logo=github" alt="License badge"/></a>
  <a href="https://github.com/minulx-10/GSMwatch/releases"><img loading="lazy" src="https://img.shields.io/github/v/release/minulx-10/GSMwatch?include_prereleases&style=for-the-badge" alt="Release badge"/></a>
  <a href="https://www.android.com/wear-os/"><img loading="lazy" src="https://img.shields.io/badge/Platform-Wear%20OS-4285F4?style=for-the-badge&logo=android&logoColor=white" alt="Wear OS badge"/></a>
</p>

<p align="center">광주소프트웨어마이스터고등학교 학생들을 위해 개발된 Wear OS 애플리케이션입니다.</p>

---

# 📖 프로젝트 개요

GSMwatch는 광주소프트웨어마이스터고등학교(GSM) 학생들의 시간 관리를 돕기 위해 기획된 앱입니다. 스마트폰을 확인하기 어려운 상황에서도 손목(Wear OS)에서 직관적으로 일과를 확인할 수 있으며, 페어링된 **휴대폰 컴패니언 앱(GSMhub)** 에서 시간표·과목을 편하게 편집하고 워치로 동기화할 수 있습니다.

### 🎯 주요 기능
1. **일과 및 휴식 타이머:** 다음 일과(기상, 수업 종료, 식사 시간 등)까지 남은 시간을 분 단위로 계산하여 제공합니다.
2. **간편한 조작:** 앱 실행 후 스와이프 제스처만으로 시간표, 식단, D-Day 정보 등을 확인할 수 있도록 구성했습니다.
3. **Wear 타일:** 앱을 열지 않아도 워치 타일에서 다음 일과까지 남은 시간을 바로 확인합니다.
4. **컴패니언 폰 앱(GSMhub):** 휴대폰에서 학년/반·커스텀 과목을 편집하면 Wear Data Layer 를 통해 워치로 동기화됩니다.

---

# 🛠 기술 스택 및 아키텍처

**1. 사용 언어 및 UI 프레임워크**
* **Kotlin**: Android 앱 개발을 위해 사용되었습니다.
* **Jetpack Compose for Wear OS**: 스마트워치의 원형 디스플레이에 적합한 UI(`ScalingLazyColumn`, `SwipeDismissableNavHost` 등)를 구현하기 위해 사용되었습니다.

**2. 디자인 및 데이터 통신**
* **디자인 최적화**: 배터리 소모를 줄이기 위해 검은색 배경을 기본으로 채택했으며, 주요 정보는 시인성을 높이기 위해 시안(Cyan) 색상으로 표기했습니다.
* **NEIS API 연동**: 공공데이터포털(NEIS) API와 연동하여 학교 급식 및 시간표 정보를 동적으로 불러옵니다. (Retrofit)

**3. 모듈 구조**
* **`:core`** — NEIS/봇 통신·모델·파싱·시간표 조립 로직 (워치/폰 공용 라이브러리).
* **`:app`** — Wear OS 워치 앱 (Jetpack Compose for Wear OS).
* **`:gsmhub`** — 휴대폰 컴패니언 앱 (Jetpack Compose Material3). `:core` 를 공유하며 Wear Data Layer 로 워치와 설정을 동기화합니다.

---

# 🚀 세부 기능

### 1) 자동 일정 변경 기능
`java.time` 라이브러리를 통해 현재 시간을 파악합니다. 금요일 석식 이후나 주말 등 정규 일과가 없는 시간대에는 다음 주 월요일의 일정을 우선하여 표시합니다.

### 2) D-Day 일정 연동
학급 서버(디스코드 봇)와 연동되어 과제 및 수행평가 일정을 표시합니다. 마감 기한이 임박한 일정은 별도의 색상으로 표시되어 쉽게 확인할 수 있습니다.

---

# 💻 로컬 빌드 설정

이 프로젝트를 로컬에서 빌드하려면 API 키 설정이 필요합니다.

### 나이스(NEIS) API Key 설정 (`local.properties`)
1. 프로젝트 루트 경로에 `local.properties` 파일을 생성합니다.
2. 발급받은 API 키를 다음 형태로 입력합니다.
   ```properties
   DATA_GSM_API_KEY=발급받은_API_키
   ```

---

# 📱 기기 설치 방법 (Sideloading)

구글 플레이스토어를 통한 배포가 아닌 경우, ADB(Android Debug Bridge)를 통해 수동으로 설치할 수 있습니다.

### 1. 워치 디버깅 활성화
1. 워치의 **[설정] > [워치 정보] > [소프트웨어 정보] > 소프트웨어 버전**을 여러 번 터치하여 '개발자 옵션'을 활성화합니다.
2. 개발자 옵션에서 **ADB 디버깅** 및 **무선 디버깅**을 활성화합니다.
3. [무선 디버깅] 메뉴에서 **[+ 새 기기 페어링]**을 선택하고 표시되는 `IP 주소`, `포트 번호`, `페어링 코드`를 확인합니다.

### 2. PC 연결 및 설치
1. [Android SDK 플랫폼 도구](https://developer.android.com/tools/releases/platform-tools?hl=ko)를 다운로드하여 압축을 해제합니다.
2. 해당 폴더에서 터미널(또는 명령 프롬프트)을 열고 아래 명령어로 워치를 페어링합니다.
   ```sh
   .\adb.exe pair [IP주소:포트번호]
   ```
3. 확인된 기기로 앱을 전송하고 설치합니다.
   ```sh
   .\adb.exe install 파일명.apk
   ```

---

<p align="center">
  <b>GSMwatch</b> Copyright © 2026. <br>
  Released under the MIT License.
</p>
