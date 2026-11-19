# TERO Factory OS · Android 태블릿 전용 앱

**연결 주소:** https://os.tero.kr/edang/

기존 TERO Factory OS 서버를 수정하지 않고 Android 태블릿에서 앱처럼 이용하기 위한 네이티브 WebView 런처입니다.

## 태블릿 전용

- **휴대폰에서는 실행되지 않습니다.** 화면 최소 폭이 600dp 이상이거나, Android 화면 크기 등급이 `large` 이상(7~8인치급 이상)인 기기만 실행됩니다.
  휴대폰에서 실행하면 "태블릿 전용 앱" 안내 후 종료되며, 브라우저로 여는 버튼을 제공합니다.
- 분할 화면(멀티 윈도우)으로 실행해도 기기 전체 화면 크기로 판단하므로 태블릿에서는 정상 실행됩니다.
- `AndroidManifest.xml`의 `<supports-screens>`로 Play 스토어/관리형 배포에서도 휴대폰에는 노출되지 않습니다.
- 판정 기준은 `MainActivity.java`의 `TABLET_MIN_WIDTH_DP`, `isTabletConfig()`에서 바꿀 수 있습니다.

## 기능

- Android 10 이상, 태블릿 가로/세로 화면 및 분할 화면 지원 (화면 회전·분할 시 페이지를 다시 불러오지 않음)
- 전체 화면에 TERO Factory OS 표시, HTML/JavaScript/DOM 저장소·로그인 쿠키 지원
- HTML 파일 첨부 및 카메라 촬영 선택, WebRTC 카메라 권한 요청 처리
- 같은 HTTPS 호스트의 CSV/XLSX/PDF 파일을 Android 다운로드 관리자에 저장 (로그인 쿠키 포함)
- 새로고침·뒤로가기, 네트워크/인증서 오류 안내
- `https://os.tero.kr` 이외의 외부 링크는 시스템 브라우저에서 열기
- HTTPS 강제 및 HTTP 혼합 콘텐츠 차단

**주의:** 앱은 웹사이트를 보여 주는 껍데기(wrapper)이며, 서버의 웹페이지 디자인을 강제로 태블릿에 최적화하지 않습니다. 화면 크기에 따른 레이아웃은 TERO 웹사이트 자체의 반응형 CSS에 따라 달라집니다.

## 방법 A: GitHub Actions에서 APK 빌드 (Android Studio 불필요)

1. GitHub에서 **새 저장소**를 만들고, 이 프로젝트 ZIP을 컴퓨터에서 압축 해제합니다.
2. 새 저장소에 압축을 해제한 폴더의 **내용 전체**(특히 `.github/workflows/build-apk.yml` 포함)를 업로드해 `main` 브랜치에 커밋합니다. **ZIP 자체를 업로드하면 안 됩니다.**
3. 저장소 상단 **Actions** → **Build TERO Android Tablet APK** → **Run workflow**를 누르세요. `main` 브랜치에 push만 해도 자동 빌드됩니다.
4. 완료된 워크플로를 열어 하단 **Artifacts**에서 **TERO-Factory-OS-Tablet-APK**를 다운로드하세요.
5. 압축 해제 후 `TERO_Factory_OS_Tablet_v1.0.0-debug.apk`를 태블릿에 복사하고 설치합니다.

GitHub Actions는 Android SDK/Gradle을 빌드 서버에서 설치하므로 사용자의 컴퓨터에 Android Studio가 필요하지 않습니다.

## 방법 B: Android Studio로 APK 빌드

1. Android Studio 최신 버전(Gradle 8.11.1, JDK 17, Android SDK 35 호환)을 준비합니다.
2. **Open**으로 이 프로젝트의 최상위 폴더(`settings.gradle`이 있는 폴더)를 엽니다.
3. Gradle 동기화를 마친 후 **Build → Build Bundle(s) / APK(s) → Build APK(s)**를 실행합니다.
4. 생성된 `app/build/outputs/apk/debug/app-debug.apk`를 태블릿에 옮겨 설치합니다.

