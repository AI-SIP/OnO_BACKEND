-- 꾸미기 해금을 능력치별로 바꾸기 위한 컬럼 셋. 이 파일은 DDL 만 담는다. 시드 교체는 V37 이다.
--
-- V34/V35 를 직접 고치지 않는다. 이 브랜치는 아직 push 도 배포도 안 됐지만, 로컬 DB 에는 이미
-- 적용돼 있을 수 있다. 적용된 파일을 고치면 Flyway 체크섬이 어긋나 `flyway repair` 없이는 앱이 뜨지 않는다.
-- 새 파일로 얹으면 이미 V35 까지 돈 DB 도, 처음부터 도는 DB 도 같은 결과에 도달한다.
--
-- MySQL 에는 ADD COLUMN IF NOT EXISTS 가 없다. DDL 이 커밋된 뒤 Flyway 가 이력을 남기기 전에
-- 커넥션이 끊기면 재기동 때 이 파일이 다시 돌면서 Duplicate column name 으로 앱이 뜨지 않는다.
-- V31 과 같은 방식으로 information_schema 를 보고 없을 때만 실행한다. 컬럼마다 따로 보는 이유는,
-- 하나로 묶으면 "세 개 중 하나만 들어간" 상태에서 빠져나올 길이 없기 때문이다.

-- required_ability: 이 아이템의 required_level 을 어느 레벨과 비교할지.
-- 값이 있으면 그 능력치 레벨, NULL 이면 총 학습 레벨이다.
-- 값은 MissionType.AbilityType enum 그대로다: ATTENDANCE, NOTE_WRITE, PROBLEM_PRACTICE, NOTE_PRACTICE.
-- 기본값을 두지 않는다. "능력치 조건이 없다"를 NULL 하나로만 표현해야 required_level 의 NULL 규칙과 어긋나지 않는다.
SET @column_exists := (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'cosmetic_item'
      AND column_name = 'required_ability'
);
SET @ddl := IF(@column_exists = 0,
    'ALTER TABLE cosmetic_item ADD COLUMN required_ability VARCHAR(32) NULL AFTER required_level',
    'SELECT 1');
PREPARE add_required_ability FROM @ddl;
EXECUTE add_required_ability;
DEALLOCATE PREPARE add_required_ability;

-- full_body: 소매와 바짓단까지 그려진 전신 의상인지.
-- 앱이 이 옷을 입히면 개구리 본체를 머리만 있는 그림으로 바꿔 깐다. 그러지 않으면 옷 밑으로 팔다리가 삐져나온다.
-- NOT NULL DEFAULT FALSE 라 기존 행은 전부 false 로 채워진다. V37 이 해당하는 옷만 true 로 올린다.
SET @column_exists := (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'cosmetic_item'
      AND column_name = 'full_body'
);
SET @ddl := IF(@column_exists = 0,
    'ALTER TABLE cosmetic_item ADD COLUMN full_body TINYINT(1) NOT NULL DEFAULT 0 AFTER required_ability',
    'SELECT 1');
PREPARE add_full_body FROM @ddl;
EXECUTE add_full_body;
DEALLOCATE PREPARE add_full_body;

-- set_name_ko: 세트의 사람이 읽는 이름. set_id 는 기계용 키라 화면에 그대로 쓸 수 없다.
-- 이 컬럼이 없던 동안 프론트가 세트 배너에 아이템 이름을 이어 붙여 썼다.
SET @column_exists := (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'cosmetic_item'
      AND column_name = 'set_name_ko'
);
SET @ddl := IF(@column_exists = 0,
    'ALTER TABLE cosmetic_item ADD COLUMN set_name_ko VARCHAR(64) NULL AFTER set_id',
    'SELECT 1');
PREPARE add_set_name_ko FROM @ddl;
EXECUTE add_set_name_ko;
DEALLOCATE PREPARE add_set_name_ko;
