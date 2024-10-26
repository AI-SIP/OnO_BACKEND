package com.aisip.OnO.backend.Dto.Problem.ProblemPractice;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProblemPracticeRegisterDto {

    private Long practiceCount;

    private String title;

    private List<Long> problemIds;
}
