package com.aisip.OnO.backend.folder.controller;

import com.aisip.OnO.backend.common.response.CommonResponse;
import com.aisip.OnO.backend.folder.dto.FolderRegisterDto;
import com.aisip.OnO.backend.folder.dto.FolderResponseDto;
import com.aisip.OnO.backend.folder.dto.FolderThumbnailResponseDto;
import com.aisip.OnO.backend.folder.service.FolderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/folder")
public class FolderController {

    private final FolderService folderService;

    // ✅ 유저의 전체 폴더 상세 정보 조회
    @GetMapping("/all")
    public CommonResponse<List<FolderResponseDto>> getAllUserFolderDetails() {
        Long userId = (Long) SecurityContextHolder.getContext().getAuthentication().getPrincipal();

        return CommonResponse.success(folderService.findAllFolders(userId));
    }

    // ✅ 모든 폴더 조회
    @GetMapping("/thumbnails")
    public CommonResponse<List<FolderThumbnailResponseDto>> getAllUserFolderThumbnails() {
        Long userId = (Long) SecurityContextHolder.getContext().getAuthentication().getPrincipal();

        return CommonResponse.success(folderService.findAllFolderThumbnails(userId));
    }

    // ✅ 루트 폴더 조회
    @GetMapping("/root")
    public CommonResponse<FolderResponseDto> getRootFolder() {
        Long userId = (Long) SecurityContextHolder.getContext().getAuthentication().getPrincipal();

        return CommonResponse.success(folderService.findRootFolder(userId));
    }

    // ✅ 특정 폴더 조회
    @GetMapping("/{folderId}")
    public CommonResponse<FolderResponseDto> getFolder(@PathVariable Long folderId) {
        Long userId = (Long) SecurityContextHolder.getContext().getAuthentication().getPrincipal();

        return CommonResponse.success(folderService.findFolder(folderId));
    }

    // ✅ 폴더 생성
    @PostMapping("")
    public CommonResponse<String> createFolder(@RequestBody FolderRegisterDto folderRegisterDto) {
        Long userId = (Long) SecurityContextHolder.getContext().getAuthentication().getPrincipal();

        folderService.createFolder(folderRegisterDto, userId);
        return CommonResponse.success("폴더가 성공적으로 생성되었습니다.");
    }

    // ✅ 폴더 정보 수정
    @PatchMapping("/{folderId}")
    public CommonResponse<String> updateFolderInfo(@RequestBody FolderRegisterDto folderRegisterDto) {
        Long userId = (Long) SecurityContextHolder.getContext().getAuthentication().getPrincipal();

        folderService.updateFolder(folderRegisterDto, userId);
        return CommonResponse.success("폴더가 성공적으로 수정되었습니다.");
    }

    // ✅ 폴더 삭제 기능
    @DeleteMapping("")
    public CommonResponse<String> deleteFoldersWithProblems(@RequestParam List<Long> deleteFolderIdList) {
        Long userId = (Long) SecurityContextHolder.getContext().getAuthentication().getPrincipal();

        folderService.deleteFoldersWithProblems(deleteFolderIdList, userId);
        return CommonResponse.success("폴더가 성공적으로 삭제되었습니다.");
    }

    // ✅ 특정 유저의 모든 폴더 삭제
    @DeleteMapping("/all")
    public CommonResponse<String> deleteAllUserFolders() {
        Long userId = (Long) SecurityContextHolder.getContext().getAuthentication().getPrincipal();

        folderService.deleteAllUserFoldersWithProblems(userId);
        return CommonResponse.success("유저의 모든 폴더가 삭제되었습니다.");
    }
}