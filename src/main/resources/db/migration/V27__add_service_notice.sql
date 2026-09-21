CREATE TABLE service_notice (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    title      VARCHAR(100) NOT NULL,
    content    VARCHAR(500) NOT NULL,
    type       VARCHAR(20)  NOT NULL,
    starts_at  DATETIME(6)  NOT NULL,
    expires_at DATETIME(6)  NOT NULL,
    created_at DATETIME(6)  NULL,
    updated_at DATETIME(6)  NULL,
    deleted_at DATETIME(6)  NULL,
    PRIMARY KEY (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

-- 활성 공지 조회는 deleted_at IS NULL AND starts_at <= now < expires_at 한 가지뿐이다.
-- 만료 시각이 먼저 걸러 주는 조건이라 expires_at 을 선행 컬럼으로 둔다.
CREATE INDEX idx_service_notice_active
    ON service_notice (expires_at, starts_at);
