---
name: deploy-verify
description: dev 또는 prod 배포 직후에 배포 workflow 와 스모크 테스트 결과, 공개 GET 응답, 배포된 커밋과 Flyway 버전, Sentry 새 이슈를 읽기 전용으로 확인해 표로 보고한다.
disable-model-invocation: true
argument-hint: "dev|prod"
allowed-tools: Bash(gh run list *) Bash(gh run view *) Bash(git fetch *) Bash(git rev-parse *) Bash(git log *) Bash(git ls-tree *) Bash(git diff *) Bash(curl -s *)
---

# /deploy-verify — 배포 직후 점검

대상: `$ARGUMENTS` (`dev` 또는 `prod`. 없으면 어느 쪽인지 묻는다)

## 지키는 것

- **조회만 한다.** `gh workflow run`, `gh run rerun`, 재배포, 컨테이너 재시작, nginx 조작을 하지 않는다.
- **서버에는 토큰 없는 GET 만 보낸다.** 인증이 필요한 API 와 쓰기 요청은 보내지 않는다. 그 확인은 `smoke-test.yml` 이 스모크 계정으로 이미 한다.
- **DB 는 직접 조회하지 않는다.** 아래 SQL 은 사용자에게 건네고, 사용자가 실행한 결과를 받아서 판단한다.
- 문제가 보이면 고치지 말고 표에 적고, 롤백 여부는 사용자가 정한다.

## 대상별 값

| | prod | dev |
|---|---|---|
| 배포 workflow | `ci-prod.yml` (CI Build & Deploy Prod, main push) | `ci-dev.yml` (CI Build & Deploy Dev, 수동 실행) |
| 수동 재배포 workflow | `deploy-prod.yml` (Manual Fallback) | `deploy-dev.yml` (Manual Fallback) |
| 도메인 | `https://ono-prod.seungminki.shop` | `https://ono-dev.seungminki.shop` |
| 이미지 태그 | `prod-<커밋 sha>` | `dev-<커밋 sha>` |
| 기대 커밋 | `origin/main` 끝 | 그 실행의 `headSha` (아무 브랜치에서나 실행할 수 있다) |

## 1. 배포 workflow 결과

```bash
gh run list --workflow ci-prod.yml -L 3 --json databaseId,headSha,headBranch,event,status,conclusion,createdAt,updatedAt
gh run list --workflow deploy-prod.yml -L 2 --json databaseId,headSha,status,conclusion,createdAt   # 수동 재배포가 더 최근인지
gh run view <id> --json jobs --jq '.jobs[] | "\(.name) \(.conclusion)"'
```
(dev 는 `ci-dev.yml`, `deploy-dev.yml`)

- 가장 최근 완료된 실행을 "이번 배포"로 본다. 진행 중이면 끝날 때까지 기다릴지 사용자에게 묻는다.
- 실패했으면 `gh run view <id> --log-failed | tail -80` 으로 어느 단계인지 본다. 헬스체크(120초)나 전환 전 확인(`scripts/smoke/pre_switch_check.sh`)에서 실패하면 workflow 가 이전 색을 유지하고 `prod-latest` 태그를 되돌린다(`ci-prod.yml`).

## 2. 스모크 테스트 결과

`smoke-test.yml` 은 배포 workflow 가 끝나면 `workflow_run` 으로 돈다. 배포 성공이면 `full`, 실패면 `read` 단계다.

```bash
gh run list --workflow smoke-test.yml -L 5 --json databaseId,event,status,conclusion,createdAt
gh run view <id> --log | grep -E $'\t스모크 테스트 \\((prod|dev)\\)\t' | grep -E '\[(통과|실패|건너뜀)\]|보낸 요청|스모크 테스트 (통과|실패)$'
```

