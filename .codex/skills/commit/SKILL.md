---
name: commit
description: 현재 변경사항의 커밋 단위와 OnO 커밋 메시지를 정리하고 실제 git commit까지 수행할 때 사용. 인자가 없어도 현재 git 변경사항을 대상으로 한다.
---

# OnO Commit

## Workflow

1. `git status`와 `git diff HEAD`로 현재 변경 전체를 파악한다.
2. 사용자 변경과 이번 작업 변경을 구분한다. 임의로 되돌리지 않는다.
3. 변경을 독립적으로 빌드/검토 가능한 논리 단위로 나눈다.
4. 각 단위에 커밋 메시지를 결정한다.
5. 논리 단위별로 필요한 파일만 `git add`하고 `git commit`을 수행한다.
6. 커밋 후 `git status`로 작업 트리에 남은 변경사항을 확인한다.

## Message Format

```text
[Feat] 구현 타이틀 - 구현 상세1 구현 상세2
```

태그 후보:

```text
[Feat] [Fix] [Refactor] [Chore] [Test] [Docs] [Perf]
```

## Output

- 생성한 커밋 단위
- 각 단위에 포함할 파일
- 커밋 메시지
- 커밋 해시
- 작업 트리에 남은 변경사항 여부
