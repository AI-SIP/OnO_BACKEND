package com.aisip.OnO.backend.controller;

import com.aisip.OnO.backend.Dto.Problem.ProblemResponseDto;
import com.aisip.OnO.backend.Dto.User.UserRegisterDto;
import com.aisip.OnO.backend.Dto.User.UserResponseDto;
import com.aisip.OnO.backend.service.FolderService;
import com.aisip.OnO.backend.service.ProblemService;
import com.aisip.OnO.backend.service.UserService;
import io.sentry.Sentry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RequiredArgsConstructor
@Controller
@RequestMapping("/admin")
public class AdminController {

    private final UserService userService;
    private final ProblemService problemService;

    private final FolderService folderService;

    @GetMapping("/main")
    public String adminPage() {
        return "admin";
    }

    @GetMapping("/user/image/view")
    public String viewImage(@RequestParam("url") String imageUrl, Model model) {
        model.addAttribute("imageUrl", imageUrl);
        return "image";
    }

    @GetMapping("/user/{userId}")
    public String getUserDetailsById(@PathVariable Long userId, Model model) {
        UserResponseDto user = userService.getUserDetailsById(userId);
        model.addAttribute("user", user);

        List<ProblemResponseDto> problems = problemService.findAllProblemsByUserId(userId);
        model.addAttribute("problems", problems);
        return "user";
    }

    @GetMapping("/users")
    public String getAllUser(Model model, Authentication authentication) {
        List<UserResponseDto> users = userService.findAllUsers();
        model.addAttribute("users", users);
        return "users";
    }

    @PostMapping("/user/{userId}")
    public String updateUserInfo(@PathVariable Long userId, @ModelAttribute UserRegisterDto userRegisterDto, Model model) {

        try {
            UserResponseDto userResponseDto = userService.updateUser(userId, userRegisterDto);
            if (userResponseDto != null) {
                model.addAttribute("user", userResponseDto);
                List<ProblemResponseDto> problems = problemService.findAllProblemsByUserId(userId);
                model.addAttribute("problems", problems);
                return "user";
            } else {
                throw new Exception("can't find user!");
                //return "users";
            }
        } catch (Exception e) {
            log.warn(e.getMessage());
            Sentry.captureException(e);
            return "users";
        }
    }

    @DeleteMapping("/user/{userId}")
    public ResponseEntity<?> deleteUserInfo(@PathVariable Long userId) {

        problemService.deleteUserProblems(userId);
        userService.deleteUserById(userId);

        return ResponseEntity.ok().body("delete complete");
    }
}
