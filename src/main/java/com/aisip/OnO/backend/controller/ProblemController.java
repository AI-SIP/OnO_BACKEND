package com.aisip.OnO.backend.controller;

import com.aisip.OnO.backend.Dto.Problem.ProblemRegisterDto;
import com.aisip.OnO.backend.Dto.Problem.ProblemResponseDto;
import com.aisip.OnO.backend.exception.ProblemNotFoundException;
import com.aisip.OnO.backend.service.ProblemService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.coyote.Response;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
            @ModelAttribute ProblemRegisterDto problemRegisterDto
    ) {
        try {
            System.out.println(problemRegisterDto.toString());
            boolean isSaved = problemService.saveProblem(userId, problemRegisterDto);

            if(isSaved){
                return ResponseEntity.ok().body("문제가 등록되었습니다.");
            } else{
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("문제 등록에 실패했습니다.");
            }

        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(
                    "Error Processing Problem: " + e.getMessage()
            );
        }
    }

    /*
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
     */

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