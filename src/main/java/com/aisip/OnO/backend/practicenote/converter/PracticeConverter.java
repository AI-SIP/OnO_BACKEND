package com.aisip.OnO.backend.practicenote.converter;

import com.aisip.OnO.backend.practicenote.entity.PracticeNote;
import com.aisip.OnO.backend.practicenote.dto.PracticeNoteResponseDto;

public class PracticeConverter {

    public static PracticeNoteResponseDto convertToResponseDto(PracticeNote practiceNote) {
        if (practiceNote == null) {
            return null;
        }

        return PracticeNoteResponseDto.builder()
                .practiceId(practiceNote.getId())
                .practiceTitle(practiceNote.getTitle())
                .practiceCount(practiceNote.getPracticeCount())
                .createdAt(practiceNote.getCreatedAt())
                .lastSolvedAt(practiceNote.getLastSolvedAt())
                .build();
    }
}
