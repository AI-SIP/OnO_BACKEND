-- 미션 보상 획득 기록 조회(GET /api/missions/history)용 인덱스.
--
-- 조회 형태는 "내 것 중 받은 것만, 받은 시각 역순" 한 가지다.
--   WHERE user_id = ? AND claimed_at IS NOT NULL ORDER BY claimed_at DESC
-- 기존 인덱스는 (user_id, period_key) 뿐이라 이 조회를 타지 못하고 사용자의 전체 진행도를 훑는다.
--
-- 정렬 키를 선행 컬럼 뒤에 두면 MySQL 이 인덱스를 역방향으로 읽어 정렬 자체를 생략한다.
-- claimed_at 이 같은 행끼리의 순서는 InnoDB 가 보조 인덱스 끝에 붙이는 기본키(id)가 갈라 주므로
-- 커서 페이지네이션의 동점 처리까지 이 인덱스 하나로 해결된다.
--
-- V29 가 CREATE TABLE IF NOT EXISTS 로 재실행에 대비한 것과 같은 이유로 여기도 대비한다.
-- DDL 이 커밋된 뒤 Flyway 가 이력을 남기기 전에 커넥션이 끊기면, 재기동 때 이 파일이 다시 돌면서
-- Duplicate key name 으로 앱이 뜨지 않는다. MySQL 에는 CREATE INDEX IF NOT EXISTS 가 없어
-- information_schema 를 보고 없을 때만 실행한다.

SET @index_exists := (
    SELECT COUNT(*)
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'mission_progress'
      AND index_name = 'idx_mission_progress_claimed'
);

SET @ddl := IF(@index_exists = 0,
    'CREATE INDEX idx_mission_progress_claimed ON mission_progress (user_id, claimed_at)',
    'SELECT 1');

PREPARE create_claimed_index FROM @ddl;
EXECUTE create_claimed_index;
DEALLOCATE PREPARE create_claimed_index;
