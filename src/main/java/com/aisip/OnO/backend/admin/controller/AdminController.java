package com.aisip.OnO.backend.admin.controller;

import com.aisip.OnO.backend.admin.service.AdminService;
import com.aisip.OnO.backend.user.dto.UserRegisterDto;
import com.aisip.OnO.backend.user.dto.UserResponseDto;
import com.aisip.OnO.backend.user.service.UserService;
import com.aisip.OnO.backend.problem.dto.ProblemResponseDto;
import com.aisip.OnO.backend.problem.service.ProblemService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
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

    private final AdminService adminService;
    private final UserService userService;
    private final ProblemService problemService;

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
    public String getUserDetailsById(@PathVariable(name = "userId") Long userId, Model model) {
        UserResponseDto user = userService.findUser(userId);
        model.addAttribute("user", user);

        List<ProblemResponseDto> problems = problemService.findUserProblems(userId);
        model.addAttribute("problems", problems);

        Long problemCount = problemService.findProblemCountByUser(userId);
        model.addAttribute("problemCount", problemCount);

        return "user";
    }

    @GetMapping("/users")
    public String getAllUsers(
            @RequestParam(defaultValue = "0", name = "page") int page,
            @RequestParam(defaultValue = "20", name = "size") int size,
            Model model
    ) {
        List<UserResponseDto> allUsers = userService.findAllUsers();

        // 페이징 계산
        int totalUsers = allUsers.size();
        int totalPages = (int) Math.ceil((double) totalUsers / size);
        int startIndex = page * size;
        int endIndex = Math.min(startIndex + size, totalUsers);

        List<UserResponseDto> pagedUsers = allUsers.subList(startIndex, endIndex);

        // 각 유저의 문제 개수 계산
        List<Long> problemCounts = pagedUsers.stream()
                .map(user -> problemService.findProblemCountByUser(user.userId()))
                .toList();

        model.addAttribute("users", pagedUsers);
        model.addAttribute("problemCounts", problemCounts);
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", totalPages);
        model.addAttribute("totalUsers", totalUsers);
        model.addAttribute("size", size);

        return "users";
    }

    @PostMapping("/user/{userId}")
    public String updateUserInfo(@PathVariable(name = "userId") Long userId, @ModelAttribute UserRegisterDto userRegisterDto, Model model) {
        userService.updateUser(userId, userRegisterDto);

        UserResponseDto userResponseDto = userService.findUser(userId);
        model.addAttribute("user", userResponseDto);

        List<ProblemResponseDto> problems = problemService.findUserProblems(userId);
        model.addAttribute("problems", problems);

        Long problemCount = problemService.findProblemCountByUser(userId);
        model.addAttribute("problemCount", problemCount);

        return "user";
    }

    @ResponseStatus(HttpStatus.OK)
    @DeleteMapping("/user/{userId}")
    public void deleteUserInfo(@PathVariable(name = "userId")Long userId) {
        userService.deleteUserById(userId);
    }

    @GetMapping("/problems")
    public String getAllProblems(
            @RequestParam(defaultValue = "0", name = "page") int page,
            @RequestParam(defaultValue = "20", name = "size") int size,
            Model model
    ) {
        List<ProblemResponseDto> allProblems = problemService.findAllProblems();

        // 페이징 계산
        int totalProblems = allProblems.size();
        int totalPages = (int) Math.ceil((double) totalProblems / size);
        int startIndex = page * size;
        int endIndex = Math.min(startIndex + size, totalProblems);

        List<ProblemResponseDto> pagedProblems = allProblems.subList(startIndex, endIndex);

        model.addAttribute("problems", pagedProblems);
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", totalPages);
        model.addAttribute("totalProblems", totalProblems);
        model.addAttribute("size", size);

        return "problems";
    }

    @GetMapping("/analysis")
    public String getAllAnalysis(Model model) {
        int allUserCount = userService.findAllUsers().size();
        int allProblemCount = problemService.findAllProblems().size();

        model.addAttribute("allUserCount", allUserCount);
        model.addAttribute("allProblemCount", allProblemCount);

        return "analysis";
    }
}
