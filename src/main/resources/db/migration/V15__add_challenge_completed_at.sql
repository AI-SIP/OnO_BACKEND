ALTER TABLE study_room_challenge
    ADD COLUMN completed_at DATETIME NULL COMMENT '챌린지 완료 시각 (최초 완료 시 1회만 기록)';
