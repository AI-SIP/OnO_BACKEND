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
    public static AdminPracticeLogResponseDto from(MissionLog missionLog, PracticeNote practiceNote) {
        return new AdminPracticeLogResponseDto(
                missionLog.getId(),
                missionLog.getUser().getId(),
                missionLog.getUser().getName(),
                missionLog.getUser().getEmail(),
                missionLog.getReferenceId(),
                practiceNote != null ? practiceNote.getTitle() : "-",
                missionLog.getPoint(),
                missionLog.getCreatedAt()
        );
    }
}
