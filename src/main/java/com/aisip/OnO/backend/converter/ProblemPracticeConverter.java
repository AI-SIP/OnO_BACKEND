package com.aisip.OnO.backend.converter;

import com.aisip.OnO.backend.Dto.Problem.ProblemPracticeResponseDto;
import com.aisip.OnO.backend.entity.Problem.Problem;
import com.aisip.OnO.backend.entity.Problem.ProblemPractice;

import java.util.List;
import java.util.stream.Collectors;

public class ProblemPracticeConverter {

    public static ProblemPracticeResponseDto convertToResponseDto(ProblemPractice problemPractice, boolean isThumbnail) {
        if (problemPractice == null) {
            return null;
        }

        ProblemPracticeResponseDto problemPracticeResponseDto = ProblemPracticeResponseDto.builder()
                .title(problemPractice.getTitle())
                .practiceCount(problemPractice.getPracticeCount())
                .build();

        if(!isThumbnail){

        }

        return problemPracticeResponseDto;
    }
}
