-- fcm_token.token 에 비유니크 보조 인덱스를 건다.
--
-- 지금 이 테이블의 인덱스는 (user_id, token) 유니크 하나뿐이다. 선두 컬럼이 user_id 라
-- token 단독 조회는 인덱스를 타지 못하고 테이블을 전부 읽는다.
-- 그 조회가 등록 경로 한복판에 있다. 등록은 로그인과 토큰 갱신 때마다 들어오고,
-- 매번 이전 소유자 행을 찾으려고 token 으로만 조회한다(FcmService -> FcmTokenWriter.register).
-- 중복 등록이 겹쳐 재시도까지 가면 같은 조회를 한 번 더 한다.
--
-- 유니크가 아니라 보통 인덱스다. 한 토큰에 행이 둘 이상 남아 있어도 ALTER 가 실패하지 않는다.
-- (V43 주석에 적은 대로, 배포 중 이전 버전 앱이 중복 쌍을 넣으면 유니크 추가는 그대로 기동 실패가 된다.)
--
-- ALGORITHM=INPLACE, LOCK=NONE 을 명시한다. MySQL 8.0 InnoDB 의 보조 인덱스 추가는
-- 온라인 DDL 대상이라 진행 중에도 INSERT/DELETE 가 계속 처리된다.
-- 명시해 두면 어떤 이유로든 온라인으로 못 할 때 테이블을 오래 잠그는 대신 즉시 실패한다.
-- (실패하면 앱이 뜨지 않으므로 바로 알 수 있고, 잠금으로 서비스가 멈추는 쪽보다 낫다.)
ALTER TABLE fcm_token
    ADD INDEX idx_fcm_token_token (token),
    ALGORITHM = INPLACE,
    LOCK = NONE;
