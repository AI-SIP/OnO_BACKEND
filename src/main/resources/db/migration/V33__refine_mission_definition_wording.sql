-- 미션 제목과 설명 문구 정리.
--
-- V30 파일을 고치지 않는다. 이미 적용된 환경이 있으면 Flyway 체크섬이 어긋나 앱이 뜨지 않는다.
-- 문구 변경은 새 마이그레이션의 UPDATE 로 얹는다. UPDATE 는 몇 번을 다시 돌려도 결과가 같다.
--
-- 설명은 전부 "~하기" 로 끝나는 행동 문구로 맞췄다. 제목은 한글 수사로 통일하고,
-- 무엇을 하라는 건지 제목만으로 안 읽히던 것들을 동사형으로 바꿨다.
--   정확하게   -> 세 문제 맞히기   (무엇을 정확하게 하라는 건지 읽히지 않았다)
--   꾸준함     -> 닷새 접속하기     (같은 이유)
--   세트 완주  -> 복습 세트 완주    (무엇의 세트인지 분명해진다)
--   서른 번의 복습 -> 서른 문제 복습 (설명이 "오답 30문제" 라 단위를 맞춘다)

UPDATE mission_definition SET description = '앱 접속하기'            WHERE code = 'DAILY_ATTEND';
UPDATE mission_definition SET description = '오답노트 1개 쓰기'      WHERE code = 'DAILY_NOTE_WRITE';
UPDATE mission_definition SET description = '오답 3문제 복습하기'    WHERE code = 'DAILY_REVIEW_3';
UPDATE mission_definition SET description = '복습에서 3문제 맞히기'  WHERE code = 'DAILY_CORRECT_3';
UPDATE mission_definition SET description = '복습 세트 1개 끝내기'   WHERE code = 'DAILY_PRACTICE_SET';
UPDATE mission_definition SET description = '오늘 기분 남기기'       WHERE code = 'DAILY_MOOD';
UPDATE mission_definition SET description = '이번 주 5일 접속하기'   WHERE code = 'WEEKLY_ATTEND_5';
UPDATE mission_definition SET description = '오답노트 10개 쓰기'     WHERE code = 'WEEKLY_NOTE_10';
UPDATE mission_definition SET description = '오답 30문제 복습하기'   WHERE code = 'WEEKLY_REVIEW_30';

UPDATE mission_definition SET title = '세 문제 맞히기'  WHERE code = 'DAILY_CORRECT_3';
UPDATE mission_definition SET title = '복습 세트 완주'  WHERE code = 'DAILY_PRACTICE_SET';
UPDATE mission_definition SET title = '닷새 접속하기'   WHERE code = 'WEEKLY_ATTEND_5';
UPDATE mission_definition SET title = '서른 문제 복습'  WHERE code = 'WEEKLY_REVIEW_30';
