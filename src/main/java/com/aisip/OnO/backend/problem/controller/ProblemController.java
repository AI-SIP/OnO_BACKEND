package com.aisip.OnO.backend.problem.controller;

import com.aisip.OnO.backend.common.response.CommonResponse;
import com.aisip.OnO.backend.problem.dto.ProblemRegisterDto;
import com.aisip.OnO.backend.problem.dto.ProblemResponseDto;
import com.aisip.OnO.backend.practicenote.service.PracticeNoteService;
import com.aisip.OnO.backend.problem.service.ProblemService;
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
    private final PracticeNoteService practiceNoteService;

    // ✅ 특정 문제 조회
    @GetMapping("/{problemId}")
    public CommonResponse<ProblemResponseDto> getProblem(Authentication authentication, @PathVariable Long problemId) {
        Long userId = (Long) authentication.getPrincipal();
        ProblemResponseDto problemResponseDto = problemService.findProblem(userId, problemId);

        return CommonResponse.success(problemResponseDto);
    }

    // ✅ 유저가 등록한 모든 문제 조회
    @ResponseStatus(HttpStatus.OK)
    @GetMapping("/all")
    public CommonResponse<List<ProblemResponseDto>> getProblemsByUserId(Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();

        return CommonResponse.success(problemService.findUserProblems(userId));
    }

    // ✅ 문제 등록 (V1))
    @PostMapping("")
    public CommonResponse<String> registerProblem(Authentication authentication, @ModelAttribute ProblemRegisterDto problemRegisterDto) {
        Long userId = (Long) authentication.getPrincipal();
        problemService.createProblem(userId, problemRegisterDto);

        return CommonResponse.success("문제가 등록되었습니다.");
    }

    // ✅ 사용자의 문제 개수 조회
    @GetMapping("/problemCount")
    public CommonResponse<Long> getUserProblemCount(Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        return CommonResponse.success(problemService.getProblemCountByUser(userId));
    }

    // ✅ 문제 수정
    @PatchMapping("")
    public CommonResponse<String> updateProblem(Authentication authentication, @ModelAttribute ProblemRegisterDto problemRegisterDto) {
        Long userId = (Long) authentication.getPrincipal();
        problemService.updateProblem(userId, problemRegisterDto);

        return CommonResponse.success("문제가 수정되었습니다.");
    }

    // ✅ 문제 삭제 (204 No Content 반환)
    @DeleteMapping("")
    public CommonResponse<String> deleteProblems(Authentication authentication, @RequestParam List<Long> deleteProblemIdList) {
        Long userId = (Long) authentication.getPrincipal();

        practiceNoteService.deleteProblemsFromAllPractice(deleteProblemIdList);
        problemService.deleteProblemList(userId, deleteProblemIdList);

        return CommonResponse.success("문제 삭제가 완료되었습니다.");
    }

    @DeleteMapping("/all")
    public CommonResponse<String> deleteAllUserProblems(Authentication authentication, @RequestParam List<Long> deleteProblemIdList) {
        Long userId = (Long) authentication.getPrincipal();

        problemService.deleteUserProblems(userId);
        return CommonResponse.success("유저의 모든 문제가 삭제되었습니다.");
    }

    // ✅ 문제 반복 풀이 추가
    @PostMapping("/solve")
    public CommonResponse<String> addSolveCount(
            Authentication authentication,
            @RequestHeader("problemId") Long problemId,
            @RequestParam(value = "solveImage", required = false) MultipartFile solveImage
    ) {
        Long userId = (Long) authentication.getPrincipal();
        problemService.addSolveCount(problemId, solveImage);

        return CommonResponse.success("문제 반복 풀이가 추가되었습니다.");
    }
}