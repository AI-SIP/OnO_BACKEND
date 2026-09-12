package com.aisip.OnO.backend.cosmetic.controller;

import com.aisip.OnO.backend.common.response.CommonResponse;
import com.aisip.OnO.backend.cosmetic.dto.CosmeticEquipRequestDto;
import com.aisip.OnO.backend.cosmetic.dto.CosmeticEquipResponseDto;
import com.aisip.OnO.backend.cosmetic.dto.CosmeticEquipSetRequestDto;
import com.aisip.OnO.backend.cosmetic.dto.CosmeticListResponseDto;
import com.aisip.OnO.backend.cosmetic.service.CosmeticService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 꾸미기 API.
 *
 * <p>사용자 식별자는 요청 본문이 아니라 인증 컨텍스트에서만 꺼낸다. 본문으로 받으면
 * 남의 장착 상태를 바꾸는 요청을 그대로 받아들이게 된다.
 *
 * <p>{@code /api/cosmetics} 는 SecurityConfig 의 별도 매처에 걸리지 않아
 * {@code anyRequest().authenticated()} 를 탄다. 비로그인 요청은 401 이다.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/cosmetics")
public class CosmeticController {

    private final CosmeticService cosmeticService;

    @GetMapping("")
    public CommonResponse<CosmeticListResponseDto> getCosmetics() {
        Long userId = (Long) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return CommonResponse.success(cosmeticService.getCosmetics(userId));
    }

    /**
     * 슬롯 하나 장착. {@code itemKey} 가 null 이면 해제한다.
     *
     * <p>같은 슬롯을 다시 걸어도 같은 결과가 되는 멱등 연산이라 POST 가 아니라 PUT 이다.
     */
    @PutMapping("/equip")
    public CommonResponse<CosmeticEquipResponseDto> equip(@Valid @RequestBody CosmeticEquipRequestDto request) {
        Long userId = (Long) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return CommonResponse.success(cosmeticService.equip(userId, request.slot(), request.itemKey()));
    }

    @PutMapping("/equip-set")
    public CommonResponse<CosmeticEquipResponseDto> equipSet(@Valid @RequestBody CosmeticEquipSetRequestDto request) {
        Long userId = (Long) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return CommonResponse.success(cosmeticService.equipSet(userId, request.setId()));
    }
}
