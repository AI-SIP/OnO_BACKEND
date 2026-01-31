package com.aisip.OnO.backend.admin.controller;

import com.aisip.OnO.backend.problem.service.ProblemService;
import com.aisip.OnO.backend.user.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Slf4j
@RequiredArgsConstructor
@Controller
@RequestMapping("/admin")
public class AdminAnalysisController {

    private final UserService userService;
    private final ProblemService problemService;

    @GetMapping("/analysis")
    public String getAllAnalysis(Model model) {
        int allUserCount = userService.findAllUsers().size();
        int allProblemCount = problemService.findAllProblems().size();

        model.addAttribute("allUserCount", allUserCount);
        model.addAttribute("allProblemCount", allProblemCount);

        return "analysis";
    }
}