package com.aisip.OnO.backend.admin.controller;

import com.aisip.OnO.backend.mission.service.MissionLogService;
import com.aisip.OnO.backend.practicenote.service.PracticeNoteService;
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
    private final PracticeNoteService practiceNoteService;

    @GetMapping("/analysis")
    public String getAllAnalysis(
            @RequestParam(name = "startDate", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(name = "endDate", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            Model model
    ) {
        long allUserCount = userService.countAllUsers();
        long allProblemCount = problemService.countAllProblems();
        long allPracticeNoteCount = practiceNoteService.countAllPracticeNotes();
        long allPracticeLogCount = missionLogService.countNotePracticeLogs();

        LocalDate today = LocalDate.now();
        LocalDate selectedStartDate = startDate != null ? startDate : today.minusDays(29);
        LocalDate selectedEndDate = endDate != null ? endDate : today;

        if (selectedStartDate.isAfter(selectedEndDate)) {
            LocalDate temp = selectedStartDate;
            selectedStartDate = selectedEndDate;
            selectedEndDate = temp;
        }

        // 선택 기간 날짜별 출석 유저 수 및 신규 가입자 수
        Map<LocalDate, Long> dailyActiveUsers = missionLogService.getDailyActiveUsersCount(selectedStartDate, selectedEndDate);
        Map<LocalDate, Long> dailyNewUsers = userService.getDailyNewUsersCount(selectedStartDate, selectedEndDate);
        Map<LocalDate, Long> dailyPracticeNotes = practiceNoteService.getDailyPracticeNotesCount(selectedStartDate, selectedEndDate);
        Map<LocalDate, Long> dailyPracticeLogs = missionLogService.getDailyNotePracticeLogsCount(selectedStartDate, selectedEndDate);

        // 선택 기간 신규 가입자 총합
        long recentNewUsersCount = dailyNewUsers.values().stream()
                .mapToLong(Long::longValue)
                .sum();
        long periodPracticeNoteCount = dailyPracticeNotes.values().stream()
                .mapToLong(Long::longValue)
                .sum();
        long periodPracticeLogCount = dailyPracticeLogs.values().stream()
                .mapToLong(Long::longValue)
                .sum();

        // 하루 평균 방문자 수 (선택 기간)
        double averageDailyVisitors = dailyActiveUsers.values().stream()
                .mapToLong(Long::longValue)
                .average()
                .orElse(0.0);

        model.addAttribute("allUserCount", allUserCount);
        model.addAttribute("allProblemCount", allProblemCount);
        model.addAttribute("allPracticeNoteCount", allPracticeNoteCount);
        model.addAttribute("allPracticeLogCount", allPracticeLogCount);
        model.addAttribute("dailyActiveUsers", dailyActiveUsers);
        model.addAttribute("dailyNewUsers", dailyNewUsers);
        model.addAttribute("dailyPracticeNotes", dailyPracticeNotes);
        model.addAttribute("dailyPracticeLogs", dailyPracticeLogs);
        model.addAttribute("recentNewUsersCount", recentNewUsersCount);
        model.addAttribute("periodPracticeNoteCount", periodPracticeNoteCount);
        model.addAttribute("periodPracticeLogCount", periodPracticeLogCount);
        model.addAttribute("averageDailyVisitors", averageDailyVisitors);
        model.addAttribute("startDate", selectedStartDate);
        model.addAttribute("endDate", selectedEndDate);
        model.addAttribute("quickStart7Days", today.minusDays(6));
        model.addAttribute("quickStart30Days", today.minusDays(29));
        model.addAttribute("quickStart90Days", today.minusDays(89));
        model.addAttribute("today", today);

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
