---
name: release-check
description: Use only when explicitly asked for a pre-deploy compatibility check ("release-check"). Extracts API-contract changes in origin/main..origin/develop (or a given range), cross-checks them against the released OnO_FRONT client, and writes a 배포 가능/주의/불가 report. Read-only.
---

# OnO Release Check

Claude 쪽 `.claude/skills/release-check/SKILL.md` 와 같은 절차다. 판정 기준과 보고서 틀은 그 파일을 읽고 따른다.

## Rules

- 읽기 전용. 코드 수정, 커밋, 머지, workflow 실행, 운영 서버 호출, DB 접속을 하지 않는다.
- 프론트 레포(`../../OnO_FRONT`)는 체크아웃하지 않고 `git -C <front> show <ref>:<path>`, `git -C <front> grep <pattern> <ref> -- lib` 로만 읽는다.
- 근거는 `레포/파일:라인`. 확인하지 못한 것은 "확인 못 함"으로 적는다.

## Workflow

1. `bash .claude/skills/release-check/extract-changes.sh [범위]` 로 변경을 뽑는다. "변경 없음"이면 끝낸다.
2. 프론트 `origin/main` 과 최신 `origin/release/*` 의 `pubspec.yaml` 버전을 확인하고 둘 다 비교 대상으로 삼는다.
3. 엔드포인트, 응답 DTO, 요청 DTO, 에러 코드, 인증, 날짜와 페이징, 알림 축별로 프론트 호출부와 파싱 코드를 대조한다.
4. Flyway 파일과 알림, 배치 변경은 잠금 대기, 재실행 가드, 발송 시각 가드를 따로 본다.
5. 불가와 주의 항목은 원본 파일을 다시 열어 확인한 뒤 판정한다.
6. 워크스페이스 `../../docs/<YYYY-MM-DD>-배포-전-호환성-검증/보고서.md` 에 쓴다.
