package com.aisip.OnO.backend.controller;

import com.aisip.OnO.backend.Dto.Folder.FolderRegisterDto;
import com.aisip.OnO.backend.Dto.Folder.FolderResponseDto;
import com.aisip.OnO.backend.service.FolderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/folder")
public class FolderController {

    private final FolderService folderService;

    @GetMapping()
    public ResponseEntity<?> getRootFolder(Authentication authentication) {
        try {
            Long userId = (Long) authentication.getPrincipal();
            log.info("userId: " + userId + " try to get root folder");

            return ResponseEntity.ok(folderService.findRootFolder(userId));
        } catch (Exception e) {
            log.warn(e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("폴더 탐색에 실패했습니다.");
        }
    }

    @GetMapping("/{folderId}")
    public ResponseEntity<?> getFolder(Authentication authentication, @PathVariable Long folderId) {

        try {
            Long userId = (Long) authentication.getPrincipal();
            log.info("userId: " + userId + " try to get folderId: " + folderId);

            return ResponseEntity.ok(folderService.findFolder(userId, folderId));
        } catch (Exception e) {
            log.warn(e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("폴더 탐색에 실패했습니다.");
        }
    }

    @GetMapping("/folders")
    public ResponseEntity<?> getAllFolderName(Authentication authentication) {
        try {
            Long userId = (Long) authentication.getPrincipal();
            log.info("userId: " + userId + " try to get all folder name");

            return ResponseEntity.ok(folderService.findAllFolderThumbnailsByUserId(userId));
        } catch (Exception e) {
            log.warn(e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("폴더 탐색에 실패했습니다.");
        }
    }

    @PostMapping()
    public ResponseEntity<?> createFolder(Authentication authentication, @ModelAttribute FolderRegisterDto folderRegisterDto) {
        try {
            Long userId = (Long) authentication.getPrincipal();
            log.info("userId: " + userId + " try to create folder");

            FolderResponseDto folderResponseDto = folderService.createFolder(userId, folderRegisterDto.getFolderName(), folderRegisterDto.getParentFolderId());

            return ResponseEntity.ok(folderResponseDto);
        } catch (Exception e) {
            log.warn(e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("폴더 생성에 실패했습니다.");
        }
    }

    @PatchMapping("/{folderId}")
    public ResponseEntity<?> updateFolder(Authentication authentication, @PathVariable Long folderId, @ModelAttribute FolderRegisterDto folderRegisterDto) {
        try {
            Long userId = (Long) authentication.getPrincipal();
            log.info("userId: " + userId + " try to update folderId: " + folderId);

            FolderResponseDto folderResponseDto = folderService.updateFolder(userId, folderId, folderRegisterDto.getFolderName(), folderRegisterDto.getParentFolderId());

            return ResponseEntity.ok(folderResponseDto);
        } catch (Exception e) {
            log.warn(e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("폴더 생성에 실패했습니다.");
        }
    }

    @DeleteMapping("/{folderId}")
    public ResponseEntity<?> deleteFolder(Authentication authentication, @PathVariable Long folderId) {
        try {
            Long userId = (Long) authentication.getPrincipal();
            log.info("userId: " + userId + " try to delete folderId: " + folderId);
            folderService.deleteFolder(userId, folderId);

            return ResponseEntity.ok("폴더 삭제에 성공했습니다");
        } catch (Exception e) {
            log.warn(e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("폴더 삭제에 실패했습니다.");
        }

    }
}