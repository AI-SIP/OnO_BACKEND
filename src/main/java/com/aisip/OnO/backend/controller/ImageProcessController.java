package com.aisip.OnO.backend.controller;

import com.aisip.OnO.backend.Dto.Process.ImageProcessRegisterDto;
import com.aisip.OnO.backend.entity.Image.ImageType;
import com.aisip.OnO.backend.entity.Problem.Problem;
import com.aisip.OnO.backend.exception.ProblemNotFoundException;
import com.aisip.OnO.backend.service.FileUploadService;
import com.aisip.OnO.backend.service.ProblemService;
import io.sentry.Sentry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/process")
public class ImageProcessController {

    private final ProblemService problemService;

    private final FileUploadService fileUploadService;

    @PostMapping("/problemImage")
    public ResponseEntity<?> registerProblemImage(
            Authentication authentication,
            @RequestParam("problemImage") MultipartFile problemImage
    ) {
        try {
            Long userId = (Long) authentication.getPrincipal();
            Problem problem = problemService.createProblem(userId);

            String problemImageUrl = fileUploadService.uploadFileToS3(problemImage, problem, ImageType.PROBLEM_IMAGE);

            return ResponseEntity.ok(Map.of("problemId", problem.getId(), "problemImageUrl", problemImageUrl));
        } catch (ProblemNotFoundException | IOException e) {
            log.error(e.getMessage());
            Sentry.captureException(e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("이미지 등록에 실패했습니다.");
        }
    }

    @PostMapping("/analysis")
    public ResponseEntity<?> getProblemAnalysis(
            Authentication authentication,
            @RequestBody Map<String, String> requestBody
    ) {
        try {
            String problemImageUrl = requestBody.get("problemImageUrl");
            String analysisResult = fileUploadService.getProblemAnalysis(problemImageUrl);

            return ResponseEntity.ok(Map.of("analysis", analysisResult));
        } catch (Exception e) {
            log.error(e.getMessage());
            Sentry.captureException(e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("이미지 분석에 실패했습니다.");
        }
    }

    @PostMapping("/processImage")
    public ResponseEntity<?> getProcessImage(
            Authentication authentication,
            @RequestBody ImageProcessRegisterDto imageProcessRegisterDto
            ) {
        try {
            String processImageUrl = fileUploadService.getProcessImageUrl(imageProcessRegisterDto);

            return ResponseEntity.ok(Map.of("processImageUrl", processImageUrl));
        } catch (Exception e) {
            log.error(e.getMessage());
            Sentry.captureException(e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("이미지 보정에 실패했습니다.");
        }
    }
}
