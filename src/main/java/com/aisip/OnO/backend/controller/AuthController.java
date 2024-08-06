package com.aisip.OnO.backend.controller;

import com.aisip.OnO.backend.Auth.AppleTokenVerifier;
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

        return ResponseEntity.ok(new AuthResponse(accessToken, refreshToken));
    }

    @PostMapping("/google")
    public ResponseEntity<?> googleLogin(@RequestBody TokenRequest tokenRequest) {
        try {
            JsonNode tokenInfo = googleTokenVerifier.verifyToken(tokenRequest.getAccessToken(), tokenRequest.getPlatform());
            String email = tokenRequest.getEmail();
            String name = tokenRequest.getName();
            String identifier = tokenRequest.getIdentifier();

            if (tokenInfo != null && identifier != null) {
                User user = userService.registerOrLoginUser(email, name, identifier, UserType.MEMBER);
                String accessToken = jwtTokenProvider.createAccessToken(user.getId());
                String refreshToken = jwtTokenProvider.createRefreshToken(user.getId());
                return ResponseEntity.ok(new AuthResponse(accessToken, refreshToken));
            } else {
                throw new IllegalArgumentException("Invalid token information or user data");
            }
        } catch (IOException e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ErrorResponse("Invalid Google token"));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ErrorResponse("Internal server error"));
        }
    }


    @PostMapping("/apple")
    public ResponseEntity<?> appleLogin(@RequestBody TokenRequest tokenRequest) {
        try {
            DecodedJWT jwt = appleTokenVerifier.verifyToken(tokenRequest.getIdToken());
            String email = tokenRequest.getEmail();
            String name = tokenRequest.getName();
            String identifier = tokenRequest.getIdentifier();

            if(jwt != null && identifier != null){
                User user = userService.registerOrLoginUser(email, name, identifier, UserType.MEMBER);
                String accessToken = jwtTokenProvider.createAccessToken(user.getId());
                String refreshToken = jwtTokenProvider.createRefreshToken(user.getId());
                return ResponseEntity.ok(new AuthResponse(accessToken, refreshToken));
            } else{
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ErrorResponse("Invalid Apple token"));
            }

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ErrorResponse("Invalid Apple token"));
        }
    }

    // Access Token을 검증하는 메서드 추가
    @GetMapping("/verifyAccessToken")
    public ResponseEntity<?> verifyAccessToken(@RequestHeader("Authorization") String authorizationHeader) {
        try {
            if (authorizationHeader != null && authorizationHeader.startsWith("Bearer ")) {
                String accessToken = authorizationHeader.substring(7);
                if (jwtTokenProvider.validateToken(accessToken)) {
                    return ResponseEntity.ok("Token is valid");
                } else {
                    System.out.println("token is invalid");
                    return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ErrorResponse("Invalid or expired access token"));
                }
            } else {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ErrorResponse("Authorization header missing or invalid"));
            }
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ErrorResponse("Token verification failed"));
        }
    }

    @PostMapping("/refresh")
    public ResponseEntity<?> refreshToken(@RequestBody TokenRequest tokenRequest) {
        try {
            String requestRefreshToken = tokenRequest.getRefreshToken();
            if (jwtTokenProvider.validateToken(requestRefreshToken)) {
                Long userId = Long.parseLong(jwtTokenProvider.getSubjectFromToken(requestRefreshToken));
                // 액세스 토큰을 생성할 때 userId만을 사용하도록 수정
                String newAccessToken = jwtTokenProvider.createAccessToken(userId);
                // 리프레시 토큰을 재발급하지 않고 기존 리프레시 토큰을 재사용하거나, 필요에 따라 새 리프레시 토큰을 생성
                return ResponseEntity.ok(new AuthResponse(newAccessToken, requestRefreshToken));
            } else {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ErrorResponse("Invalid or expired refresh token"));
            }
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ErrorResponse("Could not refresh access token"));
        }
    }


    public static class TokenRequest {
        private String idToken;

        private String accessToken;

        private String refreshToken;

        private String platform;

        private String email;

        private String name;

        private String identifier;


        public String getIdToken() {
            return idToken;
        }


        public String getAccessToken() {
            return accessToken;
        }

        public String getEmail(){
            return email;
        }

        public String getName() {
            return name;
        }

        public String getPlatform() {
            return platform;
        }

        public String getIdentifier() {
            return identifier;
        }

        public void setIdToken(String idToken) {
            this.idToken = idToken;
        }

        public void setAccessToken(String accessToken) {
            this.accessToken = accessToken;
        }

        public String getRefreshToken() {
            return refreshToken;
        }

        public void setRefreshToken(String refreshToken) {
            this.refreshToken = refreshToken;
        }
    }

    public static class AuthResponse {
        private String accessToken;

        private String refreshToken;

        public AuthResponse(String accessToken, String refreshToken) {
            this.accessToken = accessToken;this.refreshToken = refreshToken;
        }

        public String getAccessToken() {
            return accessToken;
        }

        public String getRefreshToken(){
            return refreshToken;
        }

        public void setAccessToken(String accessToken) {
            this.accessToken = accessToken;
        }

        public void setRefreshToken(String refreshToken) {
            this.refreshToken= refreshToken;
        }
    }

    public static class ErrorResponse {
        private String error;

        public ErrorResponse(String error) {
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
