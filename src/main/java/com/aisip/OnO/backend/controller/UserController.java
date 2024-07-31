package com.aisip.OnO.backend.controller;

import com.aisip.OnO.backend.Dto.User.UserResponseDto;
import com.aisip.OnO.backend.service.ProblemService;
import com.aisip.OnO.backend.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/user")
public class UserController {

    @Autowired
    private UserService userService;

    @Autowired
    private ProblemService problemService;

    @GetMapping("")
    public ResponseEntity<?> getUserInfo(Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        UserResponseDto userResponseDto = userService.getUserById(userId);
        if (userResponseDto != null) {
            return ResponseEntity.ok(userResponseDto);
        } else {
            return ResponseEntity.status(404).body(new ErrorResponse("User not found"));
        }
    }

    @DeleteMapping("")
    public ResponseEntity<?> deleteUserInfo(Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        problemService.deleteUserProblems(userId);
        userService.deleteUserById(userId);

        return ResponseEntity.ok().body("delete complete");
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
