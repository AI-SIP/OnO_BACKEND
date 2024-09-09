package com.aisip.OnO.backend.controller;

import com.aisip.OnO.backend.Dto.Problem.ProblemResponseDto;
import com.aisip.OnO.backend.Dto.User.UserRegisterDto;
import com.aisip.OnO.backend.entity.User.User;
import com.aisip.OnO.backend.service.FolderService;
import com.aisip.OnO.backend.service.ProblemService;
import com.aisip.OnO.backend.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;

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
        User user = userService.getUserDetailsById(userId);
        model.addAttribute("user", user);

        List<ProblemResponseDto> problems = problemService.findAllProblemsByUserId(userId);
        model.addAttribute("problems", problems);
        return "user";
    }

    @GetMapping("/users")
    public String getAllUser(Model model, Authentication authentication) {
        List<User> users = userService.findAllUsers();
        model.addAttribute("users", users);
        return "users";
    }

    @PostMapping("/user/{userId}")
    public String updateUserInfo(@PathVariable Long userId, @ModelAttribute UserRegisterDto userRegisterDto, Model model) {

        try {
            User user = userService.updateUser(userId, userRegisterDto);
            if (user != null) {
                model.addAttribute("user", user);
                List<ProblemResponseDto> problems = problemService.findAllProblemsByUserId(userId);
                model.addAttribute("problems", problems);
                return "user";
            } else {
                return "users";
            }
        } catch (Exception e) {
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
