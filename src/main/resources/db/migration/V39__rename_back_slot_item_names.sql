-- 등에 메는 가방(BACK) 자리의 아이템 이름 정리. 사람이 읽는 이름만 바꾼다.
--
-- 자리 이름이 '등짐' 에서 '배낭' 으로 바뀌었다. 어휘가 어색하다는 이야기가 있었고,
-- '배낭' 이 등에 멘다는 뜻이 정확하면서 앞으로 메는 BAG(450) 의 '가방' 과도 안 겹친다.
-- 자리 이름 자체는 CosmeticSlot.BACK 의 nameKo 라 코드에 있고, 여기서는 아이템 이름만 맞춘다.
--
-- '백팩' 을 '배낭' 으로 바꾸는 이유는 BAG 자리에 '미니 백팩'(bag_mini_backpack)이 있기 때문이다.
-- 자리는 '배낭' 인데 아이템은 '캔버스 백팩' 이면, 앞으로 메는 '미니 백팩' 과 이름만으로는 구별이 안 된다.
--
-- **item_key 와 slot 값은 건드리지 않는다.** 프론트 에셋 파일명과 해금표가 그 키로 맞춰져 있다.
-- 바뀌는 것은 name_ko 두 줄뿐이다.
--
-- V37 을 고치지 않고 새 파일로 얹는다. 이미 적용된 환경이 있으면 Flyway 체크섬이 어긋나 앱이 뜨지 않는다.
-- 값 변경은 새 마이그레이션의 UPDATE 로 얹는다(V33 이 미션 문구를 그렇게 고쳤다).
-- UPDATE 는 몇 번을 다시 돌려도 결과가 같다.
--
-- 주의: V37 과 V38 은 ON DUPLICATE KEY UPDATE 절에 name_ko = VALUES(name_ko) 를 달고 있다.
-- 앞으로 카탈로그를 통째로 다시 시드하는 파일을 쓴다면 거기에 이 새 이름을 담아야 한다.
-- 옛 이름을 담은 채로 그런 파일이 V39 뒤에 돌면 이 UPDATE 가 그대로 되돌아간다.

UPDATE cosmetic_item SET name_ko = '남색 배낭'   WHERE item_key = 'back_backpack_navy';
UPDATE cosmetic_item SET name_ko = '캔버스 배낭' WHERE item_key = 'back_backpack_canvas';
