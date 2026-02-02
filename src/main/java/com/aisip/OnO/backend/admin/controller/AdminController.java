package com.aisip.OnO.backend.admin.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RequiredArgsConstructor
@Controller
@RequestMapping("/admin")
public class AdminController {

    @GetMapping("/main")
    public String adminPage() {
        return "admin";
    }

    @GetMapping("/user/image/view")
    public String viewImage(@RequestParam("url") String imageUrl, Model model) {
        model.addAttribute("imageUrl", imageUrl);
        return "image";
    }
}
