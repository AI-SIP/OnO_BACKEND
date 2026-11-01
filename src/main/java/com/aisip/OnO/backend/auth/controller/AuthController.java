package com.aisip.OnO.backend.auth.controller;

import com.aisip.OnO.backend.auth.dto.TokenRequestDto;
import com.aisip.OnO.backend.auth.dto.TokenResponseDto;
import com.aisip.OnO.backend.auth.social.SocialLoginVerifier;
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

    private final SocialLoginVerifier socialLoginVerifier;

    @PostMapping("/signup/guest")
    public CommonResponse<TokenResponseDto> signUpGuest() {
        return CommonResponse.success(userAuthService.signUpGuestUser());
    }

    @PostMapping("/signup/member")
    public CommonResponse<TokenResponseDto> signUpMember(@RequestBody UserRegisterDto userRegisterDto) {
        // 소셜 플랫폼 서버를 부를 수 있어서 DB 트랜잭션(UserAuthService)에 들어가기 전에 확인한다.
        socialLoginVerifier.verify(userRegisterDto);
        return CommonResponse.success(userAuthService.signUpMemberUser(userRegisterDto));
    }

    // ✅ Refresh Token을 이용한 Access Token 재발급
    @PostMapping("/refresh")
    public CommonResponse<TokenResponseDto> refreshToken(@RequestBody TokenRequestDto tokenRequestDto) {
        return CommonResponse.success(userAuthService.refreshAccessToken(tokenRequestDto));
    }

    // ✅ 로그아웃
    @PostMapping("/logout")
    public CommonResponse<String> logout(
            @RequestHeader("Authorization") String accessToken,
            @RequestBody(required = false) TokenRequestDto tokenRequestDto
    ) {
        Long userId = (Long) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        String refreshToken = tokenRequestDto == null ? null : tokenRequestDto.getRefreshToken();
        userAuthService.logout(accessToken, userId, refreshToken);

        return CommonResponse.success("로그아웃 되었습니다.");
    }
}
