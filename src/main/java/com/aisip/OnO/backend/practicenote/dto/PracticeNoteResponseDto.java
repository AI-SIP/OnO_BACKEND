package com.aisip.OnO.backend.practicenote.dto;
import com.aisip.OnO.backend.problem.dto.ProblemResponseDto;
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
public class PracticeNoteResponseDto {

    private Long practiceId;

    private String practiceTitle;

    private Long practiceCount;

    private Long practiceSize;

    private List<ProblemResponseDto> problems;

    //private List<Long> problemIds;

    private LocalDateTime lastSolvedAt;

    private LocalDateTime createdAt;
}
