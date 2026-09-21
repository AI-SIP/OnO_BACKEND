package com.aisip.OnO.backend.admin.controller;

import com.aisip.OnO.backend.admin.dto.AdminPager;
import com.aisip.OnO.backend.admin.dto.AdminUserRows;
import com.aisip.OnO.backend.admin.repository.AdminUserQueryRepository;
import com.aisip.OnO.backend.common.exception.ApplicationException;
import com.aisip.OnO.backend.user.dto.UserRegisterDto;
import com.aisip.OnO.backend.user.exception.UserErrorCase;
import com.aisip.OnO.backend.user.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

@Slf4j
@RequiredArgsConstructor
@Controller
@RequestMapping("/admin")
public class AdminUserController {

    /** 서비스 기준 시간대. 인자 없는 now() 는 서버 기본 시간대를 따라 하루가 밀릴 수 있다. */
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    /** 한 페이지에 너무 많이 요청하면 집계 쿼리가 운영 DB 를 오래 붙잡는다. */
    private static final int MAX_PAGE_SIZE = 500;

    private static final int STREAK_LOOKBACK_DAYS = 400;

    private final UserService userService;
    private final AdminUserQueryRepository adminUserQueryRepository;

    @GetMapping("/users")
    @Transactional(readOnly = true)
    public String getAllUsers(
            @RequestParam(defaultValue = "0", name = "page") int page,
            @RequestParam(defaultValue = "20", name = "size") int size,
            @RequestParam(defaultValue = "createdAt", name = "sortBy") String sortBy,
            @RequestParam(defaultValue = "desc", name = "direction") String direction,
            @RequestParam(required = false, name = "q") String q,
            @RequestParam(required = false, name = "platform") String platform,
            HttpServletRequest request,
            Model model
    ) {
        int selectedPage = Math.max(page, 0);
        int selectedSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        String keyword = blankToNull(q);
        String selectedPlatform = blankToNull(platform);

        long totalUsers = adminUserQueryRepository.countUsers(keyword, selectedPlatform);
        List<AdminUserRows.ListRow> users = adminUserQueryRepository.findUsers(
                keyword, selectedPlatform, sortBy, direction, (long) selectedPage * selectedSize, selectedSize);

        LocalDate today = LocalDate.now(KST);
        model.addAttribute("summary", adminUserQueryRepository.summarize(today.atStartOfDay(), today.minusDays(6).atStartOfDay()));
        model.addAttribute("platforms", adminUserQueryRepository.findPlatforms());
        model.addAttribute("users", users);
        model.addAttribute("totalUsers", totalUsers);
        model.addAttribute("currentPage", selectedPage);
        model.addAttribute("size", selectedSize);
        model.addAttribute("sortBy", sortBy);
        model.addAttribute("direction", direction);
        model.addAttribute("q", keyword);
        model.addAttribute("platform", selectedPlatform);
        model.addAttribute("pager", AdminPager.of(request, "page", selectedPage, selectedSize, totalUsers));

        return "users";
    }

    @GetMapping("/user/{userId}")
    @Transactional(readOnly = true)
    public String getUserDetailsById(@PathVariable(name = "userId") Long userId, Model model) {
        AdminUserRows.Profile profile = adminUserQueryRepository.findProfile(userId)
                .orElseThrow(() -> new ApplicationException(UserErrorCase.USER_NOT_FOUND));

        model.addAttribute("user", profile);
        model.addAttribute("counts", adminUserQueryRepository.countOwnedData(userId));
        model.addAttribute("loginStreak", currentStreak(adminUserQueryRepository.findRecentLoginDates(userId, STREAK_LOOKBACK_DAYS)));
        model.addAttribute("problems", adminUserQueryRepository.findProblems(userId));
        model.addAttribute("solves", adminUserQueryRepository.findSolves(userId));
        model.addAttribute("practiceNotes", adminUserQueryRepository.findPracticeNotes(userId));
        model.addAttribute("folders", adminUserQueryRepository.findFolders(userId));
        model.addAttribute("tags", adminUserQueryRepository.findTags(userId));
        model.addAttribute("missionProgress", adminUserQueryRepository.findMissionProgress(userId));
        model.addAttribute("missionLogs", adminUserQueryRepository.findMissionLogs(userId));
        model.addAttribute("studyRooms", adminUserQueryRepository.findStudyRooms(userId));
        model.addAttribute("cosmetics", adminUserQueryRepository.findEquippedCosmetics(userId));
        model.addAttribute("achievements", adminUserQueryRepository.findAchievements(userId));
        model.addAttribute("moods", adminUserQueryRepository.findMoods(userId, 14));
        model.addAttribute("listLimit", AdminUserQueryRepository.detailListLimit());

        return "user";
    }

    @PostMapping("/user/{userId}")
    public String updateUserInfo(@PathVariable(name = "userId") Long userId, @ModelAttribute UserRegisterDto userRegisterDto, Model model) {
        userService.updateUser(userId, userRegisterDto);

        // 업데이트된 정보 다시 조회
        return "redirect:/admin/user/" + userId;
    }

    @PostMapping("/user/{userId}/level")
    @ResponseBody
    public String updateUserLevel(
            @PathVariable(name = "userId") Long userId,
            @RequestParam(name = "levelType") String levelType,
            @RequestParam(name = "levelValue") Long levelValue,
            @RequestParam(name = "pointValue") Long pointValue
    ) {
        userService.updateUserLevel(userId, levelType, levelValue, pointValue);
        return "success";
    }

    /**
     * 오늘이나 어제까지 끊기지 않고 이어진 출석 일수.
     * 오늘 아직 앱을 안 열었다고 연속 기록을 0 으로 보여 주면 실제와 다르게 읽히므로 어제부터 세는 것도 인정한다.
     */
    static int currentStreak(List<LocalDate> loginDatesDesc) {
        if (loginDatesDesc.isEmpty()) {
            return 0;
        }
        LocalDate today = LocalDate.now(KST);
        LocalDate expected = loginDatesDesc.get(0);
        if (expected.isBefore(today.minusDays(1))) {
            return 0;
        }
        int streak = 0;
        for (LocalDate date : loginDatesDesc) {
            if (!date.equals(expected)) {
                break;
            }
            streak++;
            expected = expected.minusDays(1);
        }
        return streak;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
