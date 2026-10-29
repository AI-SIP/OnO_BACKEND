-- 이미지 URL 컬럼 네 개에 비유니크 보조 인덱스를 건다.
--
-- 이미지를 등록하거나 지울 때 같은 URL 을 다른 사용자가 쓰고 있는지 URL 로 찾는데
-- (ImageUrlOwnershipService), 네 컬럼 모두 인덱스가 없어 매번 테이블을 전부 읽는다.
-- 문제를 여러 개 한 번에 지우면 URL 묶음마다 이 조회가 네 테이블에 한 번씩 나간다.
--
-- 같은 URL 행이 여럿 있을 수 있어 유니크가 아니라 보통 인덱스다.
-- 네 컬럼은 모두 VARCHAR(255) 라 utf8mb4 에서도 인덱스 길이 한도(3072바이트) 안이다.
--
-- V44 와 같은 이유로 ALGORITHM=INPLACE, LOCK=NONE 을 명시한다. 진행 중에도 쓰기가 계속 처리되고,
-- 온라인으로 못 하는 경우에는 테이블을 오래 잠그는 대신 바로 실패한다.
ALTER TABLE image_data
    ADD INDEX idx_image_data_image_url (image_url),
    ALGORITHM = INPLACE,
    LOCK = NONE;

ALTER TABLE problem_solve_image_data
    ADD INDEX idx_solve_image_data_image_url (image_url),
    ALGORITHM = INPLACE,
    LOCK = NONE;

ALTER TABLE `user`
    ADD INDEX idx_user_profile_image_url (profile_image_url),
    ALGORITHM = INPLACE,
    LOCK = NONE;

ALTER TABLE study_room
    ADD INDEX idx_study_room_thumbnail_url (thumbnail_url),
    ALGORITHM = INPLACE,
    LOCK = NONE;
