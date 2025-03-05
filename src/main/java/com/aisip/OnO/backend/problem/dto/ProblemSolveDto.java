package com.aisip.OnO.backend.problem.dto;

import lombok.*;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProblemSolveDto {
    private Long id;
    private String solveImageUrl;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
