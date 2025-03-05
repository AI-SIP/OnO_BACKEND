package com.aisip.OnO.backend.fileupload.controller;

import com.aisip.OnO.backend.fileupload.dto.FileUploadRegisterDto;
import com.aisip.OnO.backend.fileupload.service.FileUploadService;
import com.aisip.OnO.backend.problem.entity.ProblemImageType;
import com.aisip.OnO.backend.problem.entity.Problem;
import com.aisip.OnO.backend.problem.service.ProblemService;
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
public class FileUploadController {

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
        String problemImageUrl = fileUploadService.uploadFileToS3(problemImage, problem, ProblemImageType.PROBLEM_IMAGE);

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
            @RequestBody FileUploadRegisterDto fileUploadRegisterDto
    ) {
        String processImageUrl = fileUploadService.getProcessImage(fileUploadRegisterDto);

        return Map.of("processImageUrl", processImageUrl);
    }
}
