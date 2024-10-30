package com.aisip.OnO.backend.Dto.Problem.ProblemPractice;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProblemPracticeResponseDto {

    private Long practiceId;

    private String practiceTitle;

    private Long practiceCount;

    private Long practiceSize;

    private List<Long> problemIds;

    private LocalDateTime lastSolvedAt;

    private LocalDateTime createdAt;
}
