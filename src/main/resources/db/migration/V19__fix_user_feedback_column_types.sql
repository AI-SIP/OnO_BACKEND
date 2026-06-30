ALTER TABLE user_feedback
    MODIFY COLUMN nps_score                  INT,
    MODIFY COLUMN template_satisfaction      INT,
    MODIFY COLUMN review_interval_satisfaction INT,
    MODIFY COLUMN practice_note_usefulness   INT,
    MODIFY COLUMN challenge_motivation       INT,
    MODIFY COLUMN problem_sharing_usefulness INT;
