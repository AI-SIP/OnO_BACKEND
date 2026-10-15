---
name: pr
description: 현재 작업 브랜치를 push 하고 AI-SIP/OnO_BACKEND 의 develop 대상 GitHub PR 을 레포 템플릿 형식으로 생성한다. 사용자가 PR 생성을 명시적으로 요청할 때만 사용한다.
argument-hint: "[추가 설명 — 선택]"
---

# pr — GitHub PR 생성 (→ develop)

> ⚠️ **main 대상 PR 은 절대 만들지 않는다.** main merge 는 프로덕션 자동 배포로 이어지므로 항상 사용자가 직접 한다. base 는 `develop` 고정이다. merge 는 사용자가 명시적으로 요청할 때만 한다.

## 추가 설명

$ARGUMENTS

> Claude Code 는 위 자리에 슬래시 명령 인자를 넣는다. 비어 있거나 `$ARGUMENTS` 가 그대로 보이면 사용자 메시지를 추가 설명으로 쓴다.

## 단계

### 1. 브랜치 상태 확인

- `git branch --show-current` 가 `main` 또는 `develop` 이면 **즉시 중단**하고 작업 브랜치에서 실행해야 한다고 알린다.
- `git status` 에 미커밋 변경이 있으면 PR 을 만들지 않고, 먼저 `commit` 이나 정리가 필요하다고 알린 뒤 중단한다.
- `git log develop..HEAD --oneline`, `git diff develop...HEAD` 로 변경 범위를 파악한다.
- 원격 브랜치 존재 여부와 미push 커밋을 확인한다.

### 2. 변경 분석과 연관 이슈

- 작업 유형(feat / fix / refactor / chore / perf / docs / test)과 영향 범위(패키지, API, DB 스키마, 인증, 알림, 배포)를 정리한다.
- 브랜치명(`feat/123-login` → `123`)과 커밋 메시지(`(#123)`, `close #123`)에서 이슈 번호와 키워드를 뽑는다.
- `mcp__github__list_issues`(또는 `gh issue list`)로 open 이슈를 조회해 번호, 제목, 본문과 비교한다.
  - 이번 PR 로 실제 해결되는 이슈만 `close #번호` 로 연결한다.
  - 참고 이슈는 자동 종료되지 않게 `close` 없이 참고 문장으로만 적는다.
  - 없으면 "없음" 으로 적는다.

### 3. label

| 작업 유형 | label |
|---|---|
| 신규 기능 | `feature` |
| 버그 수정 | `bug` |
| 리팩터링 | `refactor` |
| 설정, 의존성 | `chore` |
| 성능 개선 | (없음) |
| 문서 | `docs` |
| 테스트 | `test` |

적용 전에 `gh label list --repo AI-SIP/OnO_BACKEND` 로 존재를 확인하고 있는 것만 붙인다. label 을 새로 만들지 않는다.

### 4. 제목

`[타입] 핵심 변경 요약` (예: `[Feat] 오답노트 태그 필터 기능 추가`). 여러 타입이 섞이면 비중이 가장 큰 하나를 쓴다.

### 5. 본문

`.github/PULL_REQUEST_TEMPLATE.md` 를 읽고 같은 구조로 채운다. 연관 이슈, 작업 내용(커밋 단위), 사용자 영향, API 호환성, DB migration, 인증/권한, 검증 결과(실행한 명령과 결과, 못 한 검증), 배포 리스크, 롤백/대응 방법을 모두 채운다. PR 본문용 markdown 파일은 따로 만들지 않는다.

### 6. 미리보기 없이 진행

사용자 확인을 받지 않고 바로 push 와 PR 생성으로 넘어간다. 제목, label, 연관 이슈, 본문은 결과 보고에서 보여 준다. 연관 이슈 판단이 애매하면 그 점만 짧게 알린다.

### 7. push

원격 브랜치가 없거나 미push 커밋이 있으면 `git push -u origin $(git branch --show-current)` 를 실행한다. 실패하면 인증, 권한, 네트워크 오류를 보고하고 중단한다.

### 8. PR 생성

Codex 의 `codex_apps` GitHub 도구가 아니라 `mcp__github__` MCP 서버 도구를 쓴다.

`mcp__github__create_pull_request`(없으면 `gh pr create`)로 만든다.

- `owner`: `AI-SIP`, `repo`: `OnO_BACKEND`
- `head`: 현재 브랜치, `base`: `develop` (고정)
- Reviewers 는 지정하지 않는다.

생성 후 PR 번호에 `mcp__github__issue_write` 를 `method=update` 로 호출해 `assignees: ["KiSeungMin"]` 과 확인한 label 을 붙인다. milestone, project, type 등 나머지는 지정하지 않는다.

### 9. 결과 보고

PR URL, 제목, base/head, push 여부, assignee, label, 연결한 이슈, `main` 을 대상으로 쓰지 않았다는 점을 알린다.
