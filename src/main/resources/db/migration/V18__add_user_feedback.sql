CREATE TABLE user_feedback (
    id              BIGINT NOT NULL AUTO_INCREMENT,
    usage_purpose   VARCHAR(500),
    usage_frequency VARCHAR(50),
    nps_score       TINYINT,
    registration_pain_points    VARCHAR(500),
    classification_method       VARCHAR(50),
    template_satisfaction       TINYINT,
    notification_effectiveness  VARCHAR(50),
    review_interval_satisfaction TINYINT,
    practice_note_used          BOOLEAN,
    practice_note_usefulness    TINYINT,
    study_room_usage            VARCHAR(50),
    challenge_motivation        TINYINT,
    problem_sharing_usefulness  TINYINT,
    most_used_feature           VARCHAR(100),
    pain_points                 TEXT,
    desired_features            TEXT,
    ip_address                  VARCHAR(50),
    submitted_at                DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;
