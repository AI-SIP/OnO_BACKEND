package com.aisip.OnO.backend.problemsolve.controller;

import com.aisip.OnO.backend.common.response.CommonResponse;
import com.aisip.OnO.backend.problemsolve.dto.ProblemSolveRegisterDto;
import com.aisip.OnO.backend.problemsolve.dto.ProblemSolveResponseDto;
import com.aisip.OnO.backend.problemsolve.dto.ProblemSolveUpdateDto;
import com.aisip.OnO.backend.problemsolve.service.ProblemSolveService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/problem-solves")
public class ProblemSolveController {

    private final ProblemSolveService problemSolveService;

    // 특정 복습 기록 조회
    @GetMapping("/{problemSolveId}")
    public CommonResponse<ProblemSolveResponseDto> getProblemSolve(
            @PathVariable("problemSolveId") Long problemSolveId) {
        Long userId = (Long) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        ProblemSolveResponseDto responseDto = problemSolveService.getProblemSolve(problemSolveId, userId);

        return CommonResponse.success(responseDto);
    }

    // 특정 문제의 모든 복습 기록 조회
    @GetMapping("/problem/{problemId}")
    public CommonResponse<List<ProblemSolveResponseDto>> getProblemSolvesByProblemId(
            @PathVariable("problemId") Long problemId) {
        Long userId = (Long) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        List<ProblemSolveResponseDto> responseDtoList = problemSolveService.getProblemSolvesByProblemId(problemId, userId);

        return CommonResponse.success(responseDtoList);
    }

    // 사용자의 모든 복습 기록 조회
    @GetMapping("/user")
    public CommonResponse<List<ProblemSolveResponseDto>> getUserProblemSolves() {
        Long userId = (Long) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        List<ProblemSolveResponseDto> responseDtoList = problemSolveService.getUserProblemSolves(userId);

        return CommonResponse.success(responseDtoList);
    }

    // 특정 문제의 복습 기록 개수 조회
    @GetMapping("/problem/{problemId}/count")
    public CommonResponse<Long> getProblemSolveCountByProblemId(@PathVariable("problemId") Long problemId) {
        Long userId = (Long) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        Long count = problemSolveService.getProblemSolveCountByProblemId(problemId, userId);

        return CommonResponse.success(count);
    }

    // 사용자의 총 복습 기록 개수 조회
    @GetMapping("/user/count")
    public CommonResponse<Long> getUserProblemSolveCount() {
        Long userId = (Long) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        Long count = problemSolveService.getUserProblemSolveCount(userId);

        return CommonResponse.success(count);
    }

    // 복습 기록 생성
    @PostMapping("")
    public CommonResponse<Long> createProblemSolve(@RequestBody ProblemSolveRegisterDto createDto) {
        Long userId = (Long) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        Long problemSolveId = problemSolveService.createProblemSolve(createDto, userId);

        return CommonResponse.success(problemSolveId);
    }

    // 복습 기록 이미지 업로드
    @PostMapping("/{problemSolveId}/images")
    public CommonResponse<String> uploadProblemSolveImages(
            @PathVariable("problemSolveId") Long problemSolveId,
            @RequestParam("images") List<MultipartFile> images) {
        Long userId = (Long) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        problemSolveService.uploadProblemSolveImages(problemSolveId, userId, images);

        return CommonResponse.success("복습 기록 이미지 업로드가 완료되었습니다.");
    }

    // 복습 기록 수정
    @PatchMapping("")
    public CommonResponse<String> updateProblemSolve(@RequestBody ProblemSolveUpdateDto updateDto) {
        Long userId = (Long) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        problemSolveService.updateProblemSolve(updateDto, userId);

        return CommonResponse.success("복습 기록이 수정되었습니다.");
    }

    // 복습 기록 삭제
    @DeleteMapping("/{problemSolveId}")
    public CommonResponse<String> deleteProblemSolve(@PathVariable("problemSolveId") Long problemSolveId) {
        Long userId = (Long) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        problemSolveService.deleteProblemSolve(problemSolveId, userId);

        return CommonResponse.success("복습 기록이 삭제되었습니다.");
    }
}