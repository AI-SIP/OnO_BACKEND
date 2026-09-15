# 스모크 테스트

배포된 서버가 사용자를 받을 수 있는 상태인지 실제 도메인으로 확인한다.
컨테이너 healthcheck 는 컨테이너 안의 `localhost:8081/actuator/health` 만 봐서, 그 앞의 Cloudflare 와 nginx,
JWT 서명 키, DB 조회가 어긋나도 알 수 없다.

## 무엇을 확인하나

| 확인 | 요청 | 통과 기준 | 이게 실패하면 |
|---|---|---|---|
| 앱까지 닿는가 | 토큰 없이 `GET /api/problems/problemCount` | `401`, `errorCode 1007` | Cloudflare 가 원 서버에 못 닿았거나(502), nginx 나 앱이 이상하다 |
| 인증과 DB 조회가 되는가 | 스모크 토큰으로 같은 요청 | `200`, `data` 가 숫자 | 서버의 JWT 서명 키가 시크릿과 다르거나 DB 조회가 안 된다 |

## 운영 서버에 영향이 없는 이유

- **GET 하나만 보낸다.** 경로는 스크립트에 고정되어 있다. 이 경로는 읽기 전용 트랜잭션에서 count 쿼리 하나만 돈다.
- **`GET /api/users` 는 쓰지 않는다.** 조회처럼 보이지만 로그인 미션 기록과 마지막 접속 시각 갱신이 같이 돈다.
- **존재하지 않는 사용자 ID 0 으로 60초짜리 토큰을 만든다.** 운영 DB 에 스모크 계정을 만들 필요가 없고 실제 사용자 데이터에 닿지 않는다.
- **한 번 실행에 요청은 최대 6개다.** 확인 2개에 시도 3번씩이고, 재시도는 연결 실패와 5xx, 429 에서만 한다.
- **리다이렉트를 따라가지 않는다.** 따라가면 토큰이 다른 호스트로 옮겨 가고 요청 수 상한도 깨져서, 3xx 는 실패로 본다.
- **배포 워크플로와 연결하지 않았다.** 수동 실행만 하고, 맥미니 러너가 아니라 GitHub 호스티드 러너에서 돈다.

로컬에 띄운 서버에서 세 번 연속 실행했을 때 MySQL 의 INSERT, UPDATE, DELETE 카운터는 그대로였고
Redis 에는 토큰 블랙리스트 조회 `GET` 만 들어왔다.

그래도 남는 것은 이 정도다.

- 서버 DB 가 이미 죽어 있으면 count 조회가 500 이 되어 기존 예외 처리대로 Discord 알림이 한 건 나간다. 5분 중복 억제가 있다.
- 토큰 없는 요청은 Spring Security 가 요청을 기억하려고 톰캣 메모리에 세션을 만들 수 있다. 한 번 실행에 최대 3개이고 DB 와 Redis 에는 닿지 않는다.

## 에러 코드를 읽는 법

서버 버전에 따라 같은 원인에도 코드가 다르게 나온다.

| 코드 | 가능한 원인 |
|---|---|
| `1009` | 시크릿이 서버의 JWT 서명 키와 다르다 (develop 이후) |
| `1005` | 시크릿이 다르다 (main 2026-07-01 버전은 서명이 틀려도 1005 를 준다), 또는 러너와 서버 시계가 어긋났다 |
| `1007` | 시크릿이 다르거나, 서버의 Redis 블랙리스트 조회가 실패했다 |

## 실행

GitHub Actions 에서 `Smoke Test` 워크플로를 `dev` 나 `prod` 로 수동 실행한다.
`workflow_dispatch` 는 워크플로 파일이 기본 브랜치(`main`)에 있어야 실행 버튼이 생긴다.
인증 확인까지 하려면 저장소 Secrets 에 서버의 `jwt.accessToken.secret` 과 같은 값을 넣는다.

| Secret | 대상 |
|---|---|
| `SMOKE_DEV_ACCESS_TOKEN_SECRET` | `https://ono-dev.seungminki.shop` |
| `SMOKE_PROD_ACCESS_TOKEN_SECRET` | `https://ono-prod.seungminki.shop` |

없으면 인증 확인은 건너뛰고 "앱까지 닿는가"만 본다.
이 시크릿은 서버의 서명 키 그 자체라 관리자 토큰도 만들 수 있다. 저장소 Secrets 대신 `main` 에서만 쓸 수 있는
GitHub Environment 에 두는 편이 더 안전하다.

로컬에서는 이렇게 돌린다. 종료 코드는 0 통과, 1 실패, 2 설정 오류다.

```bash
SMOKE_BASE_URL=https://ono-dev.seungminki.shop python3 scripts/smoke/smoke_test.py
python3 -m unittest discover -s scripts/smoke -p 'test_*.py'   # 스크립트 자체 테스트 22개
```
