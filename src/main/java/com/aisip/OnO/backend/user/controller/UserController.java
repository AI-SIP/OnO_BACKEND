package com.aisip.OnO.backend.user.controller;

import com.aisip.OnO.backend.common.response.CommonResponse;
import com.aisip.OnO.backend.user.dto.UserRegisterDto;
import com.aisip.OnO.backend.user.dto.UserResponseDto;
import com.aisip.OnO.backend.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    // ✅ 사용자 정보 조회
    @GetMapping("")
    public CommonResponse<UserResponseDto> getUserInfo() {
        Long userId = (Long) SecurityContextHolder.getContext().getAuthentication().getPrincipal();

        return CommonResponse.success(userService.findUser(userId));
    }

    // ✅ 사용자 정보 수정
    @PatchMapping("")
    public CommonResponse<String> updateUserInfo(@RequestBody UserRegisterDto userRegisterDto) {
        Long userId = (Long) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        userService.updateUser(userId, userRegisterDto);

        return CommonResponse.success("사용자 정보 수정이 완료되었습니다.");
    }

    // ✅ 사용자 계정 삭제
    @DeleteMapping("")
    public void deleteUserInfo() {
        Long userId = (Long) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        userService.deleteUserById(userId);
    }
}
