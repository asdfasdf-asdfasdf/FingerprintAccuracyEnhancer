# 지문인식 정확도 향상

<!-- BEGIN USER-EDITABLE DESCRIPTION -->

One UI 8.5에서 기본 설정 화면에서 제공되지 않는 지문인식 정확도 향상 기능을 다시 실행할 수 있도록 도와주는 앱입니다.

액티비티를 직접 실행하는 방식으로는 사용할 수 있지만, 일반적인 설정 화면 경로에서는 진입할 수 없는 기능을 간편하게 사용할 수 있도록 만들었습니다.

<!-- END USER-EDITABLE DESCRIPTION -->

## 기능

- 지문인식 정확도 설정 바로가기
- 지문 인식 설정 바로가기
- 지문 1~4에 `selected_id` 1~4를 전달하는 바로가기
- Shizuku를 이용한 필요한 권한 처리 지원
- 실행에 사용하는 `assistant` Secure Setting은 실행 전에 원래 값을 저장하고, 실행 후 복원합니다.
- 시스템 다크/라이트 모드에 맞춰 UI와 상태 표시줄을 전환합니다.
- 검은색 배경 + 흰색 지문 아이콘

## 지원 환경

**Samsung Galaxy + One UI 8.5 (Android 16.1) 이상**만 지원합니다.

Android의 `SDK_INT`는 Android 16의 마이너 릴리스에서도 36으로 유지되므로, 앱은 `SDK_INT_FULL`을 사용해 Android 16.1 이상을 추가로 확인합니다. Android 공식 문서에서도 마이너 SDK 버전 확인에 `SDK_INT_FULL`을 사용하도록 안내합니다.

또한 Samsung의 `ro.build.version.oneui` 버전을 확인하여 One UI 8.5 미만에서는 기능 화면 대신 지원되지 않는 환경 안내를 표시합니다.

## Shizuku

첫 실행 시 필요한 경우 Shizuku 권한을 한 번 요청합니다. 권한이 승인되면 필요한 설정 권한을 처리하고, 이후에는 같은 권한을 반복해서 요청하지 않도록 합니다.

Shizuku는 별도로 설치 및 활성화되어 있어야 합니다.

## 개발 환경

- Android Gradle Plugin 8.13.0
- Gradle 8.13
- JDK 17
- compileSdk 36
- 앱 실행은 Android 16.1 / One UI 8.5 이상으로 제한됩니다.

## 빌드

Android Studio에서 프로젝트를 연 후 Gradle Sync를 실행하세요.

### Release APK

1. `keystore.properties.example`을 프로젝트 루트의 `keystore.properties`로 복사합니다.
2. 실제 release keystore 정보로 값을 수정합니다.
3. Android Studio에서 **Build > Generate Signed App Bundle / APK > APK**를 선택하고 `release` 변형을 빌드합니다.

`release-key.jks`, `keystore.properties` 등의 서명 정보는 GitHub에 올리지 않습니다. `.gitignore`에 이미 포함되어 있습니다.

자세한 내용은 `RELEASE-BUILD.md`를 참고하세요.

## 오픈소스 및 라이선스

이 저장소의 소스 코드는 GNU General Public License version 3.0 (GPL-3.0-only) 조건으로 배포합니다.

이 프로젝트에는 Root Activity Launcher의 GPL-3.0 코드에서 유래한 실행 로직이 포함되어 있습니다. 출처와 범위는 `UPSTREAM-SOURCE-NOTICE.md`와 `THIRD-PARTY-NOTICES.md`를 확인하세요.

외부 의존성의 라이선스 정보는 `THIRD-PARTY-NOTICES.md`에 정리되어 있습니다.

## AI 개발 지원

개발 과정에서 OpenAI ChatGPT, Anthropic Claude, Google Gemini의 도움을 받아 코드 작성, 디버깅, 로그 분석, 조사, 문서화 및 UI 작업을 수행했습니다.

AI 도구의 사용은 해당 회사의 후원, 인증, 협력 또는 공식적인 관계를 의미하지 않습니다. 자세한 내용은 `AI-ASSISTANCE.md`를 확인하세요.

## 책임 및 보증

이 프로젝트는 현재 상태 그대로 제공됩니다. Samsung One UI / Android 내부 구성 요소에 의존하므로 향후 OS 또는 앱 업데이트에 따라 동작이 변경될 수 있습니다.

---

**Project:** FingerprintAccuracyEnhancer  
**Package:** `com.userapp.fplauncher`


## 테마

앱 내부 UI는 시스템 다크/라이트 모드를 따라가며, XML 루트 테마는 API 21+에서 제공되는 framework `Theme.Material.Light.NoActionBar`를 사용합니다.
