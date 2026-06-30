---
description: Flyway 마이그레이션 파일 작성 + 안전성 검토. DB 스키마 변경이 필요할 때 사용.
argument-hint: "<스키마 변경 내용 설명>"
---

# /migrate — DB 마이그레이션

## 작업: $ARGUMENTS

## 단계

1. **기존 마이그레이션 파악** — `src/main/resources/db/migration/`에서 최신 버전 번호 확인.  
   `code-tracer`로 변경할 테이블의 현재 엔티티·쿼리 구조 파악.

2. **마이그레이션 초안 작성** — 다음 번호(`V{N+1}__<설명>.sql`)로 SQL 초안 작성:
   - 안전한 순서: `ADD COLUMN NULL` → 백필 UPDATE → `ALTER COLUMN NOT NULL` (한 파일 또는 단계별 분리)
   - 대용량 테이블이면 락 영향 명시
   - 롤백 SQL 주석으로 포함

3. **`migration-safety` 에이전트로 검토** — 초안을 검토:
   - 되돌리기 불가 작업 여부
   - 락 위험
   - 데이터 정합성
   - Flyway 파일 형식

4. **API 변경 수반 시** — DTO·엔드포인트도 변경된다면 `api-contract-checker` 에이전트 추가 투입:
   - Flutter 앱에 breaking change 여부 판단

5. **검토 결과 보고 + 승인 대기** — 🔴·🟠 항목이 있으면 수정 후 재검토.  
   안전 확인 후: "마이그레이션 파일을 저장할까요?" 확인.

6. **파일 저장** — 승인 시 `V{N+1}__<설명>.sql` 파일 생성.

> ⚠️ dev에서 먼저 적용 테스트 후 prod 반영. 프로덕션 Flyway는 자동 실행되므로 한 번 머지되면 되돌리기 어렵다.