Gradle wrapper(`gradlew`, `gradle/wrapper/gradle-wrapper.jar`, Gradle 8.11.1)가 포함되어 있어 Android Studio가 맞는 Gradle을 자동으로 받습니다.
명령줄에서는 Android SDK(`ANDROID_HOME`)가 설치된 PC에서 `./gradlew assembleDebug`(Windows: `gradlew.bat assembleDebug`)로 빌드할 수 있습니다.

## 태블릿 설치

1. 기기에 `*.apk`를 복사합니다.
2. 기기에서 APK를 열고, 해당 파일 앱에 대해 **알 수 없는 앱 설치 허용**을 요청할 경우 승인합니다.
3. **TERO Factory OS** 앱 아이콘을 실행합니다.
4. 기존 `os.tero.kr/edang/`의 아이디와 비밀번호로 로그인합니다.

이전에 다른 빌드로 설치한 앱이 있으면 **먼저 삭제한 뒤 설치**하세요. GitHub Actions는 빌드할 때마다 새 디버그 서명키를 만들기 때문에, 다른 빌드 위에 덮어 설치하면 "패키지가 기존 패키지와 충돌" 오류가 납니다.

사내 앱 검토 및 테스트를 위한 **디버그 빌드**입니다. 장기간 배포할 운영 앱에는 별도 서명키로 release APK를 만들어야 하며, 사용자에게 동일 서명키로 업데이트를 제공해야 합니다.

## 운영 및 보안 주의 사항

- 기존 AWS/Nginx/SQLite 설정은 **변경할 필요가 없습니다.**
- 본 앱은 온라인 연결이 필요하며 별도의 오프라인 DB를 만들지 않습니다.
- 비밀번호/토큰을 앱 소스에 넣지 않았습니다. 로그인 세션은 Android WebView 쿠키를 사용합니다.
- 파일을 내려받을 때 서버의 로그인 쿠키를 다운로드 요청에 전달합니다. 다운로드한 문서에 민감정보가 있다면 태블릿 잠금·기기 관리 정책이 필요합니다.
- 외부 HTTPS 도메인은 앱 밖에서 열려 쿠키가 유출되지 않습니다.
- 브라우저의 `blob:` URL 다운로드는 기본 지원하지 않습니다. 서버 API의 일반 HTTPS 파일 다운로드를 사용하거나 외부 브라우저에서 내려받으세요.
- 사진 촬영 메뉴의 동작은 카메라 앱 및 Android 버전에 따라 다를 수 있습니다.
- 태블릿의 사진 업로드/다운로드/로그인 화면은 실제 기기에서 최종 테스트해야 합니다.
- targetSdk 35, minSdk 29, Java 17, Android Gradle Plugin 8.9.2, Gradle 8.11.1.

## 변경 이력 (태블릿 전용판)

- 휴대폰 실행 차단 및 `<supports-screens>` 태블릿 전용 선언
- Android 11 이상에서 파일 첨부 시 **사진 촬영 메뉴가 나타나지 않던 문제** 수정 (`<queries>`에 카메라 앱 등록)
- 카메라 권한이 없을 때 촬영을 누르면 **오류로 실패하던 문제** 수정 (사진 첨부 전 카메라 권한 요청, 거부 시 파일 선택만 표시)
- 촬영 결과 판정 개선 및 사용하지 않은 빈 촬영 임시 파일 정리
- 분할 화면/화면 크기 변경 시 Activity 재생성 방지 (`configChanges`에 `smallestScreenSize|screenLayout` 추가)
- Gradle wrapper 추가

## 파일 위치

- 화면/연결 로직: `app/src/main/java/kr/tero/factoryos/MainActivity.java`
- 앱 설정: `app/src/main/AndroidManifest.xml`
- 파일 촬영 경로: `app/src/main/res/xml/file_paths.xml`
- 자동 APK 빌드: `.github/workflows/build-apk.yml`
