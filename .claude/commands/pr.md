---
description: 현재 브랜치의 개발 내역을 develop 브랜치로 merge 요청하는 GitHub PR 생성.
argument-hint: "<추가 설명 (선택)>"
---

# /pr — GitHub PR 생성 (→ develop)

## 추가 설명: $ARGUMENTS

## 주의사항

> ⚠️ **main 브랜치로는 절대 PR을 생성하지 않는다.** main merge는 프로덕션 자동 배포로 이어지므로 항상 사용자가 직접 수행한다.
> 이 명령어는 현재 브랜치 → `develop` PR만 생성한다.

---

## 단계

### 1. 브랜치 상태 확인
- `git branch --show-current`로 현재 브랜치 이름 확인
- 현재 브랜치가 `main` 또는 `develop`이면 **즉시 중단**하고 작업 브랜치에서 실행해야 한다고 알린다
- `git log develop..HEAD --oneline`으로 develop 대비 커밋 목록 확인
- `git diff develop...HEAD`로 변경 내용 파악
- `git status`로 미커밋 변경사항 확인 (있으면 사용자에게 명시)
- 원격 브랜치 존재 여부와 미push 커밋 확인

### 2. 변경 내용 분석
커밋 로그와 diff를 바탕으로 다음을 파악한다:
- **작업 유형**: feat / fix / refactor / chore / perf / docs / test (복수 가능)
- **영향 범위**: 어떤 패키지·API·DB 스키마가 바뀌었는지
- **연관 이슈 후보 추출**: 커밋 메시지와 브랜치명에서 이슈 번호 또는 키워드를 추출한다
  - 브랜치명 패턴: `feat/123-login` → 번호 `123`, 키워드 `login`
  - 커밋 메시지 패턴: `close #123`, `fix #123`, `resolve #123` → 번호 `123`
  - 번호가 없으면 작업 내용을 요약한 키워드를 추출한다

### 2-1. GitHub에서 연관 이슈 검색
`mcp__github__.list_issues`를 사용해 `AI-SIP/OnO_BACKEND` 리포지토리의 **open 상태** 이슈를 검색한다:
- 2단계에서 추출한 이슈 번호나 키워드를 open issue의 번호, 제목, 본문과 비교한다
- 검색 결과 중 이번 PR 작업과 연관성이 높은 이슈를 골라 연관 이슈 목록을 확정한다
- PR에서 실제로 해결되는 이슈만 `close #이슈번호`로 연결한다
- 단순 참고 이슈는 자동 종료되지 않도록 `close` 없이 참고 문장으로만 작성한다
- 연관 이슈를 찾지 못하면 "없음"으로 처리한다

### 3. 레이블 결정
변경 내용을 분석해 다음 중 하나 이상으로 레이블을 결정한다:

| 작업 유형 | label |
|---|---|
| 신규 기능 | `feature` |
| 버그 수정 | `bug` |
| 리팩터링 | `refactor` |
| 설정·의존성·chore | `chore` |
| 성능 개선 | `performance` |
| 문서·주석 | `documentation` |
| 테스트 | `test` |

결정한 label은 적용 전에 `mcp__github__.get_label`로 존재 여부를 확인하고, 존재하는 label만 적용한다.

### 4. PR 제목 결정
브랜치명과 커밋 내역을 종합해 PR 제목을 결정한다.
- 형식: `[타입] 핵심 변경 요약` (예: `[Feat] 오답노트 태그 필터 기능 추가`)
- 여러 타입이 섞이면 가장 비중이 큰 타입 하나를 사용

### 5. PR 본문 작성
`.github/PULL_REQUEST_TEMPLATE.md` 구조를 그대로 따른다. 실제 변경 내용에 맞게 체크박스를 채운다:

```
## ✔️ 연관 이슈
- close #이슈번호   ← 2-1단계에서 찾았고 PR merge 시 닫아야 하는 이슈마다 한 줄씩 기재
- 없음              ← 관련 이슈가 없으면 기재

## 📝 작업 내용
> [커밋 단위로 bullet 정리]

## 👤 사용자 영향
- [사용자 관점에서 달라지는 점 / 영향 없으면 "없음"]

## 🔌 API 호환성
- [ ] 요청/응답 DTO 변경 없음
- [ ] 기존 Flutter 앱 버전과 호환 확인
- [ ] breaking change 있음

## 🗄️ DB migration
- [ ] 없음
- [ ] 있음: migration 파일과 기존 데이터 호환성 확인

## 🔐 인증/권한
- [ ] 영향 없음
- [ ] 사용자별 데이터 소유권 검증 확인
- [ ] 관리자/특수 권한 영향 확인

## ✅ 검증 결과
- [실행한 테스트·빌드 명령과 결과 / 실행하지 못한 검증과 남은 리스크]

## 🚀 배포 리스크
- [배포 시 주의할 점 / 없으면 "없음"]

## ↩️ 롤백/대응 방법
- [문제 발생 시 롤백 방법 / 해당 없으면 "없음"]

### 스크린샷 (선택)
```

### 6. 생성 전 미리보기 확인
PR을 생성하기 전에 다음 항목을 사용자에게 보여주고 확인을 받는다:
- **Base → Head**: `develop` ← 현재 브랜치명
- **제목**: 결정된 제목
- **Labels**: 결정된 레이블
- **연관 이슈**: `close #이슈번호` 또는 없음
- **본문 미리보기**: 작성된 본문 전체

사용자가 수정을 요청하면 반영한 뒤 재확인한다. 승인하면 다음 단계로 진행한다.

### 7. 원격 브랜치 push
원격 브랜치가 없거나 미push 커밋이 있으면 직접 push한 뒤 PR 생성을 진행한다.

```bash
git push -u origin $(git branch --show-current)
```

push 실패 시 인증, 권한, 네트워크 오류를 보고하고 PR 생성을 중단한다.

### 8. GitHub PR 생성
`mcp__github__.create_pull_request` 도구를 사용해 PR을 생성한다:
- `owner`: `AI-SIP`
- `repo`: `OnO_BACKEND`
- `title`: 4단계에서 결정한 제목
- `body`: 5단계에서 작성한 본문
- `head`: 현재 브랜치명
- `base`: `develop` (절대 고정 — main 불가)

Reviewers는 지정하지 않는다.

### 8-1. Assignee/Label 설정
PR 생성 후 생성된 PR 번호에 `mcp__github__.issue_write` 도구를 `method=update`로 호출한다:
- `owner`: `AI-SIP`
- `repo`: `OnO_BACKEND`
- `issue_number`: 생성된 PR 번호
- `assignees`: `["KiSeungMin"]` (항상 고정)
- `labels`: 3단계에서 결정하고 존재 확인된 label 배열

milestone, project, issue fields, type 등 나머지 항목은 지정하지 않는다.

### 9. 결과 보고
생성된 PR URL, base/head branch, assignee, 적용 label, 연결한 issue, `main`을 target으로 사용하지 않았다는 점을 사용자에게 알린다.
