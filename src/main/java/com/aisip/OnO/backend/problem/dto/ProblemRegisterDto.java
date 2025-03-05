package com.aisip.OnO.backend.problem.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProblemRegisterDto {

    private Long problemId;

    private MultipartFile problemImage;

    private MultipartFile answerImage;

    private MultipartFile solveImage;

    private String memo;

    private String reference;

    private Long folderId;

    private LocalDateTime solvedAt;
}

