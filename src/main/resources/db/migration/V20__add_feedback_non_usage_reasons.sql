ALTER TABLE user_feedback
    ADD COLUMN study_room_non_usage_reason VARCHAR(300) AFTER study_room_usage,
    ADD COLUMN review_set_non_usage_reason  VARCHAR(300) AFTER practice_note_usefulness;
