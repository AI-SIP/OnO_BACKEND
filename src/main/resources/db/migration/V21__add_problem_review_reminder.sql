CREATE TABLE problem_review_reminder (
    id                         BIGINT      NOT NULL AUTO_INCREMENT,
    user_id                    BIGINT      NOT NULL,
    problem_id                 BIGINT      NOT NULL,
    problem_memo_snapshot      VARCHAR(255) NULL,
    problem_reference_snapshot VARCHAR(255) NULL,
    sequence                   INT         NOT NULL,
    interval_days              INT         NOT NULL,
    scheduled_at               DATETIME    NOT NULL,
    status                     VARCHAR(30) NOT NULL,
    sent_at                    DATETIME    NULL,
    last_error_message         VARCHAR(500) NULL,
    retry_count                INT         NOT NULL DEFAULT 0,
    created_at                 DATETIME    NULL,
    updated_at                 DATETIME    NULL,
    deleted_at                 DATETIME    NULL,
    PRIMARY KEY (id)
);

CREATE INDEX idx_problem_review_reminder_due
    ON problem_review_reminder (status, scheduled_at);

CREATE INDEX idx_problem_review_reminder_user_due
    ON problem_review_reminder (user_id, status, scheduled_at);

CREATE INDEX idx_problem_review_reminder_problem
    ON problem_review_reminder (problem_id, status);

ALTER TABLE problem_review_reminder
    ADD CONSTRAINT uq_problem_review_reminder_seq
        UNIQUE (problem_id, sequence);
