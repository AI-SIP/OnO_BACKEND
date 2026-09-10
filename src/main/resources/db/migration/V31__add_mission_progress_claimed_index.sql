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
-- 문장이 하나라 중간에 끊길 지점이 없다. 실패하면 인덱스가 만들어지지 않은 상태 그대로다.
CREATE INDEX idx_mission_progress_claimed
    ON mission_progress (user_id, claimed_at);
