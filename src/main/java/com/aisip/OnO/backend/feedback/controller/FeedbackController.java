package com.aisip.OnO.backend.feedback.controller;

import com.aisip.OnO.backend.feedback.dto.FeedbackRequestDto;
import com.aisip.OnO.backend.feedback.service.FeedbackService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/feedback")
@RequiredArgsConstructor
public class FeedbackController {

    private final FeedbackService feedbackService;

    @GetMapping
    public String feedbackForm() {
        return "feedback";
    }

    @PostMapping
    public String submit(@ModelAttribute FeedbackRequestDto dto, HttpServletRequest request) {
        String ip = resolveClientIp(request);
        feedbackService.save(dto, ip);
        return "redirect:/feedback/complete";
    }

    @GetMapping("/complete")
    public String complete() {
        return "feedback-complete";
    }

    private String resolveClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
