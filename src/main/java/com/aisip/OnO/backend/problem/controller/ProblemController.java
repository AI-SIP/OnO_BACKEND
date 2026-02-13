package com.aisip.OnO.backend.problem.controller;

import com.aisip.OnO.backend.common.response.CommonResponse;
import com.aisip.OnO.backend.common.response.CursorPageResponse;
import com.aisip.OnO.backend.problem.dto.ProblemAnalysisResponseDto;
import com.aisip.OnO.backend.problem.dto.ProblemDeleteRequestDto;
import com.aisip.OnO.backend.problem.dto.ProblemImageDataRegisterDto;
import com.aisip.OnO.backend.problem.dto.ProblemRegisterDto;
import com.aisip.OnO.backend.problem.dto.ProblemResponseDto;
import com.aisip.OnO.backend.problem.service.ProblemAnalysisService;
import com.aisip.OnO.backend.problem.service.ProblemService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.propertyeditors.CustomNumberEditor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/problems")
public class ProblemController {

    private final ProblemService problemService;
    private final ProblemAnalysisService analysisService;

    @InitBinder
    public void initBinder(WebDataBinder binder) {
        binder.registerCustomEditor(Long.class, new CustomNumberEditor(Long.class, true));
    }

    // ✅ 특정 문제 조회
    @GetMapping("/{problemId}")
    public CommonResponse<ProblemResponseDto> getProblem(@PathVariable("problemId") Long problemId) {
        Long userId = (Long) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        ProblemResponseDto problemResponseDto = problemService.findProblem(problemId);

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

    // ✅ V2 API: 커서 기반 폴더의 문제 조회 (무한 스크롤)
    @GetMapping("/folder/{folderId}/V2")
    public CommonResponse<CursorPageResponse<ProblemResponseDto>> getProblemsWithCursorByUserId(
            @PathVariable("folderId") Long folderId,
            @RequestParam(value = "cursor", required = false) Long cursor,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        Long userId = (Long) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        log.info("userId: {} get problems for folderId: {} with cursor: {}, size: {}", userId, folderId, cursor, size);

        return CommonResponse.success(problemService.findProblemsByFolderWithCursor(folderId, cursor, size));
    }

    // ✅ 사용자의 문제 개수 조회
    @GetMapping("/problemCount")
    public CommonResponse<Long> getUserProblemCount() {
        Long userId = (Long) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return CommonResponse.success(problemService.findProblemCountByUser(userId));
    }

    // ✅ 문제 분석 결과 조회
    @GetMapping("/{problemId}/analysis")
    public CommonResponse<ProblemAnalysisResponseDto> getProblemAnalysis(@PathVariable("problemId") Long problemId) {
        Long userId = (Long) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        ProblemAnalysisResponseDto analysisResponseDto = analysisService.getAnalysis(problemId, userId);

        return CommonResponse.success(analysisResponseDto);
    }

    // ✅ 문제 등록
    @PostMapping("")
    public CommonResponse<Long> registerProblem(@RequestBody ProblemRegisterDto problemRegisterDto) {
        Long userId = (Long) SecurityContextHolder.getContext().getAuthentication().getPrincipal();

        // 문제 등록 + 빈 분석 객체 생성 (동기)
        Long problemId = problemService.registerProblem(problemRegisterDto, userId);
        return CommonResponse.success(problemId);
    }

    // ✅ 문제 이미지 비동기 업로드
    @PostMapping("/{problemId}/imageData")
    public CommonResponse<String> uploadProblemImages(
            @PathVariable("problemId") Long problemId,
            @RequestParam("problemImages") List<MultipartFile> problemImages,
            @RequestParam("problemImageTypes") List<String> problemImageTypes
    ) {
        Long userId = (Long) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        problemService.uploadProblemImages(problemId, userId, problemImages, problemImageTypes);
        problemService.analysisProblem(problemId);

        return CommonResponse.success("이미지 업로드가 시작되었습니다.");
    }

    // ✅ 문제 이미지 비동기 업로드
    @PatchMapping("/{problemId}/no-image")
    public CommonResponse<String> updateProblemAnalysisStatus(
            @PathVariable("problemId") Long problemId
    ) {
        Long userId = (Long) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        analysisService.updateToNoImage(problemId);

        return CommonResponse.success("이미지 업로드가 시작되었습니다.");
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

    // ✅ 문제 삭제
    @DeleteMapping("")
    public CommonResponse<String> deleteProblems(
            @RequestBody ProblemDeleteRequestDto problemDeleteRequestDto
            ) {
        problemService.deleteProblemList(problemDeleteRequestDto.deleteProblemIdList());
        return CommonResponse.success("문제 삭제가 완료되었습니다.");
    }

    @DeleteMapping("/all")
    public CommonResponse<String> deleteUserProblems(
    ) {
        Long userId = (Long) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        problemService.deleteAllUserProblems(userId);

        return CommonResponse.success("유저의 모든 문제가 삭제되었습니다.");
    }

    // ✅ 문제 이미지 데이터 삭제
    @DeleteMapping("/imageData")
    public CommonResponse<String> deleteProblemImageData(@RequestParam("imageUrl") String imageUrl) {
        Long userId = (Long) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        problemService.deleteProblemImageData(imageUrl);

        return CommonResponse.success("문제 이미지 데이터 삭제가 완료되었습니다.");
    }

}