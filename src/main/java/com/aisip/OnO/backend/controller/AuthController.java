package com.aisip.OnO.backend.controller;

import com.aisip.OnO.backend.Dto.ErrorResponseDto;
import com.aisip.OnO.backend.Dto.Token.TokenRequestDto;
import com.aisip.OnO.backend.Dto.Token.TokenResponseDto;
import com.aisip.OnO.backend.Dto.User.UserResponseDto;
import com.aisip.OnO.backend.entity.User.UserType;
import com.aisip.OnO.backend.service.UserService;
import com.aisip.OnO.backend.Auth.JwtTokenProvider;
import io.sentry.Sentry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

@Slf4j
@Controller
@RequiredArgsConstructor
@RequestMapping("/api/auth")
public class AuthController {

    private final JwtTokenProvider jwtTokenProvider;

    private final UserService userService;


    @PostMapping("/guest")
    public ResponseEntity<?> guestLogin() {

        String email = userService.makeGuestEmail();
        String name = userService.makeGuestName();
        String identifier = userService.makeGuestIdentifier();

        return getUserTokenResponse(name, identifier, email, UserType.GUEST);
    }

    @PostMapping("/google")
    public ResponseEntity<?> googleLogin(@RequestBody TokenRequestDto tokenRequestDto) {

        log.info("Starting google login with Token : " + tokenRequestDto.toString());

        try {
            String email = tokenRequestDto.getEmail();
            String name = tokenRequestDto.getName();
            String identifier = tokenRequestDto.getIdentifier();

            if (name != null && identifier != null) {
                return getUserTokenResponse(name, identifier, email, UserType.MEMBER);
            } else {
                throw new IllegalArgumentException("Invalid token information or user data " + name);
            }
        } catch (Exception e) {
            log.warn(e.getMessage());
            Sentry.captureException(e);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ErrorResponseDto(e.getMessage()));
        }
    }

    @PostMapping("/apple")
    public ResponseEntity<?> appleLogin(@RequestBody TokenRequestDto tokenRequestDto) {

        log.info("Starting apple login with Token : " + tokenRequestDto.toString());

        try {
            String email = tokenRequestDto.getEmail();
            String name = tokenRequestDto.getName();
            String identifier = tokenRequestDto.getIdentifier();

            if (name != null && identifier != null) {
                return getUserTokenResponse(name, identifier, email, UserType.MEMBER);
            } else {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ErrorResponseDto("Invalid Apple token for username : " + name));
            }

        } catch (Exception e) {
            log.warn(e.getMessage());
            Sentry.captureException(e);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ErrorResponseDto(e.getMessage()));
        }
    }

    @PostMapping("/kakao")
    public ResponseEntity<?> kakaoLogin(@RequestBody TokenRequestDto tokenRequestDto) {

        log.info("Starting kakao login with Token : " + tokenRequestDto.toString());

        try {
            String email = tokenRequestDto.getEmail();
            String name = tokenRequestDto.getName();
            String identifier = tokenRequestDto.getIdentifier();

            if (name != null && identifier != null) {
                return getUserTokenResponse(name, identifier, email, UserType.MEMBER);
            } else {
                throw new IllegalArgumentException("Invalid token information or user data for username : " + name);
            }
        } catch (Exception e) {
            log.warn(e.getMessage());
            Sentry.captureException(e);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ErrorResponseDto(e.getMessage()));
        }
    }

    @GetMapping("/verifyAccessToken")
    public ResponseEntity<?> verifyAccessToken(@RequestHeader("Authorization") String authorizationHeader) {
        try {
            if (authorizationHeader != null && authorizationHeader.startsWith("Bearer ")) {
                String accessToken = authorizationHeader.substring(7);
                if (jwtTokenProvider.validateToken(accessToken)) {
                    //log.info("success for verify access token");
                    return ResponseEntity.ok("Token is valid");
                } else {
                    log.warn("token is invalid");
                    return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ErrorResponseDto("Invalid or expired access token"));
                }
            } else {
                log.warn("Authorization header missing or invalid");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ErrorResponseDto("Authorization header missing or invalid"));
            }
        } catch (Exception e) {
            log.warn(e.getMessage());
            Sentry.captureException(e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ErrorResponseDto("Token verification failed"));
        }
    }

    @PostMapping("/refresh")
    public ResponseEntity<?> refreshToken(@RequestBody TokenRequestDto tokenRequestDto) {
        try {
            String requestRefreshToken = tokenRequestDto.getRefreshToken();
            log.info("start refresh token");
            if (jwtTokenProvider.validateToken(requestRefreshToken)) {
                Long userId = Long.parseLong(jwtTokenProvider.getSubjectFromToken(requestRefreshToken));
                String newAccessToken = jwtTokenProvider.createAccessToken(userId);

                log.info("Refresh Token Success for userId: " + userId);
                return ResponseEntity.ok(new TokenResponseDto(newAccessToken, requestRefreshToken));
            } else {
                log.warn("Invalid or expired refresh token");

                Long userId = Long.parseLong(jwtTokenProvider.getSubjectFromToken(requestRefreshToken));

                String newAccessToken = jwtTokenProvider.createAccessToken(userId);
                String newRefreshToken = jwtTokenProvider.createRefreshToken(userId);

                return ResponseEntity.ok(new TokenResponseDto(newAccessToken, newRefreshToken));
            }
        } catch (Exception e) {
            log.warn("Could not refresh access token");
            Sentry.captureException(e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ErrorResponseDto("Could not refresh access token"));
        }
    }

    private ResponseEntity<?> getUserTokenResponse(String name, String identifier, String email, UserType userType){
        try {
            UserResponseDto user = userService.registerOrLoginUser(email, name, identifier, userType);
            String accessToken = jwtTokenProvider.createAccessToken(user.getUserId());
            String refreshToken = jwtTokenProvider.createRefreshToken(user.getUserId());

            log.info(user.getUserName() + " has login");

            return ResponseEntity.ok(new TokenResponseDto(accessToken, refreshToken));
        } catch (Exception e) {
            log.warn(e.getMessage());
            Sentry.captureException(e);

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ErrorResponseDto("Could not Generate user token response"));
        }
    }
}
