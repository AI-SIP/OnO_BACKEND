package com.aisip.OnO.backend.Dto.Problem;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
//@JsonInclude(JsonInclude.Include.NON_NULL) // NULL 값이 아닌 필드만 포함
public class ProblemResponseDto {

    private Long problemId;

    private String imageUrl;

    private String processImageUrl;

    private String answerImageUrl;

    private String solveImageUrl;

    private String memo;

    private String reference;

    private LocalDate solvedAt;
}
