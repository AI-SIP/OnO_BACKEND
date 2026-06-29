ALTER TABLE study_room_feed_reaction
    MODIFY COLUMN emoji VARCHAR(80) NOT NULL;

ALTER TABLE study_room_shared_problem_reaction
    MODIFY COLUMN emoji VARCHAR(80) NOT NULL;