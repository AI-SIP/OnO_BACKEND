package com.aisip.OnO.backend.Dto.Problem.ProblemPractice;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProblemPracticeResponseDto {

    private Long id;

    private String title;

    private Long practiceCount;

    private Long practiceSize;
}
