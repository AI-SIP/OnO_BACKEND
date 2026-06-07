ALTER TABLE practice_note
    ADD COLUMN last_session_mood_emoji_key VARCHAR(80) NULL;

CREATE TABLE learning_calendar_mood (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    study_date DATE NOT NULL,
    emoji_key VARCHAR(80) NOT NULL,
    created_at DATETIME(6),
    updated_at DATETIME(6),
    deleted_at DATETIME(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_learning_calendar_mood_user_date UNIQUE (user_id, study_date),
    CONSTRAINT fk_learning_calendar_mood_user FOREIGN KEY (user_id) REFERENCES user (id)
);
CREATE INDEX idx_learning_calendar_mood_user_date ON learning_calendar_mood (user_id, study_date);
