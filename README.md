# GenieBreaker

KT GenieTV MA4000 / MARUSYS, Android 14를 위한 TV 블루투스 앱입니다.
다크 네이비·민트 UI, 큰 선택 영역과 방향키 포커스를 제공합니다.

## 첫 버전

- 주변 Bluetooth Classic 기기 검색·검색 중지
- 등록된 기기 및 주변 기기 표시, 중복 제거
- 앱 내 페어링 요청 및 상태 갱신 (최종 PIN·동의는 Android 보안 확인 창)
- 근처 기기 권한 요청과 Bluetooth 켜기 요청
- 기본 설정 앱으로 이동하지 않음
- ADB·루트·Shizuku 불필요

페어링은 실제 연결과 다릅니다. 오디오/HID 강제 연결·해제·등록 삭제, BLE 전용 검색은 아직 지원하지 않습니다.
제조사 펌웨어가 시스템 페어링 확인 창이나 켜기 요청을 차단하면 해당 동작은 실패할 수 있습니다.
Shizuku 권한 확장은 후속 범위입니다. 아직 MA4000 실기기에서 검증하지 않았습니다.

## APK

[Releases](https://github.com/JavaJoaKR/GenieBreaker/releases)에서 최신 개발 빌드 APK를 받으세요.
모든 브랜치 push마다 빌드·Lint 후 커밋별 prerelease와 APK·SHA256 체크섬을 발행합니다.
PR은 검증과 artifact 업로드만 수행합니다. 여러 커밋을 한 번에 push하면 해당 push의 최종 커밋을 빌드합니다.
Kotlin 코드와 Android 기본 UI만 사용하므로 ARM32(armeabi-v7a)에 네이티브 라이브러리 제약이 없습니다.

## 개발

JDK 17, Gradle 8.11.1, Android SDK 35 / Build Tools 35.0.0.
Gradle wrapper는 아직 포함하지 않으며 위 버전의 설치된 Gradle로 빌드합니다.

```sh
base64 --decode development-keystore.base64 > release.jks
gradle :app:assembleRelease :app:lintRelease
```

`development-keystore.base64`는 **공개 개발용 서명 키**이며 비밀이 아닙니다.
동일 키로 개발 빌드 간 업데이트를 지원합니다. 신뢰할 수 있는 공식 배포용 키로 사용하지 마세요.
정식 배포 전 별도 비공개 키와 CI Secrets로 전환해야 하며, 키 전환 시 기존 개발 APK를 삭제 후 재설치해야 합니다.

## 기기 확인

1. APK 설치 후 TV 런처에서 GenieBreaker 실행.
2. ‘권한 허용’으로 근처 기기 권한 승인.
3. Bluetooth가 꺼져 있으면 ‘블루투스 켜기’ 선택.
4. 헤드폰·키보드 등을 페어링 모드로 두고 검색.
5. 기기 선택 후 Android 페어링 확인 창 승인.
6. 등록 목록 갱신, 검색 중지, 뒤로 가기, 재실행, 권한 거절 후 복구 확인.
