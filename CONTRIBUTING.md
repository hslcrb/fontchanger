# 기여 가이드라인 (Contributing to FontCraft)

FontCraft 프로젝트에 기여해 주셔서 감사합니다! 
이 프로젝트는 안드로이드 기기의 폰트를 제약 없이 자유롭게 커스텀하고 교체할 수 있는 오픈소스 프로젝트입니다.

---

## 📌 커밋 컨벤션 (Commit Convention)

FontCraft는 **영어 접두어(Type)**와 **한국어 설명(Message)**으로 구성된 커밋 메시지 규칙을 따릅니다.

### 형식
```
<type>: <한국어 설명>

[본문 내용 (선택사항)]
```

### 접두어(Type) 규칙
- `feat`: 새로운 기능 추가 (예: `feat: 가변 폰트 축(Axes) 실시간 조절 슬라이더 추가`)
- `fix`: 버그 수정 (예: `fix: 특정 TTF 파일 파싱 시 인덱스 초과 예외 해결`)
- `docs`: 문서 수정 (예: `docs: README 빌드 및 설치 가이드 최신화`)
- `style`: 코드 스타일 수정 (코드 포맷팅, 세미콜론 누락 등, 로직 변경 없음)
- `refactor`: 코드 리팩터링 (성능 개선 및 구조 개편)
- `test`: 테스트 코드 추가 및 수정
- `chore`: 빌드 설정, 의존성 업데이트, 비 소스코드 묶음 변경 (예: `chore: Gradle 버전 카탈로그 및 종속성 업데이트`)

---

## 🛠️ 개발 환경 설정

1. **JDK**: Java 17 이상
2. **Android Studio**: Android Studio Hedgehog / Iguana / Jellyfish 이상
3. **Android SDK**: Compile SDK 34, Min SDK 26

### 빌드 및 실행
```bash
# 디버그 APK 빌드
./gradlew assembleDebug

# 릴리즈 APK 빌드
./gradlew assembleRelease
```
