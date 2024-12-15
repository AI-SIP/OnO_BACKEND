package com.aisip.OnO.backend.Dto.Problem;

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

    private String problemImageUrl;

    private String processImageUrl;

    private MultipartFile answerImage;

    private MultipartFile solveImage;

    private String memo;

    private String reference;

    private Long folderId;

    private Long templateType;

    private String analysis;

    private LocalDateTime solvedAt;
}

