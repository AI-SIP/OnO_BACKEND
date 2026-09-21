package com.aisip.OnO.backend.admin.controller;

import com.aisip.OnO.backend.admin.service.AdminStatsService;
import com.aisip.OnO.backend.notice.service.NoticeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.ZoneId;

@Slf4j
@RequiredArgsConstructor
@Controller
@RequestMapping("/admin")
public class AdminController {

    /** 서비스 기준 시간대. 인자 없는 now() 는 서버 기본 시간대를 따라 하루가 밀릴 수 있다. */
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final AdminStatsService adminStatsService;
    private final NoticeService noticeService;

    @GetMapping("/main")
    public String adminPage(Model model) {
        LocalDate today = LocalDate.now(KST);
        model.addAttribute("today", today);
        model.addAttribute("home", adminStatsService.home(today));
        model.addAttribute("activeNotice", noticeService.findActiveNoticeForAdmin());
        return "admin";
    }

    @GetMapping("/user/image/view")
    public String viewImage(@RequestParam("url") String imageUrl, Model model) {
        model.addAttribute("imageUrl", imageUrl);
        return "image";
    }
}
