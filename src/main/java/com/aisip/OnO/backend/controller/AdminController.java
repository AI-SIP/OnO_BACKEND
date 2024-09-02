package com.aisip.OnO.backend.controller;

import com.aisip.OnO.backend.Dto.Problem.ProblemResponseDto;
import com.aisip.OnO.backend.Dto.User.UserResponseDto;
import com.aisip.OnO.backend.entity.User.User;
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

    private final ProblemService problemService;

    private final UserService userService;

    @GetMapping("/main")
    public String adminPage() {
        return "admin"; // admin.html로 연결
    }

    @GetMapping("/user/image/view")
    public String viewImage(@RequestParam("url") String imageUrl, Model model) {
        model.addAttribute("imageUrl", imageUrl);
        return "image"; // image.html 파일을 렌더링
    }

    @GetMapping("/user/{userId}")
    public String getUserDetailsById(@PathVariable Long userId, Model model) {
        User user = userService.getUserDetailsById(userId);
        model.addAttribute("user", user);

        List<ProblemResponseDto> problems = problemService.findAllProblemsByUserId(userId);
        model.addAttribute("problems", problems);
        return "user";  // user.html 파일을 렌더링
    }

    @GetMapping("/users")
    public String getAllUser(Model model, Authentication authentication){
        List<User> users = userService.findAllUsers();
        model.addAttribute("users", users);
        return "users";
    }

    @PatchMapping("/user/{userId}")
    public ResponseEntity<?> updateUserInfo(@PathVariable Long userId) {

        //problemService.deleteUserProblems(userId);
        //userService.deleteUserById(userId);

        return ResponseEntity.ok().body("update complete");
    }

    @DeleteMapping("/user/{userId}")
    public ResponseEntity<?> deleteUserInfo(@PathVariable Long userId) {

        problemService.deleteUserProblems(userId);
        userService.deleteUserById(userId);

        return ResponseEntity.ok().body("delete complete");
    }
}
