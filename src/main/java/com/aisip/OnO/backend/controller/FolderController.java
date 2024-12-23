package com.aisip.OnO.backend.controller;

import com.aisip.OnO.backend.Dto.Folder.FolderRegisterDto;
import com.aisip.OnO.backend.Dto.Folder.FolderResponseDto;
import com.aisip.OnO.backend.service.FolderService;
import io.sentry.Sentry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/folder")
public class FolderController {

    private final FolderService folderService;

    @GetMapping("")
    public ResponseEntity<?> getAllFolders(Authentication authentication) {
        try {
            Long userId = (Long) authentication.getPrincipal();
            log.info("userId: " + userId + " try to get all folder");

            return ResponseEntity.ok(folderService.findAllFolders(userId));
        } catch (Exception e) {
            log.warn(e.getMessage());
            Sentry.captureException(e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("폴더 탐색에 실패했습니다.");
        }
    }

    @GetMapping("/root")
    public ResponseEntity<?> getRootFolder(Authentication authentication) {
        try {
            Long userId = (Long) authentication.getPrincipal();
            log.info("userId: " + userId + " try to get root folder");

            return ResponseEntity.ok(folderService.findRootFolder(userId));
        } catch (Exception e) {
            log.warn(e.getMessage());
            Sentry.captureException(e);
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
            Sentry.captureException(e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("폴더 탐색에 실패했습니다.");
        }
    }

    @PostMapping()
    public ResponseEntity<?> createFolder(Authentication authentication, @RequestBody FolderRegisterDto folderRegisterDto) {
        try {
            Long userId = (Long) authentication.getPrincipal();
            log.info("userId: " + userId + " try to create folder");

            FolderResponseDto folderResponseDto = folderService.createFolder(userId, folderRegisterDto.getFolderName(), folderRegisterDto.getParentFolderId());

            return ResponseEntity.ok(folderResponseDto);
        } catch (Exception e) {
            log.warn(e.getMessage());
            Sentry.captureException(e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("폴더 생성에 실패했습니다.");
        }
    }

    @PatchMapping("/{folderId}")
    public ResponseEntity<?> updateFolder(Authentication authentication, @PathVariable Long folderId, @RequestBody FolderRegisterDto folderRegisterDto) {
        try {
            Long userId = (Long) authentication.getPrincipal();
            log.info("userId: " + userId + " try to update folderId: " + folderId);

            FolderResponseDto folderResponseDto = folderService.updateFolder(userId, folderId, folderRegisterDto.getFolderName(), folderRegisterDto.getParentFolderId());

            return ResponseEntity.ok(folderResponseDto);
        } catch (Exception e) {
            log.warn(e.getMessage());
            Sentry.captureException(e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("폴더 생성에 실패했습니다.");
        }
    }

    @PatchMapping("/problem")
    public ResponseEntity<?> updateProblemPath(Authentication authentication, @RequestBody Long problemId, @RequestBody Long folderId) {
        try {
            Long userId = (Long) authentication.getPrincipal();
            log.info("userId: " + userId + " try to update folderId: " + folderId);

            FolderResponseDto folderResponseDto = folderService.updateProblemPath(userId, problemId, folderId);

            return ResponseEntity.ok(folderResponseDto);
        } catch (Exception e) {
            log.warn(e.getMessage());
            Sentry.captureException(e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("문제 경로 변경에 실패했습니다.");
        }
    }

    @DeleteMapping("")
    public ResponseEntity<?> deleteFolders(
            Authentication authentication,
            @RequestParam List<Long> deleteFolderIdList) {
        try {
            Long userId = (Long) authentication.getPrincipal();
            log.info("userId: " + userId + " try to delete folders, id list: " + deleteFolderIdList.toString());

            folderService.deleteFolderList(userId, deleteFolderIdList);
            return ResponseEntity.ok("삭제가 완료되었습니다.");
        } catch (Exception e) {
            log.warn(e.getMessage());
            Sentry.captureException(e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("폴더 삭제에 실패했습니다.");
        }
    }
}