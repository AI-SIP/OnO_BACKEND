package com.aisip.OnO.backend.problem.controller;

import com.aisip.OnO.backend.problem.dto.FolderRegisterDto;
import com.aisip.OnO.backend.problem.dto.FolderResponseDto;
import com.aisip.OnO.backend.problem.service.FolderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/folder")
public class FolderController {

    private final FolderService folderService;

    // ✅ 모든 폴더 조회
    @ResponseStatus(HttpStatus.OK)
    @GetMapping("")
    public List<FolderResponseDto> getAllFolders(Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        log.info("userId: {} 요청 - 모든 폴더 조회", userId);
        return folderService.findAllFolders(userId);
    }

    // ✅ 루트 폴더 조회
    @ResponseStatus(HttpStatus.OK)
    @GetMapping("/root")
    public FolderResponseDto getRootFolder(Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        log.info("userId: {} 요청 - 루트 폴더 조회", userId);
        return folderService.findRootFolder(userId);
    }

    // ✅ 특정 폴더 조회
    @ResponseStatus(HttpStatus.OK)
    @GetMapping("/{folderId}")
    public FolderResponseDto getFolder(Authentication authentication, @PathVariable Long folderId) {
        Long userId = (Long) authentication.getPrincipal();
        log.info("userId: {} 요청 - folderId: {} 조회", userId, folderId);
        return folderService.findFolder(folderId);
    }

    // ✅ 폴더 생성
    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping
    public FolderResponseDto createFolder(Authentication authentication, @RequestBody FolderRegisterDto folderRegisterDto) {
        Long userId = (Long) authentication.getPrincipal();
        log.info("userId: {} 요청 - 폴더 생성", userId);
        return folderService.createFolder(userId, folderRegisterDto.getFolderName(), folderRegisterDto.getParentFolderId());
    }

    // ✅ 폴더 수정
    @ResponseStatus(HttpStatus.OK)
    @PatchMapping("/{folderId}")
    public FolderResponseDto updateFolder(Authentication authentication, @PathVariable Long folderId, @RequestBody FolderRegisterDto folderRegisterDto) {
        Long userId = (Long) authentication.getPrincipal();
        log.info("userId: {} 요청 - 폴더 수정: folderId: {}", userId, folderId);
        return folderService.updateFolder(userId, folderId, folderRegisterDto.getFolderName(), folderRegisterDto.getParentFolderId());
    }

    // ✅ 문제 경로 수정
    @ResponseStatus(HttpStatus.OK)
    @PatchMapping("/problem")
    public FolderResponseDto updateProblemPath(Authentication authentication, @RequestParam Long problemId, @RequestParam Long folderId) {
        Long userId = (Long) authentication.getPrincipal();
        log.info("userId: {} 요청 - 문제 경로 수정: folderId: {}, problemId: {}", userId, folderId, problemId);
        return folderService.updateProblemPath(userId, problemId, folderId);
    }

    // ✅ 폴더 삭제
    @ResponseStatus(HttpStatus.OK)
    @DeleteMapping("")
    public void deleteFolders(Authentication authentication, @RequestParam List<Long> deleteFolderIdList) {
        Long userId = (Long) authentication.getPrincipal();
        log.info("userId: {} 요청 - 폴더 삭제: {}", userId, deleteFolderIdList);
        folderService.deleteFolderList(userId, deleteFolderIdList);
    }

    @ResponseStatus(HttpStatus.OK)
    @DeleteMapping("/all")
    public void deleteAllUserFolders(Authentication authentication, @RequestParam List<Long> deleteFolderIdList) {
        Long userId = (Long) authentication.getPrincipal();
        log.info("userId: {} 요청 - 폴더 삭제: {}", userId, deleteFolderIdList);

        folderService.deleteAllUserFolder(userId);
    }
}