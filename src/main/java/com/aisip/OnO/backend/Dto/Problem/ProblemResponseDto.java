package com.aisip.OnO.backend.Dto.Problem;

import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProblemResponseDto {

    private Long problemId;

    private String userName;

    private String problemImageUrl;

    private String processImageUrl;

    private String answerImageUrl;

    private String solveImageUrl;

    private String memo;

    private Long folderId;

    private String reference;

    private String analysis;

    private List<ProblemRepeatDto> repeats;

    @Enumerated(EnumType.STRING)
    private Long templateType;

    private LocalDateTime solvedAt;

    private LocalDateTime createdAt;

    private LocalDateTime updateAt;
}

