-- 챌린지에 작성자를 남긴다. (#310)
--
-- 챌린지는 멤버 전원이 만들 수 있는데 삭제는 방장만 할 수 있었다. 그래서 일반 멤버는 자기가 만든
-- 챌린지를 스스로 지우지 못했다. 작성자 본인에게도 삭제를 열어 주려면 누가 만들었는지를 알아야 하는데,
-- study_room_challenge 에는 그 정보가 아예 없어 컬럼을 새로 만든다.
--
-- study_room.host_user_id 와 같이 FK 없는 식별자로 둔다. user 는 소프트 삭제라 행이 남지만,
-- 같은 도메인 안에서 사람을 가리키는 방식을 하나로 맞추는 편이 읽기 쉽다.
--
-- 기존 행의 작성자는 방장으로 채운다. 지금까지 이 챌린지들을 지울 수 있던 사람이 방장뿐이었으므로,
-- 방장으로 채우면 마이그레이션 전후로 누구도 권한을 새로 얻거나 잃지 않는다.
-- 실제 작성자가 일반 멤버였더라도 이 값으로는 삭제 권한이 늘어나지 않는다(방장은 원래 다 지울 수 있다).
--
-- NULL 을 허용한 채 채운 뒤 NOT NULL 로 조인다. study_room_challenge.room_id 는 NOT NULL 이고
-- study_room.host_user_id 도 NOT NULL 이라 조인으로 못 채우는 행은 남지 않는다.
--
-- 잠금: 세 문장 모두 study_room_challenge 전체를 훑는다. 진행 중 챌린지가 방당 최대 5개인 테이블이라
-- 행 수가 적어 짧게 끝난다.

ALTER TABLE study_room_challenge
    ADD COLUMN created_by_user_id BIGINT NULL AFTER room_id;

UPDATE study_room_challenge c
JOIN study_room r ON r.id = c.room_id
SET c.created_by_user_id = r.host_user_id
WHERE c.created_by_user_id IS NULL;

ALTER TABLE study_room_challenge
    MODIFY COLUMN created_by_user_id BIGINT NOT NULL;
