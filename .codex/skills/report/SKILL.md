---
name: report
description: 특정 커밋부터 현재까지의 OnO 작업 결과 보고서 또는 PR 초안을 작성할 때 사용. 인자가 없으면 현재 git 변경사항을 대상으로 변경 분석, 사용자 영향, 검증 결과, 배포 리스크를 markdown으로 정리한다.
---

# OnO Report

## Workflow

1. 기준 커밋 해시 또는 변경 범위를 확인한다. 인자가 없으면 현재 git 변경사항을 대상으로 한다.
2. `git log`, `git diff`, `git status`로 커밋된 변경과 미커밋 변경을 구분한다.
3. 수정된 파일, 레이어, API/DB/인증/알림 영향 범위를 정리한다.
4. Before/After가 의미 있는 변경은 표로 요약한다.
5. 테스트 코드는 세부 구현보다 검증 결과 중심으로 적는다.
6. 저장 위치가 지정되지 않았으면 사용자에게 확인한다. OnO 백엔드 문서는 기본적으로 `/Users/ksm/programing/sw_maestro/OnO_BACKEND/backend/docs` 아래에 둔다.

## Output

- 변경사항 요약
- 사용자 영향
- 검증 결과
- 배포 리스크와 롤백/대응 방법
- 미커밋 변경 반영 여부
- 필요 시 PR 제목/본문 초안
