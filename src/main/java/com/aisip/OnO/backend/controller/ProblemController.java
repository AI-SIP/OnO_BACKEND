package com.aisip.OnO.backend.controller;

import com.aisip.OnO.backend.Dto.Problem.ProblemRegisterDto;
import com.aisip.OnO.backend.Dto.Problem.ProblemResponseDto;
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

    @PostMapping("/problem")
    public ResponseEntity<?> registerProblem(
            @RequestHeader("userId") Long userId,
            @RequestBody ProblemRegisterDto problemRegisterDto
    ) {
        ProblemResponseDto savedProblem = problemService.saveProblem(userId, problemRegisterDto);
        return ResponseEntity.ok().body(savedProblem);
    }

    @DeleteMapping("/problem")
    public ResponseEntity<?> deleteProblem(
            @RequestHeader("userId") Long userId,
            @RequestHeader("problemId") Long problemId
    ) {
        boolean status = problemService.deleteProblem(userId, problemId);

        if(status){
            return ResponseEntity.ok("삭제를 완료했습니다.");
        } else{
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("Problem not found or you do not have permission to delete this problem.");
        }
    }

    @GetMapping("/problems")
    public ResponseEntity<?> getProblemsByUserId(@RequestHeader("userId") Long userId){
        List<ProblemResponseDto> problems = problemService.findAllProblemsByUserId(userId);
        return ResponseEntity.ok(problems);
    }
}
