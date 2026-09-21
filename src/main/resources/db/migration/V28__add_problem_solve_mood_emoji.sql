-- 복습 기록마다 "이번 복습 어땠는지"를 이모지로 남긴다.
-- 이모지 키는 CustomEmojiValidator 화이트리스트 값이라 learning_calendar_mood 와 같은 VARCHAR(80) 을 쓴다.
-- 기존 복습 기록은 이모지 없이 그대로 두므로 nullable 이고 백필하지 않는다.
ALTER TABLE problem_solve
    ADD COLUMN mood_emoji_key VARCHAR(80) NULL;
