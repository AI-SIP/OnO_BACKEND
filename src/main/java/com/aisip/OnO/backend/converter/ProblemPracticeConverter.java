package com.aisip.OnO.backend.converter;

import com.aisip.OnO.backend.Dto.Problem.ProblemPractice.ProblemPracticeResponseDto;
import com.aisip.OnO.backend.entity.Problem.ProblemPractice;

public class ProblemPracticeConverter {

    public static ProblemPracticeResponseDto convertToResponseDto(ProblemPractice problemPractice, Long practiceSize) {
        if (problemPractice == null) {
            return null;
        }

        return ProblemPracticeResponseDto.builder()
                .practiceId(problemPractice.getId())
                .practiceTitle(problemPractice.getTitle())
                .practiceCount(problemPractice.getPracticeCount())
                .practiceSize(practiceSize)
                .createdAt(problemPractice.getCreatedAt())
                .lastSolvedAt(problemPractice.getLastSolvedAt())
                .build();
    }
}
