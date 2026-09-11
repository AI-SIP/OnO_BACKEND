-- 꾸미기 시드 교체: 총 학습 레벨 하나로 열리던 카탈로그를 능력치별 해금표로 바꾼다.
--
-- V35 를 직접 고치지 않는다. 이미 적용된 환경이 있으면 Flyway 체크섬이 어긋나 앱이 뜨지 않는다.
-- 값 변경은 새 마이그레이션으로 얹는다(V33 이 미션 문구를 그렇게 고쳤다).
--
-- 이 파일은 V35 와 달리 **기존 행을 덮어쓴다.** V35 의 ON DUPLICATE KEY UPDATE 는
-- item_key = item_key 라 "있으면 아무것도 안 한다" 였는데, 여기서는 해금 조건 자체를 갈아엎는 것이 목적이라
-- 덮어쓰지 않으면 V35 까지 돈 DB 와 처음부터 도는 DB 의 결과가 달라진다.
-- 덮어쓰는 컬럼은 카탈로그가 정하는 것뿐이다. conflicts_with 와 created_at 은 손대지 않는다.
-- 충돌 목록은 운영에서 채워 넣는 값이라 시드가 되돌리면 안 된다.
-- 몇 번을 다시 돌려도 결과가 같다.
--
-- 해금 기준이 둘로 갈린다.
--   required_ability 가 있으면  -> 그 능력치 레벨과 required_level 을 비교한다
--   required_ability 가 NULL 이면 -> 총 학습 레벨과 비교한다 (V35 까지의 동작)
-- 능력치별로 가는 이유는 셋이다. 테마 해금이 이미 능력치별로 돌고 있고(ThemeLockManager),
-- 앱에 능력치 넷을 보여 주는 스탯 화면이 새로 생겼는데 보상이 총 레벨만 보면 그 화면이 죽고,
-- "출석 Lv.5 달성" 같은 구체적인 조건을 사용자에게 보여 줄 수 있다.
--
-- 자리와 레이어 순서(작을수록 뒤에 깔린다):
--   BACKGROUND 100 배경 / BACK 200 등짐 / BASE 300 본체 / OUTFIT 400 옷 / BAG 450 가방
--   NECK 500 목 / FACE 600 얼굴 / HEAD 700 머리 / HAND 800 손 / BADGE 850 뱃지 / EFFECT 900 효과
-- **BACK 의 뜻이 V35 에서 바뀌었다.** V35 의 BACK 은 그냥 '가방' 이었고 미니 백팩·허리 가방·크로스백이
-- 거기 있었다. 이제 BACK 은 등에 메는 것(본체보다 뒤), BAG 은 앞으로 메는 것(옷 위)이다.
-- 그래서 저 셋은 BAG 으로 옮겨 간다. 등에 메는 백팩 둘이 BACK 으로 새로 들어온다.
--
-- 2차 콘텐츠로 active = 0 이던 19 개는 전부 해금 레벨을 받아 active = 1 이 된다.
-- image_url 은 여전히 앱 번들 상대 경로다. S3 로 옮길 때 이 컬럼만 바꾸면 앱 배포 없이 전환된다.
--
-- 개구리 본체('BASE') 행은 건드리지 않는다. V35 가 넣은 그대로고 해금 조건이 없다.

INSERT INTO cosmetic_item
    (item_key, slot, name_ko, image_url, required_level, required_ability, full_body,
     set_id, set_name_ko, conflicts_with, active, created_at, updated_at)
