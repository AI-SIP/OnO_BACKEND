-- 가방 자리 통합. 카탈로그의 BACK 두 줄을 BAG 으로 옮기고, 그 둘만 그리는 층 200 을 직접 갖게 한다.
-- 그리고 이미 BACK 자리에 걸어 둔 사용자 행을 옮긴다.
--
-- V37/V39 를 고치지 않고 새 파일로 얹는다. 이미 적용된 환경이 있으면 Flyway 체크섬이 어긋나 앱이 뜨지 않는다.
-- 여기는 전부 UPDATE/DELETE 라 몇 번을 다시 돌려도 결과가 같다.
--
-- **item_key 와 name_ko 는 건드리지 않는다.** 에셋 파일명과 해금표가 item_key 로 맞춰져 있고,
-- 이름(남색 배낭 / 캔버스 배낭)은 한 탭 안에서 이게 등에 메는 것인지 앞으로 메는 것인지 말해 준다.
-- 자리가 하나가 됐으니 이름이 그 구분을 대신 진다.
--
-- 주의: V37 은 ON DUPLICATE KEY UPDATE 절에 slot = VALUES(slot) 을 달고 있다. 앞으로 카탈로그를
-- 통째로 다시 시드하는 파일을 쓴다면 거기에 BAG 과 layer_order 를 담아야 한다. V39 와 같은 사정이다.

-- ─────────────── 1. 카탈로그 ───────────────

-- 배낭 둘만 자리 기본값(450)을 덮어쓴다. 나머지는 NULL 그대로라 자리 값을 쓴다.
UPDATE cosmetic_item SET slot = 'BAG', layer_order = 200
WHERE item_key IN ('back_backpack_navy', 'back_backpack_canvas');

-- ─────────────── 2. 사용자 장착 행 ───────────────
--
-- BACK 은 이제 CosmeticSlot 에 없는 값이다. 행을 남겨 두면 조회할 때 엔티티 매핑이
-- IllegalArgumentException 으로 터져 꾸미기 화면이 통째로 안 열린다. 반드시 비워야 한다.
--
-- 지우지 않고 옮기는 이유는 사용자가 실제로 고른 것이기 때문이다. 지우면 배낭을 메고 있던 사용자가
-- 다음 조회에서 이유 없이 맨등이 된다.
--
-- 한 사용자가 BACK 과 BAG 을 둘 다 갖고 있으면 하나만 남길 수 있다((user_id, slot) 기본키).
-- **BAG 쪽을 남긴다.** 살아남는 자리의 행이라 그대로 두면 되고, 앞으로 메는 가방이 위에 그려져
-- 더 눈에 띈다. updated_at 이 최신인 쪽을 남기는 규칙도 생각했지만 쓸 수 없다.
-- 기본 프리셋을 행으로 굳힐 때 모든 자리를 같은 NOW(6) 으로 쓰기 때문에, 대부분의 사용자는
-- 두 행의 시각이 정확히 같아 시각으로는 아무것도 가릴 수 없다.

-- 2-1. BAG 행이 이미 있는 사용자의 BACK 행을 버린다.
DELETE back FROM user_cosmetic_loadout back
JOIN user_cosmetic_loadout bag
  ON bag.user_id = back.user_id AND bag.slot = 'BAG'
WHERE back.slot = 'BACK';

-- 2-2. 남은 BACK 행을 BAG 으로 옮긴다. 2-1 이 충돌할 상대를 전부 치웠으므로 기본키 충돌이 없다.
--      item_key '__none__'(일부러 비운 자리) 행도 그대로 옮긴다. 비워 둔 것도 사용자의 선택이고,
--      지우면 "한 번도 안 건드린 사용자" 쪽으로 기울어 기본 프리셋이 되살아날 수 있다.
UPDATE user_cosmetic_loadout SET slot = 'BAG' WHERE slot = 'BACK';
