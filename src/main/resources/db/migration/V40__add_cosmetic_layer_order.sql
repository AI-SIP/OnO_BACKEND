-- 아이템이 자리의 그리는 층을 덮어쓸 수 있게 하는 컬럼. 이 파일은 DDL 만 담는다. 값 채우기는 V41 이다.
--
-- 가방 자리를 BACK(200) 과 BAG(450) 둘로 나눠 뒀는데 각각 2개·3개뿐이라 탭을 나눌 만큼이 아니었고,
-- 사용자에게 "왜 가방 자리가 둘이지" 를 설명해야 하는 구조였다. 그래서 BAG 하나로 합친다.
--
-- 문제는 그리는 층이다. 배낭은 개구리 뒤(200), 앞가방은 옷 위(450). 자리를 합치면서 층까지 통일하면
-- 배낭이 개구리 앞으로 나와 이상해진다. 자리는 하나로 두되 그 둘만 층을 따로 갖게 한다.
--
-- layer_order 가 NULL 이면 자리 값(CosmeticSlot.layerOrder)을 쓴다. 0 같은 마법값을 쓰지 않는다.
-- required_level 과 같은 이유다. "덮어쓰지 않는다" 와 "0층에 그린다" 는 다른 말이다.
-- 프론트는 item.layerOrder ?? slot.layerOrder 로 푼다.
--
-- MySQL 에 ADD COLUMN IF NOT EXISTS 가 없다. DDL 이 커밋된 뒤 Flyway 가 이력을 남기기 전에
-- 커넥션이 끊기면 재기동 때 이 파일이 다시 돌면서 Duplicate column name 으로 앱이 뜨지 않는다.
-- V36 과 같은 방식으로 information_schema 를 보고 없을 때만 실행한다.

SET @column_exists := (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'cosmetic_item'
      AND column_name = 'layer_order'
);
SET @ddl := IF(@column_exists = 0,
    'ALTER TABLE cosmetic_item ADD COLUMN layer_order INT NULL AFTER slot',
    'SELECT 1');
PREPARE add_layer_order FROM @ddl;
EXECUTE add_layer_order;
DEALLOCATE PREPARE add_layer_order;
