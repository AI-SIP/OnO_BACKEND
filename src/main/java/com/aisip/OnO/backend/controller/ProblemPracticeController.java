package com.aisip.OnO.backend.controller;

import com.aisip.OnO.backend.Dto.Problem.ProblemPractice.ProblemPracticeResponseDto;
import com.aisip.OnO.backend.Dto.Problem.ProblemResponseDto;
import com.aisip.OnO.backend.exception.ProblemNotFoundException;
import com.aisip.OnO.backend.service.ProblemPracticeService;
import com.aisip.OnO.backend.service.ProblemService;
import io.sentry.Sentry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/problem/practice")
public class ProblemPracticeController {

    private final ProblemService problemService;

    private final ProblemPracticeService problemPracticeService;

    @GetMapping("/{practiceId}")
    public ResponseEntity<?> getProblemPracticeDetail(
            Authentication authentication,
            @PathVariable("practiceId") Long practiceId
    ) {
        try {
            Long userId = (Long) authentication.getPrincipal();
            List<ProblemResponseDto> problems = problemService.findAllProblemsByPracticeId(practiceId);

            log.info("userId: " + userId + " get problem  practice for practice id: " + practiceId);
            return ResponseEntity.ok(problems);
        } catch (ProblemNotFoundException e) {
            log.error(e.getMessage());
            Sentry.captureException(e);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        }
    }

    @GetMapping("/all")
    public ResponseEntity<?> getProblemPracticeDetail(
            Authentication authentication
    ) {
        try {
            Long userId = (Long) authentication.getPrincipal();
            List<ProblemPracticeResponseDto> problemPracticeResponseDtoList = problemPracticeService.findAllPracticeByUser(userId);

            log.info("userId: " + userId + " get all problem practice");
            return ResponseEntity.ok(problemPracticeResponseDtoList);
        } catch (ProblemNotFoundException e) {
            log.error(e.getMessage());
            Sentry.captureException(e);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        }
    }
}
