package com.aisip.OnO.backend.admin.controller;

import com.aisip.OnO.backend.mission.service.MissionLogService;
import com.aisip.OnO.backend.problem.service.ProblemService;
import com.aisip.OnO.backend.user.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.time.LocalDate;
import java.util.Map;

@Slf4j
@RequiredArgsConstructor
@Controller
@RequestMapping("/admin")
public class AdminAnalysisController {

    private final UserService userService;
    private final ProblemService problemService;
    private final MissionLogService missionLogService;

    @GetMapping("/analysis")
    public String getAllAnalysis(Model model) {
        int allUserCount = userService.findAllUsers().size();
        int allProblemCount = problemService.findAllProblems().size();

        // 최근 30일간 날짜별 출석 유저 수
        Map<LocalDate, Long> dailyActiveUsers = missionLogService.getDailyActiveUsersCount(30);

        model.addAttribute("allUserCount", allUserCount);
        model.addAttribute("allProblemCount", allProblemCount);
        model.addAttribute("dailyActiveUsers", dailyActiveUsers);

        return "analysis";
    }
}