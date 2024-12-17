package com.aisip.OnO.backend.controller;

import com.aisip.OnO.backend.Dto.Problem.ProblemRegisterDto;
import com.aisip.OnO.backend.Dto.Problem.ProblemResponseDto;
import com.aisip.OnO.backend.exception.ProblemNotFoundException;
import com.aisip.OnO.backend.exception.ProblemRegisterException;
import com.aisip.OnO.backend.service.ProblemPracticeService;
import com.aisip.OnO.backend.service.ProblemService;
import io.sentry.Sentry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.propertyeditors.CustomNumberEditor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/problem")
public class ProblemController {

    @InitBinder
    public void initBinder(WebDataBinder binder) {
        binder.registerCustomEditor(Long.class, new CustomNumberEditor(Long.class, true));
    }

    private final ProblemService problemService;

    private final ProblemPracticeService problemPracticeService;

    @GetMapping("/{problemId}")
    public ResponseEntity<?> getProblem(
            Authentication authentication,
            @PathVariable("problemId") Long problemId
    ) {
        try {
            Long userId = (Long) authentication.getPrincipal();
            ProblemResponseDto problemResponseDto = problemService.findProblem(userId, problemId);

            log.info("userId: " + userId + " get problem for problemId: " + problemResponseDto.getProblemId());
            return ResponseEntity.ok(problemResponseDto);
        } catch (ProblemNotFoundException e) {
            log.error(e.getMessage());
            Sentry.captureException(e);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        }
    }

    @GetMapping("/all")
    public ResponseEntity<?> getProblemsByUserId(Authentication authentication) {
        try {
            Long userId = (Long) authentication.getPrincipal();
            List<ProblemResponseDto> problems = problemService.findUserProblems(userId);

            log.info("userId: " + userId + " get all problems");
            return ResponseEntity.ok(problems);
        } catch (Exception e) {
            log.error(e.getMessage());
            Sentry.captureException(e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("문제 조회에 실패했습니다.");
        }
    }

    @PostMapping("")
    public ResponseEntity<?> registerProblem(
            Authentication authentication,
            @ModelAttribute ProblemRegisterDto problemRegisterDto
    ) {
        try {
            Long userId = (Long) authentication.getPrincipal();
            boolean isSaved = problemService.saveProblem(userId, problemRegisterDto);

            if (isSaved) {
                log.info("userId: " + userId + " success for register problem");
                return ResponseEntity.ok().body("문제가 등록되었습니다.");
            } else {
                throw new ProblemRegisterException("userId: " + userId + " failed for register problem");
            }

        } catch (Exception e) {
            log.error(e.getMessage());
            Sentry.captureException(e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    "Error Register Problem: " + e.getMessage()
            );
        }
    }

    @PostMapping("/V2")
    public ResponseEntity<?> registerProblemV2(
            Authentication authentication,
            @ModelAttribute ProblemRegisterDto problemRegisterDto
    ) {
        try {
            Long userId = (Long) authentication.getPrincipal();
            boolean isSaved = problemService.saveProblem(userId, problemRegisterDto);

            if (isSaved) {
                log.info("userId: " + userId + " success for register problem");
                return ResponseEntity.ok().body("문제가 등록되었습니다.");
            } else {
                throw new ProblemRegisterException("userId: " + userId + " failed for register problem");
            }

        } catch (Exception e) {
            log.error(e.getMessage());
            Sentry.captureException(e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    "Error Register Problem: " + e.getMessage()
            );
        }
    }

    @PatchMapping("")
    public ResponseEntity<?> updateProblem(
            Authentication authentication, @ModelAttribute ProblemRegisterDto problemRegisterDto
    ) {
        try {

            Long userId = (Long) authentication.getPrincipal();
            boolean isUpdated = problemService.updateProblem(userId, problemRegisterDto);

            if (isUpdated) {
                log.info("userId: " + userId + " success for update problem");
                return ResponseEntity.ok().body("문제가 수정되었습니다.");
            } else {
                throw new ProblemRegisterException("userId: " + userId + " failed for update problem");
            }

        } catch (Exception e) {
            log.error(e.getMessage());
            Sentry.captureException(e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    "Error Processing Problem: " + e.getMessage()
            );
        }
    }

    @DeleteMapping("")
    public ResponseEntity<?> deleteProblem(
            Authentication authentication,
            @RequestHeader("problemId") Long problemId
    ) {
        try {
            Long userId = (Long) authentication.getPrincipal();
            problemPracticeService.deleteProblemFromAllPractice(problemId);
            problemService.deleteProblem(userId, problemId);

            log.info("userId: " + userId + " success for delete problem");
            return ResponseEntity.ok("삭제를 완료했습니다.");
        } catch (Exception e) {
            log.warn(e.getMessage());
            Sentry.captureException(e);
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(e.getMessage());
        }
    }

    @PostMapping("/repeat")
    public ResponseEntity<?> addRepeatCount(
            Authentication authentication,
            @RequestHeader("problemId") Long problemId,
            @RequestParam(value = "solveImage", required = false) MultipartFile solveImage
            ){
        try{
            Long userId = (Long) authentication.getPrincipal();
            problemService.addRepeatCount(problemId, solveImage);

            log.info("userId: " + userId + " repeat problemId : " + problemId);
            return ResponseEntity.ok("삭제를 완료했습니다.");
        } catch (Exception e) {
            log.warn(e.getMessage());
            Sentry.captureException(e);
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(e.getMessage());
        }
    }
}