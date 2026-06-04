---
name: migrate
description: OnO DB schema 변경과 Flyway 마이그레이션 파일 작성/검토가 필요할 때 사용. 사용자가 "migrate <스키마 변경>"를 요청하면 기존 migration, entity, query 영향을 확인하고 안전한 SQL 초안을 만든다.
---

# OnO Migrate

## Workflow

1. 스키마 변경 목적, 대상 테이블, 사용자 데이터 영향 범위를 먼저 확인한다.
2. `src/main/resources/db/migration/`의 최신 Flyway 버전 번호와 기존 변경 이력을 확인한다.
3. 관련 entity, repository, query, DTO/API 계약을 추적한다.
4. 운영 DB 기준으로 되돌리기 어려운 작업인지 확인한다.
5. 마이그레이션은 가능한 안전한 순서로 설계한다.
   - `ADD COLUMN NULL`
   - 필요한 경우 backfill `UPDATE`
   - 검증 후 `NOT NULL`, index, constraint 적용
6. 대용량 테이블이면 lock, full scan, 배포 시간, rollback 가능성을 명시한다.
7. SQL 파일은 `V{N+1}__<snake_case_description>.sql` 형식으로 제안한다.
8. 파일 저장 전에는 사용자에게 최종 확인한다. 승인 없이 production 영향 가능성이 있는 migration 파일을 확정하지 않는다.

## Guardrails

- H2 인메모리 DB가 아닌 환경에 `ddl-auto=create` 또는 `create-drop`을 추가하지 않는다.
- `DROP`, `TRUNCATE`, column type 축소, `NOT NULL` 즉시 추가, 대량 backfill은 별도 위험으로 표시한다.
- rollback SQL 또는 대응 절차를 주석이나 보고에 포함한다.
- API 응답/요청 구조가 함께 바뀌면 Flutter 호환성과 기존 앱 버전 영향을 확인한다.

## Output

- 변경 목적과 대상 schema
- 기존 코드/쿼리 영향
- migration SQL 초안 또는 생성 파일
- rollback/대응 방법
- dev/prod 적용 전 검증 방법
- 배포 리스크와 확인 질문
