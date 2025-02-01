package com.aisip.OnO.backend.converter;

import com.aisip.OnO.backend.Dto.Problem.ProblemPractice.ProblemPracticeResponseDto;
import com.aisip.OnO.backend.entity.Problem.Practice;

public class PracticeConverter {

    public static ProblemPracticeResponseDto convertToResponseDto(Practice practice) {
        if (practice == null) {
            return null;
        }

        return ProblemPracticeResponseDto.builder()
                .practiceId(practice.getId())
                .practiceTitle(practice.getTitle())
                .practiceCount(practice.getPracticeCount())
                .createdAt(practice.getCreatedAt())
                .lastSolvedAt(practice.getLastSolvedAt())
                .build();
    }
}
