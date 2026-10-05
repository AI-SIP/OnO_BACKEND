<div align="center">

# OnO Backend

### 손쉽게 작성하는 나만의 AI 오답노트, OnO 의 API 서버

틀린 문제를 찍어 두면 AI 가 분석해 주고, 다시 풀 때가 되면 먼저 알려 줍니다.

<a href="https://apps.apple.com/kr/app/오노-ono-손쉬운-나만의-오답노트/id6602886624"><img src="https://img.shields.io/badge/App%20Store-000000?style=for-the-badge&logo=apple&logoColor=white" height="32" alt="App Store" /></a>
<a href="https://play.google.com/store/apps/details?id=com.ono.app"><img src="https://img.shields.io/badge/Google%20Play-000000?style=for-the-badge&logo=googleplay&logoColor=white" height="32" alt="Google Play" /></a>
<a href="https://github.com/AI-SIP/OnO_FRONT"><img src="https://img.shields.io/badge/Flutter%20%EC%95%B1-02569B?style=for-the-badge&logo=flutter&logoColor=white" height="32" alt="Flutter 앱 저장소" /></a>

<img src=".github/readme/hero.png" width="900" alt="한 번 틀린 문제, 두 번은 안 틀리게" />

</div>

<br>

## 목차

- [서비스 소개](#서비스-소개)
- [기술 스택](#기술-스택)
- [서비스 아키텍처](#서비스-아키텍처)
- [운영하면서 고친 것](#운영하면서-고친-것)
- [남아 있는 문제](#남아-있는-문제)
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

서버는 클라우드가 아니라 직접 운영하는 Mac mini 한 대에 Docker Compose 로 올라가 있고, dev 와 prod 가 포트만 달리해서 같이 돌고 있습니다.
요청은 호스트의 Nginx 가 받아서 Blue(8080) 와 Green(8081) 중 지금 살아 있는 쪽 컨테이너로 넘깁니다.
오답노트의 문제 이미지는 앱이 presigned URL 로 S3 에 직접 올리고, 서버는 그 주소를 받아 저장합니다.

main 에 머지되면 GitHub Actions 가 이미지를 빌드해 Docker Hub 에 올리고, 서버에 붙어 있는 self-hosted runner 가 그 이미지로 안 쓰는 쪽 색을 띄웁니다.
헬스체크와 전환 전 확인을 통과해야 Nginx upstream 을 바꾸고, 하나라도 실패하면 이전 색이 그대로 요청을 받습니다.
배포가 끝나면 스모크 테스트가 실제 API 를 한 번 더 호출해 보고, 그 뒤로도 매시간 확인해서 상태가 바뀌면 Discord 로 알려 줍니다.

<br>

## 운영하면서 고친 것

2024년 8월에 출시한 뒤로 실사용자가 쓰는 동안 실제로 문제가 됐던 것들이고, 지금은 아래처럼 돌고 있습니다.

| 문제 | 원인과 조치 |
| --- | --- |
| **실패한 Discord 알림이 끝없이 다시 전송됐습니다** | 전송에 성공한 뒤 로깅에서 난 예외가 실패로 잡혔고, 실패한 메시지가 백오프 없이 큐로 바로 돌아가 5분 동안 같은 웹훅을 계속 보냈습니다. 지금은 1초, 2초, 4초 간격으로 세 번까지만 다시 시도하고, 그래도 실패하면 DLQ 로 보냅니다. 큐 4개 모두 같은 규칙입니다 |
| **Blue-Green 배포 중에 복습 알림이 멈췄습니다** | 내려가는 쪽 컨테이너가 자기 이미지에 없는 잡을 집으면서 Quartz 트리거가 ERROR 로 굳었는데, Quartz 는 이를 스스로 풀지 않습니다. 5분마다 ERROR 트리거를 찾아 되돌리는 작업을 붙였습니다 |
| **RabbitMQ 가 멈추면 스터디룸 공유와 댓글까지 500 으로 끝났습니다** | 푸시를 큐에 넣다 난 예외가 바깥 트랜잭션을 롤백시켰기 때문입니다. 큐 적재를 트랜잭션 밖으로 빼서, 푸시가 안 나가도 공유와 댓글은 저장되게 했습니다 |
| **챌린지 알림이 한밤중에 나갈 수 있었습니다** | D-1 알림이 마감 시각(그날 23시 59분)을 그대로 따라가고 있었습니다. 지금은 같은 날 09시와 18시 중 가까운 시각으로 맞춰 보냅니다 |
| **추천 복습에서 빠진 문제는 다시 틀려도 돌아오지 않았습니다** | 정답을 맞힌 날이 3일을 넘으면 계속 걸러지고 있었습니다. 마지막으로 틀린 뒤의 정답 날만 세도록 바꿔서, 다시 틀리면 처음부터 다시 추천합니다 |
| **한국 시간 0시부터 9시 사이에만 생기는 날짜 버그를 테스트가 못 잡았습니다** | 운영 JVM 은 `Asia/Seoul` 인데 CI 는 UTC 로 돌고 있었습니다. 테스트 JVM 도 `Asia/Seoul` 로 맞췄습니다 |

<br>

## 남아 있는 문제

| 내용 | 지금 상태 |
| --- | --- |
| [**#266**](https://github.com/AI-SIP/OnO_BACKEND/issues/266) **Refresh Token 교체에 유예가 없습니다** | 갱신 응답이 한 번 유실되면 세션이 끊기고, 게스트는 계정을 잃습니다 |
| [**#238**](https://github.com/AI-SIP/OnO_BACKEND/issues/238) **주간 리포트 배치가 다중 인스턴스에서 중복 생성됩니다** | 인스턴스를 둘 이상 띄우면 같은 배치가 양쪽에서 돌다 유니크 제약에 걸려, 그 주 리포트가 하나도 생기지 않습니다. 지금은 한 대만 돌아 드러나지 않습니다 |
| [**#232**](https://github.com/AI-SIP/OnO_BACKEND/issues/232) **문제 등록이 커넥션을 2개 잡습니다** | 동시 요청이 몰리면 커넥션 풀이 고갈될 수 있습니다 |

<br>

## 작업 규칙

- `develop` 에서 `feat/<이슈 번호>-<내용>` 이나 `fix/<이슈 번호>-<내용>` 브랜치를 따고, `develop` 으로 PR 을 올립니다. `main` 은 운영 배포용입니다.
- 커밋 메시지는 `[Feat]`, `[Fix]`, `[Refactor]`, `[Chore]`, `[Test]`, `[Docs]`, `[Perf]` 중 하나로 시작합니다.
- DB 스키마는 `src/main/resources/db/migration` 에 Flyway 파일(`V<번호>__<설명>.sql`)로만 바꿉니다.

<br>

<div align="center">
<sub>문의 ono.dev.team@gmail.com</sub>
</div>
