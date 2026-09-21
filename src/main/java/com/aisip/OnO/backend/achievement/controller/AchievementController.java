package com.aisip.OnO.backend.achievement.controller;

import com.aisip.OnO.backend.achievement.dto.AchievementListResponseDto;
import com.aisip.OnO.backend.achievement.service.AchievementService;
import com.aisip.OnO.backend.common.response.CommonResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 훈장 API.
 *
 * <p>사용자 식별자는 요청 본문이나 파라미터가 아니라 인증 컨텍스트에서만 꺼낸다. 밖에서 받으면
 * 남의 훈장을 들여다보는 요청을 그대로 받아들이게 된다. {@code CosmeticController} 와 같은 방식이다.
 *
 * <p>{@code /api/achievements} 는 SecurityConfig 의 별도 매처에 걸리지 않아
 * {@code anyRequest().authenticated()} 를 탄다. 비로그인 요청은 401 이다.
 *
 * <p><b>GET 이지만 쓰기가 일어난다.</b> 판정을 조회 시점에 하고 새로 채운 훈장을 그 자리에 적기 때문이다.
 * 조건을 적립 경로마다 심으면 이 기능이 붙기 전에 이미 오답노트를 백 개 적은 사람이 아무것도 못 받는데,
 * 조회할 때 세면 소급이 저절로 된다. 같은 요청을 몇 번을 보내도 행은 하나라 멱등하다.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/achievements")
public class AchievementController {

    private final AchievementService achievementService;

    @GetMapping("")
    public CommonResponse<AchievementListResponseDto> getAchievements() {
        Long userId = (Long) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return CommonResponse.success(achievementService.getAchievements(userId));
    }
}
