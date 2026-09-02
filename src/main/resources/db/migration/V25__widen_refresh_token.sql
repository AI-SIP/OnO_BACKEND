-- refresh_token.refresh_token 을 varchar(255) -> varchar(512) 로 넓힌다.
--
-- 원본 JWT 를 그대로 저장하는데 현재 클레임 구성으로 실측 최대 244자다. 아직 터지지는
-- 않았지만 클레임이 하나만 늘어도 255를 넘긴다. 저장이 잘리면 이후 갱신 요청이 세션을
-- 찾지 못해 REFRESH_TOKEN_NOT_FOUND(1002) 가 된다.
--
-- 셋 중 유일하게 인덱스가 걸린 컬럼이고, collation 이 바뀌면 인증 경로의 동등 비교에
-- 영향이 갈 수 있는 유일한 컬럼이다. 지금 터지는 버그가 아니라 예방적 확장이므로
-- V23/V24 와 같은 배포에 묶지 말고, 트래픽이 낮은 시간대에 단독으로 적용하는 것을 권한다.
--
-- 프로덕션 flyway_schema_history 확인 결과 V17 은 적용되어 있다(2026-06-30). 즉
-- 프리픽스 인덱스 idx_refresh_token_token (refresh_token(255)) 가 프로덕션에 실재한다.
-- 이 인덱스는 그대로 둔다.
-- 프리픽스 길이는 문자 수 기준이라 utf8mb4 에서 1020바이트이고 InnoDB 한계(3072) 안이다.
-- 컬럼이 넓어져도 인덱스 정의는 유효하며, 동등 비교는 프리픽스로 후보를 좁힌 뒤
-- 행에서 전체 값을 재확인하는 방식이라 정확성도 유지된다.
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
--    WHERE table_schema = DATABASE() AND table_name = 'refresh_token';
--   SHOW CREATE TABLE refresh_token;

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
--        SELECT COUNT(*) FROM refresh_token WHERE CHAR_LENGTH(refresh_token) > 255;
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

ALTER TABLE refresh_token
    MODIFY COLUMN refresh_token VARCHAR(512)
        CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
    ALGORITHM=INPLACE, LOCK=NONE;
