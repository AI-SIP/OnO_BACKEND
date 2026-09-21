-- soft delete 와 유니크 인덱스가 어긋나던 것을 고친다.
--
-- tag 와 problem_tag_mapping 은 @SQLDelete 로 deleted_at 만 채우고 행을 남기는데,
-- 유니크 인덱스는 deleted_at 을 보지 않아서 삭제된 행이 계속 자리를 차지하고 있었다.
-- 조회는 @SQLRestriction("deleted_at IS NULL") 때문에 그 행을 못 찾으니 INSERT 로 넘어가고,
-- DB 제약에 걸려 500 이 났다. 그래서 한 번 지운 태그는 같은 이름으로 다시 만들 수 없었다.
-- (Sentry JAVA-SPRING-BOOT-4Z: Duplicate entry '434-발상' for key 'tag.idx_tag_user_normalized')
--
-- 인덱스에 alive_key 를 넣어서 "살아있는 행은 하나뿐, 삭제된 행은 여러 개 공존"을 만든다.
--   살아있는 행: alive_key = 1970-01-01 (모두 같은 값이라 중복이 막힌다)
--   삭제된 행:   alive_key = deleted_at (삭제 시각이 서로 달라 여러 건이 공존한다)
--
-- id(AUTO_INCREMENT)를 참조하면 MySQL 이 거부하기 때문에(ERROR 3109) deleted_at 을 쓴다.
-- VIRTUAL 이라 테이블을 다시 쓰지 않고 메타데이터만 바뀐다.

-- ---------------------------------------------------------------------------
-- tag
-- ---------------------------------------------------------------------------
ALTER TABLE tag
    ADD COLUMN alive_key DATETIME(6)
    AS (IFNULL(deleted_at, '1970-01-01 00:00:00.000000')) VIRTUAL;

SET @idx := (SELECT COUNT(*) FROM information_schema.STATISTICS
             WHERE table_schema = DATABASE()
               AND table_name = 'tag'
               AND index_name = 'idx_tag_user_normalized');
SET @sql := IF(@idx > 0,
               'ALTER TABLE tag DROP INDEX idx_tag_user_normalized',
               'DO 0');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

CREATE UNIQUE INDEX idx_tag_user_normalized
    ON tag (user_id, normalized_name, alive_key);

-- ---------------------------------------------------------------------------
-- problem_tag_mapping
-- 같은 구조라 태그 생성이 풀리면 다음은 여기서 같은 500 이 난다.
-- ---------------------------------------------------------------------------
ALTER TABLE problem_tag_mapping
    ADD COLUMN alive_key DATETIME(6)
    AS (IFNULL(deleted_at, '1970-01-01 00:00:00.000000')) VIRTUAL;

SET @idx := (SELECT COUNT(*) FROM information_schema.STATISTICS
             WHERE table_schema = DATABASE()
               AND table_name = 'problem_tag_mapping'
               AND index_name = 'uk_problem_tag_mapping_problem_tag');
SET @sql := IF(@idx > 0,
               'ALTER TABLE problem_tag_mapping DROP INDEX uk_problem_tag_mapping_problem_tag',
               'DO 0');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

CREATE UNIQUE INDEX uk_problem_tag_mapping_problem_tag
    ON problem_tag_mapping (problem_id, tag_id, alive_key);
