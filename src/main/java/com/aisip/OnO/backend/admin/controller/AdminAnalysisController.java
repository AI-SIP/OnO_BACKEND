package com.aisip.OnO.backend.admin.controller;

import com.aisip.OnO.backend.mission.service.MissionLogService;
import com.aisip.OnO.backend.practicenote.service.PracticeNoteService;
import com.aisip.OnO.backend.problem.entity.AnalysisStatus;
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
import java.time.temporal.ChronoUnit;
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
        long allProblemAnalysisCount = problemService.countAllProblemAnalyses();
        Map<AnalysisStatus, Long> allAnalysisStatusCounts = problemService.countProblemAnalysesByStatus();

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
        Map<LocalDate, Long> dailyVisits = missionLogService.getDailyVisitCount(selectedStartDate, selectedEndDate);
        Map<LocalDate, Long> dailyNewUsers = userService.getDailyNewUsersCount(selectedStartDate, selectedEndDate);
        Map<LocalDate, Long> dailyPracticeNotes = practiceNoteService.getDailyPracticeNotesCount(selectedStartDate, selectedEndDate);
        Map<LocalDate, Long> dailyPracticeLogs = missionLogService.getDailyNotePracticeLogsCount(selectedStartDate, selectedEndDate);
        Map<LocalDate, Long> dailyProblems = problemService.getDailyProblemsCount(selectedStartDate, selectedEndDate);
        Map<AnalysisStatus, Long> periodAnalysisStatusCounts = problemService.countProblemAnalysesByStatus(selectedStartDate, selectedEndDate);

        // 선택 기간 신규 가입자 총합
        long recentNewUsersCount = dailyNewUsers.values().stream()
                .mapToLong(Long::longValue)
                .sum();
        long periodVisitCount = dailyVisits.values().stream()
                .mapToLong(Long::longValue)
                .sum();
        long periodActiveUserCount = dailyActiveUsers.values().stream()
                .mapToLong(Long::longValue)
                .sum();
        long periodUniqueVisitorCount = missionLogService.countUniqueVisitors(selectedStartDate, selectedEndDate);
        long periodPracticeNoteCount = dailyPracticeNotes.values().stream()
                .mapToLong(Long::longValue)
                .sum();
        long periodPracticeLogCount = dailyPracticeLogs.values().stream()
                .mapToLong(Long::longValue)
                .sum();
        long periodProblemCount = dailyProblems.values().stream()
                .mapToLong(Long::longValue)
                .sum();
        long periodCompletedAnalysisCount = periodAnalysisStatusCounts.getOrDefault(AnalysisStatus.COMPLETED, 0L);
        long periodFailedAnalysisCount = periodAnalysisStatusCounts.getOrDefault(AnalysisStatus.FAILED, 0L);
        long periodProcessingAnalysisCount = periodAnalysisStatusCounts.getOrDefault(AnalysisStatus.PROCESSING, 0L);
        long periodNotStartedAnalysisCount = periodAnalysisStatusCounts.getOrDefault(AnalysisStatus.NOT_STARTED, 0L);
        long periodNoImageAnalysisCount = periodAnalysisStatusCounts.getOrDefault(AnalysisStatus.NO_IMAGE, 0L);
        long periodRateLimitExceededAnalysisCount = periodAnalysisStatusCounts.getOrDefault(AnalysisStatus.RATE_LIMIT_EXCEEDED, 0L);
        long periodFinishedAnalysisCount = periodCompletedAnalysisCount + periodFailedAnalysisCount;
        double periodAnalysisFailureRate = periodFinishedAnalysisCount == 0
                ? 0.0
                : (double) periodFailedAnalysisCount * 100 / periodFinishedAnalysisCount;

        long selectedDays = ChronoUnit.DAYS.between(selectedStartDate, selectedEndDate) + 1;
        double averageDailyVisitors = selectedDays > 0
                ? (double) periodActiveUserCount / selectedDays
                : 0.0;

        model.addAttribute("allUserCount", allUserCount);
        model.addAttribute("allProblemCount", allProblemCount);
        model.addAttribute("allPracticeNoteCount", allPracticeNoteCount);
        model.addAttribute("allPracticeLogCount", allPracticeLogCount);
        model.addAttribute("allProblemAnalysisCount", allProblemAnalysisCount);
        model.addAttribute("allCompletedAnalysisCount", allAnalysisStatusCounts.getOrDefault(AnalysisStatus.COMPLETED, 0L));
        model.addAttribute("allFailedAnalysisCount", allAnalysisStatusCounts.getOrDefault(AnalysisStatus.FAILED, 0L));
        model.addAttribute("allProcessingAnalysisCount", allAnalysisStatusCounts.getOrDefault(AnalysisStatus.PROCESSING, 0L));
        model.addAttribute("allNotStartedAnalysisCount", allAnalysisStatusCounts.getOrDefault(AnalysisStatus.NOT_STARTED, 0L));
        model.addAttribute("allNoImageAnalysisCount", allAnalysisStatusCounts.getOrDefault(AnalysisStatus.NO_IMAGE, 0L));
        model.addAttribute("allRateLimitExceededAnalysisCount", allAnalysisStatusCounts.getOrDefault(AnalysisStatus.RATE_LIMIT_EXCEEDED, 0L));
        model.addAttribute("dailyActiveUsers", dailyActiveUsers);
        model.addAttribute("dailyVisits", dailyVisits);
        model.addAttribute("dailyNewUsers", dailyNewUsers);
        model.addAttribute("dailyPracticeNotes", dailyPracticeNotes);
        model.addAttribute("dailyPracticeLogs", dailyPracticeLogs);
        model.addAttribute("dailyProblems", dailyProblems);
        model.addAttribute("recentNewUsersCount", recentNewUsersCount);
        model.addAttribute("periodVisitCount", periodVisitCount);
        model.addAttribute("periodActiveUserCount", periodActiveUserCount);
        model.addAttribute("periodUniqueVisitorCount", periodUniqueVisitorCount);
        model.addAttribute("periodPracticeNoteCount", periodPracticeNoteCount);
        model.addAttribute("periodPracticeLogCount", periodPracticeLogCount);
        model.addAttribute("periodProblemCount", periodProblemCount);
        model.addAttribute("periodCompletedAnalysisCount", periodCompletedAnalysisCount);
        model.addAttribute("periodFailedAnalysisCount", periodFailedAnalysisCount);
        model.addAttribute("periodProcessingAnalysisCount", periodProcessingAnalysisCount);
        model.addAttribute("periodNotStartedAnalysisCount", periodNotStartedAnalysisCount);
        model.addAttribute("periodNoImageAnalysisCount", periodNoImageAnalysisCount);
        model.addAttribute("periodRateLimitExceededAnalysisCount", periodRateLimitExceededAnalysisCount);
        model.addAttribute("periodAnalysisFailureRate", periodAnalysisFailureRate);
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
