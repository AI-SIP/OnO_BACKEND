# 블랙박스 시나리오 테스트

배포할 jar 를 운영 Dockerfile 그대로 컨테이너로 띄우고, **HTTP 요청만으로** 앱이 쓰는 기능 전체를 확인합니다(#422).

```bash
./gradlew blackboxTest                                   # 전체 (Docker 필요, 약 3~4분)
./gradlew blackboxTest --tests '*StudyRoomScenarioTest'  # 한 묶음만
```

실패하면 `build/blackbox/app.log`(앱 컨테이너 로그)와 `build/reports/tests/blackboxTest` 를 봅니다.

<br>

## 다른 테스트와 무엇이 다른가

| | `./gradlew test` (MockMvc) | `./gradlew blackboxTest` | 스모크 (`scripts/smoke`) |
|---|---|---|---|
| 대상 | 같은 JVM 의 스프링 컨텍스트 | 컨테이너로 띄운 배포 jar | 실제 운영, dev 서버 |
| 인증 | SecurityContext 를 직접 세움 | 가입 API 로 받은 실제 토큰 | 서명 키로 만든 토큰 |
| 데이터 준비 | DB 에 직접 넣음 | API 로만 만듦 | 스모크 계정 안에서만 |
| 외부 연동 | 빈 단위 목 | HTTP 가짜 서버(WireMock, s3mock) | 실제 (흔적이 남는 것은 뺌) |
| RabbitMQ 리스너, Quartz | 꺼짐 | 켜짐 | 켜짐 |
| 범위 | 클래스 단위 | 앱이 쓰는 엔드포인트 전부 | 25개 정도 |

<br>

## 구성

```
테스트 JVM ──▶ gateway(socat) ──▶ app ──▶ mysql, redis, rabbitmq, wiremock, s3mock
     └──────────────────────────────────▶ wiremock, s3mock (가짜 응답 등록, 객체 확인)
```

- **앱 컨테이너는 internal 네트워크에만 붙습니다.** 밖으로 나가는 길이 없어서 실제 FCM, OpenAI, S3, Discord, 소셜 로그인 서버에 닿을 수 없습니다. `StackSafetyTest` 가 이것을 매번 확인합니다.
- **테스트 소스셋에 main 출력이 없습니다.** 앱 클래스를 import 할 수 없어서 리포지토리로 데이터를 넣거나 빈을 바꾸는 코드는 컴파일되지 않습니다.
- **설정은 `src/blackboxTest/resources/application-blackbox.yml` 입니다.** 운영 설정과 같은 값을 쓰지 않습니다.

| 외부 연동 | 처리 |
|---|---|
| OpenAI | WireMock 이 고정 분석 결과를 돌려줌 (`OpenAiStub`). 장애 시나리오는 우선순위 높은 매핑으로 덮어씀 |
| 카카오, 구글, 애플 로그인 | WireMock 이 토큰 정보와 JWKS 를 내줌. 구글, 애플 id token 은 테스트가 만든 RSA 키로 서명 (`SocialLogin`) |
| S3 | `adobe/s3mock`. 앱의 S3 엔드포인트를 `cloud.aws.s3.endpoint` 로 바꿈 (운영 설정에는 이 키가 없음) |
| FCM | 더미 키 + 막힌 네트워크라 발송이 일어날 수 없음. 발송 내용은 보지 않음 |
| Discord 웹훅 | WireMock. 서버 오류 알림이 늘면 시나리오를 실패시킴 (`BlackboxTest`) |

<br>

## 운영 스키마와 맞추는 것

Flyway 를 빈 DB 에 돌릴 수 없어서(V1 이 no-op) 스키마는 Hibernate 가 만듭니다. 그래서 운영과 다른 두 가지를 스택이 기동할 때 맞춥니다.

| 차이 | 맞추는 방법 |
|---|---|
| 미션 정의, 꾸미기 아이템 카탈로그가 비어 있음 | 운영과 같은 시드 마이그레이션을 같은 순서로 실행 (`BlackboxStack.CATALOG_MIGRATIONS`) |
| 외래키에 `ON DELETE CASCADE` 가 없음 (방 삭제가 실패함) | 마이그레이션에서 CASCADE 규칙을 읽어 같은 외래키를 바꿈 |

카탈로그를 고치는 마이그레이션을 새로 만들면 `CATALOG_MIGRATIONS` 와 화이트박스의 `MissionDefinitionSeeder`, `CosmeticItemSeeder` 에 같이 더합니다.

<br>

## 시나리오

모든 묶음이 성공 흐름, 토큰 없음(401), 남의 데이터 차단, 잘못된 입력(400)을 같이 봅니다.

| 파일 | 다루는 것 |
|---|---|
| `AuthScenarioTest` | 게스트 가입, 카카오, 구글, 애플 가입과 재로그인, 토큰 갱신과 재사용 거절, 로그아웃 |
| `UserScenarioTest` | 내 정보, 알림 설정, 프로필 이미지(멀티파트, presigned), 탈퇴 뒤 토큰 거절 |
| `FolderScenarioTest` | 트리, 이동, 커서 페이지, 삭제와 안의 문제, 루트 보호 |
| `ProblemScenarioTest` | v1, v2, 배치 등록, 검색, 수정, 이미지 추가와 삭제(S3 반영), 남의 폴더, 태그, 이미지 차단 |
| `ProblemAnalysisScenarioTest` | 분석 요청부터 OpenAI 를 거쳐 결과 조회까지, 실패 뒤 재분석, 하루 한도 |
| `ProblemSolveScenarioTest` | 복습 기록과 이미지, 문제 쪽 집계 반영 |
| `PracticeNoteScenarioTest` | 복습노트, 알림 요일 검증, 복습 완료, 남의 문제 담기 차단 |
| `TagScenarioTest` | 태그 만들기(대소문자 무시 중복), 삭제, 추천 |
| `FileUploadScenarioTest` | presigned URL, 서버 경유 업로드, 삭제와 소유자 확인 |
| `LearningScenarioTest` | 학습 캘린더와 기분, 학습 리포트 |
| `RewardScenarioTest` | 미션 진행과 보상 수령(중복 거절), 업적, 꾸미기 장착 |
| `NoticeAndFeedbackScenarioTest` | 관리자 폼 로그인으로 공지 등록, 사용자 조회와 닫기, 웹 피드백 |
| `FcmScenarioTest` | 기기 토큰 등록 |
| `StudyRoomScenarioTest` | 사용자 3명(방장, 멤버, 방 밖)으로 가입, 피드, 공유 문제와 댓글, 챌린지, 내보내기, 방 삭제, 방 밖 차단 |
| `StackSafetyTest` | 앱 컨테이너가 외부로 못 나가는지 |

**보지 않는 것**: 시간이 흘러야 생기는 데이터(주간 리포트 생성, 복습 리마인더 발송, 연속 학습 날짜 경계)는 컨테이너 시계를 옮길 수 없어서 빈 상태의 응답 모양까지만 봅니다. 관리자 화면은 공지 등록에 필요한 로그인까지만 봅니다.

<br>

## 시나리오를 더할 때

- 사용자는 `TestUser.guest()` 로 시나리오마다 새로 만듭니다. 다른 시나리오의 데이터와 섞이지 않습니다.
- 남의 데이터 차단은 사용자 A 가 만든 id 를 사용자 B 토큰으로 부르는 형태로 씁니다(`expectDenied()`).
- 비동기 결과(분석, 피드, S3 삭제)는 `eventually(...)` 로 기다립니다. 고정 sleep 을 쓰지 않습니다.
- 일부러 서버 오류를 내는 시나리오는 `allowServerErrorAlerts()` 를 부릅니다. 그 외에는 500 이 한 번이라도 나면 실패합니다.
- errorCode 는 `ErrorCodes` 에 숫자로 옮겨 둡니다. 앱 클래스를 import 할 수 없기 때문이고, 숫자가 바뀌면 출시된 앱이 깨지므로 여기서 걸리는 것이 맞습니다.
