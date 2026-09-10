-- 미션 시스템 1차: 일일/주간 미션 정의와 사용자별 진행도. 이 파일은 DDL 만 담는다.
-- 시드는 V30 에 따로 둔다. MySQL DDL 은 트랜잭션이 아니라서, 테이블은 만들어지고 뒤이은 INSERT 에서 끊기면
-- Flyway 는 실패로 기록하는데 테이블은 남는다. 재기동하면 "table already exists" 로 또 실패해
-- flyway repair 없이는 앱이 뜨지 않는다. blue-green 배포 구간이면 그대로 장애다.
--
-- 같은 이유로 두 테이블 모두 IF NOT EXISTS 로 만들고, 조회용 인덱스도 별도 CREATE INDEX 가 아니라
-- 테이블 정의 안에 둔다. MySQL 에는 CREATE INDEX IF NOT EXISTS 가 없어 문장을 나누면 그 문장이
-- 재실행 지점이 되기 때문이다. 이 파일의 문장은 몇 번을 다시 돌려도 안전하다.

CREATE TABLE IF NOT EXISTS mission_definition (
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
--
-- idx_mission_progress_lookup 은 "이 사용자의 이번 기간 진행도 전부" 조회용이다.
CREATE TABLE IF NOT EXISTS mission_progress (
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
    UNIQUE KEY uk_mission_progress (user_id, mission_id, period_key),
    KEY idx_mission_progress_lookup (user_id, period_key)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
