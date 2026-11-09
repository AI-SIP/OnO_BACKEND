package com.aisip.OnO.backend.admin.controller;

import com.aisip.OnO.backend.admin.service.AdminStatsService;
import com.aisip.OnO.backend.mission.service.MissionLogService;
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
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@RequiredArgsConstructor
@Controller
@RequestMapping("/admin")
public class AdminAnalysisController {

    /** 서비스 기준 시간대. 인자 없는 now() 는 서버 기본 시간대를 따라 하루가 밀릴 수 있다. */
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final UserService userService;
    private final MissionLogService missionLogService;
    private final AdminStatsService adminStatsService;

    /** 한 번에 볼 수 있는 최대 기간. 날짜별 표와 차트가 한 화면에 들어가는 선이다. */
    private static final int MAX_RANGE_DAYS = 366;

    @GetMapping("/analysis")
    public String getAllAnalysis(
            @RequestParam(name = "startDate", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(name = "endDate", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            Model model
    ) {
        LocalDate today = LocalDate.now(KST);
        LocalDate selectedStartDate = startDate != null ? startDate : today.minusDays(29);
        LocalDate selectedEndDate = endDate != null ? endDate : today;

        if (selectedStartDate.isAfter(selectedEndDate)) {
            LocalDate temp = selectedStartDate;
            selectedStartDate = selectedEndDate;
            selectedEndDate = temp;
        }
        if (ChronoUnit.DAYS.between(selectedStartDate, selectedEndDate) + 1 > MAX_RANGE_DAYS) {
            selectedStartDate = selectedEndDate.minusDays(MAX_RANGE_DAYS - 1);
        }

        // 지표를 따로따로 부르면 요청 하나가 커넥션을 두 개 쓴다. 한 트랜잭션에서 모두 센다. (#405)
        AdminStatsService.AnalysisPage page = adminStatsService.analysisPage(selectedStartDate, selectedEndDate, today);
        AdminStatsService.Period period = page.period();
        AdminStatsService.Daily daily = page.daily();

        model.addAttribute("startDate", selectedStartDate);
        model.addAttribute("endDate", selectedEndDate);
        model.addAttribute("days", period.days());
        model.addAttribute("previousStartDate", period.previous().start());
        model.addAttribute("previousEndDate", period.previous().end());
        model.addAttribute("today", today);
        model.addAttribute("quickStart7Days", today.minusDays(6));
        model.addAttribute("quickStart30Days", today.minusDays(29));
        model.addAttribute("quickStart90Days", today.minusDays(89));
        model.addAttribute("quickStartMonth", today.withDayOfMonth(1));

        model.addAttribute("dailyActiveUsers", daily.activeUsers());
        model.addAttribute("dailyNewUsers", daily.newUsers());
        model.addAttribute("dailyProblems", daily.problems());
        model.addAttribute("dailySolves", daily.solves());
        model.addAttribute("dailyPracticeNotes", daily.practiceNotes());
        model.addAttribute("dailyRows", daily.rowsNewestFirst());
        model.addAttribute("dailyTotals", daily.totals());

        model.addAttribute("userStats", page.userStats());
        model.addAttribute("learningStats", page.learningStats());
        model.addAttribute("analysisStats", page.analysisStats());
        model.addAttribute("growthStats", page.growthStats());
        model.addAttribute("roomStats", page.roomStats());
        model.addAttribute("topProblemWriters", page.topProblemWriters());
        model.addAttribute("topSolvers", page.topSolvers());

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
