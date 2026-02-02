package com.aisip.OnO.backend.admin.controller;

import com.aisip.OnO.backend.mission.service.MissionLogService;
import com.aisip.OnO.backend.problem.service.ProblemService;
import com.aisip.OnO.backend.user.dto.UserResponseDto;
import com.aisip.OnO.backend.user.entity.User;
import com.aisip.OnO.backend.user.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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

        // 최근 30일간 날짜별 출석 유저 수 및 신규 가입자 수
        Map<LocalDate, Long> dailyActiveUsers = missionLogService.getDailyActiveUsersCount(30);
        Map<LocalDate, Long> dailyNewUsers = userService.getDailyNewUsersCount(30);

        // 최근 30일 신규 가입자 총합
        long recentNewUsersCount = dailyNewUsers.values().stream()
                .mapToLong(Long::longValue)
                .sum();

        // 하루 평균 방문자 수 (최근 30일)
        double averageDailyVisitors = dailyActiveUsers.values().stream()
                .mapToLong(Long::longValue)
                .average()
                .orElse(0.0);

        model.addAttribute("allUserCount", allUserCount);
        model.addAttribute("allProblemCount", allProblemCount);
        model.addAttribute("dailyActiveUsers", dailyActiveUsers);
        model.addAttribute("dailyNewUsers", dailyNewUsers);
        model.addAttribute("recentNewUsersCount", recentNewUsersCount);
        model.addAttribute("averageDailyVisitors", averageDailyVisitors);

        return "analysis";
    }

    @GetMapping("/analysis/daily-new-users")
    public String getDailyNewUsers(
            @RequestParam("date") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            Model model) {

        List<UserResponseDto> users = userService.getUsersByDate(date);

        model.addAttribute("date", date);
        model.addAttribute("users", users);
        model.addAttribute("type", "new");
        model.addAttribute("title", "신규 가입자");

        return "daily-users";
    }

    @GetMapping("/analysis/daily-active-users")
    public String getDailyActiveUsers(
            @RequestParam("date") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            Model model) {

        List<User> activeUsers = missionLogService.getActiveUsersByDate(date);
        List<UserResponseDto> users = activeUsers.stream()
                .map(UserResponseDto::from)
                .collect(Collectors.toList());

        model.addAttribute("date", date);
        model.addAttribute("users", users);
        model.addAttribute("type", "active");
        model.addAttribute("title", "출석 유저");

        return "daily-users";
    }
}