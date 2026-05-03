-- Problem 테이블: 복습 스케줄 필드 추가
ALTER TABLE problem
    ADD COLUMN next_review_at     DATE         NULL,
    ADD COLUMN review_interval    INT          NOT NULL DEFAULT 1,
    ADD COLUMN consecutive_correct_count INT   NOT NULL DEFAULT 0;

-- User 테이블: 마지막 앱 접속 시각 추가 (알림 대상 필터링용)
ALTER TABLE user
    ADD COLUMN last_active_at DATETIME NULL;

-- 복습 대상 조회 성능을 위한 인덱스
CREATE INDEX idx_problem_review_due ON problem (user_id, next_review_at);