-- image_data: problem_id + image_type + created_at 복합 인덱스
-- validateSolveImageNotRegisteredToday 쿼리 최적화
CREATE INDEX idx_image_data_problem_type_created
    ON image_data (problem_id, image_type, created_at);

-- problem_review_reminder: NOT EXISTS 서브쿼리 최적화
-- findDueReminders의 (user_id, status, sent_at, deleted_at) 필터
CREATE INDEX idx_reminder_sent
    ON problem_review_reminder (user_id, status, sent_at, deleted_at);
