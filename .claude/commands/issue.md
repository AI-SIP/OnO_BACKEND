---
description: AI-SIP/OnO_BACKEND 리포지토리에 GitHub 이슈를 생성한다. 텍스트 설명 또는 파일 경로를 받아 템플릿에 맞게 이슈를 작성한다.
argument-hint: "<이슈 설명 또는 파일 경로>"
---

# /issue — GitHub 이슈 생성

## 입력: $ARGUMENTS

## 단계

### 1. 입력 파악
- `$ARGUMENTS`가 존재하는 파일 경로이면 해당 파일을 읽어 내용을 이슈 소스로 사용한다.
- 파일 경로가 아니면 텍스트 그대로를 이슈 소스로 사용한다.
- 인자가 없으면 사용자에게 이슈 내용을 물어본다.

### 2. 이슈 유형 판단
입력 내용을 분석해 다음 중 하나로 분류한다:

| 유형 | 사용 템플릿 | labels | 제목 prefix |
|---|---|---|---|
| 버그·오류·에러·crash·NPE | Bug Report Template | `bug` | `[bug]` |
| 기능 추가·신규 개발 | Common Issue Template | `feature` | `[feat]` |
| 리팩터·코드 정리 | Common Issue Template | `refactor` | `[refactor]` |
| 설정·환경·의존성·chore | Common Issue Template | `chore` | `[chore]` |
| 성능 개선 | Common Issue Template | `performance` | `[perf]` |
| 문서·주석 | Common Issue Template | `documentation` | `[docs]` |
| 테스트 | Common Issue Template | `test` | `[test]` |
| 기타·불명확 | Common Issue Template | (없음) | `[chore]` |

### 3. 템플릿 채우기

**Bug Report Template** (버그 유형):
```
## ⚙️ 어떤 버그인가요?
[버그 핵심 요약 — 1~2문장]

### 스크린샷(선택)
[입력에 첨부 이미지·로그가 있으면 삽입, 없으면 섹션 생략]

## 🔎 어떤 상황에서 발생한 버그인가요?
**Given** — [사전 조건]
**When** — [행동]
**Then** — [실제 결과 / 오류]

## ✅ 예상 결과
[정상적으로 동작해야 하는 결과]
```

**Common Issue Template** (그 외):
```
## 📝 Description
[작업에 대한 간략한 설명]

## ✅ TODO
- [ ] [할 일 1]
- [ ] [할 일 2]
(입력에서 구체적인 작업 항목을 추출해 체크리스트로 작성)

## 💡 ETC (선택)
[기타 참고 사항, 고려할 점, 논의 필요 사항 — 없으면 섹션 생략]
```

### 4. 이슈 생성 전 미리보기 확인
이슈를 생성하기 전에 다음 항목을 사용자에게 보여주고 확인한다:
- **제목**: 결정된 제목
- **Labels**: 결정된 레이블
- **본문 미리보기**: 작성된 본문 전체

사용자가 수정을 요청하면 반영한 뒤 재확인한다. 승인하면 5단계로 진행한다.

### 5. GitHub 이슈 생성
`mcp__github__issue_write` 도구를 사용해 이슈를 생성한다:
- `owner`: `AI-SIP`
- `repo`: `OnO_BACKEND`
- `method`: `create`
- `title`: 3단계에서 결정한 제목
- `body`: 3단계에서 작성한 본문
- `assignees`: `["KiSeungMin"]` (항상 고정)
- `labels`: 2단계에서 결정한 레이블 배열

### 6. 결과 보고
생성된 이슈 URL을 사용자에게 알린다.