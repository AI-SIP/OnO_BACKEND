package com.aisip.OnO.backend.controller;

import com.aisip.OnO.backend.Auth.AppleTokenVerifier;
import com.aisip.OnO.backend.service.UserService;
import com.aisip.OnO.backend.Auth.GoogleTokenVerifier;
import com.aisip.OnO.backend.Auth.JwtTokenProvider;
import com.aisip.OnO.backend.entity.User;
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

    @PostMapping("/google")
    public ResponseEntity<?> googleLogin(@RequestBody TokenRequest tokenRequest) {
        try {
            JsonNode tokenInfo = googleTokenVerifier.verifyToken(tokenRequest.getAccessToken(), tokenRequest.getPlatform());
            String email = tokenRequest.getEmail();
            String name = tokenRequest.getName();
            String identifier = tokenRequest.getIdentifier();

            if (tokenInfo != null && identifier != null) {
                User user = userService.registerOrLoginUser(email, name, identifier);
                String token = jwtTokenProvider.createToken(user.getUserId(), user.getEmail());
                return ResponseEntity.ok(new AuthResponse(token));
            } else {
                throw new IllegalArgumentException("Invalid token information or user data");
            }
        } catch (IOException  e) {
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
                User user = userService.registerOrLoginUser(email, name, identifier);
                String token = jwtTokenProvider.createToken(user.getUserId(), user.getEmail());
                return ResponseEntity.ok(new AuthResponse(token));
            } else{
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ErrorResponse("Invalid Apple token"));
            }

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ErrorResponse("Invalid Apple token"));
        }
    }


    public static class TokenRequest {
        private String idToken;

        private String accessToken;

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
    }

    public static class AuthResponse {
        private String token;

        public AuthResponse(String token) {
            this.token = token;
        }

        public String getToken() {
            return token;
        }

        public void setToken(String token) {
            this.token = token;
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
