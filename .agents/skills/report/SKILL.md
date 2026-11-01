---
name: report
description: 기준 커밋부터 현재까지의 OnO 작업 결과를 Before/After 중심 보고서로 정리해 docs/reports/ 에 저장한다. 작업 결과 보고서나 "report <커밋>" 을 요청할 때 사용한다.
argument-hint: "[기준 커밋 해시 — 없으면 현재 변경분]"
---

# report — 결과 보고서

## 기준

$ARGUMENTS

> Claude Code 는 위 자리에 슬래시 명령 인자를 넣는다. 비어 있거나 `$ARGUMENTS` 가 그대로 보이면 사용자 메시지에서 기준을 읽는다.

기준이 없으면 현재 변경분(커밋되지 않은 것 포함)을 대상으로 한다.

## 단계

1. **변경 분석**: `<커밋>..HEAD` 의 `git log`, `git diff` 와 `git status` 로 커밋된 변경과 미커밋 변경을 나눈다.
   - 수정된 파일과 레이어, API, DB, 인증, 알림 영향 범위
   - 각 변경의 목적과 Before/After 비교 지점
   - Claude Code 에서는 `code-tracer` 에이전트로 분석한다.
2. **작성**: 존댓말, 핵심 위주, Before/After 표를 넣는다. 테스트 코드 내용은 빼고 검증 결과만 적는다. 외부에 공유해도 되는 수준으로 쓴다.
   - Claude Code 에서는 `report-writer` 에이전트에 맡긴다.
3. **저장**: 파일명 `<개발사항>_보고서.md` 로 `docs/reports/` 아래에만 저장한다. `docs/` 는 gitignore 대상이라 로컬에만 남는다.

## 보고서에 들어갈 것

- 변경사항 요약
- 사용자 영향
- 검증 결과
- 배포 리스크와 롤백/대응 방법
- 미커밋 변경 반영 여부
