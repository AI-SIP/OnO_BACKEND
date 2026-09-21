-- 메모가 255자를 넘으면 문제 등록 자체가 500 으로 실패했다 (Sentry JAVA-SPRING-BOOT-5B / 5A).
-- memo 는 ddl-auto 시절 기본값 varchar(255) 로 만들어졌고, 사용자 자유 입력이라 쉽게 넘긴다.
--
-- utf8mb4 기준 varchar(255) 는 이미 1020 바이트라 2바이트 길이 접두사를 쓰고 있어,
-- varchar(1000)(4000 바이트) 로 늘려도 접두사 크기가 그대로다 → INPLACE 로 테이블 재작성 없이 처리된다.
-- TEXT 로 바꾸면 ALGORITHM=COPY 가 되어 problem 테이블 전체가 잠기므로 선택하지 않았다.
ALTER TABLE problem
    MODIFY COLUMN memo VARCHAR(1000) NULL,
    ALGORITHM = INPLACE, LOCK = NONE;
