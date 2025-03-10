package com.aisip.OnO.backend.admin.controller;

import com.aisip.OnO.backend.problem.dto.ProblemResponseDto;
import com.aisip.OnO.backend.problem.entity.ProblemImageType;
import com.aisip.OnO.backend.problem.entity.ProblemTemplateType;
import com.aisip.OnO.backend.user.dto.UserRegisterDto;
import com.aisip.OnO.backend.user.dto.UserResponseDto;
import com.aisip.OnO.backend.fileupload.service.FileUploadService;
import com.aisip.OnO.backend.problem.service.FolderService;
import com.aisip.OnO.backend.problem.service.ProblemService;
import com.aisip.OnO.backend.user.service.UserService;
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

    private final UserService userService;
    private final ProblemService problemService;

    private final FolderService folderService;

    private final FileUploadService fileUploadService;

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
        UserResponseDto user = userService.findUser(userId);
        model.addAttribute("user", user);

        List<ProblemResponseDto> problems = problemService.findUserProblems(userId);
        model.addAttribute("problems", problems);
        return "user";
    }

    @GetMapping("/users")
    public String getAllUsers(Model model, Authentication authentication) {
        List<UserResponseDto> users = userService.findAllUsers();
        List<Long> problemCounts = problemService.getAllUsersProblemCount(users);
        model.addAttribute("users", users);
        model.addAttribute("problemCounts", problemCounts);
        return "users";
    }

    @PostMapping("/user/{userId}")
    public String updateUserInfo(@PathVariable Long userId, @ModelAttribute UserRegisterDto userRegisterDto, Model model) {
        userService.updateUser(userId, userRegisterDto);

        UserResponseDto userResponseDto = userService.findUser(userId);
        model.addAttribute("user", userResponseDto);
        List<ProblemResponseDto> problems = problemService.findUserProblems(userId);
        model.addAttribute("problems", problems);

        return "user";
    }

    @ResponseStatus(HttpStatus.OK)
    @DeleteMapping("/user/{userId}")
    public void deleteUserInfo(@PathVariable Long userId) {

        problemService.deleteAllUserProblems(userId);
        folderService.deleteAllUserFolders(userId);
        userService.deleteUserById(userId);
    }

    @GetMapping("/problems")
    public String getAllProblems(Model model, Authentication authentication) {

        List<ProblemResponseDto> problems = problemService.findAllProblems();
        model.addAttribute("problems", problems);

        return "problems";
    }

    @GetMapping("/analysis")
    public String getAllAnalysis(Model model, Authentication authentication) {

        int allUserCount = userService.findAllUsers().size();

        int allProblemCount = problemService.findAllProblems().size();
        Long nullTemplateCount = problemService.현getTemplateTypeCount(null);
        Long simpleTemplateCount = problemService.getTemplateTypeCount(ProblemTemplateType.SIMPLE_TEMPLATE);
        Long cleanTemplateCount = problemService.getTemplateTypeCount(ProblemTemplateType.CLEAN_TEMPLATE);
        Long specialTemplateCount = problemService.getTemplateTypeCount(ProblemTemplateType.SPECIAL_TEMPLATE);

        Long problemImageCount = fileUploadService.getImageTypeCount(ProblemImageType.PROBLEM_IMAGE);
        Long answerImageCount = fileUploadService.getImageTypeCount(ProblemImageType.ANSWER_IMAGE);
        Long solveImageCount = fileUploadService.getImageTypeCount(ProblemImageType.SOLVE_IMAGE);
        Long processImageCount = fileUploadService.getImageTypeCount(ProblemImageType.PROCESS_IMAGE);


        model.addAttribute("allUserCount", allUserCount);
        model.addAttribute("allProblemCount", allProblemCount);
        model.addAttribute("nullTemplateCount", nullTemplateCount);
        model.addAttribute("simpleTemplateCount", simpleTemplateCount);
        model.addAttribute("cleanTemplateCount", cleanTemplateCount);
        model.addAttribute("specialTemplateCount", specialTemplateCount);
        model.addAttribute("problemImageCount", problemImageCount);
        model.addAttribute("answerImageCount", answerImageCount);
        model.addAttribute("solveImageCount", solveImageCount);
        model.addAttribute("processImageCount", processImageCount);

        return "analysis";
    }
}
