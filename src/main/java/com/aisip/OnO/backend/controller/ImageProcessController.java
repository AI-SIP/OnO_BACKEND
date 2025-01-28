package com.aisip.OnO.backend.controller;

import com.aisip.OnO.backend.Dto.Process.ImageProcessRegisterDto;
import com.aisip.OnO.backend.entity.Image.ImageType;
import com.aisip.OnO.backend.entity.Problem.Problem;
import com.aisip.OnO.backend.service.FileUploadService;
import com.aisip.OnO.backend.service.ProblemService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/process")
public class ImageProcessController {

    private final ProblemService problemService;

    private final FileUploadService fileUploadService;

    @ResponseStatus(HttpStatus.OK)
    @PostMapping("/problemImage")
    public Map<String, Object> registerProblemImage(
            Authentication authentication,
            @RequestParam("problemImage") MultipartFile problemImage
    ) {
        Long userId = (Long) authentication.getPrincipal();
        Problem problem = problemService.createProblem(userId);
        String problemImageUrl = fileUploadService.uploadFileToS3(problemImage, problem, ImageType.PROBLEM_IMAGE);

        return Map.of("problemId", problem.getId(), "problemImageUrl", problemImageUrl);
    }

    // ✅ 이미지 분석 요청
    @ResponseStatus(HttpStatus.OK)
    @PostMapping("/analysis")
    public Map<String, String> getProblemAnalysis(
            Authentication authentication,
            @RequestBody Map<String, String> requestBody
    ) {
        String problemImageUrl = requestBody.get("problemImageUrl");
        String analysisResult = fileUploadService.getProblemAnalysis(problemImageUrl);

        return Map.of("analysis", analysisResult);
    }

    // ✅ 이미지 보정 요청
    @ResponseStatus(HttpStatus.OK)
    @PostMapping("/processImage")
    public Map<String, String> getProcessImage(
            Authentication authentication,
            @RequestBody ImageProcessRegisterDto imageProcessRegisterDto
    ) {
        String processImageUrl = fileUploadService.getProcessImage(imageProcessRegisterDto);

        return Map.of("processImageUrl", processImageUrl);
    }
}
