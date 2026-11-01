---
name: migrate
description: OnO DB 스키마 변경을 위한 Flyway 마이그레이션 SQL 을 작성하고 운영 안전성을 검토한다. 사용자가 마이그레이션 작성을 명시적으로 요청할 때만 사용한다.
argument-hint: "<스키마 변경 내용>"
disable-model-invocation: true
---

# migrate — DB 마이그레이션

> ⚠️ 프로덕션 Flyway 는 배포 때 자동 실행된다. 한 번 머지되면 되돌리기 어려우니 dev 에서 먼저 적용해 본 뒤 prod 에 반영한다.

## 작업

$ARGUMENTS

> Claude Code 는 위 자리에 슬래시 명령 인자를 넣는다. 비어 있거나 `$ARGUMENTS` 가 그대로 보이면 사용자 메시지에서 작업을 읽는다.

## 단계

1. **현재 상태 파악**
   - 변경 목적, 대상 테이블, 사용자 데이터 영향 범위를 정리한다.
   - `src/main/resources/db/migration/` 의 최신 버전을 **숫자 기준으로** 찾는다(`ls | sort -V`). 사전순으로 보면 `V9` 가 `V47` 보다 뒤에 온다.
   - 관련 엔티티, repository, 쿼리, DTO 를 추적한다(Claude Code 에서는 `code-tracer` 에이전트).
2. **초안 작성**: 다음 번호 `V{N+1}__<snake_case_설명>.sql` 로 작성한다.
   - 안전한 순서: `ADD COLUMN NULL` → 필요하면 backfill `UPDATE` → 검증 후 `NOT NULL`, 인덱스, 제약
   - 대용량 테이블이면 락, 풀스캔, 배포 시간 영향을 적는다.
   - 롤백 SQL 이나 대응 절차를 주석으로 넣는다.
3. **안전성 검토**: 되돌리기 불가 작업, 락 위험, 데이터 정합성, Flyway 파일 형식을 본다(Claude Code 에서는 `migration-safety` 에이전트).
   - `DROP`, `TRUNCATE`, 컬럼 타입 축소, `NOT NULL` 즉시 추가, 대량 backfill 은 별도 위험으로 표시한다.
   - `ddl-auto` 를 `create` / `create-drop` 으로 바꾸지 않는다.
4. **API 영향**: DTO 나 엔드포인트도 바뀌면 기존 Flutter 앱 버전과 호환되는지 확인한다(Claude Code 에서는 `api-contract-checker` 에이전트).
5. **보고와 승인 대기**: 🔴, 🟠 항목이 있으면 고친 뒤 다시 검토한다. 안전하다고 판단되면 "마이그레이션 파일을 저장할까요?" 하고 확인받는다.
6. **저장**: 승인을 받은 뒤에만 파일을 만든다.

## 산출물

- 변경 목적과 대상 스키마
- 기존 코드와 쿼리 영향
- 마이그레이션 SQL (초안 또는 생성한 파일)
- 롤백과 대응 방법
- dev/prod 적용 전 검증 방법과 배포 리스크
