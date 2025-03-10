package com.aisip.OnO.backend.problem.controller;

import com.aisip.OnO.backend.common.response.CommonResponse;
import com.aisip.OnO.backend.problem.dto.ProblemImageDataRegisterDto;
import com.aisip.OnO.backend.problem.dto.ProblemRegisterDto;
import com.aisip.OnO.backend.problem.dto.ProblemResponseDto;
import com.aisip.OnO.backend.problem.service.ProblemService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.propertyeditors.CustomNumberEditor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/problem")
public class ProblemController {

    private final ProblemService problemService;

    @InitBinder
    public void initBinder(WebDataBinder binder) {
        binder.registerCustomEditor(Long.class, new CustomNumberEditor(Long.class, true));
    }

    // ✅ 특정 문제 조회
    @GetMapping("/{problemId}")
    public CommonResponse<ProblemResponseDto> getProblem(@PathVariable Long problemId) {
        Long userId = (Long) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        ProblemResponseDto problemResponseDto = problemService.findProblem(problemId, userId);

        return CommonResponse.success(problemResponseDto);
    }

    // ✅ 유저가 등록한 모든 문제 조회
    @ResponseStatus(HttpStatus.OK)
    @GetMapping("/all")
    public CommonResponse<List<ProblemResponseDto>> getProblemsByUserId() {
        Long userId = (Long) SecurityContextHolder.getContext().getAuthentication().getPrincipal();

        return CommonResponse.success(problemService.findUserProblems(userId));
    }

    // ✅ 사용자의 문제 개수 조회
    @GetMapping("/problemCount")
    public CommonResponse<Long> getUserProblemCount() {
        Long userId = (Long) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return CommonResponse.success(problemService.findProblemCountByUser(userId));
    }

    // ✅ 문제 등록
    @PostMapping("")
    public CommonResponse<String> registerProblem(@RequestBody ProblemRegisterDto problemRegisterDto) {
        Long userId = (Long) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        problemService.registerProblem(problemRegisterDto, null, userId);

        return CommonResponse.success("문제가 등록되었습니다.");
    }

    // ✅ 이미지 데이터 등록
    @PostMapping("/imageData")
    public CommonResponse<String> registerProblemImageData(@RequestBody ProblemImageDataRegisterDto problemImageDataRegisterDto) {
        Long userId = (Long) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        problemService.registerProblemImageData(problemImageDataRegisterDto, userId);

        return CommonResponse.success("문제가 등록되었습니다.");
    }

    // ✅ 문제 수정
    @PatchMapping("")
    public CommonResponse<String> updateProblemInfo(@RequestBody ProblemRegisterDto problemRegisterDto) {
        Long userId = (Long) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        problemService.updateProblemInfo(problemRegisterDto, userId);

        return CommonResponse.success("문제가 수정되었습니다.");
    }

    // ✅ 문제 삭제
    @DeleteMapping("/some")
    public CommonResponse<String> deleteProblems(@RequestParam("deleteProblemIdList") List<Long> deleteProblemIdList) {
        Long userId = (Long) SecurityContextHolder.getContext().getAuthentication().getPrincipal();

        problemService.deleteProblemList(deleteProblemIdList);

        return CommonResponse.success("문제 삭제가 완료되었습니다.");
    }

    // ✅ 문제 이미지 데이터 삭제
    @DeleteMapping("/imageData")
    public CommonResponse<String> deleteProblemImageData(@RequestParam("imageUrl") String imageUrl) {
        Long userId = (Long) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        problemService.deleteProblemImageData(imageUrl);

        return CommonResponse.success("문제 이미지 데이터 삭제가 완료되었습니다.");
    }

    // ✅ 특정 유저의 모든 문제 삭제
    @DeleteMapping("/all")
    public CommonResponse<String> deleteAllUserProblems() {
        Long userId = (Long) SecurityContextHolder.getContext().getAuthentication().getPrincipal();

        problemService.deleteUserProblems(userId);
        return CommonResponse.success("유저의 모든 문제가 삭제되었습니다.");
    }
}