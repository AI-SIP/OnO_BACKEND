package com.aisip.OnO.backend.auth.service;

import com.aisip.OnO.backend.auth.dto.TokenRequestDto;
import com.aisip.OnO.backend.auth.dto.TokenResponseDto;
import com.aisip.OnO.backend.auth.entity.Authority;
import com.aisip.OnO.backend.user.dto.UserRegisterDto;
import com.aisip.OnO.backend.user.dto.UserResponseDto;
import com.aisip.OnO.backend.user.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class UserAuthService {

    private final JwtTokenService jwtTokenService;

    private final UserService userService;

    public TokenResponseDto signUpGuestUser() {

        UserResponseDto userResponseDto = userService.registerGuestUser();
        log.info("userId : {} has sign up", userResponseDto.userId());

        return jwtTokenService.generateTokens(userResponseDto.userId(), Authority.ROLE_GUEST);
    }

    public TokenResponseDto signUpMemberUser(UserRegisterDto userRegisterDto) {

        UserResponseDto userResponseDto = userService.registerMemberUser(userRegisterDto);
        log.info("userId : {} user has sign up", userResponseDto.userId());

        return jwtTokenService.generateTokens(userResponseDto.userId(), Authority.ROLE_MEMBER);
    }

    public TokenResponseDto refreshAccessToken(TokenRequestDto tokenRequestDto) {
        return jwtTokenService.refreshAccessToken(tokenRequestDto.getRefreshToken());
    }
}
