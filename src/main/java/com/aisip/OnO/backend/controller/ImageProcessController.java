package com.aisip.OnO.backend.controller;

import com.aisip.OnO.backend.exception.ProblemNotFoundException;
import com.aisip.OnO.backend.service.ProblemService;
import io.sentry.Sentry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/process")
public class ImageProcessController {

    private final ProblemService problemService;

    @PostMapping("/problemImage")
    public ResponseEntity<?> registerProblemImage(
            Authentication authentication,
            @RequestParam("problemImage") MultipartFile problemImage
    ) {
        try {
            Long userId = (Long) authentication.getPrincipal();
            return ResponseEntity.ok("ok");
        } catch (ProblemNotFoundException e) {
            log.error(e.getMessage());
            Sentry.captureException(e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("이미지 등록에 실패했습니다.");
        }
    }

    @GetMapping("/analysis")
    public ResponseEntity<?> getProblemAnalysis(
            Authentication authentication
    ) {
        try {
            Long userId = (Long) authentication.getPrincipal();
            return ResponseEntity.ok("ok");
        } catch (ProblemNotFoundException e) {
            log.error(e.getMessage());
            Sentry.captureException(e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("이미지 분석에 실패했습니다.");
        }
    }

    @GetMapping("/processImage")
    public ResponseEntity<?> getProcessImage(
            Authentication authentication
    ) {
        try {
            Long userId = (Long) authentication.getPrincipal();
            return ResponseEntity.ok("ok");
        } catch (ProblemNotFoundException e) {
            log.error(e.getMessage());
            Sentry.captureException(e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("이미지 보정에 실패했습니다.");
        }
    }
}
