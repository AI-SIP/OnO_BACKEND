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
-- 문장이 하나라 중간에 끊길 지점이 없다.
ALTER TABLE mission_progress
    ADD COLUMN reward_type_snapshot  VARCHAR(20) NULL,
    ADD COLUMN reward_value_snapshot INT         NULL;
