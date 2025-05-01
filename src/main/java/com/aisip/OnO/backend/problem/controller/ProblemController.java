package com.aisip.OnO.backend.problem.controller;

import com.aisip.OnO.backend.common.response.CommonResponse;
import com.aisip.OnO.backend.problem.dto.ProblemDeleteRequestDto;
import com.aisip.OnO.backend.problem.dto.ProblemImageDataRegisterDto;
import com.aisip.OnO.backend.problem.dto.ProblemRegisterDto;
import com.aisip.OnO.backend.problem.dto.ProblemResponseDto;
import com.aisip.OnO.backend.problem.service.ProblemService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.propertyeditors.CustomNumberEditor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/problems")
public class ProblemController {

    private final ProblemService problemService;

    @InitBinder
    public void initBinder(WebDataBinder binder) {
        binder.registerCustomEditor(Long.class, new CustomNumberEditor(Long.class, true));
    }

    // ✅ 특정 문제 조회
    @GetMapping("/{problemId}")
    public CommonResponse<ProblemResponseDto> getProblem(@PathVariable("problemId") Long problemId) {
        Long userId = (Long) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        ProblemResponseDto problemResponseDto = problemService.findProblem(problemId, userId);

        return CommonResponse.success(problemResponseDto);
    }

    // ✅ 유저가 등록한 모든 문제 조회
    @GetMapping("/user")
    public CommonResponse<List<ProblemResponseDto>> getProblemsByUserId() {
        Long userId = (Long) SecurityContextHolder.getContext().getAuthentication().getPrincipal();

        return CommonResponse.success(problemService.findUserProblems(userId));
    }

    // ✅ 특정 폴더 내부의 모든 문제 조회
    @GetMapping("/folder/{folderId}")
    public CommonResponse<List<ProblemResponseDto>> getProblemsByUserId(@PathVariable("folderId") Long folderId) {
        Long userId = (Long) SecurityContextHolder.getContext().getAuthentication().getPrincipal();

        return CommonResponse.success(problemService.findFolderProblemList(folderId));
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
        problemService.registerProblem(problemRegisterDto, userId);

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
    @PatchMapping("/info")
    public CommonResponse<String> updateProblemInfo(@RequestBody ProblemRegisterDto problemRegisterDto) {
        Long userId = (Long) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        problemService.updateProblemInfo(problemRegisterDto, userId);

        return CommonResponse.success("문제가 수정되었습니다.");
    }

    // ✅ 문제 경로 변경
    @PatchMapping("/path")
    public CommonResponse<String> updateProblemPath(@RequestBody ProblemRegisterDto problemRegisterDto) {
        Long userId = (Long) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        problemService.updateProblemFolder(problemRegisterDto, userId);

        return CommonResponse.success("문제가 수정되었습니다.");
    }

    // ✅ 문제 이미지 데이터 변경
    @PatchMapping("/imageData")
    public CommonResponse<String> updateProblemImageData(@RequestBody ProblemRegisterDto problemRegisterDto) {
        Long userId = (Long) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        problemService.updateProblemImageData(problemRegisterDto, userId);

        return CommonResponse.success("문제가 수정되었습니다.");
    }

    // ✅ 문제 삭제
    @DeleteMapping("")
    public CommonResponse<String> deleteProblems(
            @RequestBody ProblemDeleteRequestDto deleteRequestDto
    ) {
        problemService.deleteProblems(deleteRequestDto);
        return CommonResponse.success("문제 삭제가 완료되었습니다.");
    }

    // ✅ 문제 이미지 데이터 삭제
    @DeleteMapping("/imageData")
    public CommonResponse<String> deleteProblemImageData(@RequestParam("imageUrl") String imageUrl) {
        Long userId = (Long) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        problemService.deleteProblemImageData(imageUrl);

        return CommonResponse.success("문제 이미지 데이터 삭제가 완료되었습니다.");
    }
}