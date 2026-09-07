package com.aisip.OnO.backend.util.fcm.controller;

import com.aisip.OnO.backend.common.response.CommonResponse;
import com.aisip.OnO.backend.util.fcm.dto.FcmTokenRequestDto;
import com.aisip.OnO.backend.util.fcm.dto.NotificationRequestDto;
import com.aisip.OnO.backend.util.fcm.service.FcmService;
import jakarta.validation.Valid;
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
    public CommonResponse<String> registerFcmToken(@Valid @RequestBody FcmTokenRequestDto fcmTokenRequestDto) {
        Long userId = (Long) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        // token 은 @NotBlank 로 이미 걸러졌으므로 null 방어가 필요 없다.
        log.info("FCM token registration requested - userId: {}, tokenLength: {}",
                userId, fcmTokenRequestDto.token().length());

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
