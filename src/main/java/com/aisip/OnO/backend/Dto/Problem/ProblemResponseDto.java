package com.aisip.OnO.backend.Dto.Problem;

import lombok.*;

import java.time.LocalDateTime;

@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
//@JsonInclude(JsonInclude.Include.NON_NULL) // NULL 값이 아닌 필드만 포함
public class ProblemResponseDto {

    private Long problemId;

    private String problemImageUrl;

    private String processImageUrl;

    private String answerImageUrl;

    private String solveImageUrl;

    private String memo;

    private String reference;

    private LocalDateTime solvedAt;
}
