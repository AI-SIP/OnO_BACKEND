-- 꾸미기 아이템 시드. 관리자 화면은 2차라 카탈로그는 마이그레이션으로만 들어온다.
--
-- ON DUPLICATE KEY UPDATE item_key = item_key 는 "이미 있으면 아무것도 하지 않는다"는 뜻이다.
-- uk_cosmetic_item_key 가 중복을 잡아 주므로 이 문장은 몇 번을 다시 돌려도 결과가 같다.
-- 운영 중에 이름이나 이미지를 손으로 고쳤을 수 있어 덮어쓰지 않는다. 값을 바꾸려면 새 마이그레이션에서
-- UPDATE 로 얹는다(V33 이 미션 문구를 그렇게 고쳤다). 이 파일을 고치면 이미 적용된 환경에서
-- Flyway 체크섬이 어긋나 앱이 뜨지 않는다.
--
-- **image_url 은 지금 번들 상대 경로다.** S3 에 아직 아무것도 올라가 있지 않다.
-- 'assets/Cosmetic/hat_beanie.png' 처럼 앱 번들 경로로 시드해 두면, 나중에 S3 로 옮길 때
-- 이 컬럼 값만 'https://...' 로 바꾸는 UPDATE 한 번으로 전환된다. 앱을 다시 배포할 필요가 없다.
-- 프론트는 값이 'http' 로 시작하는지로 네트워크 이미지와 번들 이미지를 갈라 처리하기로 했다.
--
-- 'BASE' 행은 장착 슬롯이 아니라 개구리 본체 이미지다. 슬롯 이름을 'BASE' 로 둬서
-- 장착 가능한 슬롯 목록에서 빠지고 아이템 목록에도 나가지 않는다. 본체 이미지까지 데이터로 두는 이유는
-- 위와 같다. S3 전환이 코드 배포 없이 끝나야 한다.
--
-- required_level 이 NULL 인 행은 레벨로는 열리지 않는다. active = 0 인 2차 콘텐츠가 전부 그렇다.
-- 이미지를 다 그리고 나면 active 를 1 로 올리고 required_level 을 채우는 마이그레이션을 새로 쓴다.
--
-- conflicts_with 는 전부 비워 둔다. 컬럼과 자동 해제 로직은 이미 있으니, 같이 못 쓰는 조합이
-- 생기면 데이터만 채우면 된다.

-- 개구리 본체. 장착 대상이 아니다.
INSERT INTO cosmetic_item
    (item_key, slot, name_ko, image_url, required_level, set_id, conflicts_with, active, created_at, updated_at)
VALUES
    ('BASE', 'BASE', '개구리 본체', 'assets/Cosmetic/BASE.png', NULL, NULL, NULL, 1, NOW(6), NOW(6))
ON DUPLICATE KEY UPDATE item_key = item_key;

-- 레벨 해금 아이템. 총 학습 레벨 2 부터 15 까지 한 단계에 하나씩, 마지막 15 는 졸업 세트 세 점이다.
INSERT INTO cosmetic_item
    (item_key, slot, name_ko, image_url, required_level, set_id, conflicts_with, active, created_at, updated_at)
VALUES
    ('headband_sprout',   'HEAD',       '새싹 머리띠',   'assets/Cosmetic/headband_sprout.png',   2,  NULL,       NULL, 1, NOW(6), NOW(6)),
    ('bg_spring',         'BACKGROUND', '봄 배경',       'assets/Cosmetic/bg_spring.png',         3,  NULL,       NULL, 1, NOW(6), NOW(6)),
    ('glasses_round',     'FACE',       '동그란 안경',   'assets/Cosmetic/glasses_round.png',     4,  NULL,       NULL, 1, NOW(6), NOW(6)),
    ('scarf',             'NECK',       '목도리',        'assets/Cosmetic/scarf.png',             5,  NULL,       NULL, 1, NOW(6), NOW(6)),
    ('hat_beanie',        'HEAD',       '비니',          'assets/Cosmetic/hat_beanie.png',        6,  NULL,       NULL, 1, NOW(6), NOW(6)),
    ('bg_study',          'BACKGROUND', '공부방 배경',   'assets/Cosmetic/bg_study.png',          7,  NULL,       NULL, 1, NOW(6), NOW(6)),
    ('bag_mini_backpack', 'BACK',       '미니 백팩',     'assets/Cosmetic/bag_mini_backpack.png', 8,  NULL,       NULL, 1, NOW(6), NOW(6)),
    ('outfit_cardigan',   'OUTFIT',     '가디건',        'assets/Cosmetic/outfit_cardigan.png',   9,  NULL,       NULL, 1, NOW(6), NOW(6)),
    ('prop_study',        'HAND',       '공부 도구',     'assets/Cosmetic/prop_study.png',        10, NULL,       NULL, 1, NOW(6), NOW(6)),
    ('glasses_sun',       'FACE',       '선글라스',      'assets/Cosmetic/glasses_sun.png',       11, NULL,       NULL, 1, NOW(6), NOW(6)),
    ('hat_bucket',        'HEAD',       '버킷햇',        'assets/Cosmetic/hat_bucket.png',        12, NULL,       NULL, 1, NOW(6), NOW(6)),
    ('bg_night',          'BACKGROUND', '밤하늘 배경',   'assets/Cosmetic/bg_night.png',          13, NULL,       NULL, 1, NOW(6), NOW(6)),
    ('hat_crown',         'HEAD',       '왕관',          'assets/Cosmetic/hat_crown.png',         14, NULL,       NULL, 1, NOW(6), NOW(6)),
    ('hat_graduate',      'HEAD',       '학사모',        'assets/Cosmetic/hat_graduate.png',      15, 'graduate', NULL, 1, NOW(6), NOW(6)),
    ('outfit_graduate',   'OUTFIT',     '졸업 가운',     'assets/Cosmetic/outfit_graduate.png',   15, 'graduate', NULL, 1, NOW(6), NOW(6)),
    ('prop_diploma',      'HAND',       '졸업장',        'assets/Cosmetic/prop_diploma.png',      15, 'graduate', NULL, 1, NOW(6), NOW(6))
