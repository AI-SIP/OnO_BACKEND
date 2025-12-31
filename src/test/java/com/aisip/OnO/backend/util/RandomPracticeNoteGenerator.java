package com.aisip.OnO.backend.util;

import com.aisip.OnO.backend.practicenote.dto.PracticeNoteRegisterDto;
import com.aisip.OnO.backend.practicenote.dto.PracticeNotificationRegisterDto;
import com.aisip.OnO.backend.practicenote.entity.PracticeNote;

import java.util.List;
import java.util.UUID;

/**
 * 테스트용 랜덤 PracticeNote 생성기
 * 통합 테스트에서 고유한 PracticeNote 엔티티를 쉽게 생성할 수 있도록 지원
 */
public class RandomPracticeNoteGenerator {

    private static final String DEFAULT_TITLE_PREFIX = "테스트복습노트";
    private static final PracticeNotificationRegisterDto DEFAULT_NOTIFICATION =
            new PracticeNotificationRegisterDto(1, 9, 0, "NONE", null);

    /**
     * 랜덤 제목과 빈 문제 리스트를 가진 기본 테스트 PracticeNote 생성
     * @param userId 복습노트를 소유할 사용자 ID
     * @return 생성된 PracticeNote 엔티티
     */
    public static PracticeNote createRandomPracticeNote(Long userId) {
        String randomTitle = DEFAULT_TITLE_PREFIX + "_" + UUID.randomUUID().toString().substring(0, 8);

        PracticeNoteRegisterDto practiceNoteRegisterDto = new PracticeNoteRegisterDto(
                null,
                randomTitle,
                List.of(),
                DEFAULT_NOTIFICATION
        );

        return PracticeNote.from(practiceNoteRegisterDto, userId);
    }

    /**
     * 지정된 제목으로 테스트 PracticeNote 생성
     * @param title 복습노트 제목
     * @param userId 복습노트를 소유할 사용자 ID
     * @return 생성된 PracticeNote 엔티티
     */
    public static PracticeNote createRandomPracticeNote(String title, Long userId) {
        PracticeNoteRegisterDto practiceNoteRegisterDto = new PracticeNoteRegisterDto(
                null,
                title,
                List.of(),
                DEFAULT_NOTIFICATION
        );

        return PracticeNote.from(practiceNoteRegisterDto, userId);
    }

    /**
     * 문제 ID 리스트와 함께 랜덤 PracticeNote 생성
     * @param problemIdList 복습노트에 포함될 문제 ID 리스트
     * @param userId 복습노트를 소유할 사용자 ID
     * @return 생성된 PracticeNote 엔티티
     */
    public static PracticeNote createRandomPracticeNoteWithProblems(List<Long> problemIdList, Long userId) {
        String randomTitle = DEFAULT_TITLE_PREFIX + "_" + UUID.randomUUID().toString().substring(0, 8);

        PracticeNoteRegisterDto practiceNoteRegisterDto = new PracticeNoteRegisterDto(
                null,
                randomTitle,
                problemIdList,
                DEFAULT_NOTIFICATION
        );

        return PracticeNote.from(practiceNoteRegisterDto, userId);
    }

    /**
     * 제목과 문제 ID 리스트를 지정하여 PracticeNote 생성
     * @param title 복습노트 제목
     * @param problemIdList 복습노트에 포함될 문제 ID 리스트
     * @param userId 복습노트를 소유할 사용자 ID
     * @return 생성된 PracticeNote 엔티티
     */
    public static PracticeNote createRandomPracticeNote(String title, List<Long> problemIdList, Long userId) {
        PracticeNoteRegisterDto practiceNoteRegisterDto = new PracticeNoteRegisterDto(
                null,
                title,
                problemIdList,
                DEFAULT_NOTIFICATION
        );

        return PracticeNote.from(practiceNoteRegisterDto, userId);
    }

    /**
     * 커스텀 알림 설정과 함께 PracticeNote 생성
     * @param title 복습노트 제목
     * @param problemIdList 복습노트에 포함될 문제 ID 리스트
     * @param notification 알림 설정
     * @param userId 복습노트를 소유할 사용자 ID
     * @return 생성된 PracticeNote 엔티티
     */
    public static PracticeNote createRandomPracticeNote(
            String title,
            List<Long> problemIdList,
            PracticeNotificationRegisterDto notification,
            Long userId) {

        PracticeNoteRegisterDto practiceNoteRegisterDto = new PracticeNoteRegisterDto(
                null,
                title,
                problemIdList,
                notification
        );

        return PracticeNote.from(practiceNoteRegisterDto, userId);
    }

    /**
     * 짧은 제목을 가진 랜덤 PracticeNote 생성 (디버깅에 유용)
     * @param userId 복습노트를 소유할 사용자 ID
     * @return 생성된 PracticeNote 엔티티
     */
    public static PracticeNote createRandomPracticeNoteWithShortTitle(Long userId) {
        String randomTitle = "note_" + UUID.randomUUID().toString().substring(0, 4);

        PracticeNoteRegisterDto practiceNoteRegisterDto = new PracticeNoteRegisterDto(
                null,
                randomTitle,
                List.of(),
                DEFAULT_NOTIFICATION
        );

        return PracticeNote.from(practiceNoteRegisterDto, userId);
    }

    /**
     * 기본 알림 설정 DTO 반환
     * @return 기본 알림 설정 (알림 없음)
     */
    public static PracticeNotificationRegisterDto getDefaultNotification() {
        return DEFAULT_NOTIFICATION;
    }

    /**
     * 커스텀 알림 설정 DTO 생성
     * @param repeatInterval 반복 간격
     * @param hour 시간
     * @param minute 분
     * @param repeatType 반복 타입
     * @param repeatDays 반복 요일 (선택)
     * @return 커스텀 알림 설정
     */
    public static PracticeNotificationRegisterDto createNotification(
            Integer repeatInterval,
            Integer hour,
            Integer minute,
            String repeatType,
            List<Integer> repeatDays) {

        return new PracticeNotificationRegisterDto(
                repeatInterval,
                hour,
                minute,
                repeatType,
                repeatDays
        );
    }
}
