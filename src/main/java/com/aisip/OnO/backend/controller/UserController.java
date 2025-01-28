package com.aisip.OnO.backend.controller;

import com.aisip.OnO.backend.Dto.User.UserRegisterDto;
import com.aisip.OnO.backend.Dto.User.UserResponseDto;
import com.aisip.OnO.backend.service.FolderService;
import com.aisip.OnO.backend.service.ProblemPracticeService;
import com.aisip.OnO.backend.service.ProblemService;
import com.aisip.OnO.backend.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
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
    private final ProblemPracticeService problemPracticeService;

    // ✅ 사용자 정보 조회
    @ResponseStatus(HttpStatus.OK)
    @GetMapping("")
    public UserResponseDto getUserInfo(Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        log.info("userId: {} get user info", userId);
        return userService.getUserById(userId);
    }

    // ✅ 사용자 정보 수정
    @ResponseStatus(HttpStatus.OK)
    @PatchMapping("")
    public UserResponseDto updateUserInfo(Authentication authentication, @RequestBody UserRegisterDto userRegisterDto) {
        Long userId = (Long) authentication.getPrincipal();
        log.info("userId: {} update user info", userId);
        return userService.updateUser(userId, userRegisterDto);
    }

    // ✅ 사용자의 문제 개수 조회
    @ResponseStatus(HttpStatus.OK)
    @GetMapping("/problemCount")
    public Long getUserProblemCount(Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        log.info("userId: {} get user problem count", userId);
        return problemService.getProblemCountByUser(userId);
    }

    // ✅ 사용자 계정 삭제 (204 No Content 반환)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @DeleteMapping("")
    public void deleteUserInfo(Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        problemPracticeService.deleteAllPracticesByUser(userId);
        problemService.deleteUserProblems(userId);
        folderService.deleteAllUserFolder(userId);
        userService.deleteUserById(userId);
        log.info("userId: {} has been deleted", userId);
    }
}
