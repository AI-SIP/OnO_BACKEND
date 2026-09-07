package com.aisip.OnO.backend.notice.controller;

import com.aisip.OnO.backend.common.response.CommonResponse;
import com.aisip.OnO.backend.notice.dto.NoticeResponseDto;
import com.aisip.OnO.backend.notice.service.NoticeService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/notices")
public class NoticeController {

    private final NoticeService noticeService;

    /**
     * 지금 띄울 공지를 내려준다.
     *
     * <p>보여줄 공지가 없으면 {@code data} 없이 성공 응답만 나간다.
     * ({@code CommonResponse} 가 null 필드를 빼기 때문에 키 자체가 사라진다.)
     */
    @GetMapping("/active")
    public CommonResponse<NoticeResponseDto> getActiveNotice() {
        Long userId = (Long) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return CommonResponse.success(noticeService.findActiveNoticeForUser(userId));
    }

    /**
     * 그만 보기. 누른 유저에게만 24시간 동안 숨긴다.
     */
    @PostMapping("/{noticeId}/dismiss")
    public CommonResponse<String> dismissNotice(@PathVariable(name = "noticeId") Long noticeId) {
        Long userId = (Long) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        noticeService.dismissNotice(noticeId, userId);

        return CommonResponse.success("공지를 24시간 동안 숨겼습니다.");
    }
}
