package com.aisip.OnO.backend.util.fcm.controller;

import com.aisip.OnO.backend.common.response.CommonResponse;
import com.aisip.OnO.backend.util.fcm.dto.FcmTokenRequestDto;
import com.aisip.OnO.backend.util.fcm.dto.NotificationRequestDto;
import com.aisip.OnO.backend.util.fcm.service.FcmService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/fcm")
public class FcmController {

    private final FcmService fcmService;

    @PostMapping("/token")
    public CommonResponse<String> registerFcmToken(@RequestBody FcmTokenRequestDto fcmTokenRequestDto) {
        Long userId = (Long) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        log.info("fcm token: {}", fcmTokenRequestDto.token());

        fcmService.registerToken(fcmTokenRequestDto, userId);
        return CommonResponse.success("문제가 등록되었습니다.");
    }

    @PostMapping("/send")
    public CommonResponse<String> sendNoti() {
        Long userId = (Long) SecurityContextHolder.getContext().getAuthentication().getPrincipal();

        NotificationRequestDto notificationRequestDto = new NotificationRequestDto(
                null,
                "복습할 시간이에요!",
                "복습한지 1주 지났습니다.",
                Map.of("hello", "hello")
        );

        fcmService.sendNotificationToAllUserDevice(userId, notificationRequestDto);
        return CommonResponse.success("문제가 등록되었습니다.");
    }
}
