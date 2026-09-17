-- FCM 토큰 하나가 여러 사용자에게 묶인 행을 정리한다. (#271)
--
-- 토큰은 기기 하나를 가리킨다. 그런데 등록이 (user_id, token) 쌍 단위라, 같은 기기에서 A 가 로그아웃하고
-- B 가 로그인하면 (A, T) 와 (B, T) 가 함께 남았다. 그 뒤로 A 앞 알림(댓글 작성자 이름, 미리보기 포함)이
-- B 가 쓰는 기기에 떴다. 등록 경로는 이번 변경에서 이전 소유자 행을 지우도록 고쳤고,
-- 이 파일은 그 전에 쌓인 행을 정리한다.
--
-- 1) 탈퇴한 사용자의 토큰 행을 지운다. user 는 deleted_at 을 채우는 소프트 삭제라 행이 남아 있다.
--    탈퇴 계정 행이 2) 에서 가장 최근 행으로 뽑히면 지금 그 기기를 쓰는 사용자의 행이 지워지므로 먼저 뺀다.
--
-- 2) 토큰마다 가장 최근에 만들어진 행 하나만 남긴다. 순서는 created_at, 같으면 id 다.
--    - 등록은 이미 있는 쌍을 건드리지 않는다(FcmService.registerToken). 그래서 updated_at 은 created_at 과 같고,
--      "최근 행" 은 그 기기에 새 계정이 처음 등록된 시점이다.
--    - id 를 첫 기준으로 쓰지 않는다. FcmToken 은 테이블 기반 생성기(fcm_token_seq)로 id 를 미리 블록 단위로
--      받아 두기 때문에, 앱 인스턴스가 둘 이상이면 id 순서가 저장 순서와 어긋날 수 있다.
--    - 한계: 같은 기기에서 A → B → A 로 돌아온 경우 A 의 재등록은 새 행을 만들지 않아 B 행이 남는다.
--      이 경우에도 새 코드에서는 그 기기에서 다음 로그인이나 토큰 갱신이 일어나는 순간 바로잡힌다.
--    - 행을 모두 지우는 쪽은 택하지 않았다. 앱은 로그인과 토큰 갱신 때만 토큰을 올리므로,
--      지금 기기를 쓰는 사용자까지 다음 로그인 전까지 알림을 못 받게 된다.
--
-- token 단독 유니크 제약은 걸지 않는다. Flyway 는 새 앱이 뜰 때 돌고 그동안 이전 버전 앱이 계속 등록을 받는다.
-- 정리와 제약 추가 사이에 중복 쌍이 한 번만 들어와도 ALTER 가 실패하고, MySQL DDL 은 롤백되지 않아
-- 새 앱이 뜨지 못한 채 수동 복구가 필요해진다. 중복은 등록 경로에서 막고, 제약은 별도 작업으로 본다.
--
-- 두 문장 모두 다시 돌려도 지울 행이 없어 안전하다.
-- 잠금: 두 DELETE 모두 fcm_token 전체를 한 번 읽으며, 읽는 행에 공유 잠금이 걸려 문장이 끝날 때까지
-- 토큰 등록 INSERT 가 기다린다. 행 수에 비례해 짧게 끝나는 단일 문장이다.

DELETE f
FROM fcm_token f
JOIN `user` u ON u.id = f.user_id
WHERE u.deleted_at IS NOT NULL;

-- MySQL 은 DELETE 대상 테이블을 같은 문장의 서브쿼리에서 읽지 못한다(1093).
-- 윈도 함수가 들어간 파생 테이블은 병합되지 않고 임시 테이블로 먼저 만들어지므로 이 제약에 걸리지 않는다.
DELETE f
FROM fcm_token f
JOIN (
    SELECT ranked.id
    FROM (
        SELECT id,
               ROW_NUMBER() OVER (PARTITION BY token ORDER BY created_at DESC, id DESC) AS row_num
        FROM fcm_token
    ) ranked
    WHERE ranked.row_num > 1
) stale ON stale.id = f.id;
