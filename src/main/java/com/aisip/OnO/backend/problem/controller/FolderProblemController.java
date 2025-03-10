package com.aisip.OnO.backend.problem.controller;

import com.aisip.OnO.backend.common.response.CommonResponse;
import com.aisip.OnO.backend.problem.dto.ProblemRegisterDto;
import com.aisip.OnO.backend.problem.service.FolderProblemFacadeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/folder/problem")
public class FolderProblemController {

    private final FolderProblemFacadeService folderProblemFacadeService;

    // ✅ 문제 등록
    @PostMapping("")
    public CommonResponse<String> registerProblem(@RequestBody ProblemRegisterDto problemRegisterDto) {
        Long userId = (Long) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        folderProblemFacadeService.registerProblemToFolder(problemRegisterDto, userId);

        return CommonResponse.success("문제가 등록되었습니다.");
    }

    // ✅ 문제 경로 변경
    @PatchMapping("")
    public CommonResponse<String> updateProblemPath(@RequestBody ProblemRegisterDto problemRegisterDto) {
        Long userId = (Long) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        folderProblemFacadeService.updateProblemPath(problemRegisterDto, userId);

        return CommonResponse.success("문제가 수정되었습니다.");
    }


    // ✅ 폴더 삭제 기능
    @DeleteMapping("")
    public CommonResponse<String> deleteFoldersWithProblems(@RequestParam List<Long> deleteFolderIdList) {
        Long userId = (Long) SecurityContextHolder.getContext().getAuthentication().getPrincipal();

        folderProblemFacadeService.deleteFoldersWithProblems(deleteFolderIdList, userId);
        return CommonResponse.success("폴더가 성공적으로 삭제되었습니다.");
    }

    // ✅ 특정 유저의 모든 폴더 삭제
    @DeleteMapping("/all")
    public CommonResponse<String> deleteAllUserFolders() {
        Long userId = (Long) SecurityContextHolder.getContext().getAuthentication().getPrincipal();

        folderProblemFacadeService.deleteAllUserFoldersWithProblems(userId);
        return CommonResponse.success("유저의 모든 폴더가 삭제되었습니다.");
    }
}
