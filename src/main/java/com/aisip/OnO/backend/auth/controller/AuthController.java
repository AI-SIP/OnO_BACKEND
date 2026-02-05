package com.aisip.OnO.backend.auth.controller;

import com.aisip.OnO.backend.auth.dto.TokenRequestDto;
import com.aisip.OnO.backend.auth.dto.TokenResponseDto;
import com.aisip.OnO.backend.common.response.CommonResponse;
import com.aisip.OnO.backend.auth.service.UserAuthService;
import com.aisip.OnO.backend.user.dto.UserRegisterDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/auth")
public class AuthController {

    private final UserAuthService userAuthService;

    @PostMapping("/signup/guest")
    public CommonResponse<TokenResponseDto> signUpGuest() {
        return CommonResponse.success(userAuthService.signUpGuestUser());
    }

    @PostMapping("/signup/member")
    public CommonResponse<TokenResponseDto> signUpMember(@RequestBody UserRegisterDto userRegisterDto) {
        return CommonResponse.success(userAuthService.signUpMemberUser(userRegisterDto));
    }

    // ✅ Refresh Token을 이용한 Access Token 재발급
    @PostMapping("/refresh")
    public CommonResponse<TokenResponseDto> refreshToken(@RequestBody TokenRequestDto tokenRequestDto) {
        return CommonResponse.success(userAuthService.refreshAccessToken(tokenRequestDto));
    }

    // ✅ 로그아웃
    @PostMapping("/logout")
    public CommonResponse<String> logout(@RequestHeader("Authorization") String accessToken) {
        Long userId = (Long) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        userAuthService.logout(accessToken, userId);

        return CommonResponse.success("로그아웃 되었습니다.");
    }
}
