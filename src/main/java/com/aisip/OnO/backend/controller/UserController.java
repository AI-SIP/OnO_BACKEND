package com.aisip.OnO.backend.controller;

import com.aisip.OnO.backend.Dto.ErrorResponseDto;
import com.aisip.OnO.backend.Dto.User.UserResponseDto;
import com.aisip.OnO.backend.service.FolderService;
import com.aisip.OnO.backend.service.ProblemService;
import com.aisip.OnO.backend.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/user")
public class UserController {

    private final UserService userService;

    private final ProblemService problemService;

    private final FolderService folderService;

    @GetMapping("")
    public ResponseEntity<?> getUserInfo(Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        UserResponseDto userResponseDto = userService.getUserById(userId);
        if (userResponseDto != null) {
            log.info("userId: " + userId + " get user info");
            return ResponseEntity.ok(userResponseDto);
        } else {
            log.info("userId: " + userId + " not found");
            return ResponseEntity.status(404).body(new ErrorResponseDto("User not found"));
        }
    }

    @DeleteMapping("")
    public ResponseEntity<?> deleteUserInfo(Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();

        problemService.deleteUserProblems(userId);
        folderService.deleteAllUserFolder(userId);
        userService.deleteUserById(userId);

        log.info("userId: " + userId + " has deleted");
        return ResponseEntity.ok().body("delete complete");
    }
}
