package com.aisip.OnO.backend.fcm.controller;

import com.aisip.OnO.backend.common.response.CommonResponse;
import com.aisip.OnO.backend.fcm.dto.FcmTokenRequestDto;
import com.aisip.OnO.backend.fcm.service.FcmTokenService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/fcm")
public class FcmController {

    private final FcmTokenService fcmTokenService;

    @PostMapping("/token")
    public CommonResponse<String> registerFcmToken(@RequestBody FcmTokenRequestDto fcmTokenRequestDto) {
        Long userId = (Long) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        log.info("fcm token: {}", fcmTokenRequestDto.token());

        fcmTokenService.registerToken(fcmTokenRequestDto, userId);
        return CommonResponse.success("문제가 등록되었습니다.");
    }
}
