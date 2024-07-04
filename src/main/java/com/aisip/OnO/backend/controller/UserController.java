package com.aisip.OnO.backend.controller;

import com.aisip.OnO.backend.Dto.User.UserRegisterDto;
import com.aisip.OnO.backend.Dto.User.UserResponseDto;
import com.aisip.OnO.backend.exception.UserNotFoundException;
import com.aisip.OnO.backend.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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
    public ResponseEntity<UserResponseDto> saveUser(@RequestBody UserRegisterDto userRegisterDto) {
        UserResponseDto userResponseDto = userService.saveUser(userRegisterDto);
        return ResponseEntity.ok().body(userResponseDto);
    }

    @GetMapping("/autoLogin/{googleId}")
    public ResponseEntity<?> autoLogin(@PathVariable String googleId) {
        try {
            UserResponseDto userResponseDto = userService.getUserByGoogleId(googleId);
            return ResponseEntity.ok().body(userResponseDto);
        } catch (UserNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        }
    }
}