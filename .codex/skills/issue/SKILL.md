---
name: issue
description: 사용자가 자유롭게 쓴 설명이나 파일 내용을 바탕으로 AI-SIP/OnO_BACKEND 에 GitHub 이슈를 생성할 때 사용. 사용자가 "issue"를 요청하면 이슈 템플릿에 맞춰 본문을 쓰고 이슈를 만든다.
---

# OnO Issue

## Workflow

1. 입력이 존재하는 파일 경로이면 그 파일을 읽어 이슈 원문으로 쓴다. 아니면 입력 텍스트를 그대로 쓴다. 입력이 없으면 이슈 내용을 묻는다.
2. 입력 성격에 맞는 템플릿을 고른다.
   - 버그, 오류, 예외, 장애, 잘못된 동작, 회귀: `.github/ISSUE_TEMPLATE/bug-report-template.md`, 제목 `[bug] ...`, label `bug`
   - 기능 추가, 개선, 리팩터링, 문서, 설정, 운영 작업, 테스트: `.github/ISSUE_TEMPLATE/common-issue-template.md`, 제목 `[feat]`, `[refactor]`, `[chore]`, `[docs]`, `[test]`, `[perf]` 중 하나
3. label 후보는 `bug`, `feature`, `refactor`, `chore`, `docs`, `test` 중에서 고른다. 적용 전에 `gh label list --repo AI-SIP/OnO_BACKEND` 로 실제 존재하는지 확인하고, 없는 label 은 빼고 진행한다. label 을 새로 만들지 않는다.
4. 템플릿 항목을 채운다.
   - 버그: 어떤 버그인가, 발생 상황(Given / When / Then), 예상 결과
   - 그 외: Description, TODO 체크리스트, ETC(없으면 생략)
   - 근거가 있으면 `파일:라인` 형식으로 적는다. 운영 영향이나 재현 조건처럼 판단에 필요한 정보가 없으면 "확인 필요"로 적는다.
   - 공개 레포다. 비밀값, 운영 IP, 보안 우회 방법을 단계별로 적지 않는다.
5. 생성 전에 제목, label, 본문을 사용자에게 보여 주고 확인을 받는다.
6. `mcp__github__.issue_write` 를 `method=create` 로 호출해 이슈를 만든다.
   - `owner`: `AI-SIP`
   - `repo`: `OnO_BACKEND`
   - `assignees`: `["KiSeungMin"]`
   - `labels`: 존재 확인된 label 배열
   - issue type 은 유효한 값을 확인할 수 있을 때만 지정한다.

## Output

- 이슈 번호와 URL
- 적용한 assignee, label, type
