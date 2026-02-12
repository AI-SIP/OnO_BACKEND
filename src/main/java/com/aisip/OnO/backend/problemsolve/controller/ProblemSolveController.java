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
    @GetMapping("/{practiceRecordId}")
    public CommonResponse<ProblemSolveResponseDto> getPracticeRecord(
            @PathVariable("practiceRecordId") Long practiceRecordId) {
        Long userId = (Long) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        ProblemSolveResponseDto responseDto = problemSolveService.getPracticeRecord(practiceRecordId, userId);

        return CommonResponse.success(responseDto);
    }

    // 특정 문제의 모든 복습 기록 조회
    @GetMapping("/problem/{problemId}")
    public CommonResponse<List<ProblemSolveResponseDto>> getPracticeRecordsByProblemId(
            @PathVariable("problemId") Long problemId) {
        Long userId = (Long) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        List<ProblemSolveResponseDto> responseDtoList = problemSolveService.getPracticeRecordsByProblemId(problemId, userId);

        return CommonResponse.success(responseDtoList);
    }

    // 사용자의 모든 복습 기록 조회
    @GetMapping("/user")
    public CommonResponse<List<ProblemSolveResponseDto>> getUserPracticeRecords() {
        Long userId = (Long) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        List<ProblemSolveResponseDto> responseDtoList = problemSolveService.getUserPracticeRecords(userId);

        return CommonResponse.success(responseDtoList);
    }

    // 특정 문제의 복습 기록 개수 조회
    @GetMapping("/problem/{problemId}/count")
    public CommonResponse<Long> getPracticeRecordCountByProblemId(@PathVariable("problemId") Long problemId) {
        Long userId = (Long) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        Long count = problemSolveService.getPracticeRecordCountByProblemId(problemId, userId);

        return CommonResponse.success(count);
    }

    // 사용자의 총 복습 기록 개수 조회
    @GetMapping("/user/count")
    public CommonResponse<Long> getUserPracticeRecordCount() {
        Long userId = (Long) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        Long count = problemSolveService.getUserPracticeRecordCount(userId);

        return CommonResponse.success(count);
    }

    // 복습 기록 생성
    @PostMapping("")
    public CommonResponse<Long> createPracticeRecord(@RequestBody ProblemSolveRegisterDto createDto) {
        Long userId = (Long) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        Long practiceRecordId = problemSolveService.createPracticeRecord(createDto, userId);

        return CommonResponse.success(practiceRecordId);
    }

    // 복습 기록 이미지 업로드
    @PostMapping("/{practiceRecordId}/images")
    public CommonResponse<String> uploadPracticeRecordImages(
            @PathVariable("practiceRecordId") Long practiceRecordId,
            @RequestParam("images") List<MultipartFile> images) {
        Long userId = (Long) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        problemSolveService.uploadPracticeRecordImages(practiceRecordId, userId, images);

        return CommonResponse.success("연습 기록 이미지 업로드가 완료되었습니다.");
    }

    // 복습 기록 수정
    @PatchMapping("")
    public CommonResponse<String> updatePracticeRecord(@RequestBody ProblemSolveUpdateDto updateDto) {
        Long userId = (Long) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        problemSolveService.updatePracticeRecord(updateDto, userId);

        return CommonResponse.success("연습 기록이 수정되었습니다.");
    }

    // 복습 기록 삭제
    @DeleteMapping("/{practiceRecordId}")
    public CommonResponse<String> deletePracticeRecord(@PathVariable("practiceRecordId") Long practiceRecordId) {
        Long userId = (Long) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        problemSolveService.deletePracticeRecord(practiceRecordId, userId);

        return CommonResponse.success("연습 기록이 삭제되었습니다.");
    }
}