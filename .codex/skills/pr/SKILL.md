---
name: pr
description: OnO 현재 브랜치의 개발 내역을 바탕으로 develop 브랜치에 merge 요청하는 GitHub PR을 직접 생성할 때 사용. 사용자가 "pr"을 요청하면 현재 브랜치에서 develop으로 PR을 생성한다.
---

# OnO PR

## Workflow

1. 현재 브랜치를 확인한다. 현재 브랜치가 `main` 또는 `develop`이면 PR을 생성하지 말고 작업 브랜치에서 실행해야 한다고 보고한다.
2. base branch는 항상 `develop`으로 고정한다. `main` branch를 base로 하는 PR은 절대 생성하지 않는다. `main` merge와 production 배포는 항상 사용자가 직접 담당한다.
3. `git status`로 미커밋 변경사항을 확인한다. 미커밋 변경사항이 있으면 PR을 생성하지 말고 먼저 커밋 또는 정리가 필요하다고 보고한다.
4. 현재 브랜치가 원격에 push되어 있는지 확인한다. 원격 브랜치가 없거나 미push 커밋이 있으면 `git push -u origin 현재브랜치`로 직접 push해 원격 브랜치를 만든 뒤 진행한다.
5. `develop..현재브랜치`의 `git log`와 `git diff`를 확인해 변경 범위를 파악한다.
6. GitHub에서 `AI-SIP/OnO_BACKEND`의 open issue를 먼저 조회해 현재 변경사항과 관련된 이슈가 있는지 확인한다.
   - `mcp__github__.list_issues`를 사용한다.
   - 브랜치명, 커밋 메시지, 변경 파일, 작업 키워드와 이슈 제목/본문을 비교한다.
   - 실제로 이번 PR에서 해결되는 이슈가 있으면 PR 본문 `연관 이슈` 항목에 `close #이슈번호`를 포함한다.
   - 단순 참고 이슈는 자동 종료되지 않도록 `close` 없이 참고 문장으로만 적는다.
   - 관련 이슈가 없으면 `연관 이슈` 항목에 `없음`이라고 명시한다.
7. `.github/PULL_REQUEST_TEMPLATE.md`를 반드시 읽고 같은 구조로 PR 본문을 작성한다.
8. 수정된 파일, API/DB/인증/권한/알림/배포 영향 범위를 정리한다.
9. 테스트와 검증은 실행 여부, 명령, 결과를 구분해 적는다.
10. PR 제목은 브랜치명과 커밋 내역을 종합해 `[Feat]`, `[Fix]`, `[Refactor]`, `[Chore]`, `[Docs]`, `[Test]` 중 가장 적절한 타입으로 작성한다.
11. Labels는 변경 성격에 맞게 `feat`, `fix`, `refactor`, `chore`, `docs`, `test`, `bug` 중 적절한 후보를 고르되, `mcp__github__.get_label`로 존재 여부를 확인한 label만 적용한다.
12. `mcp__github__.create_pull_request`로 PR을 생성한다.
    - `owner`: `AI-SIP`
    - `repo`: `OnO_BACKEND`
    - `base`: `develop`
    - `head`: 현재 브랜치명
    - `title`: 작성한 PR 제목
    - `body`: 작성한 PR 본문
    - Reviewers는 지정하지 않는다.
13. PR 생성 후 생성된 PR 번호에 `mcp__github__.issue_write`를 `method=update`로 호출해 Assignees와 Labels를 설정한다.
    - `owner`: `AI-SIP`
    - `repo`: `OnO_BACKEND`
    - `issue_number`: 생성된 PR 번호
    - `assignees`: `["KiSeungMin"]`
    - `labels`: 존재 확인된 label 배열
14. milestone, project, issue fields, type 등 나머지 항목은 지정하지 않는다.

## Output

- 생성된 PR 번호와 URL
- base/head branch
- 원격 브랜치 생성 또는 push 수행 여부
- Assignee: `KiSeungMin`
- 적용한 label
- 연결한 issue와 `close #이슈번호` 포함 여부
- `main`을 target으로 사용하지 않았다는 확인
- 사용자 영향
- 검증 결과
- 배포 리스크와 rollback/대응 방법
