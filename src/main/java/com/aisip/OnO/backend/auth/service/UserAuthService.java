package com.aisip.OnO.backend.auth.service;

import com.aisip.OnO.backend.auth.dto.TokenRequestDto;
import com.aisip.OnO.backend.auth.dto.TokenResponseDto;
import com.aisip.OnO.backend.user.dto.UserRegisterDto;
import com.aisip.OnO.backend.user.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserAuthService {

    private final JwtTokenService jwtTokenService;

    private final UserService userService;

    public TokenResponseDto loginUser(UserRegisterDto userRegisterDto) {
        Long userId = userService.loginUser(userRegisterDto);
        return jwtTokenService.generateTokens(userId);
    }

    public TokenResponseDto refreshAccessToken(TokenRequestDto tokenRequestDto) {
        return jwtTokenService.refreshAccessToken(tokenRequestDto.getAccessToken());
    }
}
