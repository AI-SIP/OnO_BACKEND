-- 프로필 프레임 8종을 치장 카탈로그에 편입한다. 컬럼 추가는 없고 시드만 늘어난다.
--
-- V37 을 직접 고치지 않는다. 이미 적용된 환경이 있으면 Flyway 체크섬이 어긋나 앱이 뜨지 않는다.
-- 기존 55 줄은 해금표에서 한 줄도 바뀌지 않았으므로 여기서는 덮어쓸 것이 없다.
-- 그래도 V37 과 같은 ON DUPLICATE KEY UPDATE 절을 그대로 쓴다. 이 파일이 다시 돌 때
-- 행이 카탈로그가 정한 값으로 수렴한다는 성질이 두 파일에서 같아야 하기 때문이다.
-- conflicts_with 와 created_at 은 V37 과 같은 이유로 손대지 않는다.
--
-- **FRAME 은 개구리에 겹치지 않는다.** 원형 프로필 사진의 테두리라 개구리 합성에서는 빠지고
-- 프로필 위젯이 따로 쓴다. layer_order 1000 은 옷장에서의 자리 순서를 정하려고 둔 값이지
-- 개구리 위에 그린다는 뜻이 아니다. 그 구분은 응답의 slots[].composited = false 가 한다
-- (CosmeticSlot.FRAME 주석 참고). 목록에서 빼지는 않는다. 옷장에는 보여 줘야 한다.
--
-- **에셋 경로가 혼자 다르다.** 나머지 치장은 assets/Cosmetic/{item_key}.png 인데
-- 프레임만 assets/ProfileFrame/{item_key}.svg 다. 원형 테두리라 확대해도 깨지면 안 돼서 SVG 다.
-- image_url 한 컬럼이 경로와 확장자를 다 들고 있어 이 차이가 코드로 새지 않는다.
-- S3 로 옮길 때도 이 컬럼만 바꾸면 되는 것은 같다.
--
-- 해금 자리는 계절 배경과 짝을 맞췄다. 봄 배경(출석 2) 다음 레벨에 봄 프레임(출석 3) 하는 식이라
-- 한 능력치 안에서 같은 레벨에 두 개가 열리는 자리가 생긴다(출석 3 / 5 / 13 / 15).
-- 슬롯이 서로 달라 기본 프리셋이 흔들리지는 않는다.

INSERT INTO cosmetic_item
    (item_key, slot, name_ko, image_url, required_level, required_ability, full_body,
     set_id, set_name_ko, conflicts_with, active, created_at, updated_at)
VALUES
    -- 출석 (ATTENDANCE). 계절 배경 바로 다음 레벨에 같은 계절 프레임을 둔다.
    ('frame_spring',  'FRAME',  '봄 프레임',      'assets/ProfileFrame/frame_spring.svg',  3,   'ATTENDANCE',   0, NULL, NULL, NULL, 1, NOW(6), NOW(6)),
    ('frame_summer',  'FRAME',  '여름 프레임',    'assets/ProfileFrame/frame_summer.svg',  5,   'ATTENDANCE',   0, NULL, NULL, NULL, 1, NOW(6), NOW(6)),
    ('frame_autumn',  'FRAME',  '가을 프레임',    'assets/ProfileFrame/frame_autumn.svg',  10,  'ATTENDANCE',   0, NULL, NULL, NULL, 1, NOW(6), NOW(6)),
    ('frame_winter',  'FRAME',  '겨울 프레임',    'assets/ProfileFrame/frame_winter.svg',  13,  'ATTENDANCE',   0, NULL, NULL, NULL, 1, NOW(6), NOW(6)),
    ('frame_night',   'FRAME',  '밤하늘 프레임',  'assets/ProfileFrame/frame_night.svg',   15,  'ATTENDANCE',   0, NULL, NULL, NULL, 1, NOW(6), NOW(6)),

    -- 오답노트 작성 (NOTE_WRITE). 공부방 배경(작성 12) 뒤에 공부방 프레임을 둔다.
    ('frame_study',   'FRAME',  '공부방 프레임',  'assets/ProfileFrame/frame_study.svg',   14,  'NOTE_WRITE',   0, NULL, NULL, NULL, 1, NOW(6), NOW(6)),

    -- 총 학습 레벨 (required_ability = NULL). 잎새는 잎새 별 뱃지 바로 뒤,
    -- 마스터는 랜턴(16)과 눈송이 뱃지(18) 사이다.
    ('frame_leaf',    'FRAME',  '잎새 프레임',    'assets/ProfileFrame/frame_leaf.svg',    4,   NULL,           0, NULL, NULL, NULL, 1, NOW(6), NOW(6)),
    ('frame_master',  'FRAME',  '마스터 프레임',  'assets/ProfileFrame/frame_master.svg',  17,  NULL,           0, NULL, NULL, NULL, 1, NOW(6), NOW(6))

ON DUPLICATE KEY UPDATE
    slot             = VALUES(slot),
    name_ko          = VALUES(name_ko),
    image_url        = VALUES(image_url),
    required_level   = VALUES(required_level),
    required_ability = VALUES(required_ability),
    full_body        = VALUES(full_body),
    set_id           = VALUES(set_id),
    set_name_ko      = VALUES(set_name_ko),
    active           = VALUES(active),
    updated_at       = NOW(6);
