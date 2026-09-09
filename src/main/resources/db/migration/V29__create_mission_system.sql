-- 미션 시스템 1차: 일일/주간 미션 정의와 사용자별 진행도.
--
-- 리셋 배치를 두지 않는다. 진행도 행은 period_key(일일 yyyy-MM-dd, 주간 yyyy-'W'ww)로 갈라지므로
-- 오늘 키로 조회하면 어제 진행도는 애초에 잡히지 않는다. Quartz 가 isClustered:false 인 채로
-- blue-green 배포를 하고 있어 스케줄 작업이 배포 구간에 양쪽에서 도는 위험이 있는데,
-- 리셋 배치를 만들지 않으면 그 위험을 통째로 피한다.

CREATE TABLE mission_definition (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    code         VARCHAR(60)  NOT NULL,
    title        VARCHAR(60)  NOT NULL,
    description  VARCHAR(200) NOT NULL,
    icon_key     VARCHAR(40)  NOT NULL,
    category     VARCHAR(20)  NOT NULL,
    metric       VARCHAR(40)  NOT NULL,
    target       INT          NOT NULL,
    reward_type  VARCHAR(20)  NOT NULL,
    reward_value INT          NOT NULL,
    sort_order   INT          NOT NULL DEFAULT 0,
    active       TINYINT(1)   NOT NULL DEFAULT 1,
    created_at   DATETIME(6)  NULL,
    updated_at   DATETIME(6)  NULL,
    deleted_at   DATETIME(6)  NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_mission_definition_code (code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- target_snapshot: 나중에 미션 목표를 바꿔도 진행 중이던 사용자는 옛 목표로 끝나야 한다.
-- 진행도 행을 만들 때의 target 을 박아두고 완료 판정은 이 값으로 한다.
--
-- uk_mission_progress 는 성능용이 아니라 정합성용이다. 진행도 증가를
-- INSERT ... ON DUPLICATE KEY UPDATE 한 문장으로 처리하는데, 그 upsert 가 성립하는 근거가 이 유니크 키다.
CREATE TABLE mission_progress (
    id              BIGINT      NOT NULL AUTO_INCREMENT,
    user_id         BIGINT      NOT NULL,
    mission_id      BIGINT      NOT NULL,
    period_key      VARCHAR(20) NOT NULL,
    current_value   INT         NOT NULL DEFAULT 0,
    target_snapshot INT         NOT NULL,
    completed_at    DATETIME(6) NULL,
    claimed_at      DATETIME(6) NULL,
    created_at      DATETIME(6) NULL,
    updated_at      DATETIME(6) NULL,
    deleted_at      DATETIME(6) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_mission_progress (user_id, mission_id, period_key)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- 목록 조회는 "이 사용자의 이번 기간 진행도 전부" 한 가지뿐이다.
CREATE INDEX idx_mission_progress_lookup
    ON mission_progress (user_id, period_key);

-- 시드 10종. 관리자 화면은 2차라 정의는 마이그레이션으로만 들어온다.
INSERT INTO mission_definition
    (code, title, description, icon_key, category, metric, target, reward_type, reward_value, sort_order, active, created_at, updated_at)
VALUES
    ('DAILY_ATTEND', '출석', '오늘 앱 켜기', 'attendance', 'DAILY', 'LOGIN_DAY', 1, 'XP', 10, 1, 1, NOW(6), NOW(6)),
    ('DAILY_NOTE_WRITE', '오늘의 오답', '오답노트 1개 등록', 'note_write', 'DAILY', 'PROBLEM_CREATED', 1, 'XP', 10, 2, 1, NOW(6), NOW(6)),
    ('DAILY_REVIEW_3', '세 문제만', '오답 3문제 복습', 'review', 'DAILY', 'SOLVE_RECORDED', 3, 'XP', 15, 3, 1, NOW(6), NOW(6)),
    ('DAILY_CORRECT_3', '정확하게', '복습해서 3문제 맞히기', 'accuracy', 'DAILY', 'SOLVE_CORRECT', 3, 'XP', 20, 4, 1, NOW(6), NOW(6)),
    ('DAILY_PRACTICE_SET', '세트 완주', '복습 세트 하나 끝내기', 'practice_set', 'DAILY', 'PRACTICE_NOTE_COMPLETED', 1, 'XP', 15, 5, 1, NOW(6), NOW(6)),
    ('DAILY_MOOD', '오늘 기분', '학습 달력에 기분 남기기', 'mood', 'DAILY', 'MOOD_LOGGED', 1, 'XP', 5, 6, 1, NOW(6), NOW(6));

INSERT INTO mission_definition
    (code, title, description, icon_key, category, metric, target, reward_type, reward_value, sort_order, active, created_at, updated_at)
VALUES
    ('WEEKLY_ATTEND_5', '꾸준함', '이번 주 5일 출석', 'attendance', 'WEEKLY', 'LOGIN_DAY', 5, 'XP', 100, 1, 1, NOW(6), NOW(6)),
    ('WEEKLY_NOTE_10', '열 권의 노트', '오답노트 10개 등록', 'note_write', 'WEEKLY', 'PROBLEM_CREATED', 10, 'XP', 80, 2, 1, NOW(6), NOW(6)),
    ('WEEKLY_REVIEW_30', '서른 번의 복습', '복습 30회', 'review', 'WEEKLY', 'SOLVE_RECORDED', 30, 'XP', 100, 3, 1, NOW(6), NOW(6)),
    ('WEEKLY_SET_3', '세 번의 완주', '복습 세트 3개 끝내기', 'practice_set', 'WEEKLY', 'PRACTICE_NOTE_COMPLETED', 3, 'XP', 80, 4, 1, NOW(6), NOW(6));
