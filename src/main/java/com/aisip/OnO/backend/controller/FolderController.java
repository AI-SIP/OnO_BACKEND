package com.aisip.OnO.backend.controller;

import com.aisip.OnO.backend.Dto.Folder.FolderRegisterDto;
import com.aisip.OnO.backend.Dto.Folder.FolderResponseDto;
import com.aisip.OnO.backend.service.FolderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/folder")
public class FolderController {

    private FolderService folderService;

    @GetMapping("/{folderId}")
    public ResponseEntity<?> getFolder(Authentication authentication, @PathVariable Long folderId) {
        return ResponseEntity.ok(folderService.findFolder(folderId));
    }

    @PostMapping()
    public ResponseEntity<?> createFolder(Authentication authentication, @ModelAttribute FolderRegisterDto folderRegisterDto) {
        try {
            Long userId = (Long) authentication.getPrincipal();
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
            FolderResponseDto folderResponseDto = folderService.updateFolder(userId, folderId, folderRegisterDto.getFolderName(), folderRegisterDto.getParentFolderId());

            return ResponseEntity.ok(folderResponseDto);
        } catch (Exception e) {
            log.warn(e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("폴더 생성에 실패했습니다.");
        }
    }

    @DeleteMapping("/{folderId}")
    public ResponseEntity<?> deleteFolder(Authentication authentication, @PathVariable Long folderId) {
        folderService.deleteFolder(folderId);
        return ResponseEntity.ok("폴더 삭제에 성공했습니다");
    }
}