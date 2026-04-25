package com.aisip.OnO.backend.admin.dto;

import com.aisip.OnO.backend.practicenote.entity.PracticeNote;
import com.aisip.OnO.backend.user.entity.User;
import java.time.LocalDateTime;

public record AdminPracticeNoteResponseDto(
        Long practiceNoteId,
        Long userId,
        String userName,
        String userEmail,
        String practiceTitle,
        Long problemCount,
        Long practiceCount,
        LocalDateTime lastSolvedAt,
        LocalDateTime createdAt
) {
    public static AdminPracticeNoteResponseDto from(PracticeNote practiceNote, User user, Long problemCount) {
        return new AdminPracticeNoteResponseDto(
                practiceNote.getId(),
                practiceNote.getUserId(),
                user != null ? user.getName() : "-",
                user != null ? user.getEmail() : "-",
                practiceNote.getTitle(),
                problemCount,
                practiceNote.getPracticeCount(),
                practiceNote.getLastSolvedAt(),
                practiceNote.getCreatedAt()
        );
    }
}