ON DUPLICATE KEY UPDATE item_key = item_key;

-- 2차 콘텐츠. 이미지가 아직 없어 active = 0 으로 넣어 둔다.
-- 지금 넣는 이유는 item_key 를 여기서 확정해 두기 위해서다. 프론트 에셋 이름과 어긋나면
-- 나중에 둘 중 하나를 고쳐야 하는데, 이미 깔린 앱이 있으면 그때는 못 고친다.
INSERT INTO cosmetic_item
    (item_key, slot, name_ko, image_url, required_level, set_id, conflicts_with, active, created_at, updated_at)
VALUES
    ('hat_beret',              'HEAD',       '베레모',          'assets/Cosmetic/hat_beret.png',              NULL, NULL, NULL, 0, NOW(6), NOW(6)),
    ('headphone',              'HEAD',       '헤드폰',          'assets/Cosmetic/headphone.png',              NULL, NULL, NULL, 0, NOW(6), NOW(6)),
    ('glasses_heart',          'FACE',       '하트 안경',       'assets/Cosmetic/glasses_heart.png',          NULL, NULL, NULL, 0, NOW(6), NOW(6)),
    ('bowtie',                 'NECK',       '나비넥타이',      'assets/Cosmetic/bowtie.png',                 NULL, NULL, NULL, 0, NOW(6), NOW(6)),
    ('neck_medal',             'NECK',       '메달',            'assets/Cosmetic/neck_medal.png',             NULL, NULL, NULL, 0, NOW(6), NOW(6)),
    ('neck_camera',            'NECK',       '카메라',          'assets/Cosmetic/neck_camera.png',            NULL, NULL, NULL, 0, NOW(6), NOW(6)),
    ('outfit_school',          'OUTFIT',     '교복',            'assets/Cosmetic/outfit_school.png',          NULL, NULL, NULL, 0, NOW(6), NOW(6)),
    ('outfit_hoodie',          'OUTFIT',     '후드티',          'assets/Cosmetic/outfit_hoodie.png',          NULL, NULL, NULL, 0, NOW(6), NOW(6)),
    ('outfit_raincoat',        'OUTFIT',     '우비',            'assets/Cosmetic/outfit_raincoat.png',        NULL, NULL, NULL, 0, NOW(6), NOW(6)),
    ('bag_waist_pouch',        'BACK',       '허리 가방',       'assets/Cosmetic/bag_waist_pouch.png',        NULL, NULL, NULL, 0, NOW(6), NOW(6)),
    ('bag_crossbody_satchel',  'BACK',       '크로스백',        'assets/Cosmetic/bag_crossbody_satchel.png',  NULL, NULL, NULL, 0, NOW(6), NOW(6)),
    ('prop_bouquet',           'HAND',       '꽃다발',          'assets/Cosmetic/prop_bouquet.png',           NULL, NULL, NULL, 0, NOW(6), NOW(6)),
    ('prop_umbrella',          'HAND',       '우산',            'assets/Cosmetic/prop_umbrella.png',          NULL, NULL, NULL, 0, NOW(6), NOW(6)),
    ('badge_leaf_star',        'BADGE',      '잎새 별 뱃지',    'assets/Cosmetic/badge_leaf_star.png',        NULL, NULL, NULL, 0, NOW(6), NOW(6)),
    ('bg_autumn',              'BACKGROUND', '가을 배경',       'assets/Cosmetic/bg_autumn.png',              NULL, NULL, NULL, 0, NOW(6), NOW(6)),
    ('bg_rainy',               'BACKGROUND', '비 오는 날 배경', 'assets/Cosmetic/bg_rainy.png',               NULL, NULL, NULL, 0, NOW(6), NOW(6)),
    ('bg_space',               'BACKGROUND', '우주 배경',       'assets/Cosmetic/bg_space.png',               NULL, NULL, NULL, 0, NOW(6), NOW(6)),
    ('bg_sunset',              'BACKGROUND', '노을 배경',       'assets/Cosmetic/bg_sunset.png',              NULL, NULL, NULL, 0, NOW(6), NOW(6)),
    ('bg_winter',              'BACKGROUND', '겨울 배경',       'assets/Cosmetic/bg_winter.png',              NULL, NULL, NULL, 0, NOW(6), NOW(6))
ON DUPLICATE KEY UPDATE item_key = item_key;
