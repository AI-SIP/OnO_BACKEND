-- 꾸미기(코스메틱) 1차: 아이템 카탈로그와 사용자별 장착 상태. 이 파일은 DDL 만 담는다.
-- 시드는 V35 에 따로 둔다. V29 와 같은 이유다. MySQL DDL 은 트랜잭션이 아니라서, 테이블은 만들어지고
-- 뒤이은 INSERT 에서 끊기면 Flyway 는 실패로 기록하는데 테이블은 남는다. 재기동하면
-- "table already exists" 로 또 실패해 flyway repair 없이는 앱이 뜨지 않는다.
--
-- 같은 이유로 두 테이블 모두 IF NOT EXISTS 로 만들고, 인덱스도 별도 CREATE INDEX 가 아니라
-- 테이블 정의 안에 둔다. MySQL 에는 CREATE INDEX IF NOT EXISTS 가 없어 문장을 나누면
-- 그 문장 자체가 새로운 재실행 지점이 되기 때문이다(V31 이 information_schema 가드를 쓴 이유가 그거다).
-- 새로 만드는 테이블은 가드가 아예 필요 없는 형태로 쓰는 편이 낫다. 이 파일의 문장은 몇 번을 다시 돌려도 안전하다.

-- cosmetic_item: 꾸미기 아이템 카탈로그. 관리자 화면은 2차라 1차에서는 마이그레이션 시드로만 들어온다.
--
-- required_level 이 NULL 이면 "레벨로는 열리지 않는다"는 뜻이다. 0 이나 -1 같은 마법값을 쓰지 않는다.
-- 보유 여부는 저장하지 않고 required_level 과 사용자 레벨을 비교해 매번 계산한다.
--
-- conflicts_with 는 콤마로 구분한 item_key 목록이다. 지금은 전부 비어 있지만 컬럼과 처리 로직을
-- 먼저 만들어 둔다. 후드 옷과 모자처럼 같이 못 쓰는 조합이 나오면 데이터만 채우면 된다.
--
-- set_id 는 세트 장착(PUT /api/cosmetics/equip-set)이 묶어서 거는 단위다.
CREATE TABLE IF NOT EXISTS cosmetic_item (
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    item_key       VARCHAR(64)  NOT NULL,
    slot           VARCHAR(32)  NOT NULL,
    name_ko        VARCHAR(64)  NOT NULL,
    image_url      VARCHAR(512) NOT NULL,
    required_level INT          NULL,
    set_id         VARCHAR(64)  NULL,
    conflicts_with VARCHAR(512) NULL,
    active         TINYINT(1)   NOT NULL DEFAULT 1,
    created_at     DATETIME(6)  NULL,
    updated_at     DATETIME(6)  NULL,
    PRIMARY KEY (id),
    -- 장착 요청과 레이아웃 저장이 모두 item_key 로 아이템을 가리킨다. 중복이 생기면 어느 쪽을
    -- 가리키는지 알 수 없어지므로 정합성용 유니크 키다. 시드의 재실행 안전성도 이 키가 받쳐 준다.
    UNIQUE KEY uk_cosmetic_item_key (item_key),
    -- 레벨업 해금 알림이 "levelBefore < required_level <= levelAfter 인 활성 아이템" 을 찾는다.
    KEY idx_cosmetic_item_active_level (active, required_level),
    KEY idx_cosmetic_item_set (set_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- user_cosmetic_loadout: 사용자가 슬롯별로 무엇을 걸고 있는지.
--
-- **(user_id, slot) 복합 기본키가 이 설계의 핵심이다.** 한 슬롯에 두 개가 들어가는 것을
-- 애플리케이션 검사가 아니라 DB 가 막는다. 장착은 INSERT ... ON DUPLICATE KEY UPDATE 한 문장으로
-- 처리하는데, 그 upsert 가 성립하는 근거가 바로 이 기본키다.
-- 같은 사용자가 같은 슬롯에 두 아이템을 동시에 걸어도 중복 행이 생길 수 없고,
-- "읽어서 있으면 UPDATE 없으면 INSERT" 로 갈랐을 때 나는 유니크 충돌 예외 경로도 없다.
--
-- 대리키(id)를 두지 않는다. 대리키를 두면 유니크 키는 별개의 보조 인덱스가 되고, 조회가
-- (user_id, slot) 로 들어오는데 정작 클러스터드 인덱스는 쓸모없는 id 가 된다.
-- 여기서는 자연키가 곧 조회 키라 그대로 기본키로 두는 편이 읽기도 쓰기도 싸다.
--
-- user 에 외래키를 걸지 않는다. mission_progress 와 같은 이유로, INSERT 마다 부모 사용자 행에
-- 공유 잠금이 붙으면 이미 사용자 행을 배타 잠금으로 잡는 미션 보상 지급 경로와 잠금 순서가 엇갈린다.
--
-- item_key 에 '__none__' 이 들어간 행은 "이 슬롯을 일부러 비웠다"는 표시다. 자세한 설명은
-- UserCosmeticLoadout 엔티티 주석에 있다. 행을 아예 지우면 "한 번도 안 건드린 사용자"와
-- 구별되지 않아 기본 프리셋이 되살아난다.
CREATE TABLE IF NOT EXISTS user_cosmetic_loadout (
    user_id    BIGINT      NOT NULL,
    slot       VARCHAR(32) NOT NULL,
    item_key   VARCHAR(64) NOT NULL,
    updated_at DATETIME(6) NULL,
    PRIMARY KEY (user_id, slot)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
