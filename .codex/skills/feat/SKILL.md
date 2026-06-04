---
name: feat
description: Use for OnO feature implementation. Trigger when the user requests "feat" or plan-based implementation. If no argument is provided, inspect current git changes and continue with remaining implementation, cleanup, and validation work.
---

# OnO Feat

## Workflow

1. 기준 구현 명세서나 작업 설명을 확인한다.
2. 인자가 없으면 `git status`와 `git diff HEAD` 기준으로 현재 변경사항을 먼저 읽고, 이어서 구현해야 할 남은 작업이나 검증 대상을 파악한다.
3. 기준 문서가 불명확하고 현재 변경사항만으로 구현 의도가 확정되지 않으면 어떤 명세서를 사용할지 사용자에게 묻는다.
4. 명세서와 실제 코드가 다르면 실제 코드 기준으로 판단하고 차이를 설명한다.
5. 구현 범위를 명세서 또는 현재 변경사항의 의도에 맞춰 제한한다. 임의 확장, 과한 추상화, 무관한 리팩터링을 하지 않는다.
6. 백엔드 구현 시 API 계약, DTO, validation, 예외 처리, transaction, 권한, 성능, 로그, 데이터 정합성을 확인한다.
7. DB schema 변경은 migration/명시 SQL을 우선한다. H2 인메모리가 아닌 환경에 `ddl-auto=create` 또는 `create-drop`을 추가하지 않는다.
8. 기능 위험도에 맞춰 단위 테스트 또는 API 통합 테스트를 추가/갱신한다.
9. 가능한 범위에서 포맷팅, 정적 검사, 관련 테스트, 빌드를 실행한다.
10. 완료 직전에 아래 자가 반증 질문에 답하고, 필요한 경우 추가 수정 또는 리스크 보고를 한다.
    - 내가 놓쳤을 가능성이 큰 것은?
    - 이 변경이 실제 사용자에게 깨질 수 있는 경로는?
    - 이 결론이 틀렸다면 어떤 조건 때문인가?

## Completion Report

- 변경한 내용
- 사용자에게 미치는 영향
- 실행한 검증과 결과
- 자가 반증 결과
- 남은 리스크 또는 확인 질문
- 추천 커밋 단위와 커밋 메시지
