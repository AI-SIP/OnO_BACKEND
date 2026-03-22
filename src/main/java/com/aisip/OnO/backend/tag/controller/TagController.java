package com.aisip.OnO.backend.tag.controller;

import com.aisip.OnO.backend.common.response.CommonResponse;
import com.aisip.OnO.backend.tag.dto.TagCreateRequestDto;
import com.aisip.OnO.backend.tag.dto.TagResponseDto;
import com.aisip.OnO.backend.tag.service.TagService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/tags")
public class TagController {

    private final TagService tagService;

    @PostMapping("")
    public CommonResponse<TagResponseDto> createTag(
            @RequestBody TagCreateRequestDto tagCreateRequestDto
    ) {
        Long userId = (Long) SecurityContextHolder.getContext().getAuthentication().getPrincipal();

        return CommonResponse.success(tagService.createTag(userId, tagCreateRequestDto));
    }
}
