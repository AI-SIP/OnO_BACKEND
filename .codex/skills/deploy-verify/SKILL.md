---
name: deploy-verify
description: Use only when explicitly asked to verify a deployment ("deploy-verify dev|prod"). Read-only post-deploy check of the deploy workflow, smoke-test.yml, public GET responses, deployed commit and Flyway version, and new Sentry issues, reported as a table.
---

# OnO Deploy Verify

Claude 쪽 `.claude/skills/deploy-verify/SKILL.md` 와 같은 절차다. 명령과 기대값은 그 파일을 읽고 따른다.

## Rules

- 조회만 한다. `gh workflow run`, `gh run rerun`, 재배포, 컨테이너 재시작을 하지 않는다.
- 서버에는 토큰 없는 GET 만 보낸다(`/`, `/robots.txt`, `/api/problems/problemCount`).
- DB 는 직접 조회하지 않는다. Flyway 와 Quartz 확인 SQL 은 사용자에게 건넨다.

## Workflow

1. `gh run list --workflow ci-prod.yml` (dev 는 `ci-dev.yml`) 와 `deploy-prod.yml` / `deploy-dev.yml` 로 이번 배포 실행과 `headSha` 를 찾는다.
2. 그 뒤에 돈 `smoke-test.yml` 실행의 `[통과]`, `[실패]` 줄을 확인한다.
3. 공개 GET 3개를 보낸다. 기대값은 200, 200, 401 + errorCode 1007.
4. `headSha` 와 `origin/main` 을 비교하고, `git ls-tree` 로 기대 Flyway 최고 버전을 구한다.
5. Sentry 도구가 있으면 배포 이후 새 이슈를 보고, 없으면 확인 방법만 안내한다.
6. 항목 / 결과 / 근거 표로 보고한다.
