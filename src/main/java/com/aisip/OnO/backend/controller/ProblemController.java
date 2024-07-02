package com.aisip.OnO.backend.controller;

import com.aisip.OnO.backend.Dto.Problem.ProblemRegisterDto;
import com.aisip.OnO.backend.Dto.Problem.ProblemResponseDto;
import com.aisip.OnO.backend.service.ProblemService;
import lombok.RequiredArgsConstructor;
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

    @GetMapping("/problems")
    public ResponseEntity<?> getProblemsByUserId(@RequestHeader("userId") Long userId){
        List<ProblemResponseDto> problems = problemService.findAllProblemsByUserId(userId);
        return ResponseEntity.ok(problems);
    }
}
