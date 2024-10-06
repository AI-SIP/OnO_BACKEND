package com.aisip.OnO.backend.converter;

import com.aisip.OnO.backend.Dto.Problem.ProblemRepeatDto;
import com.aisip.OnO.backend.Dto.Problem.ProblemResponseDto;
import com.aisip.OnO.backend.entity.Image.ImageData;
import com.aisip.OnO.backend.entity.Problem.Problem;
import com.aisip.OnO.backend.entity.Problem.ProblemRepeat;
import com.aisip.OnO.backend.entity.Problem.TemplateType;

import java.util.List;
import java.util.stream.Collectors;

public class ProblemConverter {

    public static ProblemResponseDto convertToResponseDto(Problem problem, List<ImageData> images, List<ProblemRepeat> repeats) {
        if (problem == null) {
            return null;
        }

        ProblemResponseDto problemResponseDto = ProblemResponseDto.builder()
                .problemId(problem.getId())
                .memo(problem.getMemo())
                .reference(problem.getReference())
                .solvedAt(problem.getSolvedAt())
                .createdAt(problem.getCreatedAt())
                .updateAt(problem.getUpdatedAt())
                .folderId(problem.getFolder().getId())
                .analysis(problem.getAnalysis())
                .repeats(repeats.stream().map(problemRepeat -> ProblemRepeatDto.builder()
                            .id(problemRepeat.getId())
                            .createdAt(problemRepeat.getCreatedAt())
                            .updatedAt(problemRepeat.getUpdatedAt())
                            .build()
                ).collect(Collectors.toList()))
                .templateType(problem.getTemplateType() != null ? problem.getTemplateType().getCode() : TemplateType.SIMPLE_TEMPLATE.getCode())
                .build();

        for (ImageData image : images) {
            switch (image.getImageType()) {
                case PROBLEM_IMAGE -> problemResponseDto.setProblemImageUrl(image.getImageUrl());
                case PROCESS_IMAGE -> problemResponseDto.setProcessImageUrl(image.getImageUrl());
                case ANSWER_IMAGE -> problemResponseDto.setAnswerImageUrl(image.getImageUrl());
                case SOLVE_IMAGE -> problemResponseDto.setSolveImageUrl(image.getImageUrl());
            }
        }

        return problemResponseDto;
    }
}
