package com.aisip.OnO.backend.controller;

import com.aisip.OnO.backend.Dto.User.UserRegisterDto;
import com.aisip.OnO.backend.Dto.User.UserResponseDto;
import com.aisip.OnO.backend.entity.User;
import com.aisip.OnO.backend.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/user")
public class UserController {

    private final UserService userService;

    @GetMapping("/{userId}")
    public ResponseEntity<UserResponseDto> getUserById(@PathVariable Long userId) {

        UserResponseDto userResponseDto = userService.getUserByUserId(userId);
        return ResponseEntity.ok().body(userResponseDto);
    }

    @PostMapping("")
    public ResponseEntity<?> saveUser(@RequestBody UserRegisterDto userRegisterDto) {
        UserResponseDto userResponseDto = userService.saveUser(userRegisterDto);
        return ResponseEntity.ok().body(userResponseDto);
    }

    @GetMapping("/autoLogin/{googleId}")
    public ResponseEntity<?> autoLogin(@PathVariable String googleId) {
        UserResponseDto userResponseDto = userService.getUserByGoogleId(googleId);
        if (userResponseDto != null) {
            return ResponseEntity.ok().body(userResponseDto);
        } else {
            return ResponseEntity.status(401).body("{\"isLoggedIn\": false}");
        }
    }
}
