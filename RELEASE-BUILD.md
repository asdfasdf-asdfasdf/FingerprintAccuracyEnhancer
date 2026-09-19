# 릴리즈 APK 서명 가이드

## 1. 릴리즈 키 생성

Android Studio의 Terminal 또는 JDK가 설치된 터미널에서 프로젝트 루트로 이동한 뒤 다음 형식으로 실행합니다.

```powershell
keytool -genkeypair -v -keystore release-key.jks -alias fingerprintaccuracy -keyalg RSA -keysize 2048 -validity 10000
```

비밀번호와 키 정보를 직접 정하고 안전한 곳에 백업하세요.

## 2. 서명 정보 파일 생성

`keystore.properties.example`을 복사해서 프로젝트 루트에 `keystore.properties`를 만들고 실제 값을 넣습니다. 예:

```properties
storeFile=release-key.jks
storePassword=YOUR_STORE_PASSWORD
keyAlias=fingerprintaccuracy
keyPassword=YOUR_KEY_PASSWORD
```

`release-key.jks`와 `keystore.properties`는 `.gitignore`에 등록되어 있어 공개 저장소에 커밋하지 않습니다.

## 3. APK 빌드

Android Studio에서 **Build > Generate Signed App Bundle / APK > APK**를 선택하고 `release`를 선택합니다.

또는 터미널에서:

```powershell
.\gradlew.bat assembleRelease
```

생성 위치:

```text
app\build\outputs\apk\release\app-release.apk
```

## 키 분실 주의

같은 앱의 향후 업데이트를 같은 서명 키로 배포하려면 릴리즈 키를 계속 보관해야 합니다. 키 파일과 비밀번호를 별도로 안전하게 백업하세요.


## 공개 배포 전 확인

- `release-key.jks`는 GitHub에 업로드하지 않습니다.
- `keystore.properties`도 GitHub에 업로드하지 않습니다.
- APK만 GitHub Releases에 첨부합니다.
- 앱은 Samsung Galaxy의 One UI 8.5 / Android 16.1 이상에서만 기능 화면을 표시합니다.
