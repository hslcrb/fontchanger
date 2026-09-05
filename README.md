# FontCraft (폰트크래프트) 🎨
> **안드로이드 시스템 글꼴을 완전히 자유자재로 커스텀하고 교체하는 오픈소스 시스템 폰트 체인저**

[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)
[![Android](https://img.shields.io/badge/Platform-Android%208.0%2B-green.svg)](https://android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0.0-purple.svg)](https://kotlinlang.org)
[![Compose](https://img.shields.io/badge/Jetpack%20Compose-BOM%202024.06-brightgreen.svg)](https://developer.android.com/jetpack/compose)

FontCraft는 안드로이드 폰의 서체(글꼴)를 원하는 폰트(TTF, OTF, TTC)로 자유자재로 변경하고, 글꼴 크기·자간·행간·굵기를 실시간으로 미세 조정한 뒤 기기 환경에 맞춰 안전하게 적용할 수 있는 강력한 안드로이드 오픈소스 앱입니다.

---

## ✨ 주요 기능 (Key Features)

### 1. 🔤 모든 TTF / OTF / TTC 폰트 완벽 지원
- 기기 내부 저장소의 모든 글꼴 파일을 즉시 불러와 내부 TrueType/OpenType `name` 테이블을 네이티브로 파싱합니다.
- 패밀리 이름, 스타일(Weight), 가변 폰트(Variable Font) 여부, 버전 등의 메타데이터를 자동 추출합니다.

### 2. 🎛️ 실시간 타이포그래피 커스텀 (Real-time Typography Customizer)
- **크기 배율 (Font Size Scale)**: 0.8x ~ 1.4x 조절
- **자간 (Letter Spacing)**: -0.05em ~ 0.20em 미세 튜닝
- **행간 (Line Height)**: 1.0x ~ 2.0x 줄간격 조정
- **굵기 (Target Weight)**: 100(Thin) ~ 900(Black)
- **실시간 프리뷰**: 일반 문장, 영문/기호/숫자, 안드로이드 알림창/메시지 UI 모의 환경 실시간 렌더링.

### 3. 🚀 기기별 맞춤 적용 엔진 (Multi-Target Installation Engine)
- **Magisk / KernelSU / APatch 모듈 생성 (가장 권장)**
  - `/system/fonts` 및 `fonts.xml`을 systemless 방식으로 안전하게 오버레이하는 플래시용 ZIP 모듈을 원클릭으로 빌드합니다.
  - 내장 Root Shell을 통한 즉시 플래시 및 소프트 리부트(kill zygote) 지원.
- **Samsung One UI FlipFont (#mono_ / 테마 파크 연동)**
  - 루팅되지 않은 삼성 갤럭시 기기에서도 사용자 폰트를 적용할 수 있도록 데이터 패키징 및 단계별 백업/복원 가이드를 제공합니다.
- **Shizuku ADB 권한 연동**
  - PC 연결 없이 무선 디버깅(Shizuku) 특권을 활용한 시스템 제어 지원.

### 4. 🛡️ 1-클릭 순정 원상복구 (One-Click Restore)
- 적용된 폰트 모듈 삭제 및 시스템 폰트 렌더링 캐시 초기화 기능 내장으로 언제든 안전하게 순정 상태로 되돌릴 수 있습니다.

---

## 🏗️ 프로젝트 아키텍처 및 디렉토리 구조

```
fontchanger/
├── .github/workflows/
│   └── android-build.yml        # CI/CD: Push/PR 시 자동 APK 빌드 & 아티팩트 업로드
├── app/
│   ├── build.gradle.kts         # 앱 모듈 빌드 스크립트 (Compose, Shizuku, Coroutines)
│   ├── proguard-rules.pro       # 프로가드 난독화 및 최적화 규칙
│   └── src/main/
│       ├── AndroidManifest.xml  # 권한, FileProvider, TTF/OTF 파일 Intent 필터
│       ├── res/                 # 아이콘, 컬러, 다크 테마, XML 리소스
│       └── java/com/openfont/changer/
│           ├── FontCraftApp.kt  # Application 클래스, Shizuku 바인더 확인
│           ├── MainActivity.kt  # 메인 액티비티, 탭 네비게이션 & 인텐트 수신
│           ├── data/
│           │   ├── model/       # FontMetadata, PresetFont, ApplyTarget 모델
│           │   └── repository/  # 프리텐다드, D2Coding, 마루부리 등 프리셋 카탈로그
│           ├── engine/
│           │   ├── FontParser.kt              # TTF/OTF 내부 Name 테이블 바이트 파서
│           │   ├── MagiskModuleGenerator.kt   # Systemless Zip 모듈 패키징 엔진
│           │   ├── SamsungFlipFontBuilder.kt  # One UI FlipFont 패키징 & 가이드
│           │   ├── RootShellExecutor.kt       # su / Shizuku 특권 셸 및 리부트 실행기
│           │   └── BackupRestoreManager.kt    # 모듈 제거 및 폰트 캐시 원상복구 관리자
│           └── ui/
│               ├── theme/       # Material3 테마, 색상, 타이포그래피
│               └── screens/
│                   ├── HomeScreen.kt          # 환경 진단 및 추천 폰트 리스트
│                   ├── CustomFontScreen.kt    # TTF 불러오기 & 실시간 타이포 튜너
│                   ├── ApplyMethodScreen.kt   # 모듈 생성, 공유, 플래시 마법사
│                   └── SettingsScreen.kt      # 순정 복구 & 캐시 청소
├── gradle/libs.versions.toml     # Gradle Version Catalog (최신 의존성 관리)
├── settings.gradle.kts
├── build.gradle.kts
├── CONTRIBUTING.md              # 커밋 컨벤션 (영어 접두어 + 한국어 메시지)
└── LICENSE                      # Apache License 2.0
```

---

## 📦 빌드 및 APK 생성 방법

### 사전 요구 사항
- Android Studio Hedgehog (2023.1.1) 이상 또는 JDK 17 이상
- Android SDK (Compile SDK: 34, Min SDK: 26)

### 로컬 빌드 명령어
```bash
# 저장소 클론
git clone https://github.com/openfont-craft/fontcraft.git
cd fontcraft

# 디버그 APK 빌드 (app/build/outputs/apk/debug/app-debug.apk 생성)
./gradlew assembleDebug

# 릴리즈 APK 빌드
./gradlew assembleRelease
```

### GitHub Actions를 통한 자동 빌드
- 리포지토리에 커밋을 푸시하거나 PR을 생성하면 `.github/workflows/android-build.yml`이 자동으로 실행되어 GitHub Actions Artifacts 탭에서 완성된 APK 파일을 직접 다운로드할 수 있습니다.

---

## 🤝 기여 안내 (Contributing)

이 프로젝트는 오픈소스 커뮤니티의 기여를 환영합니다!
기여 전 [CONTRIBUTING.md](CONTRIBUTING.md)의 **커밋 컨벤션**을 꼭 확인해 주세요.

- 커밋 메시지 규칙: `<type>: <한국어 기능 설명>` (예: `feat: 가변 폰트 축 실시간 조절 슬라이더 추가`)

---

## 📜 라이선스 (License)

FontCraft 프로젝트는 [Apache License 2.0](LICENSE) 하에 배포됩니다.
