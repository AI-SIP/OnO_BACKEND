package com.aisip.OnO.backend.practicenote.dto;
import com.aisip.OnO.backend.practicenote.entity.PracticeNote;
import com.aisip.OnO.backend.problem.dto.ProblemResponseDto;
import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

@Builder(access = AccessLevel.PRIVATE)
public record PracticeNoteDetailResponseDto(
        Long practiceNoteId,

        String practiceTitle,

        Long practiceCount,

        List<Long> problemIdList,

        PracticeNotificationResponseDto practiceNotification,

        LocalDateTime lastSolvedAt,

        LocalDateTime createdAt,

        LocalDateTime updatedAt
) {
    public static PracticeNoteDetailResponseDto from(PracticeNote practiceNote, List<Long> problemIdList) {

        PracticeNotificationResponseDto notificationDto = null;
        if (practiceNote.getPracticeNotification() != null) {
            notificationDto = PracticeNotificationResponseDto.from(
                    practiceNote.getPracticeNotification()
            );
        }

        return PracticeNoteDetailResponseDto.builder()
                .practiceNoteId(practiceNote.getId())
                .practiceTitle(practiceNote.getTitle())
                .practiceCount(practiceNote.getPracticeCount())
                .problemIdList(problemIdList)
                .practiceNotification(notificationDto)
                .lastSolvedAt(practiceNote.getLastSolvedAt())
                .createdAt(practiceNote.getCreatedAt())
                .updatedAt(practiceNote.getUpdatedAt())
                .build();
    }
}
