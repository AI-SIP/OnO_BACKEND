package com.aisip.OnO.backend.practicenote.dto;

import com.aisip.OnO.backend.practicenote.entity.PracticeNote;
import lombok.AccessLevel;
import lombok.Builder;

import java.time.LocalDateTime;

@Builder(access = AccessLevel.PRIVATE)
public record PracticeNoteThumbnailResponseDto(
        Long practiceNoteId,

        String practiceTitle,

        Long practiceCount,

        LocalDateTime lastSolvedAt
) {
    public static PracticeNoteThumbnailResponseDto from(PracticeNote practiceNote) {
        return PracticeNoteThumbnailResponseDto.builder()
                .practiceNoteId(practiceNote.getId())
                .practiceTitle(practiceNote.getTitle())
                .practiceCount(practiceNote.getPracticeCount())
                .lastSolvedAt(practiceNote.getLastSolvedAt())
                .build();
    }
}
