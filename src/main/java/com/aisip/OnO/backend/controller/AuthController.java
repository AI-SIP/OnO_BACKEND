package com.aisip.OnO.backend.controller;

import com.aisip.OnO.backend.Auth.AppleTokenVerifier;
import com.aisip.OnO.backend.service.AuthService;
import com.aisip.OnO.backend.Auth.GoogleTokenVerifier;
import com.aisip.OnO.backend.Auth.JwtTokenProvider;
import com.aisip.OnO.backend.entity.User;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.fasterxml.jackson.databind.JsonNode;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.common.io.BaseEncoding;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.security.GeneralSecurityException;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Autowired
    private GoogleTokenVerifier googleTokenVerifier;

    @Autowired
    private AppleTokenVerifier appleTokenVerifier;

    @Autowired
    private AuthService authService;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    /*
    @PostMapping("/google")
    public ResponseEntity<?> googleLogin(@RequestBody TokenRequest tokenRequest) {
        try {
            GoogleIdToken.Payload payload = googleTokenVerifier.verifyToken(tokenRequest.getIdToken(), tokenRequest.getPlatform());
            User user = authService.registerOrLoginUser(payload.getEmail(), (String) payload.get("name"), payload.getEmail());
            String token = jwtTokenProvider.createToken(user.getUserId(), user.getEmail());
            return ResponseEntity.ok(new AuthResponse(token));
        } catch (IllegalArgumentException e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ErrorResponse("Invalid ID token format"));
        } catch (BaseEncoding.DecodingException e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ErrorResponse("ID token decoding error"));
        } catch (GeneralSecurityException | IOException e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ErrorResponse("Invalid Google token"));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ErrorResponse("Internal server error"));
        }
    }

     */

    @PostMapping("/google")
    public ResponseEntity<?> googleLogin(@RequestBody TokenRequest tokenRequest) {
        try {
            JsonNode tokenInfo = googleTokenVerifier.verifyToken(tokenRequest.getAccessToken(), tokenRequest.getPlatform());
            String email = tokenRequest.getEmail();
            String name = tokenRequest.getName();

            if (tokenInfo != null && email != null && name != null) {
                User user = authService.registerOrLoginUser(email, name, email);
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
            System.out.println("tokenRequest: " + tokenRequest);
            DecodedJWT jwt = appleTokenVerifier.verifyToken(tokenRequest.getIdToken());
            System.out.println("jwt: " + jwt);
            System.out.println("email: " + jwt.getClaim("email").asString());
            System.out.println("name: " + jwt.getClaim("name").asString());
            String email = tokenRequest.getEmail();
            String name = tokenRequest.getName();
            String identifier = jwt.getClaim("email").asString();
            User user = authService.registerOrLoginUser(email, name, identifier);
            String token = jwtTokenProvider.createToken(user.getUserId(), user.getEmail());
            return ResponseEntity.ok(new AuthResponse(token));
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
