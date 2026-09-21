# 지문인식 정확도 향상

<!-- BEGIN USER-EDITABLE DESCRIPTION -->
Samsung Galaxy에서 지문 관련 시스템 액티비티를 빠르게 실행하기 위한 Android 프로젝트입니다.

> **이 구간은 직접 수정하세요.** 앱 소개, 기능, 사용 대상, 주의사항 등을 원하는 문구로 바꾸면 됩니다.
> 별도의 긴 설명을 관리하고 싶다면 `APP-DESCRIPTION.md`도 함께 수정하세요.
<!-- END USER-EDITABLE DESCRIPTION -->

## 기능

- 지문 1~4 바로가기 (`selected_id` 1~4)
- 지문 인식 설정 바로가기
- Shizuku를 이용한 권한이 필요한 작업 지원
- Assistant 실행 경로에서 `assistant` Secure Setting을 임시로 변경한 뒤 원래 값으로 복구
- 검은 배경 + 흰색 지문 아이콘
- Jetpack Compose 기반 UI

## 빌드

Android Studio에서 프로젝트를 연 후 Gradle Sync를 실행하고 `app`을 빌드하세요.

## 호환성 주의

이 프로젝트는 Samsung One UI / Android의 내부 액티비티와 비공개(non-exported) 구성에 의존합니다.
특정 기기 또는 OS 버전에서만 동작할 수 있으며, Samsung의 업데이트에 따라 동작이 바뀌거나 사용할 수 없게 될 수 있습니다.

또한 Shizuku를 사용하는 기능은 사용자가 별도로 Shizuku를 설치하고 활성화해야 할 수 있습니다.

## 오픈소스 및 라이선스

이 저장소의 소스 코드는 GNU General Public License version 3.0 (GPL-3.0-only) 조건으로 배포합니다.

이 라이선스를 선택한 이유는 프로젝트에 포함된 Root Activity Launcher 유래 실행 로직이 해당 프로젝트의 GPL-3.0 코드에 기반하기 때문입니다. 출처와 적용 범위는 `UPSTREAM-SOURCE-NOTICE.md`와 `THIRD-PARTY-NOTICES.md`를 확인하세요.

외부 의존성은 각 원 프로젝트의 라이선스를 따릅니다. 이 저장소에서 확인할 수 있는 외부 의존성 고지는 `THIRD-PARTY-NOTICES.md`에 정리했습니다.

## AI 개발 지원

이 프로젝트는 개발 과정에서 OpenAI ChatGPT, Anthropic Claude, Google Gemini의 도움을 받아 코드 작성, 디버깅, 조사, 문서화 및 UI 작업을 수행했습니다.

AI 도구의 사용은 해당 회사의 후원, 인증, 협력 또는 공식적인 관계를 의미하지 않습니다.
자세한 내용은 `AI-ASSISTANCE.md`를 참고하세요.

## 책임 및 보증

이 프로젝트는 "있는 그대로" 제공됩니다. 기기, Android 버전, One UI 버전에 따라 동작이 달라질 수 있으므로 사용 전에 코드를 검토하시기 바랍니다.

---

**Project:** FingerprintAccuracyEnhancer  
**Package:** `com.userapp.fplauncher`


## 릴리즈 APK 만들기

1. `keystore.properties.example`을 프로젝트 루트의 `keystore.properties`로 복사합니다.
2. `storeFile`, `storePassword`, `keyAlias`, `keyPassword`를 실제 릴리즈 키 정보로 수정합니다.
3. Android Studio에서 **Build > Generate Signed App Bundle / APK > APK**를 선택하고 `release` 변형을 빌드합니다.
4. 생성된 APK는 보통 `app/build/outputs/apk/release/` 아래에 있습니다.

**중요:** `*.jks`, `*.keystore`, `*.p12`, `keystore.properties`는 GitHub에 올리면 안 됩니다. `.gitignore`에 이미 등록되어 있습니다.

키 생성과 서명 설정의 자세한 예시는 `RELEASE-BUILD.md`를 확인하세요.
