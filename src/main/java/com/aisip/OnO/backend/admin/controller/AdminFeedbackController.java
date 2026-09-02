package com.aisip.OnO.backend.admin.controller;

import com.aisip.OnO.backend.feedback.dto.FeedbackResponseDto;
import com.aisip.OnO.backend.feedback.service.FeedbackService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequestMapping("/admin/feedbacks")
@RequiredArgsConstructor
public class AdminFeedbackController {

    private final FeedbackService feedbackService;

    @GetMapping
    public String feedbackList(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Model model
    ) {
        // 쿼리 파라미터는 사용자 입력이다. page 가 음수이거나 size 가 0 이하면
        // PageRequest.of 가 IllegalArgumentException 을 던져 관리자 화면이 500 이 됐다.
        // 다른 관리자 목록 화면과 같은 방식으로 유효 범위에 맞춰 보정한다.
        int selectedPage = Math.max(page, 0);
        int selectedSize = Math.max(size, 1);

        Page<FeedbackResponseDto> pageResult = feedbackService.findAll(selectedPage, selectedSize);

        model.addAttribute("feedbacks", pageResult.getContent());
        model.addAttribute("totalCount", feedbackService.count());
        model.addAttribute("averageNps", feedbackService.averageNps());
        model.addAttribute("currentPage", selectedPage);
        model.addAttribute("totalPages", pageResult.getTotalPages());
        model.addAttribute("size", selectedSize);

        int blockSize = 10;
        int blockStart = (selectedPage / blockSize) * blockSize;
        int blockEnd = Math.min(blockStart + blockSize - 1, pageResult.getTotalPages() - 1);
        model.addAttribute("pageBlockStart", blockStart);
        model.addAttribute("pageBlockEnd", Math.max(blockEnd, blockStart));
        model.addAttribute("hasPreviousBlock", blockStart > 0);
        model.addAttribute("hasNextBlock", blockEnd < pageResult.getTotalPages() - 1);

        return "admin-feedback";
    }

    @GetMapping("/{id}")
    public String feedbackDetail(@PathVariable Long id, Model model) {
        model.addAttribute("feedback", feedbackService.findById(id));
        return "admin-feedback-detail";
    }
}
