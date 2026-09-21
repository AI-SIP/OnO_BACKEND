-- problem_review_reminder.problem_memo_snapshot 을 varchar(255) -> varchar(1000) 으로 넓힌다.
--
-- V21 이 이 컬럼을 VARCHAR(255) 로 만들었다. problem.memo 를 그대로 복사해 담는 스냅샷이라
-- V23 만 적용하면 리마인더 생성 시점에 같은 truncation 이 그대로 재발한다.
-- 반드시 V23 과 함께 나가야 한다.
--
-- 같은 테이블의 problem_reference_snapshot(255) 은 원본 problem.reference 가 255 라 그대로 둔다.
--
-- [프로덕션 적용 순서 - 실측 반영]
-- 프로덕션 조회 결과 problem_review_reminder 테이블 자체가 아직 없다. V21 이 프로덕션에
-- 적용되지 않았다는 뜻이다. Flyway 는 버전 순서대로 적용하므로 다음 배포에서
-- V21 이 테이블을 만든 뒤 이 파일이 컬럼을 넓힌다. 순서상 문제는 없다.
--
-- V21 을 직접 고쳐 처음부터 VARCHAR(1000) 으로 만드는 편이 깔끔해 보이지만 그렇게 하지 않는다.
-- V21 이 dev 에는 이미 적용됐을 수 있고, validate-on-migrate: true 이므로 적용된 마이그레이션의
-- 내용을 바꾸면 체크섬 불일치로 dev 기동이 실패한다.
--
-- [락 / 알고리즘]
-- ALGORITHM=INPLACE, LOCK=NONE 을 명시한다. 절이 없으면 MySQL 이 알아서 고르는데,
-- INPLACE 가 불가능한 조건이면 에러 없이 조용히 COPY 로 내려가 테이블을 통째로 재작성하고
-- 그동안 쓰기가 막힌다. 명시하면 불가능할 때 ER_ALTER_OPERATION_NOT_SUPPORTED(1845) 로
-- 즉시 실패하므로, 알 수 없는 길이의 장애가 1초짜리 실패로 바뀐다.
--
-- lock_wait_timeout 은 MDL 대기 pile-up 방지용이다. 메타데이터만 바꾸는 ALTER 도 시작·종료에
-- 배타적 MDL 을 잠깐 잡는데, 장시간 트랜잭션이 테이블을 붙들고 있으면 DDL 이 대기하고
-- 그 뒤의 모든 쿼리가 DDL 뒤에 줄을 선다. 10초로 끊으면 실패는 하되 서비스는 멀쩡하다.
--
-- [적용 전 확인 - 읽기 전용]
-- MODIFY COLUMN 은 컬럼 정의를 통째로 교체한다. 명시하지 않은 CHARACTER SET/COLLATE 는
-- 유지되지 않고 테이블 기본값으로 리셋된다. 컬럼 collation 이 테이블 기본과 다르면
-- collation 변경 = 인덱스 재구축 = COPY 가 되므로, 아래로 먼저 확인하고
-- 다를 경우 현재 값을 ALTER 에 그대로 재기술해야 한다.
--   SELECT column_name, character_maximum_length, character_octet_length,
--          character_set_name, collation_name
--     FROM information_schema.columns
--    WHERE table_schema = DATABASE() AND table_name = 'problem_review_reminder';
--   SHOW CREATE TABLE problem_review_reminder;

--
-- [실패 시 복구]
-- MySQL 은 DDL 트랜잭션이 없다. 실패하면 flyway_schema_history 에 success=0 으로 남고,
-- validate-on-migrate: true 라 이후 모든 기동이 "Detected failed migration" 으로 죽는다.
-- 배포 파이프라인이 막히므로 아래로 이력을 정리한 뒤 재배포한다.
--   SELECT installed_rank, version, description, success
--     FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 5;
--   DELETE FROM flyway_schema_history WHERE version = '25' AND success = 0;
-- 이 ALTER 는 목표 정의로 수렴하는 문장이라 이미 적용된 상태에서 재실행해도 안전하다.
--
-- [롤백]
-- 되돌리지 않는 것이 원칙이다. 컬럼 확장은 구버전 앱과도 호환되므로 앱만 되돌리면 된다.
-- 굳이 축소해야 한다면 순서와 비용을 알고 해야 한다:
--   1. 앱을 먼저 롤백한다. 앱이 긴 값을 통과시키는 상태에서 컬럼만 줄이면 즉시 500 이 재발한다.
--   2. 초과 데이터가 0 건인지 확인한다:
--        SELECT COUNT(*) FROM problem_review_reminder WHERE CHAR_LENGTH(problem_memo_snapshot) > 255;
--   3. VARCHAR 축소는 INPLACE 가 불가능해 테이블 전체 재작성 + 쓰기 차단이다. 확장보다 위험하다.
--   4. flyway_schema_history 에서 해당 버전 행을 지운다. 안 지우면 스키마와 이력이 영구히 어긋난다.

--
-- [collation 명시 이유 - 프로덕션 실측 반영]
-- 프로덕션 조회 결과 이 컬럼은 utf8mb4 / utf8mb4_unicode_ci 이고 octet_length 는 1020 이다.
-- MODIFY COLUMN 은 명시하지 않은 CHARACTER SET/COLLATE 를 테이블 기본값으로 리셋하므로,
-- 테이블 기본값이 컬럼과 다를 경우 collation 이 바뀌면서 인덱스 재구축(COPY)이 일어난다.
-- 현재 값을 그대로 재기술해 테이블 기본값이 무엇이든 컬럼 정의가 바뀌지 않도록 한다.
--
-- 길이 프리픽스: 1020 octet -> 목표 크기도 255 octet 초과라 둘 다 2바이트 구간이다.
-- 프리픽스 크기가 바뀌지 않으므로 메타데이터 변경만으로 끝난다.


SET SESSION lock_wait_timeout = 10;

ALTER TABLE problem_review_reminder
    MODIFY COLUMN problem_memo_snapshot VARCHAR(1000)
        CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL,
    ALGORITHM=INPLACE, LOCK=NONE;