- 이번 배포 `updatedAt` 이후에 생긴 `workflow_run` 실행을 고른다. 아직 없으면 몇 분 뒤 다시 본다.
- 앞의 grep 은 "스크립트 자체 테스트" 단계의 출력을 빼고 실제 스모크 단계만 남긴다.
- `[실패]` 줄이 있으면 첫 줄을 그대로 표에 옮긴다.

## 3. 공개 GET 확인 (토큰 없이, 요청 3개)

```bash
BASE=https://ono-prod.seungminki.shop   # dev 는 ono-dev
curl -s -o /dev/null -w '/ %{http_code} %{time_total}s\n' $BASE/
curl -s -o /dev/null -w '/robots.txt %{http_code} %{time_total}s\n' $BASE/robots.txt
curl -s -w '\n%{http_code} %{time_total}s\n' $BASE/api/problems/problemCount
```

| 요청 | 기대 |
|---|---|
| `GET /` | 200 (랜딩 페이지) |
| `GET /robots.txt` | 200 |
| `GET /api/problems/problemCount` | 401, 본문 `errorCode` 1007. 앱까지 닿았고 인증 필터가 켜져 있다는 뜻이다. 200 이면 인증이 풀린 것이다 |

`/actuator/health` 는 관리 포트(8081)라 도메인으로는 404 다. 컨테이너 헬스체크 결과는 1단계 workflow 로그에서 본다.

## 4. 배포된 커밋과 Flyway 버전

```bash
git fetch origin --quiet
git rev-parse origin/main                     # prod: 배포 실행의 headSha 와 같아야 최신 main 이 나간 것
git log -1 --format='%h %ad %s' --date=short <headSha>
git ls-tree --name-only <headSha> src/main/resources/db/migration/ | grep -oE 'V[0-9]+' | sort -V | tail -1   # 기대 최고 버전
git diff --name-only <직전 배포 headSha> <headSha> -- src/main/resources/db/migration/   # 이번에 새로 돈 마이그레이션
```

실제 적용 버전은 사용자에게 아래 읽기 전용 SQL 을 건네서 확인받는다.

```sql
SELECT version, description, success, installed_on
FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 5;   -- 최고 version 이 기대값, success 전부 1
SELECT trigger_name, trigger_state FROM QRTZ_TRIGGERS;             -- ERROR 상태가 없어야 한다
```

`QRTZ_TRIGGERS` 는 blue-green 겹침 구간에 구 컨테이너가 새 잡을 몰라 트리거를 ERROR 로 만든 적이 있어서 같이 본다.

## 5. Sentry 새 이슈

- Sentry MCP 도구가 연결돼 있고 인증돼 있으면, 이번 배포 시각 이후 처음 생긴(first seen) 백엔드 이슈와 이벤트가 급증한 이슈를 조회한다.
- 연결돼 있지 않으면 조회하지 않고, 사용자에게 Sentry 웹의 Issues 에서 "First Seen" 을 배포 시각 이후로 걸러 보라고 안내만 한다.
- 인증 관련 코드(1005 만료, 1007 인증 실패, 1009 위조)는 배포 전후로 비율이 바뀌면 같이 적는다.

## 6. 보고

```
| 항목 | 결과 | 근거 |
|---|---|---|
| 배포 workflow | 성공/실패 (run id, headSha, 걸린 시간) | gh run view |
| 스모크 테스트 | 통과/실패 (단계, 첫 실패) | gh run view --log |
| 공개 GET | 200 / 200 / 401·1007 | curl |
| 배포 커밋 | origin/main 과 일치 여부 | git rev-parse |
| Flyway | 기대 Vnn / 실제(사용자 확인 전이면 "확인 대기") | git ls-tree, SQL |
| Quartz 트리거 | ERROR 없음 / 확인 대기 | SQL |
| Sentry 새 이슈 | n건 / 조회 못 함 | Sentry MCP |
```

표 아래에 이상이 있는 항목만 한두 줄씩 덧붙인다. 롤백이 필요해 보이면 그렇게 적되 실행하지 않는다.
