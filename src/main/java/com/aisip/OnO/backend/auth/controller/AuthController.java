package com.aisip.OnO.backend.auth.controller;

import com.aisip.OnO.backend.auth.dto.TokenRequestDto;
import com.aisip.OnO.backend.auth.dto.TokenResponseDto;
import com.aisip.OnO.backend.common.response.CommonResponse;
import com.aisip.OnO.backend.user.dto.UserRegisterDto;
import com.aisip.OnO.backend.auth.service.UserAuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

@Slf4j
@Controller
@RequiredArgsConstructor
@RequestMapping("/api/auth")
public class AuthController {

    private final UserAuthService userAuthService;


    // ✅ User Login
    @PostMapping("/login")
    public CommonResponse<TokenResponseDto> login(@RequestBody UserRegisterDto userRegisterDto) {
        return CommonResponse.success(userAuthService.loginUser(userRegisterDto));
    }

    // ✅ Refresh Token을 이용한 Access Token 재발급
    @PostMapping("/refresh")
    public CommonResponse<TokenResponseDto> refreshToken(@RequestBody TokenRequestDto tokenRequestDto) {
        return CommonResponse.success(userAuthService.refreshAccessToken(tokenRequestDto));
    }
}
