# 기기 오류 코드

오류 코드는 `GB-XX-XXXX` 형식의 ASCII 문자 10개로 구성된다. 오류 분류와 예외 유형이 같으면 동일한 코드를 생성한다. 표에 없는 예외도 같은 규칙을 적용한다.

앱의 **설정 → 문제 기록**에서 코드를 확인할 수 있다. 기록은 기기에 저장된다. 오류 코드에는 기기 정보나 주소가 포함되지 않으며, 기록을 수동으로 복사할 수 있다.

- `GB-UI-0001` — Flutter 화면에서 Android에 보낸 요청을 처리하지 못한 경우
- `GB-FS-0002` — 기기에 저장한 오류 기록을 읽지 못한 경우

| 코드 | 기능 | 오류 유형 |
| --- | --- | --- |
| `GB-BT-5BEC` | 블루투스 | SecurityException |
| `GB-BT-8C6B` | 블루투스 | NoSuchMethodException |
| `GB-BT-9C9A` | 블루투스 | IllegalStateException |
| `GB-BT-FAB2` | 블루투스 | IllegalArgumentException |
| `GB-BT-0657` | 블루투스 | IOException |
| `GB-BT-7394` | 블루투스 | 요청 거절. 설정 → 지니TV 충돌 진단에서 실패 단계 확인 |
| `GB-BT-F8FD` | 블루투스 | Blocked |
| `GB-BT-3CE1` | 블루투스 | Failure |
| `GB-BT-B536` | 블루투스 | Event |
| `GB-AP-5654` | 앱 실행 | SecurityException |
| `GB-AP-FE35` | 앱 실행 | NoSuchMethodException |
| `GB-AP-A97B` | 앱 실행 | IllegalStateException |
| `GB-AP-EDD7` | 앱 실행 | IllegalArgumentException |
| `GB-AP-04B6` | 앱 실행 | IOException |
| `GB-AP-A394` | 앱 실행 | Rejected |
| `GB-AP-D438` | 앱 실행 | Blocked |
| `GB-AP-BDA2` | 앱 실행 | Failure |
| `GB-AP-ECE0` | 앱 실행 | Event |
| `GB-CL-9F1D` | 듀얼앱 | SecurityException |
| `GB-CL-EB67` | 듀얼앱 | NoSuchMethodException |
| `GB-CL-70D4` | 듀얼앱 | IllegalStateException |
| `GB-CL-BB76` | 듀얼앱 | IllegalArgumentException |
| `GB-CL-3D43` | 듀얼앱 | IOException |
| `GB-CL-8C23` | 듀얼앱 | Rejected |
| `GB-CL-0E55` | 듀얼앱 | Blocked |
| `GB-CL-C087` | 듀얼앱 | Failure |
| `GB-CL-AC59` | 듀얼앱 | Event |
| `GB-IN-0D7B` | 설치 | SecurityException |
| `GB-IN-D158` | 설치 | NoSuchMethodException |
| `GB-IN-8689` | 설치 | IllegalStateException |
| `GB-IN-FE31` | 설치 | IllegalArgumentException |
| `GB-IN-D838` | 설치 | IOException |
| `GB-IN-F350` | 설치 | Rejected |
| `GB-IN-45E6` | 설치 | Blocked |
| `GB-IN-0A47` | 설치 | Failure |
| `GB-IN-88A7` | 설치 | Event |
| `GB-FS-21CE` | 파일 | SecurityException |
| `GB-FS-497A` | 파일 | NoSuchMethodException |
| `GB-FS-8F9B` | 파일 | IllegalStateException |
| `GB-FS-C03E` | 파일 | IllegalArgumentException |
| `GB-FS-37D1` | 파일 | IOException |
| `GB-FS-FD15` | 파일 | Rejected |
| `GB-FS-F4A8` | 파일 | Blocked |
| `GB-FS-8234` | 파일 | Failure |
| `GB-FS-07F1` | 파일 | Event |
| `GB-UP-9E9C` | 업데이트 | SecurityException |
| `GB-UP-B0C1` | 업데이트 | NoSuchMethodException |
| `GB-UP-6C6E` | 업데이트 | IllegalStateException |
| `GB-UP-1AF1` | 업데이트 | IllegalArgumentException |
| `GB-UP-7108` | 업데이트 | IOException |
| `GB-UP-18DB` | 업데이트 | Rejected |
| `GB-UP-7CA5` | 업데이트 | Blocked |
| `GB-UP-257D` | 업데이트 | Failure |
| `GB-UP-8E90` | 업데이트 | Event |
| `GB-CR-EECE` | 앱 종료 | SecurityException |
| `GB-CR-A160` | 앱 종료 | NoSuchMethodException |
| `GB-CR-E948` | 앱 종료 | IllegalStateException |
| `GB-CR-F815` | 앱 종료 | IllegalArgumentException |
| `GB-CR-CA21` | 앱 종료 | IOException |
| `GB-CR-0FA2` | 앱 종료 | Rejected |
| `GB-CR-A547` | 앱 종료 | Blocked |
| `GB-CR-CE5D` | 앱 종료 | Failure |
| `GB-CR-11D0` | 앱 종료 | Event |
| `GB-UI-C1F1` | 화면 | SecurityException |
| `GB-UI-9774` | 화면 | NoSuchMethodException |
| `GB-UI-B0B0` | 화면 | IllegalStateException |
| `GB-UI-3821` | 화면 | IllegalArgumentException |
| `GB-UI-451C` | 화면 | IOException |
| `GB-UI-8EA3` | 화면 | Rejected |
| `GB-UI-CF52` | 화면 | Blocked |
| `GB-UI-6A66` | 화면 | Failure |
| `GB-UI-8AE2` | 화면 | Event |
