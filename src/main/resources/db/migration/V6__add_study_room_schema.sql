CREATE TABLE study_room (
    id BIGINT NOT NULL AUTO_INCREMENT,
    version BIGINT,
    name VARCHAR(20) NOT NULL,
    host_user_id BIGINT NOT NULL,
    thumbnail_url VARCHAR(255),
    created_at DATETIME(6),
    updated_at DATETIME(6),
    deleted_at DATETIME(6),
    PRIMARY KEY (id)
);
CREATE INDEX idx_study_room_host_user ON study_room (host_user_id);

CREATE TABLE study_room_member (
    id BIGINT NOT NULL AUTO_INCREMENT,
    room_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    role VARCHAR(20) NOT NULL,
    weekly_goal INT,
    created_at DATETIME(6),
    updated_at DATETIME(6),
    deleted_at DATETIME(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_study_room_member_room_user UNIQUE (room_id, user_id),
    CONSTRAINT fk_study_room_member_room FOREIGN KEY (room_id) REFERENCES study_room (id) ON DELETE CASCADE,
    CONSTRAINT fk_study_room_member_user FOREIGN KEY (user_id) REFERENCES user (id)
);
CREATE INDEX idx_study_room_member_user ON study_room_member (user_id);
CREATE INDEX idx_study_room_member_room ON study_room_member (room_id);

CREATE TABLE study_room_invite_code (
    id BIGINT NOT NULL AUTO_INCREMENT,
    room_id BIGINT NOT NULL,
    code VARCHAR(6) NOT NULL,
    expired_at DATETIME(6) NOT NULL,
    created_at DATETIME(6),
    updated_at DATETIME(6),
    deleted_at DATETIME(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_study_room_invite_code UNIQUE (code),
    CONSTRAINT fk_study_room_invite_room FOREIGN KEY (room_id) REFERENCES study_room (id) ON DELETE CASCADE
);
CREATE INDEX idx_study_room_invite_room ON study_room_invite_code (room_id);

CREATE TABLE study_room_feed (
    id BIGINT NOT NULL AUTO_INCREMENT,
    room_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    event_type VARCHAR(40) NOT NULL,
    metadata_json TEXT,
    created_at DATETIME(6),
    updated_at DATETIME(6),
    deleted_at DATETIME(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_study_room_feed_room FOREIGN KEY (room_id) REFERENCES study_room (id) ON DELETE CASCADE,
    CONSTRAINT fk_study_room_feed_user FOREIGN KEY (user_id) REFERENCES user (id)
);
CREATE INDEX idx_study_room_feed_room_created ON study_room_feed (room_id, created_at);
CREATE INDEX idx_study_room_feed_room_user_type_created ON study_room_feed (room_id, user_id, event_type, created_at);

CREATE TABLE study_room_feed_reaction (
    id BIGINT NOT NULL AUTO_INCREMENT,
    feed_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    emoji VARCHAR(16) NOT NULL,
    created_at DATETIME(6),
    updated_at DATETIME(6),
    deleted_at DATETIME(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_study_room_feed_reaction UNIQUE (feed_id, user_id, emoji),
    CONSTRAINT fk_study_room_feed_reaction_feed FOREIGN KEY (feed_id) REFERENCES study_room_feed (id) ON DELETE CASCADE,
    CONSTRAINT fk_study_room_feed_reaction_user FOREIGN KEY (user_id) REFERENCES user (id)
);

CREATE TABLE study_room_challenge (
    id BIGINT NOT NULL AUTO_INCREMENT,
    room_id BIGINT NOT NULL,
    title VARCHAR(40) NOT NULL,
    type VARCHAR(20) NOT NULL,
    metric VARCHAR(40) NOT NULL,
    target_value INT NOT NULL,
    start_at DATETIME(6) NOT NULL,
    end_at DATETIME(6) NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at DATETIME(6),
    updated_at DATETIME(6),
    deleted_at DATETIME(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_study_room_challenge_room FOREIGN KEY (room_id) REFERENCES study_room (id) ON DELETE CASCADE
);
CREATE INDEX idx_study_room_challenge_room_status_end ON study_room_challenge (room_id, status, end_at);

CREATE TABLE study_session (
    id BIGINT NOT NULL AUTO_INCREMENT,
    room_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    started_at DATETIME(6) NOT NULL,
    ended_at DATETIME(6),
    duration_minutes INT,
    created_at DATETIME(6),
    updated_at DATETIME(6),
    deleted_at DATETIME(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_study_session_room FOREIGN KEY (room_id) REFERENCES study_room (id) ON DELETE CASCADE,
    CONSTRAINT fk_study_session_user FOREIGN KEY (user_id) REFERENCES user (id)
);
CREATE INDEX idx_study_session_room_ended ON study_session (room_id, ended_at);
CREATE INDEX idx_study_session_user_ended ON study_session (user_id, ended_at);

CREATE TABLE study_room_shared_problem (
    id BIGINT NOT NULL AUTO_INCREMENT,
    room_id BIGINT NOT NULL,
    shared_by_user_id BIGINT NOT NULL,
    problem_id BIGINT NOT NULL,
    comment VARCHAR(100),
    created_at DATETIME(6),
    updated_at DATETIME(6),
    deleted_at DATETIME(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_study_room_shared_problem_room FOREIGN KEY (room_id) REFERENCES study_room (id) ON DELETE CASCADE,
    CONSTRAINT fk_study_room_shared_problem_user FOREIGN KEY (shared_by_user_id) REFERENCES user (id),
    CONSTRAINT fk_study_room_shared_problem_problem FOREIGN KEY (problem_id) REFERENCES problem (id)
);
CREATE INDEX idx_study_room_shared_problem_room_created ON study_room_shared_problem (room_id, created_at);
CREATE INDEX idx_study_room_shared_problem_problem ON study_room_shared_problem (problem_id);

CREATE TABLE study_room_shared_problem_reaction (
    id BIGINT NOT NULL AUTO_INCREMENT,
    shared_problem_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    emoji VARCHAR(16) NOT NULL,
    created_at DATETIME(6),
    updated_at DATETIME(6),
    deleted_at DATETIME(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_study_room_shared_problem_reaction UNIQUE (shared_problem_id, user_id, emoji),
    CONSTRAINT fk_study_room_shared_problem_reaction_shared FOREIGN KEY (shared_problem_id) REFERENCES study_room_shared_problem (id) ON DELETE CASCADE,
    CONSTRAINT fk_study_room_shared_problem_reaction_user FOREIGN KEY (user_id) REFERENCES user (id)
);

CREATE TABLE study_room_weekly_report (
    id BIGINT NOT NULL AUTO_INCREMENT,
    room_id BIGINT NOT NULL,
    week_start DATE NOT NULL,
    week_end DATE NOT NULL,
    top_member_name VARCHAR(255),
    top_member_problem_count INT NOT NULL,
    longest_streak_name VARCHAR(255),
    longest_streak_days INT NOT NULL,
    total_problems INT NOT NULL,
    challenges_completed INT NOT NULL,
    cheer_message VARCHAR(255) NOT NULL,
    created_at DATETIME(6),
    updated_at DATETIME(6),
    deleted_at DATETIME(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_study_room_weekly_report UNIQUE (room_id, week_start),
    CONSTRAINT fk_study_room_weekly_report_room FOREIGN KEY (room_id) REFERENCES study_room (id) ON DELETE CASCADE
);

CREATE TABLE study_room_weekly_report_read (
    id BIGINT NOT NULL AUTO_INCREMENT,
    report_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    read_at DATETIME(6) NOT NULL,
    created_at DATETIME(6),
    updated_at DATETIME(6),
    deleted_at DATETIME(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_study_room_weekly_report_read UNIQUE (report_id, user_id),
    CONSTRAINT fk_study_room_weekly_report_read_report FOREIGN KEY (report_id) REFERENCES study_room_weekly_report (id) ON DELETE CASCADE,
    CONSTRAINT fk_study_room_weekly_report_read_user FOREIGN KEY (user_id) REFERENCES user (id)
);
