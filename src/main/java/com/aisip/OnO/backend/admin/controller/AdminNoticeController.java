package com.aisip.OnO.backend.admin.controller;

import com.aisip.OnO.backend.common.exception.ApplicationException;
import com.aisip.OnO.backend.notice.dto.NoticeCreateRequestDto;
import com.aisip.OnO.backend.notice.dto.NoticeResponseDto;
import com.aisip.OnO.backend.notice.entity.NoticeType;
import com.aisip.OnO.backend.notice.service.NoticeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Slf4j
@RequiredArgsConstructor
@Controller
@RequestMapping("/admin/notice")
public class AdminNoticeController {

    private final NoticeService noticeService;

    @GetMapping
    public String noticePage(Model model) {
        NoticeResponseDto activeNotice = noticeService.findActiveNoticeForAdmin();

        model.addAttribute("activeNotice", activeNotice);
        model.addAttribute("noticeTypes", NoticeType.values());
        return "admin-notice";
    }

    /**
     * 공지 등록. 이미 걸려 있는 공지가 있으면 서비스가 그것을 내리고 새 공지로 바꾼다.
     *
     * <p>검증 실패를 {@link ApplicationException} 그대로 흘리면 전역 핸들러가 JSON 을
     * 내려보내서 관리자 화면이 깨진다. 여기서 잡아 메시지만 화면으로 되돌린다.
     */
    @PostMapping
    public String registerNotice(
            @RequestParam(name = "title") String title,
            @RequestParam(name = "content") String content,
            @RequestParam(name = "type") NoticeType type,
            @RequestParam(name = "durationHours", required = false) Integer durationHours,
            RedirectAttributes redirectAttributes
    ) {
        try {
            NoticeResponseDto notice = noticeService.registerNotice(
                    new NoticeCreateRequestDto(title, content, type, durationHours)
            );
            redirectAttributes.addFlashAttribute("resultMessage",
                    "공지를 등록했습니다. " + notice.expiresAt() + " 까지 노출됩니다.");
        } catch (ApplicationException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }

        return "redirect:/admin/notice";
    }

    @PostMapping("/{noticeId}/delete")
    public String removeNotice(
            @PathVariable(name = "noticeId") Long noticeId,
            RedirectAttributes redirectAttributes
    ) {
        try {
            noticeService.removeNotice(noticeId);
            redirectAttributes.addFlashAttribute("resultMessage", "공지를 내렸습니다.");
        } catch (ApplicationException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }

        return "redirect:/admin/notice";
    }
}
