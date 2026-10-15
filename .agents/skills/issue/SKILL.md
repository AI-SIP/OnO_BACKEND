---
name: issue
description: 텍스트 설명이나 파일 내용을 받아 AI-SIP/OnO_BACKEND 에 레포 템플릿 형식의 GitHub 이슈를 생성한다. 사용자가 이슈 생성을 명시적으로 요청할 때만 사용한다.
argument-hint: "<이슈 설명 또는 파일 경로>"
---

# issue — GitHub 이슈 생성

## 입력

$ARGUMENTS

> Claude Code 는 위 자리에 슬래시 명령 인자를 넣는다. 비어 있거나 `$ARGUMENTS` 가 그대로 보이면 사용자 메시지에서 입력을 읽는다.

## 단계

### 1. 입력 파악

- 입력이 존재하는 파일 경로이면 그 파일을 읽어 이슈 원문으로 쓴다. 아니면 텍스트를 그대로 쓴다.
- 입력이 없으면 이슈 내용을 묻는다.

### 2. 유형 판단

| 유형 | 템플릿 | label 후보 | 제목 prefix |
|---|---|---|---|
| 버그, 오류, 예외, 장애, 회귀 | `.github/ISSUE_TEMPLATE/bug-report-template.md` | `bug` | `[bug]` |
| 기능 추가, 개선 | `.github/ISSUE_TEMPLATE/common-issue-template.md` | `feature` | `[feat]` |
| 리팩터링 | common | `refactor` | `[refactor]` |
| 설정, 환경, 의존성 | common | `chore` | `[chore]` |
| 성능 개선 | common | (없음) | `[perf]` |
| 문서, 주석 | common | `docs` | `[docs]` |
| 테스트 | common | `test` | `[test]` |
| 기타, 불명확 | common | (없음) | `[chore]` |

label 은 적용 전에 `gh label list --repo AI-SIP/OnO_BACKEND` 로 실제 존재하는지 확인하고, 없는 label 은 빼고 진행한다. label 을 새로 만들지 않는다.

### 3. 본문 작성

템플릿 파일을 읽고 같은 구조로 채운다.

- 버그: 어떤 버그인가, 발생 상황(Given / When / Then), 예상 결과. 스크린샷이나 로그가 없으면 그 절은 뺀다.
- 그 외: Description, TODO 체크리스트, ETC(없으면 생략).
- 근거가 있으면 `파일:라인` 으로 적는다. 운영 영향이나 재현 조건처럼 판단에 필요한 정보가 없으면 "확인 필요" 로 적는다.
- **공개 레포다.** 비밀값, 운영 IP, 보안 우회 방법을 단계별로 적지 않는다.

### 4. 생성 전 미리보기

제목, label, 본문 전체를 사용자에게 보여 주고 확인을 받는다. 수정 요청이 있으면 반영하고 다시 확인한다.

### 5. 생성

Codex 의 `codex_apps` GitHub 도구가 아니라 `mcp__github__` MCP 서버 도구를 쓴다.

`mcp__github__issue_write` 를 `method=create` 로 호출한다(없으면 `gh issue create`).

- `owner`: `AI-SIP`, `repo`: `OnO_BACKEND`
- `assignees`: `["KiSeungMin"]` (고정)
- `labels`: 존재를 확인한 label 배열
- issue type 은 유효한 값을 확인할 수 있을 때만 지정한다.

### 6. 결과 보고

이슈 번호와 URL, 적용한 assignee, label 을 알린다.
