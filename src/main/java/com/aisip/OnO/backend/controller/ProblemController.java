package com.aisip.OnO.backend.controller;

import com.aisip.OnO.backend.Dto.Problem.ProblemRegisterDto;
import com.aisip.OnO.backend.Dto.Problem.ProblemResponseDto;
import com.aisip.OnO.backend.exception.ProblemNotFoundException;
import com.aisip.OnO.backend.service.ProblemService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api")
public class ProblemController {

    private final ProblemService problemService;

    @GetMapping("/problem/{problemId}")
    public ResponseEntity<?> getProblemByUserId(
            @RequestHeader("userId") Long userId,
            @PathVariable("problemId") Long problemId
    ) {
        try {
            ProblemResponseDto problemResponseDto = problemService.findProblemByUserId(userId, problemId);
            return ResponseEntity.ok(problemResponseDto);
        } catch (ProblemNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        }
    }

    @GetMapping("/problems")
    public ResponseEntity<?> getProblemsByUserId(@RequestHeader("userId") Long userId){
        List<ProblemResponseDto> problems = problemService.findAllProblemsByUserId(userId);
        return ResponseEntity.ok(problems);
    }

    @PostMapping("/problem")
    public ResponseEntity<?> registerProblem(
            @RequestHeader("userId") Long userId,
            @RequestBody ProblemRegisterDto problemRegisterDto
    ) {
        ProblemResponseDto savedProblem = problemService.saveProblem(userId, problemRegisterDto);
        return ResponseEntity.ok().body(savedProblem);
    }

    @PutMapping("/problem/{problemId}")
    public ResponseEntity<?> updateProblem(
            @RequestHeader("userId") Long userId,
            @PathVariable("problemId") Long problemId,
            @RequestBody ProblemRegisterDto problemRegisterDto
    ) {
        try {
            ProblemResponseDto updatedProblem = problemService.updateProblem(userId, problemId, problemRegisterDto);
            return ResponseEntity.ok(updatedProblem);
        } catch (ProblemNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        }
    }

    @DeleteMapping("/problem")
    public ResponseEntity<?> deleteProblem(
            @RequestHeader("userId") Long userId,
            @RequestHeader("problemId") Long problemId
    ) {
        try {
            problemService.deleteProblem(userId, problemId);
            return ResponseEntity.ok("삭제를 완료했습니다.");
        } catch (ProblemNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(e.getMessage());
        }
    }
}