package com.aisip.OnO.backend.controller;

import com.aisip.OnO.backend.Dto.Problem.ProblemRegisterDto;
import com.aisip.OnO.backend.Dto.Problem.ProblemResponseDto;
import com.aisip.OnO.backend.service.PracticeService;
import com.aisip.OnO.backend.service.ProblemService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.propertyeditors.CustomNumberEditor;
import org.springframework.http.HttpStatus;
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
    private final PracticeService practiceService;

    // ✅ 특정 문제 조회
    @ResponseStatus(HttpStatus.OK)
    @GetMapping("/{problemId}")
    public ProblemResponseDto getProblem(Authentication authentication, @PathVariable Long problemId) {
        Long userId = (Long) authentication.getPrincipal();
        ProblemResponseDto problemResponseDto = problemService.findProblem(userId, problemId);

        log.info("userId: {} get problem for problemId: {}", userId, problemResponseDto.getProblemId());
        return problemResponseDto;
    }

    // ✅ 유저가 등록한 모든 문제 조회
    @ResponseStatus(HttpStatus.OK)
    @GetMapping("/all")
    public List<ProblemResponseDto> getProblemsByUserId(Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        log.info("userId: {} get all problems", userId);
        return problemService.findUserProblems(userId);
    }

    // ✅ 문제 등록 (V1)
    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping("")
    public String registerProblem(Authentication authentication, @ModelAttribute ProblemRegisterDto problemRegisterDto) {
        Long userId = (Long) authentication.getPrincipal();
        problemService.createProblem(userId, problemRegisterDto);

        log.info("userId: {} success for register problem", userId);
        return "문제가 등록되었습니다.";
    }

    // ✅ 문제 수정
    @ResponseStatus(HttpStatus.OK)
    @PatchMapping("")
    public String updateProblem(Authentication authentication, @ModelAttribute ProblemRegisterDto problemRegisterDto) {
        Long userId = (Long) authentication.getPrincipal();
        problemService.updateProblem(userId, problemRegisterDto);

        log.info("userId: {} success for update problem", userId);
        return "문제가 수정되었습니다.";
    }

    // ✅ 문제 삭제 (204 No Content 반환)
    @ResponseStatus(HttpStatus.OK)
    @DeleteMapping("")
    public void deleteProblems(Authentication authentication, @RequestParam List<Long> deleteProblemIdList) {
        Long userId = (Long) authentication.getPrincipal();
        log.info("userId: {} try to delete problems, id list: {}", userId, deleteProblemIdList);

        practiceService.deleteProblemsFromAllPractice(deleteProblemIdList);
        problemService.deleteProblemList(userId, deleteProblemIdList);
    }

    // ✅ 문제 반복 풀이 추가
    @ResponseStatus(HttpStatus.OK)
    @PostMapping("/repeat")
    public String addRepeatCount(
            Authentication authentication,
            @RequestHeader("problemId") Long problemId,
            @RequestParam(value = "solveImage", required = false) MultipartFile solveImage
    ) {
        Long userId = (Long) authentication.getPrincipal();
        problemService.addRepeatCount(problemId, solveImage);

        log.info("userId: {} repeat problemId: {}", userId, problemId);
        return "문제 반복 풀이가 추가되었습니다.";
    }
}