package com.aisip.OnO.backend.admin.dto;

import com.aisip.OnO.backend.mission.entity.MissionLog;
import com.aisip.OnO.backend.practicenote.entity.PracticeNote;
import java.time.LocalDateTime;

public record AdminPracticeLogResponseDto(
        Long missionLogId,
        Long userId,
        String userName,
        String userEmail,
        Long practiceNoteId,
        String practiceTitle,
        Long point,
        LocalDateTime createdAt
) {
    /**
     * {@code point} 는 저장된 값이 아니라 미션 타입의 정가다.
     *
     * <p>{@code mission_log.point} 는 "이 기록으로 자동 적립이 돌았는가"를 담게 되어, 미션을 받을 수 있는
     * 앱에서 온 요청의 행은 0 이다(#318). 이 화면이 보여 주는 값의 뜻은 "복습 세트 완료 하나의 값어치"라
     * 적립 여부와 무관하므로, 화면이 지금까지 보여 주던 값을 그대로 유지한다.
     */
    public static AdminPracticeLogResponseDto from(MissionLog missionLog, PracticeNote practiceNote) {
        return new AdminPracticeLogResponseDto(
                missionLog.getId(),
                missionLog.getUser().getId(),
                missionLog.getUser().getName(),
                missionLog.getUser().getEmail(),
                missionLog.getReferenceId(),
                practiceNote != null ? practiceNote.getTitle() : "-",
                missionLog.getMissionType().getPoint(),
                missionLog.getCreatedAt()
        );
    }
}
