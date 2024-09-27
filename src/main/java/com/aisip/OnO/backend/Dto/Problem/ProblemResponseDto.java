package com.aisip.OnO.backend.Dto.Problem;

import com.aisip.OnO.backend.entity.Problem.TemplateType;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.*;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProblemResponseDto {

    private Long problemId;

    private String problemImageUrl;

    private String processImageUrl;

    private String answerImageUrl;

    private String solveImageUrl;

    private String memo;

    private String reference;

    private String analysis;

    @Enumerated(EnumType.STRING)
    private TemplateType templateType;

    private LocalDateTime solvedAt;

    private LocalDateTime createdAt;

    private LocalDateTime updateAt;
}
