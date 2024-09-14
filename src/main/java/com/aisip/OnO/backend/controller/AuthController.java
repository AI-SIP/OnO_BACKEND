package com.aisip.OnO.backend.controller;

import com.aisip.OnO.backend.Auth.AppleTokenVerifier;
import com.aisip.OnO.backend.Dto.ErrorResponseDto;
import com.aisip.OnO.backend.Dto.Token.TokenRequestDto;
import com.aisip.OnO.backend.Dto.Token.TokenResponseDto;
import com.aisip.OnO.backend.entity.User.UserType;
import com.aisip.OnO.backend.service.UserService;
import com.aisip.OnO.backend.Auth.GoogleTokenVerifier;
import com.aisip.OnO.backend.Auth.JwtTokenProvider;
import com.aisip.OnO.backend.entity.User.User;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;

@Slf4j
@Controller
@RequiredArgsConstructor
@RequestMapping("/api/auth")
public class AuthController {

    private final GoogleTokenVerifier googleTokenVerifier;

    private final AppleTokenVerifier appleTokenVerifier;

    private final JwtTokenProvider jwtTokenProvider;

    private final UserService userService;


    @PostMapping("/guest")
    public ResponseEntity<?> guestLogin() {

        String email = userService.makeGuestEmail();
        String name = userService.makeGuestName();
        String identifier = userService.makeGuestIdentifier();

        User user = userService.registerOrLoginUser(email, name, identifier, UserType.GUEST);
        String accessToken = jwtTokenProvider.createAccessToken(user.getId());
        String refreshToken = jwtTokenProvider.createRefreshToken(user.getId());

        log.info(user.getName() + " has login for guest");
        return ResponseEntity.ok(new TokenResponseDto(accessToken, refreshToken));
    }

    @PostMapping("/google")
    public ResponseEntity<?> googleLogin(@RequestBody TokenRequestDto tokenRequestDto) {

        log.info("Starting google login with Token : " + tokenRequestDto.toString());

        try {
            JsonNode tokenInfo = googleTokenVerifier.verifyToken(tokenRequestDto.getAccessToken(), tokenRequestDto.getPlatform());
            String email = tokenRequestDto.getEmail();
            String name = tokenRequestDto.getName();
            String identifier = tokenRequestDto.getIdentifier();

            log.info("start google login verify access token");

            if (tokenInfo != null && identifier != null) {
                User user = userService.registerOrLoginUser(email, name, identifier, UserType.MEMBER);
                String accessToken = jwtTokenProvider.createAccessToken(user.getId());
                String refreshToken = jwtTokenProvider.createRefreshToken(user.getId());

                log.info(user.getName() + " has login for Google Login");
                return ResponseEntity.ok(new TokenResponseDto(accessToken, refreshToken));
            } else {
                log.warn("Invalid Token Issue for username: " + name);
                throw new IllegalArgumentException("Invalid token information or user data");
            }
        } catch (IOException e) {
            log.warn(e.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ErrorResponseDto("Invalid Google token"));
        } catch (Exception e) {
            log.warn(e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ErrorResponseDto("Internal server error"));
        }
    }

    @PostMapping("/apple")
    public ResponseEntity<?> appleLogin(@RequestBody TokenRequestDto tokenRequestDto) {
        try {
            DecodedJWT jwt = appleTokenVerifier.verifyToken(tokenRequestDto.getIdToken());
            String email = tokenRequestDto.getEmail();
            String name = tokenRequestDto.getName();
            String identifier = tokenRequestDto.getIdentifier();

            log.info("start apple login verify access token");

            if (jwt != null && identifier != null) {
                User user = userService.registerOrLoginUser(email, name, identifier, UserType.MEMBER);
                String accessToken = jwtTokenProvider.createAccessToken(user.getId());
                String refreshToken = jwtTokenProvider.createRefreshToken(user.getId());

                log.info(user.getName() + " has login for Apple Login");
                return ResponseEntity.ok(new TokenResponseDto(accessToken, refreshToken));
            } else {
                log.warn("Invalid Token Issue for username: " + name);
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ErrorResponseDto("Invalid Apple token"));
            }

        } catch (Exception e) {
            log.warn(e.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ErrorResponseDto("Invalid Apple token"));
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
                User user = userService.registerOrLoginUser(email, name, identifier, UserType.MEMBER);
                String accessToken = jwtTokenProvider.createAccessToken(user.getId());
                String refreshToken = jwtTokenProvider.createRefreshToken(user.getId());

                log.info(user.getName() + " has login for kakao Login");
                return ResponseEntity.ok(new TokenResponseDto(accessToken, refreshToken));
            } else {
                log.warn("Invalid Token Issue for username: " + name);
                throw new IllegalArgumentException("Invalid token information or user data");
            }
        } catch (Exception e) {
            log.warn(e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ErrorResponseDto("Internal server error"));
        }
    }

    @GetMapping("/verifyAccessToken")
    public ResponseEntity<?> verifyAccessToken(@RequestHeader("Authorization") String authorizationHeader) {
        try {
            log.info("start verify access token");
            if (authorizationHeader != null && authorizationHeader.startsWith("Bearer ")) {
                String accessToken = authorizationHeader.substring(7);
                if (jwtTokenProvider.validateToken(accessToken)) {
                    log.info("success for verify access token");
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
            log.warn("Token verification failed");
            log.warn(e.getMessage());
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
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ErrorResponseDto("Could not refresh access token"));
        }
    }
}
