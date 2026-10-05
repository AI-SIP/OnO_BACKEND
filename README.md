<div align="center">

# OnO Backend

### 손쉽게 작성하는 나만의 AI 오답노트, OnO 의 API 서버

틀린 문제를 찍어 두면 AI 가 분석해 주고, 다시 풀 때가 되면 먼저 알려 줍니다.

<a href="https://apps.apple.com/kr/app/오노-ono-손쉬운-나만의-오답노트/id6602886624"><img src="https://img.shields.io/badge/App%20Store-000000?style=for-the-badge&logo=apple&logoColor=white" height="32" alt="App Store" /></a>
<a href="https://play.google.com/store/apps/details?id=com.ono.app"><img src="https://img.shields.io/badge/Google%20Play-000000?style=for-the-badge&logo=googleplay&logoColor=white" height="32" alt="Google Play" /></a>
<a href="https://github.com/AI-SIP/OnO_FRONT"><img src="https://img.shields.io/badge/Flutter%20%EC%95%B1-02569B?style=for-the-badge&logo=flutter&logoColor=white" height="32" alt="Flutter 앱 저장소" /></a>

<img src="https://raw.githubusercontent.com/AI-SIP/OnO_FRONT/main/.github/readme/hero.png" width="820" alt="OnO 주요 화면" />

</div>

<br>

## 목차

