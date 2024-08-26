package com.aisip.OnO.backend.Dto.Problem;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
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

    private LocalDateTime solvedAt;
}
