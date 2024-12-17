package com.aisip.OnO.backend.converter;

import com.aisip.OnO.backend.Dto.Problem.ProblemPractice.ProblemPracticeResponseDto;
import com.aisip.OnO.backend.entity.Problem.ProblemPractice;

import java.util.List;

public class ProblemPracticeConverter {

    public static ProblemPracticeResponseDto convertToResponseDto(ProblemPractice problemPractice) {
        if (problemPractice == null) {
            return null;
        }

        return ProblemPracticeResponseDto.builder()
                .practiceId(problemPractice.getId())
                .practiceTitle(problemPractice.getTitle())
                .practiceCount(problemPractice.getPracticeCount())
                .createdAt(problemPractice.getCreatedAt())
                .lastSolvedAt(problemPractice.getLastSolvedAt())
                .build();
    }
}