- [서비스 소개](#서비스-소개)
- [기술 스택](#기술-스택)
- [서비스 아키텍처](#서비스-아키텍처)
- [서버에서 신경 쓴 부분](#서버에서-신경-쓴-부분)
- [로컬에서 실행하기](#로컬에서-실행하기)
- [작업 규칙](#작업-규칙)

<br>

## 서비스 소개

오답노트가 좋다는 건 다들 알지만, 문제를 옮겨 적고 다시 찾아 푸는 일이 번거로워서 오래 가지 못합니다.
OnO 는 문제를 사진으로 올리면 한 장이 완성되고, 복습할 때가 된 문제를 먼저 모아 주는 앱입니다.
2024년 8월 앱 스토어에 처음 출시했고, 지금도 실사용자가 쓰고 있는 서비스입니다.

아래는 앱의 주요 화면과, 그 화면 뒤에서 이 서버가 하는 일입니다.

<table>
  <tr>
    <td align="center" width="25%"><img src="https://raw.githubusercontent.com/AI-SIP/OnO_FRONT/main/.github/readme/06-write-images.png" width="180" alt="오답노트 작성" /></td>
    <td align="center" width="25%"><img src="https://raw.githubusercontent.com/AI-SIP/OnO_FRONT/main/.github/readme/17-ai-analysis.png" width="180" alt="AI 오답분석" /></td>
    <td align="center" width="25%"><img src="https://raw.githubusercontent.com/AI-SIP/OnO_FRONT/main/.github/readme/13-review-due.png" width="180" alt="추천 복습 문제" /></td>
    <td align="center" width="25%"><img src="https://raw.githubusercontent.com/AI-SIP/OnO_FRONT/main/.github/readme/20-retry-history.png" width="180" alt="복습 기록" /></td>
  </tr>
  <tr>
    <td align="center"><b>오답노트 작성</b><br/><sub>사진은 앱이 S3 에 바로 올리고<br/>서버는 문제와 이미지 주소를 저장합니다</sub></td>
    <td align="center"><b>AI 오답분석</b><br/><sub>등록 응답과 분리해 큐로 넘기고<br/>OpenAI 결과를 나중에 채웁니다</sub></td>
    <td align="center"><b>추천 복습 문제</b><br/><sub>다시 푼 기록으로 다음 복습일을<br/>계산해서 그날 모아 줍니다</sub></td>
    <td align="center"><b>다시 풀기</b><br/><sub>회차마다 정답, 오답, 부분 정답과<br/>풀이 시간을 남깁니다</sub></td>
  </tr>
  <tr>
    <td align="center"><img src="https://raw.githubusercontent.com/AI-SIP/OnO_FRONT/main/.github/readme/25-set-run.png" width="180" alt="복습 세트" /></td>
    <td align="center"><img src="https://raw.githubusercontent.com/AI-SIP/OnO_FRONT/main/.github/readme/28-room-rank.png" width="180" alt="스터디룸" /></td>
    <td align="center"><img src="https://raw.githubusercontent.com/AI-SIP/OnO_FRONT/main/.github/readme/35-report.png" width="180" alt="학습 리포트" /></td>
    <td align="center"><img src="https://raw.githubusercontent.com/AI-SIP/OnO_FRONT/main/.github/readme/43-mission-daily.png" width="180" alt="미션" /></td>
  </tr>
  <tr>
    <td align="center"><b>복습 세트</b><br/><sub>정해 둔 시간이 되면<br/>예약 작업이 푸시를 보냅니다</sub></td>
    <td align="center"><b>스터디룸</b><br/><sub>랭킹과 챌린지, 문제 공유,<br/>매주 월요일 주간 리포트</sub></td>
    <td align="center"><b>학습 리포트</b><br/><sub>학습 지표를 모아 다음 주 목표를 추천하고<br/>AI 가 실패하면 규칙 기반으로 채웁니다</sub></td>
    <td align="center"><b>미션과 레벨</b><br/><sub>일일, 주간 미션 진행도와<br/>경험치, 꾸미기 잠금 해제</sub></td>
  </tr>
</table>

화면별 설명은 [앱 저장소 README](https://github.com/AI-SIP/OnO_FRONT) 에 더 자세히 있습니다.

<br>

## 기술 스택

**Backend**

![Java](https://img.shields.io/badge/Java_17-ED8B00?style=flat-square&logo=openjdk&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring_Boot_3.3-6DB33F?style=flat-square&logo=springboot&logoColor=white)
![Spring Security](https://img.shields.io/badge/Spring_Security-6DB33F?style=flat-square&logo=springsecurity&logoColor=white)
![JPA](https://img.shields.io/badge/Spring_Data_JPA-6DB33F?style=flat-square&logo=spring&logoColor=white)
![QueryDSL](https://img.shields.io/badge/QueryDSL-0769AD?style=flat-square)
![Quartz](https://img.shields.io/badge/Quartz-1F4E79?style=flat-square)
![Thymeleaf](https://img.shields.io/badge/Thymeleaf-005F0F?style=flat-square&logo=thymeleaf&logoColor=white)

**Data**

![MySQL](https://img.shields.io/badge/MySQL_8.0-4479A1?style=flat-square&logo=mysql&logoColor=white)
![Flyway](https://img.shields.io/badge/Flyway-CC0200?style=flat-square&logo=flyway&logoColor=white)
![Redis](https://img.shields.io/badge/Redis_7.2-DC382D?style=flat-square&logo=redis&logoColor=white)
![RabbitMQ](https://img.shields.io/badge/RabbitMQ_3.13-FF6600?style=flat-square&logo=rabbitmq&logoColor=white)
![Amazon S3](https://img.shields.io/badge/Amazon_S3-569A31?style=flat-square&logo=amazons3&logoColor=white)

**Infra**

![Docker](https://img.shields.io/badge/Docker-2496ED?style=flat-square&logo=docker&logoColor=white)
![Nginx](https://img.shields.io/badge/Nginx-009639?style=flat-square&logo=nginx&logoColor=white)
![GitHub Actions](https://img.shields.io/badge/GitHub_Actions-2088FF?style=flat-square&logo=githubactions&logoColor=white)
![Prometheus](https://img.shields.io/badge/Prometheus-E6522C?style=flat-square&logo=prometheus&logoColor=white)
![Grafana](https://img.shields.io/badge/Grafana-F46800?style=flat-square&logo=grafana&logoColor=white)
![Loki](https://img.shields.io/badge/Loki-F2A900?style=flat-square&logo=grafana&logoColor=white)
![Sentry](https://img.shields.io/badge/Sentry-362D59?style=flat-square&logo=sentry&logoColor=white)

**External**

![OpenAI](https://img.shields.io/badge/OpenAI-412991?style=flat-square&logo=openai&logoColor=white)
![FCM](https://img.shields.io/badge/Firebase_Cloud_Messaging-FFCA28?style=flat-square&logo=firebase&logoColor=black)
![Discord](https://img.shields.io/badge/Discord_Webhook-5865F2?style=flat-square&logo=discord&logoColor=white)

<br>

## 서비스 아키텍처

![서비스 아키텍처](.github/readme/architecture.png)

서버는 클라우드가 아니라 직접 운영하는 서버 한 대에 Docker Compose 로 올라가 있고, dev 와 prod 가 포트만 달리해서 같이 돌고 있습니다.
요청은 호스트의 Nginx 가 받아서 Blue(8080) 와 Green(8081) 중 지금 살아 있는 쪽 컨테이너로 넘깁니다.
문제 이미지는 앱이 presigned URL 로 S3 에 직접 올리기 때문에, 서버는 이미지 파일을 직접 받지 않고 주소만 저장합니다.

main 에 머지되면 GitHub Actions 가 이미지를 빌드해 Docker Hub 에 올리고, 서버에 붙어 있는 self-hosted runner 가 그 이미지로 안 쓰는 쪽 색을 띄웁니다.
헬스체크와 전환 전 확인을 통과해야 Nginx upstream 을 바꾸고, 하나라도 실패하면 이전 색이 그대로 요청을 받습니다.
배포가 끝나면 스모크 테스트가 실제 API 를 한 번 더 호출해 보고, 그 뒤로도 매시간 확인해서 상태가 바뀌면 Discord 로 알려 줍니다.

<br>

## 서버에서 신경 쓴 부분

### AI 분석은 등록 요청과 분리했습니다

처음에는 문제를 등록하는 요청 안에서 OpenAI 를 기다렸는데, 분석이 길어지면 등록 응답도 같이 늦어졌습니다.
지금은 등록은 바로 끝내고 분석 요청만 RabbitMQ `gpt.analysis` 큐에 넣어 두면, Consumer 가 따로 OpenAI 를 불러 결과를 채웁니다.
OpenAI 호출 한도 때문에 이 Consumer 는 동시에 1~2개만 돌게 제한했습니다.

푸시 발송(`fcm.notification`), S3 이미지 삭제(`s3.delete`), 운영 알림(`discord.webhook`)도 같은 방식으로 큐를 거칩니다.
큐마다 DLQ 를 두었고, 실패하면 1초, 2초, 4초 간격으로 세 번까지 다시 시도한 뒤 DLQ 로 보냅니다.
예전에 실패한 메시지가 끝없이 재전달되면서 Discord 알림이 쏟아진 적이 있어서, 재큐잉은 꺼 두었습니다.

### 복습 일정은 다시 푼 기록으로 계산합니다

| 다시 푼 결과 | 다음 복습 |
| --- | --- |
| 정답 | 간격을 두 배로 늘립니다 (최대 30일) |
| 오답이나 부분 정답 | 다음 날로 되돌리고 간격도 1일부터 다시 셉니다 |

마지막으로 틀린 뒤 서로 다른 3일에 맞히면 추천에서 빠지고, 그 뒤에 다시 틀리면 처음부터 다시 셉니다.
기록 하나를 고치거나 지우면 그 문제의 기록 전체를 처음부터 다시 따라가며 계산하기 때문에, 중간 기록이 바뀌어도 일정이 어긋나지 않습니다.
계산은 `ReviewIntervalCalculator` 한 곳에 모여 있습니다.

### 푸시는 Quartz 예약 작업이 보냅니다

| 작업 | 언제 | 하는 일 |
| --- | --- | --- |
| `ProblemReviewReminderJob` | 5분마다 | 복습할 때가 된 문제 알림 (09시부터 21시 사이에만) |
| `ReviewDueNotificationJob` | 매일 09시 | 오늘 복습할 문제 수, 오래 접속하지 않은 사용자에게 다시 오라는 알림 |
| `PracticeNotificationJob` | 사용자가 정한 시각 | 복습 세트마다 걸어 둔 알림 |
| `StudyRoomWeeklyReportJob` | 매주 월요일 08시 | 지난주 스터디룸 리포트 생성 |
| `ChallengeNotificationJob` | 챌린지마다 한 번 | 챌린지 중간 지점과 D-1 알림 |

JDBC JobStore 를 써서 서버를 다시 띄워도 예약이 남아 있고, 모든 시각은 한국 시간 기준입니다.
실제 사용자에게 푸시가 나가는 경로라, 발송 쪽 코드를 고칠 때는 dev 서버에서 먼저 확인합니다.

### 사용자는 자기 데이터에만 접근할 수 있습니다

모든 조회와 수정은 JWT 에서 꺼낸 `userId` 로 소유권을 확인하고, 관리자 화면(`/admin`)은 별도 로그인을 거칩니다.
Access Token 은 30분, Refresh Token 은 7일이고, 갱신할 때마다 둘 다 새로 발급합니다.
로그아웃하거나 탈퇴한 사용자의 Access Token 은 Redis 블랙리스트에 올려서 만료 전이라도 막습니다.

### 이미 배포된 앱 버전을 깨지 않는 것을 먼저 봅니다

스토어에 올라간 예전 버전 앱이 계속 이 서버를 호출하기 때문에, 응답 필드를 지우거나 이름을 바꾸는 변경은 하지 않는 쪽을 택합니다.
모든 응답은 `{ "data": ... }` 또는 `{ "errorCode": ..., "message": ... }` 형태로 감싸고, 에러 코드는 도메인마다 `ErrorCase` enum 에 모아 두었습니다.

<br>

## 로컬에서 실행하기

`application-local.yml` 과 `FirebaseAdminKey.json` 은 키가 들어 있어서 저장소에 올리지 않습니다. 팀원에게 받아 각각 `src/main/resources/` 와 프로젝트 루트에 두고, 루트의 `.env` 에 `MYSQL_ROOT_PASSWORD` 와 `RABBITMQ_PASSWORD` 를 적어 주세요.

```bash
make up                                                  # MySQL, Redis, RabbitMQ 띄우기
./gradlew bootRun --args='--spring.profiles.active=local'
```

로컬에서는 Redis 가 6380, RabbitMQ 가 5673 포트를 씁니다.
local 프로필은 RabbitMQ Listener 를 꺼 두었기 때문에, 실제 AI 분석이나 푸시는 나가지 않습니다.
API 문서는 서버를 띄운 뒤 `http://localhost:8080/swagger-ui/index.html` 에서 볼 수 있습니다.

테스트는 Testcontainers 로 MySQL, Redis, RabbitMQ 를 따로 띄우기 때문에 Docker 만 켜져 있으면 됩니다.

```bash
make test                                        # 전체 테스트와 커버리지 리포트
make test-only T='com.aisip.OnO.backend.tag.*'   # 일부만
```

PR 마다 같은 테스트가 GitHub Actions 에서 돌고, 라인 커버리지 85% 나 브랜치 커버리지 70% 아래로 내려가면 머지할 수 없습니다.

<br>

## 작업 규칙

- `develop` 에서 `feat/<이슈 번호>-<내용>` 이나 `fix/<이슈 번호>-<내용>` 브랜치를 따고, `develop` 으로 PR 을 올립니다. `main` 은 운영 배포용입니다.
- 커밋 메시지는 `[Feat]`, `[Fix]`, `[Refactor]`, `[Chore]`, `[Test]`, `[Docs]`, `[Perf]` 중 하나로 시작합니다.
- DB 스키마는 `src/main/resources/db/migration` 에 Flyway 파일(`V<번호>__<설명>.sql`)로만 바꿉니다.

<br>

<div align="center">
<sub>문의 ono.dev.team@gmail.com</sub>
</div>
