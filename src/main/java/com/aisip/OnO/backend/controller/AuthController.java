package com.aisip.OnO.backend.controller;

import com.aisip.OnO.backend.Auth.AppleTokenVerifier;
import com.aisip.OnO.backend.Dto.Token.TokenRequestDto;
import com.aisip.OnO.backend.Dto.Token.TokenResponseDto;
import com.aisip.OnO.backend.entity.User.UserType;
import com.aisip.OnO.backend.service.UserService;
import com.aisip.OnO.backend.Auth.GoogleTokenVerifier;
import com.aisip.OnO.backend.Auth.JwtTokenProvider;
import com.aisip.OnO.backend.entity.User.User;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Autowired
    private GoogleTokenVerifier googleTokenVerifier;

    @Autowired
    private AppleTokenVerifier appleTokenVerifier;

    @Autowired
    private UserService userService;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @PostMapping("/guest")
    public ResponseEntity<?> guestLogin(){

        String email = userService.makeGuestEmail();
        String name = userService.makeGuestName();
        String identifier = userService.makeGuestIdentifier();

        User user = userService.registerOrLoginUser(email, name, identifier, UserType.GUEST);
        String accessToken = jwtTokenProvider.createAccessToken(user.getId());
        String refreshToken = jwtTokenProvider.createRefreshToken(user.getId());

        return ResponseEntity.ok(new TokenResponseDto(accessToken, refreshToken));
    }

    @PostMapping("/google")
    public ResponseEntity<?> googleLogin(@RequestBody TokenRequestDto tokenRequestDto) {
        try {
            JsonNode tokenInfo = googleTokenVerifier.verifyToken(tokenRequestDto.getAccessToken(), tokenRequestDto.getPlatform());
            String email = tokenRequestDto.getEmail();
            String name = tokenRequestDto.getName();
            String identifier = tokenRequestDto.getIdentifier();

            if (tokenInfo != null && identifier != null) {
                User user = userService.registerOrLoginUser(email, name, identifier, UserType.MEMBER);
                String accessToken = jwtTokenProvider.createAccessToken(user.getId());
                String refreshToken = jwtTokenProvider.createRefreshToken(user.getId());
                return ResponseEntity.ok(new TokenResponseDto(accessToken, refreshToken));
            } else {
                throw new IllegalArgumentException("Invalid token information or user data");
            }
        } catch (IOException e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ErrorResponseDto("Invalid Google token"));
        } catch (Exception e) {
            e.printStackTrace();
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

            if(jwt != null && identifier != null){
                User user = userService.registerOrLoginUser(email, name, identifier, UserType.MEMBER);
                String accessToken = jwtTokenProvider.createAccessToken(user.getId());
                String refreshToken = jwtTokenProvider.createRefreshToken(user.getId());
                return ResponseEntity.ok(new TokenResponseDto(accessToken, refreshToken));
            } else{
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ErrorResponseDto("Invalid Apple token"));
            }

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ErrorResponseDto("Invalid Apple token"));
        }
    }

    @GetMapping("/verifyAccessToken")
    public ResponseEntity<?> verifyAccessToken(@RequestHeader("Authorization") String authorizationHeader) {
        try {
            if (authorizationHeader != null && authorizationHeader.startsWith("Bearer ")) {
                String accessToken = authorizationHeader.substring(7);
                if (jwtTokenProvider.validateToken(accessToken)) {
                    return ResponseEntity.ok("Token is valid");
                } else {
                    System.out.println("token is invalid");
                    return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ErrorResponseDto("Invalid or expired access token"));
                }
            } else {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ErrorResponseDto("Authorization header missing or invalid"));
            }
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ErrorResponseDto("Token verification failed"));
        }
    }

    @PostMapping("/refresh")
    public ResponseEntity<?> refreshToken(@RequestBody TokenRequestDto tokenRequestDto) {
        try {
            String requestRefreshToken = tokenRequestDto.getRefreshToken();
            if (jwtTokenProvider.validateToken(requestRefreshToken)) {
                Long userId = Long.parseLong(jwtTokenProvider.getSubjectFromToken(requestRefreshToken));
                String newAccessToken = jwtTokenProvider.createAccessToken(userId);

                return ResponseEntity.ok(new TokenResponseDto(newAccessToken, requestRefreshToken));
            } else {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ErrorResponseDto("Invalid or expired refresh token"));
            }
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ErrorResponseDto("Could not refresh access token"));
        }
    }




    public static class ErrorResponseDto {
        private String error;

        public ErrorResponseDto(String error) {
            this.error = error;
        }

        public String getError() {
            return error;
        }

        public void setError(String error) {
            this.error = error;
        }
    }
}