VALUES
    -- 출석 (ATTENDANCE). 배경과 전경 효과가 여기 붙는다. 매일 들어오는 것으로 화면 분위기가 바뀐다.
    ('bg_spring',              'BACKGROUND',  '봄 배경',         'assets/Cosmetic/bg_spring.png',             2,   'ATTENDANCE',        0, NULL, NULL, NULL, 1, NOW(6), NOW(6)),
    ('effect_petals',          'EFFECT',      '꽃잎 효과',       'assets/Cosmetic/effect_petals.png',         3,   'ATTENDANCE',        0, NULL, NULL, NULL, 1, NOW(6), NOW(6)),
    ('bg_summer',              'BACKGROUND',  '여름 배경',       'assets/Cosmetic/bg_summer.png',             4,   'ATTENDANCE',        0, NULL, NULL, NULL, 1, NOW(6), NOW(6)),
    ('effect_sparkle',         'EFFECT',      '반짝임 효과',     'assets/Cosmetic/effect_sparkle.png',        5,   'ATTENDANCE',        0, NULL, NULL, NULL, 1, NOW(6), NOW(6)),
    ('bg_rainy',               'BACKGROUND',  '비 오는 날 배경', 'assets/Cosmetic/bg_rainy.png',              6,   'ATTENDANCE',        0, NULL, NULL, NULL, 1, NOW(6), NOW(6)),
    ('effect_fireflies',       'EFFECT',      '반딧불 효과',     'assets/Cosmetic/effect_fireflies.png',      8,   'ATTENDANCE',        0, NULL, NULL, NULL, 1, NOW(6), NOW(6)),
    ('bg_autumn',              'BACKGROUND',  '가을 배경',       'assets/Cosmetic/bg_autumn.png',             9,   'ATTENDANCE',        0, NULL, NULL, NULL, 1, NOW(6), NOW(6)),
    ('bg_sunset',              'BACKGROUND',  '노을 배경',       'assets/Cosmetic/bg_sunset.png',             11,  'ATTENDANCE',        0, NULL, NULL, NULL, 1, NOW(6), NOW(6)),
    ('bg_winter',              'BACKGROUND',  '겨울 배경',       'assets/Cosmetic/bg_winter.png',             12,  'ATTENDANCE',        0, NULL, NULL, NULL, 1, NOW(6), NOW(6)),
    ('effect_snow',            'EFFECT',      '눈 내리는 효과',  'assets/Cosmetic/effect_snow.png',           13,  'ATTENDANCE',        0, NULL, NULL, NULL, 1, NOW(6), NOW(6)),
    ('bg_night',               'BACKGROUND',  '밤하늘 배경',     'assets/Cosmetic/bg_night.png',              14,  'ATTENDANCE',        0, NULL, NULL, NULL, 1, NOW(6), NOW(6)),
    ('bg_space',               'BACKGROUND',  '우주 배경',       'assets/Cosmetic/bg_space.png',              15,  'ATTENDANCE',        0, NULL, NULL, NULL, 1, NOW(6), NOW(6)),

    -- 오답노트 작성 (NOTE_WRITE). 가방과 손에 드는 것. 쓰는 사람의 짐이 늘어나는 쪽으로 묶었다.
    ('bag_mini_backpack',      'BAG',         '미니 백팩',       'assets/Cosmetic/bag_mini_backpack.png',     2,   'NOTE_WRITE',        0, NULL, NULL, NULL, 1, NOW(6), NOW(6)),
    ('prop_notebook',          'HAND',        '공책',            'assets/Cosmetic/prop_notebook.png',         3,   'NOTE_WRITE',        0, NULL, NULL, NULL, 1, NOW(6), NOW(6)),
    ('back_backpack_navy',     'BACK',        '네이비 백팩',     'assets/Cosmetic/back_backpack_navy.png',    5,   'NOTE_WRITE',        0, NULL, NULL, NULL, 1, NOW(6), NOW(6)),
    ('prop_study',             'HAND',        '공부 도구',       'assets/Cosmetic/prop_study.png',            6,   'NOTE_WRITE',        0, NULL, NULL, NULL, 1, NOW(6), NOW(6)),
    ('bag_waist_pouch',        'BAG',         '허리 가방',       'assets/Cosmetic/bag_waist_pouch.png',       8,   'NOTE_WRITE',        0, NULL, NULL, NULL, 1, NOW(6), NOW(6)),
    ('back_backpack_canvas',   'BACK',        '캔버스 백팩',     'assets/Cosmetic/back_backpack_canvas.png',  9,   'NOTE_WRITE',        0, NULL, NULL, NULL, 1, NOW(6), NOW(6)),
    ('bag_crossbody_satchel',  'BAG',         '크로스백',        'assets/Cosmetic/bag_crossbody_satchel.png', 11,  'NOTE_WRITE',        0, NULL, NULL, NULL, 1, NOW(6), NOW(6)),
    ('bg_study',               'BACKGROUND',  '공부방 배경',     'assets/Cosmetic/bg_study.png',              12,  'NOTE_WRITE',        0, NULL, NULL, NULL, 1, NOW(6), NOW(6)),
    ('prop_tumbler',           'HAND',        '텀블러',          'assets/Cosmetic/prop_tumbler.png',          13,  'NOTE_WRITE',        0, NULL, NULL, NULL, 1, NOW(6), NOW(6)),

    -- 문제 복습 (PROBLEM_PRACTICE). 얼굴과 머리. 안경처럼 '보는 것' 이 복습과 어울린다.
    ('glasses_round',          'FACE',        '동그란 안경',     'assets/Cosmetic/glasses_round.png',         2,   'PROBLEM_PRACTICE',  0, NULL, NULL, NULL, 1, NOW(6), NOW(6)),
    ('hat_beanie',             'HEAD',        '비니',            'assets/Cosmetic/hat_beanie.png',            4,   'PROBLEM_PRACTICE',  0, NULL, NULL, NULL, 1, NOW(6), NOW(6)),
    ('face_cheek_stickers',    'FACE',        '볼 스티커',       'assets/Cosmetic/face_cheek_stickers.png',   5,   'PROBLEM_PRACTICE',  0, NULL, NULL, NULL, 1, NOW(6), NOW(6)),
    ('glasses_sun',            'FACE',        '선글라스',        'assets/Cosmetic/glasses_sun.png',           6,   'PROBLEM_PRACTICE',  0, NULL, NULL, NULL, 1, NOW(6), NOW(6)),
    ('hat_bucket',             'HEAD',        '버킷햇',          'assets/Cosmetic/hat_bucket.png',            8,   'PROBLEM_PRACTICE',  0, NULL, NULL, NULL, 1, NOW(6), NOW(6)),
    ('face_eye_patch',         'FACE',        '안대',            'assets/Cosmetic/face_eye_patch.png',        9,   'PROBLEM_PRACTICE',  0, NULL, NULL, NULL, 1, NOW(6), NOW(6)),
    ('hat_beret',              'HEAD',        '베레모',          'assets/Cosmetic/hat_beret.png',             10,  'PROBLEM_PRACTICE',  0, NULL, NULL, NULL, 1, NOW(6), NOW(6)),
    ('glasses_heart',          'FACE',        '하트 안경',       'assets/Cosmetic/glasses_heart.png',         12,  'PROBLEM_PRACTICE',  0, NULL, NULL, NULL, 1, NOW(6), NOW(6)),
    ('headphone',              'HEAD',        '헤드폰',          'assets/Cosmetic/headphone.png',             13,  'PROBLEM_PRACTICE',  0, NULL, NULL, NULL, 1, NOW(6), NOW(6)),
    ('face_moustache',         'FACE',        '콧수염',          'assets/Cosmetic/face_moustache.png',        14,  'PROBLEM_PRACTICE',  0, NULL, NULL, NULL, 1, NOW(6), NOW(6)),
    ('head_earmuffs_winter',   'HEAD',        '겨울 귀마개',     'assets/Cosmetic/head_earmuffs_winter.png',  15,  'PROBLEM_PRACTICE',  0, NULL, NULL, NULL, 1, NOW(6), NOW(6)),

    -- 복습 세트 복습 (NOTE_PRACTICE). 목과 옷. 옷 넷은 전신이라 본체 그림이 머리만 남는다.
    ('scarf',                  'NECK',        '목도리',          'assets/Cosmetic/scarf.png',                 2,   'NOTE_PRACTICE',     0, NULL, NULL, NULL, 1, NOW(6), NOW(6)),
    ('bowtie',                 'NECK',        '나비넥타이',      'assets/Cosmetic/bowtie.png',                3,   'NOTE_PRACTICE',     0, NULL, NULL, NULL, 1, NOW(6), NOW(6)),
    ('outfit_cardigan',        'OUTFIT',      '가디건',          'assets/Cosmetic/outfit_cardigan.png',       5,   'NOTE_PRACTICE',     1, NULL, NULL, NULL, 1, NOW(6), NOW(6)),
    ('neck_scarf_coral',       'NECK',        '산호빛 스카프',   'assets/Cosmetic/neck_scarf_coral.png',      6,   'NOTE_PRACTICE',     0, NULL, NULL, NULL, 1, NOW(6), NOW(6)),
    ('outfit_hoodie',          'OUTFIT',      '후드티',          'assets/Cosmetic/outfit_hoodie.png',         8,   'NOTE_PRACTICE',     1, NULL, NULL, NULL, 1, NOW(6), NOW(6)),
    ('neck_camera',            'NECK',        '카메라',          'assets/Cosmetic/neck_camera.png',           9,   'NOTE_PRACTICE',     0, NULL, NULL, NULL, 1, NOW(6), NOW(6)),
    ('outfit_raincoat',        'OUTFIT',      '우비',            'assets/Cosmetic/outfit_raincoat.png',       10,  'NOTE_PRACTICE',     1, NULL, NULL, NULL, 1, NOW(6), NOW(6)),
    ('neck_medal',             'NECK',        '메달',            'assets/Cosmetic/neck_medal.png',            12,  'NOTE_PRACTICE',     0, NULL, NULL, NULL, 1, NOW(6), NOW(6)),
    ('outfit_school',          'OUTFIT',      '교복',            'assets/Cosmetic/outfit_school.png',         13,  'NOTE_PRACTICE',     1, NULL, NULL, NULL, 1, NOW(6), NOW(6)),

    -- 총 학습 레벨 (required_ability = NULL). 능력치 넷을 골고루 올려야 오르는 값이라
    -- 한쪽만 파는 사람에게는 늦게 열린다. 뱃지와 손에 드는 것, 그리고 마지막 학사 세트를 둔다.
    -- 상한이 15 에서 20 으로 올라가 16 / 18 / 19 / 20 자리가 생겼다.
    ('headband_sprout',        'HEAD',        '새싹 머리띠',     'assets/Cosmetic/headband_sprout.png',       2,   NULL,                0, NULL, NULL, NULL, 1, NOW(6), NOW(6)),
    ('badge_leaf_star',        'BADGE',       '잎새 별 뱃지',    'assets/Cosmetic/badge_leaf_star.png',       3,   NULL,                0, NULL, NULL, NULL, 1, NOW(6), NOW(6)),
    ('badge_star',             'BADGE',       '별 뱃지',         'assets/Cosmetic/badge_star.png',            5,   NULL,                0, NULL, NULL, NULL, 1, NOW(6), NOW(6)),
    ('badge_heart',            'BADGE',       '하트 뱃지',       'assets/Cosmetic/badge_heart.png',           7,   NULL,                0, NULL, NULL, NULL, 1, NOW(6), NOW(6)),
    ('prop_bouquet',           'HAND',        '꽃다발',          'assets/Cosmetic/prop_bouquet.png',          8,   NULL,                0, NULL, NULL, NULL, 1, NOW(6), NOW(6)),
    ('badge_music',            'BADGE',       '음표 뱃지',       'assets/Cosmetic/badge_music.png',           10,  NULL,                0, NULL, NULL, NULL, 1, NOW(6), NOW(6)),
    ('prop_umbrella',          'HAND',        '우산',            'assets/Cosmetic/prop_umbrella.png',         12,  NULL,                0, NULL, NULL, NULL, 1, NOW(6), NOW(6)),
    ('badge_flame',            'BADGE',       '불꽃 뱃지',       'assets/Cosmetic/badge_flame.png',           14,  NULL,                0, NULL, NULL, NULL, 1, NOW(6), NOW(6)),
    ('prop_lantern',           'HAND',        '랜턴',            'assets/Cosmetic/prop_lantern.png',          16,  NULL,                0, NULL, NULL, NULL, 1, NOW(6), NOW(6)),
    ('badge_snowflake',        'BADGE',       '눈송이 뱃지',     'assets/Cosmetic/badge_snowflake.png',       18,  NULL,                0, NULL, NULL, NULL, 1, NOW(6), NOW(6)),
    ('hat_crown',              'HEAD',        '왕관',            'assets/Cosmetic/hat_crown.png',             19,  NULL,                0, NULL, NULL, NULL, 1, NOW(6), NOW(6)),
    ('hat_graduate',           'HEAD',        '학사모',          'assets/Cosmetic/hat_graduate.png',          20,  NULL,                0, 'graduate', '학사 세트', NULL, 1, NOW(6), NOW(6)),
    ('outfit_graduate',        'OUTFIT',      '졸업 가운',       'assets/Cosmetic/outfit_graduate.png',       20,  NULL,                1, 'graduate', '학사 세트', NULL, 1, NOW(6), NOW(6)),
    ('prop_diploma',           'HAND',        '졸업장',          'assets/Cosmetic/prop_diploma.png',          20,  NULL,                0, 'graduate', '학사 세트', NULL, 1, NOW(6), NOW(6))

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
