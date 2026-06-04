---
name: commit
description: 현재 변경사항의 커밋 단위와 OnO 커밋 메시지를 제안할 때 사용. 인자가 없어도 현재 git 변경사항을 대상으로 한다. 실제 git commit은 사용자가 명시적으로 요청하기 전까지 실행하지 않는다.
---

# OnO Commit

## Workflow

1. `git status`와 `git diff HEAD`로 현재 변경 전체를 파악한다.
2. 사용자 변경과 이번 작업 변경을 구분한다. 임의로 되돌리지 않는다.
3. 변경을 독립적으로 빌드/검토 가능한 논리 단위로 나눈다.
4. 각 단위에 커밋 메시지를 제안한다.
5. 실제 `git add` 또는 `git commit`은 사용자가 명시적으로 요청한 경우에만 수행한다.

## Message Format

```text
[Feat] 구현 타이틀 - 구현 상세1 구현 상세2
```

태그 후보:

```text
[Feat] [Fix] [Refactor] [Chore] [Test] [Docs] [Perf]
```

## Output

- 추천 커밋 단위
- 각 단위에 포함할 파일
- 커밋 메시지
- 필요한 경우 커밋 순서
