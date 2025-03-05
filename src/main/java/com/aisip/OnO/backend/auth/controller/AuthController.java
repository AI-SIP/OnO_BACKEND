package com.aisip.OnO.backend.auth.controller;

import com.aisip.OnO.backend.auth.dto.TokenRequestDto;
import com.aisip.OnO.backend.auth.dto.TokenResponseDto;
import com.aisip.OnO.backend.user.dto.UserRegisterDto;
import com.aisip.OnO.backend.user.dto.UserResponseDto;
import com.aisip.OnO.backend.user.entity.UserType;
import com.aisip.OnO.backend.auth.service.JwtTokenService;
import com.aisip.OnO.backend.user.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

@Slf4j
@Controller
@RequiredArgsConstructor
@RequestMapping("/api/auth")
public class AuthController {

    private final JwtTokenService jwtTokenService;
    private final UserService userService;

    // ✅ 게스트 로그인
    @ResponseStatus(HttpStatus.OK)
    @PostMapping("/login/guest")
    public TokenResponseDto guestLogin() {
        UserResponseDto user = userService.registerGuestUser();
        return jwtTokenService.generateTokens(user);
    }

    // ✅ 소셜 로그인
    @ResponseStatus(HttpStatus.OK)
    @PostMapping("/login/social")
    public TokenResponseDto socialLogin(@RequestBody UserRegisterDto userRegisterDto) {
        log.info("Starting login with user info: {}", userRegisterDto);
        UserResponseDto user = userService.registerOrLoginUser(userRegisterDto, UserType.MEMBER);
        return jwtTokenService.generateTokens(user);
    }

    // ✅ Access Token 검증
    @ResponseStatus(HttpStatus.OK)
    @GetMapping("/verifyAccessToken")
    public String verifyAccessToken(@RequestHeader("Authorization") String authorizationHeader) {
        jwtTokenService.verifyAccessToken(authorizationHeader);
        return "Token is valid";
    }

    // ✅ Refresh Token을 이용한 Access Token 재발급
    @ResponseStatus(HttpStatus.OK)
    @PostMapping("/refresh")
    public TokenResponseDto refreshToken(@RequestBody TokenRequestDto tokenRequestDto) {
        return jwtTokenService.refreshAccessToken(tokenRequestDto.getRefreshToken());
    }
}
