-- 훈장. 사용자가 어떤 훈장을 언제 받았는지만 담는다.
--
-- 테이블이 하나뿐인 이유는 훈장 목록 자체를 코드의 enum(Achievement)으로 두기 때문이다.
-- 치장 아이템과 달리 훈장은 관리자가 늘리는 것이 아니라 앱 에셋과 함께 배포되는 것이라,
-- 카탈로그 테이블을 두면 시드 마이그레이션과 앱 번들이 따로 놀 여지만 생긴다.
-- 그래서 V34/V35 처럼 DDL 과 시드를 나눌 일도 없다. 이 파일에는 시드가 없다.
--
-- **(user_id, achievement_key) 복합 기본키가 멱등성의 근거다.** 판정이 조회할 때마다 돌기 때문에
-- 훈장 화면을 두 번 열면 같은 INSERT 가 두 번 나간다. 중복을 막는 것을 애플리케이션 검사에 맡기면
-- 두 요청이 동시에 "없다"를 읽고 둘 다 INSERT 하는 창이 열린다.
-- 이 기본키가 있으면 INSERT ... ON DUPLICATE KEY UPDATE 한 문장으로 끝나고, 그 창 자체가 없다.
--
-- 대리키(id)를 두지 않는다. 조회는 언제나 user_id 로 들어오고 쓰기는 (user_id, achievement_key) 다.
-- 자연키가 곧 조회 키라 그대로 기본키로 두는 편이 읽기도 쓰기도 싸다. user_cosmetic_loadout 과 같다.
--
-- user 에 외래키를 걸지 않는다. mission_progress, user_cosmetic_loadout 과 같은 이유로,
-- INSERT 마다 부모 사용자 행에 공유 잠금이 붙으면 이미 사용자 행을 배타 잠금으로 잡는
-- 미션 보상 지급 경로와 잠금 순서가 엇갈릴 수 있다.
--
-- achievement_key 는 enum 이름(ARCHIVIST)이 아니라 API 와 앱 에셋이 쓰는 계약 키(archivist)를 담는다.
-- 행을 직접 들여다봤을 때 무슨 훈장인지 바로 알 수 있어야 한다.
--
-- earned_at 은 애플리케이션이 KST 로 채워 넣는다. NOW(6) 을 쓰지 않는 이유는 DB 서버의 시간대가
-- 앱과 다르면 받은 시각이 아홉 시간 어긋난 채로 앱에 뜨기 때문이다.
--
-- 한 번 받은 훈장은 취소되지 않는다. 조건을 다시 계산했을 때 안 맞아도 행은 그대로 둔다.
-- 오답노트를 지웠다고 기록광을 뺏으면 지우는 것이 무서워진다. 그래서 이 테이블에는 DELETE 경로가 없다.
--
-- V34 와 같은 이유로 IF NOT EXISTS 로 만들고 인덱스도 테이블 정의 안에 둔다. MySQL 에는
-- CREATE INDEX IF NOT EXISTS 가 없어 문장을 나누면 그 문장이 새로운 재실행 지점이 된다
-- (V31 이 information_schema 가드를 쓴 이유가 그거다). 이 파일의 문장은 몇 번을 다시 돌려도 안전하다.
CREATE TABLE IF NOT EXISTS user_achievement (
    user_id         BIGINT      NOT NULL,
    achievement_key VARCHAR(32) NOT NULL,
    earned_at       DATETIME(6) NOT NULL,
    PRIMARY KEY (user_id, achievement_key)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
