-- 미션 제목의 수를 한글 수사에서 숫자로 바꾼다.
--
-- V33 은 제목을 한글 수사로 통일했는데, 앱 QA 에서 수는 숫자로 적기로 했다(이슈 #341).
-- 설명은 V33 에서 이미 숫자라 제목만 고친다. code 와 목표값은 그대로라 진행도와 보상은 바뀌지 않는다.
--
-- V30, V33 을 고치지 않는 이유는 V33 과 같다. 이미 적용된 환경에서 Flyway 체크섬이 어긋난다.
--   열 권의 노트   -> 오답노트 10개      (무엇을 열 개 하라는 건지 설명을 봐야 읽혔다)
--   세 번의 완주   -> 복습 세트 3번 완주 (무엇의 완주인지 분명해진다)

UPDATE mission_definition SET title = '3문제만'            WHERE code = 'DAILY_REVIEW_3';
UPDATE mission_definition SET title = '3문제 맞히기'       WHERE code = 'DAILY_CORRECT_3';
UPDATE mission_definition SET title = '5일 접속하기'       WHERE code = 'WEEKLY_ATTEND_5';
UPDATE mission_definition SET title = '오답노트 10개'      WHERE code = 'WEEKLY_NOTE_10';
UPDATE mission_definition SET title = '30문제 복습'        WHERE code = 'WEEKLY_REVIEW_30';
UPDATE mission_definition SET title = '복습 세트 3번 완주' WHERE code = 'WEEKLY_SET_3';
