-- 미션 정의 시드 10종 (일일 6, 주간 4). 관리자 화면은 2차라 정의는 마이그레이션으로만 들어온다.
--
-- ON DUPLICATE KEY UPDATE code = code 는 "이미 있으면 아무것도 하지 않는다"는 뜻이다.
-- uk_mission_definition_code 가 중복을 잡아 주므로, 이 문장은 몇 번을 다시 돌려도 결과가 같다.
-- 운영 중에 목표나 보상을 손으로 고쳤을 수 있어 덮어쓰지 않는다. 값을 바꾸려면 새 마이그레이션을 쓴다.

INSERT INTO mission_definition
    (code, title, description, icon_key, category, metric, target, reward_type, reward_value, sort_order, active, created_at, updated_at)
VALUES
    ('DAILY_ATTEND', '출석', '오늘 앱 켜기', 'attendance', 'DAILY', 'LOGIN_DAY', 1, 'XP', 10, 1, 1, NOW(6), NOW(6)),
    ('DAILY_NOTE_WRITE', '오늘의 오답', '오답노트 1개 등록', 'note_write', 'DAILY', 'PROBLEM_CREATED', 1, 'XP', 10, 2, 1, NOW(6), NOW(6)),
    ('DAILY_REVIEW_3', '세 문제만', '오답 3문제 복습', 'review', 'DAILY', 'SOLVE_RECORDED', 3, 'XP', 15, 3, 1, NOW(6), NOW(6)),
    ('DAILY_CORRECT_3', '정확하게', '복습해서 3문제 맞히기', 'accuracy', 'DAILY', 'SOLVE_CORRECT', 3, 'XP', 20, 4, 1, NOW(6), NOW(6)),
    ('DAILY_PRACTICE_SET', '세트 완주', '복습 세트 하나 끝내기', 'practice_set', 'DAILY', 'PRACTICE_NOTE_COMPLETED', 1, 'XP', 15, 5, 1, NOW(6), NOW(6)),
    ('DAILY_MOOD', '오늘 기분', '학습 달력에 기분 남기기', 'mood', 'DAILY', 'MOOD_LOGGED', 1, 'XP', 5, 6, 1, NOW(6), NOW(6))
ON DUPLICATE KEY UPDATE code = code;

INSERT INTO mission_definition
    (code, title, description, icon_key, category, metric, target, reward_type, reward_value, sort_order, active, created_at, updated_at)
VALUES
    ('WEEKLY_ATTEND_5', '꾸준함', '이번 주 5일 출석', 'attendance', 'WEEKLY', 'LOGIN_DAY', 5, 'XP', 100, 1, 1, NOW(6), NOW(6)),
    ('WEEKLY_NOTE_10', '열 권의 노트', '오답노트 10개 등록', 'note_write', 'WEEKLY', 'PROBLEM_CREATED', 10, 'XP', 80, 2, 1, NOW(6), NOW(6)),
    ('WEEKLY_REVIEW_30', '서른 번의 복습', '복습 30회', 'review', 'WEEKLY', 'SOLVE_RECORDED', 30, 'XP', 100, 3, 1, NOW(6), NOW(6)),
    ('WEEKLY_SET_3', '세 번의 완주', '복습 세트 3개 끝내기', 'practice_set', 'WEEKLY', 'PRACTICE_NOTE_COMPLETED', 3, 'XP', 80, 4, 1, NOW(6), NOW(6))
ON DUPLICATE KEY UPDATE code = code;
