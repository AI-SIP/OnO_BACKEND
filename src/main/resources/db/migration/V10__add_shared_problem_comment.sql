CREATE TABLE study_room_shared_problem_comment (
    id BIGINT NOT NULL AUTO_INCREMENT,
    shared_problem_id BIGINT NOT NULL,
    author_id BIGINT NOT NULL,
    content VARCHAR(1000) NOT NULL,
    created_at DATETIME(6),
    updated_at DATETIME(6),
    deleted_at DATETIME(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_shared_problem_comment_problem FOREIGN KEY (shared_problem_id) REFERENCES study_room_shared_problem (id) ON DELETE CASCADE,
    CONSTRAINT fk_shared_problem_comment_author FOREIGN KEY (author_id) REFERENCES user (id)
);
CREATE INDEX idx_shared_problem_comment_problem_id ON study_room_shared_problem_comment (shared_problem_id, id);
CREATE INDEX idx_shared_problem_comment_author ON study_room_shared_problem_comment (author_id);
