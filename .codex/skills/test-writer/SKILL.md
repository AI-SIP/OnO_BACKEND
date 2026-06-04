---
name: test-writer
description: OnO 백엔드 또는 Flutter 테스트를 새로 작성하거나 보강할 때 사용. Spring/JUnit/API 통합 테스트, Flutter widget/unit 테스트, 버그 재현 테스트가 필요한 요청에서 자동으로 사용한다.
---

# OnO Test Writer

## Workflow

1. 변경된 동작을 검증 가능한 성공/실패 조건으로 바꾼다.
2. 기존 테스트 구조, test profile, fixture, 인증 helper, DB 격리 방식을 먼저 확인한다.
3. 버그 수정이면 가능하면 실패하는 테스트를 먼저 추가하고 수정 후 통과시킨다.
4. 테스트 범위는 위험도에 맞춘다.
   - 단위 테스트: validation, 예외 분기, 데이터 변환, 핵심 비즈니스 로직
   - API 통합 테스트: 요청/응답, 인증/권한, validation 실패, DB 반영, 에러 응답
   - Flutter 테스트: null/empty/error/loading 상태와 핵심 위젯 렌더링
5. 테스트가 어려운 구조이면 이유와 대체 검증을 명확히 남긴다.

## OnO 체크

- 사용자별 데이터 격리와 권한 실패 케이스를 우선 확인한다.
- 검색, 오답노트, 스터디그룹, 결제, 알림, 동기화 흐름은 성공 케이스만으로 끝내지 않는다.
- MySQL/PostgreSQL 기반 환경에서 테스트 편의를 위해 `ddl-auto=create` 또는 `create-drop`을 추가하지 않는다.
