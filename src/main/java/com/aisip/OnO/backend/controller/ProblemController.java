package com.aisip.OnO.backend.controller;

import com.aisip.OnO.backend.Dto.Problem.ProblemRegisterDto;
import com.aisip.OnO.backend.Dto.Problem.ProblemResponseDto;
import com.aisip.OnO.backend.exception.ProblemNotFoundException;
import com.aisip.OnO.backend.service.ProblemService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RequiredArgsConstructor
@RestController
@RequestMapping("/api")
public class ProblemController {

    private final ProblemService problemService;

    @GetMapping("/problem/{problemId}")
    public ResponseEntity<?> getProblemByUserId(
            Authentication authentication,
            @PathVariable("problemId") Long problemId
    ) {
        try {
            Long userId = (Long) authentication.getPrincipal();
            ProblemResponseDto problemResponseDto = problemService.findProblemByUserId(userId, problemId);
            return ResponseEntity.ok(problemResponseDto);
        } catch (ProblemNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        }
    }

    @GetMapping("/problems")
    public ResponseEntity<?> getProblemsByUserId(Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        List<ProblemResponseDto> problems = problemService.findAllProblemsByUserId(userId);
        return ResponseEntity.ok(problems);
    }

    @PostMapping("/problem")
    public ResponseEntity<?> registerProblem(
            Authentication authentication,
            @ModelAttribute ProblemRegisterDto problemRegisterDto
    ) {
        try {
            Long userId = (Long) authentication.getPrincipal();
            boolean isSaved = problemService.saveProblem(userId, problemRegisterDto);

            if (isSaved) {
                return ResponseEntity.ok().body("문제가 등록되었습니다.");
            } else {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("문제 등록에 실패했습니다.");
            }

        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(
                    "Error Processing Problem: " + e.getMessage()
            );
        }
    }

    @PatchMapping("/problem")
    public ResponseEntity<?> updateProblem(
            Authentication authentication, @ModelAttribute ProblemRegisterDto problemRegisterDto
    ) {
        try {
            Long userId = (Long) authentication.getPrincipal();
            boolean isUpdated = problemService.updateProblem(userId, problemRegisterDto);

            if (isUpdated) {
                return ResponseEntity.ok().body("문제가 수정되었습니다.");
            } else{
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("문제 등록에 실패했습니다.");
            }

        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(
                    "Error Processing Problem: " + e.getMessage()
            );
        }
    }

    @DeleteMapping("/problem")
    public ResponseEntity<?> deleteProblem(
            Authentication authentication,
            @RequestHeader("problemId") Long problemId
    ) {
        try {
            Long userId = (Long) authentication.getPrincipal();
            problemService.deleteProblem(userId, problemId);
            return ResponseEntity.ok("삭제를 완료했습니다.");
        } catch (ProblemNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(e.getMessage());
        }
    }
}