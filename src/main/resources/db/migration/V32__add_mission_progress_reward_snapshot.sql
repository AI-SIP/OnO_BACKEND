-- 보상 스냅샷. 받은 시점의 보상 종류와 값을 진행도 행에 박아 둔다.
--
-- 기록 조회가 "현재" 미션 정의의 보상값을 읽으면, 운영 중에 보상을 바꿨을 때
-- 예전에 받은 기록까지 새 값으로 보인다. 80 XP 를 받은 사람의 기록이 어느 날 50 XP 로 바뀐다.
-- target_snapshot 을 둔 이유와 정확히 같은 문제다.
--
-- 지금 넣는 게 싸다. 보상 받기 기능이 아직 배포 전이라 claimed_at 이 채워진 행이 운영에 하나도 없다.
-- 나중에 넣으면 이미 받은 행들을 소급해서 채워야 하는데, 그때의 보상값은 알 방법이 없다.
--
-- 옛 행을 위해 nullable 로 둔다. 스냅샷이 비어 있으면 조회가 현재 정의로 폴백한다.
--
-- V29·V31 과 같은 이유로 재실행에 대비한다. ALTER 가 커밋된 뒤 이력이 남기 전에 끊기면
-- 재기동 때 Duplicate column name 으로 앱이 뜨지 않는다. MySQL 에는 ADD COLUMN IF NOT EXISTS 가 없어
-- information_schema 를 보고 없을 때만 실행한다.

SET @column_exists := (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'mission_progress'
      AND column_name = 'reward_value_snapshot'
);

SET @ddl := IF(@column_exists = 0,
    'ALTER TABLE mission_progress
         ADD COLUMN reward_type_snapshot  VARCHAR(20) NULL,
         ADD COLUMN reward_value_snapshot INT         NULL',
    'SELECT 1');

PREPARE add_reward_snapshot FROM @ddl;
EXECUTE add_reward_snapshot;
DEALLOCATE PREPARE add_reward_snapshot;
