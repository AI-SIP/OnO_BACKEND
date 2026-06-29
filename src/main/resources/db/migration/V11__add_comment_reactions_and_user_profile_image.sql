ALTER TABLE user
    ADD COLUMN profile_image_url VARCHAR(255);

CREATE TABLE study_room_shared_problem_comment_reaction (
    id BIGINT NOT NULL AUTO_INCREMENT,
    comment_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    emoji VARCHAR(80) NOT NULL,
    created_at DATETIME(6),
    updated_at DATETIME(6),
    deleted_at DATETIME(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_shared_problem_comment_reaction UNIQUE (comment_id, user_id, emoji),
    CONSTRAINT fk_shared_problem_comment_reaction_comment FOREIGN KEY (comment_id) REFERENCES study_room_shared_problem_comment (id) ON DELETE CASCADE,
    CONSTRAINT fk_shared_problem_comment_reaction_user FOREIGN KEY (user_id) REFERENCES user (id)
);
CREATE INDEX idx_shared_problem_comment_reaction_comment ON study_room_shared_problem_comment_reaction (comment_id);
CREATE INDEX idx_shared_problem_comment_reaction_user ON study_room_shared_problem_comment_reaction (user_id);
