package com.aisip.OnO.backend.controller;

import com.aisip.OnO.backend.Dto.ErrorResponseDto;
import com.aisip.OnO.backend.Dto.User.UserRegisterDto;
import com.aisip.OnO.backend.Dto.User.UserResponseDto;
import com.aisip.OnO.backend.exception.UserNotFoundException;
import com.aisip.OnO.backend.service.FolderService;
import com.aisip.OnO.backend.service.ProblemService;
import com.aisip.OnO.backend.service.UserService;
import io.sentry.Sentry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

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
        try {
            if (userResponseDto != null) {
                log.info("userId: " + userId + " get user info");
                return ResponseEntity.ok(userResponseDto);
            } else {
                throw new UserNotFoundException("userId: " + userId + " not found");
            }
        } catch (UserNotFoundException e) {
            Sentry.captureException(e);
            return ResponseEntity.status(404).body(new ErrorResponseDto("User not found"));
        }
    }

    @PatchMapping("")
    public ResponseEntity<?> updateUserInfo(Authentication authentication, @RequestBody UserRegisterDto userRegisterDto) {
        Long userId = (Long) authentication.getPrincipal();
        UserResponseDto userResponseDto = userService.updateUser(userId, userRegisterDto);
        try{
            if (userResponseDto != null) {
                log.info("userId: " + userId + " update user info");
                return ResponseEntity.ok(userResponseDto);
            } else {
                throw new UserNotFoundException("userId: " + userId + " not found");
            }
        } catch (UserNotFoundException e) {
            Sentry.captureException(e);
            return ResponseEntity.status(404).body(new ErrorResponseDto("User not found"));
        }
    }

    @GetMapping("/problemCount")
    public ResponseEntity<?> getUserProblemCount(Authentication authentication) {
        try {
            Long userId = (Long) authentication.getPrincipal();
            Long userProblemCount = userService.findAllProblemCountByUserId(userId);

            return ResponseEntity.ok(userProblemCount);
        } catch (Exception e) {
            log.error("error for get user problem count : " + e);
            Sentry.captureException(e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    "Error get user problem count: " + e.getMessage()
            );
        }
    }

    @DeleteMapping("")
    public ResponseEntity<?> deleteUserInfo(Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        try{
            problemService.deleteUserProblems(userId);
            folderService.deleteAllUserFolder(userId);
            userService.deleteUserById(userId);

            log.info("userId: " + userId + " has deleted");
            return ResponseEntity.ok().body("delete complete");
        } catch(Exception e){
            Sentry.captureException(e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    "Error delete user: " + e.getMessage()
            );
        }
    }
}
