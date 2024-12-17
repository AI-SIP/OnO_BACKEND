package com.aisip.OnO.backend.controller;

import com.aisip.OnO.backend.Dto.Problem.ProblemPractice.ProblemPracticeRegisterDto;
import com.aisip.OnO.backend.Dto.Problem.ProblemPractice.ProblemPracticeResponseDto;
import com.aisip.OnO.backend.Dto.Problem.ProblemResponseDto;
import com.aisip.OnO.backend.exception.ProblemPracticeNotFoundException;
import com.aisip.OnO.backend.service.ProblemPracticeService;
import io.sentry.Sentry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/problem/practice")
public class ProblemPracticeController {

    //private final ProblemService problemService;

    private final ProblemPracticeService problemPracticeService;

    @GetMapping("/{practiceId}")
    public ResponseEntity<?> getPracticeDetail(
            Authentication authentication,
            @PathVariable("practiceId") Long practiceId
    ) {
        try {
            Long userId = (Long) authentication.getPrincipal();

            ProblemPracticeResponseDto problemPracticeResponseDto = problemPracticeService.findPractice(practiceId);

            log.info("userId: " + userId + " get problem  practice for practice id: " + practiceId);
            return ResponseEntity.ok(problemPracticeResponseDto);
        } catch (Exception e) {
            log.error(e.getMessage());
            Sentry.captureException(e);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        }
    }

    @GetMapping("/thumbnail/all")
    public ResponseEntity<?> getAllPracticeThumbnail(
            Authentication authentication
    ) {
        try {
            Long userId = (Long) authentication.getPrincipal();
            List<ProblemPracticeResponseDto> problemPracticeResponseDtoList = problemPracticeService.findAllPracticesByUser(userId);

            log.info("userId: " + userId + " get all problem practice");
            return ResponseEntity.ok(problemPracticeResponseDtoList);
        } catch (Exception e) {
            log.error(e.getMessage());
            Sentry.captureException(e);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        }
    }

    @GetMapping("/all")
    public ResponseEntity<?> getAllPracticeDetail(
            Authentication authentication
    ) {
        try {
            Long userId = (Long) authentication.getPrincipal();
            List<ProblemPracticeResponseDto> problemPracticeResponseDtoList = problemPracticeService.findAllPracticesByUser(userId);

            log.info("userId: " + userId + " get all problem practice");
            return ResponseEntity.ok(problemPracticeResponseDtoList);
        } catch (Exception e) {
            log.error(e.getMessage());
            Sentry.captureException(e);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        }
    }

    @PostMapping("")
    public ResponseEntity<?> registerPractice(
            Authentication authentication,
            @RequestBody ProblemPracticeRegisterDto problemPracticeRegisterDto
            ) {
        try {
            Long userId = (Long) authentication.getPrincipal();
            boolean isSaved = problemPracticeService.createPractice(userId, problemPracticeRegisterDto);

            if(isSaved){
                log.info("userId: " + userId + " register problem  practice");
                return ResponseEntity.ok("복습 리스트 생성을 완료했습니다.");
            } else{
                throw new ProblemPracticeNotFoundException("복습 리스트 생성 과정에서 문제가 발생했습니다.");
            }
        } catch (Exception e) {
            log.error(e.getMessage());
            Sentry.captureException(e);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        }
    }

    @PatchMapping("/complete/{practiceId}")
    public ResponseEntity<?> addPracticeCount(
            Authentication authentication,
            @PathVariable("practiceId") Long practiceId
    ) {
        try {
            Long userId = (Long) authentication.getPrincipal();
            boolean isUpdated = problemPracticeService.addPracticeCount(practiceId);

            if(isUpdated){
                log.info("userId: " + userId + " complete problem practice");
                return ResponseEntity.ok("복습을 완료했습니다.");
            } else{
                throw new ProblemPracticeNotFoundException("복습 완료에 실패했습니다.");
            }
        } catch (Exception e) {
            log.error(e.getMessage());
            Sentry.captureException(e);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        }
    }

    @PatchMapping("")
    public ResponseEntity<?> updatePractice(
            Authentication authentication,
            @RequestBody ProblemPracticeRegisterDto problemPracticeRegisterDto
    ) {
        try {
            Long userId = (Long) authentication.getPrincipal();
            boolean isUpdated = problemPracticeService.updatePractice(problemPracticeRegisterDto);

            if(isUpdated){
                log.info("userId: " + userId + " register problem  practice");
                return ResponseEntity.ok("복습 리스트 수정을 완료했습니다.");
            } else{
                throw new ProblemPracticeNotFoundException("복습 리스트 생성 과정에서 문제가 발생했습니다.");
            }
        } catch (Exception e) {
            log.error(e.getMessage());
            Sentry.captureException(e);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        }
    }

    @DeleteMapping("")
    public ResponseEntity<?> deletePractices(
            Authentication authentication,
            @RequestParam List<Long> deletePracticeIds
    ) {
        try {
            Long userId = (Long) authentication.getPrincipal();
            problemPracticeService.deletePractices(deletePracticeIds);

            log.info("userId: " + userId + " delete problem  practice ids: " + deletePracticeIds.toString());
            return ResponseEntity.ok("복습 리스트 삭제를 완료했습니다.");
        } catch (Exception e) {
            log.error(e.getMessage());
            Sentry.captureException(e);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        }
    }
}
