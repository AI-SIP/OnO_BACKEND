package com.aisip.OnO.backend.problem;

import com.aisip.OnO.backend.problem.dto.ProblemSolveDto;
import com.aisip.OnO.backend.problem.entity.ProblemSolve;
import com.aisip.OnO.backend.problem.dto.ProblemResponseDto;
import com.aisip.OnO.backend.problem.entity.ProblemImageData;
import com.aisip.OnO.backend.problem.entity.ProblemTemplateType;
import com.aisip.OnO.backend.problem.entity.Problem;

import java.util.List;
import java.util.stream.Collectors;

public class ProblemConverter {

    public static ProblemResponseDto convertToResponseDto(Problem problem, List<ProblemImageData> images, List<ProblemSolve> repeats) {
        if (problem == null) {
            return null;
        }

        ProblemResponseDto problemResponseDto = ProblemResponseDto.builder()
                .userName(problem.getUser() != null ? problem.getUser().getName() : "유저 없음")
                .problemId(problem.getId())
                .memo(problem.getMemo())
                .reference(problem.getReference())
                .solvedAt(problem.getSolvedAt() != null ? problem.getSolvedAt() : problem.getCreatedAt())
                .createdAt(problem.getCreatedAt())
                .updateAt(problem.getUpdatedAt())
                .folderId(problem.getFolder() != null ? problem.getFolder().getId() : null)
                .analysis(problem.getAnalysis())
                .repeats(repeats.stream().map(problemSolve -> ProblemSolveDto.builder()
                            .id(problemSolve.getId())
                            .solveImageUrl(problemSolve.getSolveImageUrl() != null ? problemSolve.getSolveImageUrl() : null)
                            .createdAt(problemSolve.getCreatedAt())
                            .updatedAt(problemSolve.getUpdatedAt())
                            .build()
                ).collect(Collectors.toList()))
                .templateType(problem.getProblemTemplateType() != null ? problem.getProblemTemplateType().getCode() : ProblemTemplateType.SIMPLE_TEMPLATE.getCode())
                .build();

        for (ProblemImageData image : images) {
            switch (image.getProblemImageType()) {
                case PROBLEM_IMAGE -> problemResponseDto.setProblemImageUrl(image.getImageUrl());
                case PROCESS_IMAGE -> problemResponseDto.setProcessImageUrl(image.getImageUrl());
                case ANSWER_IMAGE -> problemResponseDto.setAnswerImageUrl(image.getImageUrl());
                case SOLVE_IMAGE -> problemResponseDto.setSolveImageUrl(image.getImageUrl());
            }
        }

        return problemResponseDto;
    }
}
