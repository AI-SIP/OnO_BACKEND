-- User: 알림 스팸 방지용 마지막 알림 발송일
ALTER TABLE user
    ADD COLUMN last_notified_at DATE NULL;

-- 기존 유저 last_active_at 백필 (updated_at 기준)
UPDATE user SET last_active_at = updated_at WHERE last_active_at IS NULL AND deleted_at IS NULL;

-- 스케줄러 전체 날짜 범위 스캔용 인덱스 (next_review_at 선두)
CREATE INDEX idx_problem_next_review_date ON problem (next_review_at, user_id);