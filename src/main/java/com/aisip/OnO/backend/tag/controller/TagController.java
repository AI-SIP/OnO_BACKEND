package com.aisip.OnO.backend.tag.controller;

import com.aisip.OnO.backend.common.response.CommonResponse;
import com.aisip.OnO.backend.tag.dto.TagCreateRequestDto;
import com.aisip.OnO.backend.tag.dto.TagResponseDto;
import com.aisip.OnO.backend.tag.service.TagService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

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

    @GetMapping("")
    public CommonResponse<List<TagResponseDto>> getUserTags() {
        Long userId = (Long) SecurityContextHolder.getContext().getAuthentication().getPrincipal();

        return CommonResponse.success(tagService.getUserTags(userId));
    }

    @DeleteMapping("/{tagId}")
    public CommonResponse<String> deleteTag(@PathVariable("tagId") Long tagId) {
        Long userId = (Long) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        tagService.deleteTag(userId, tagId);

        return CommonResponse.success("태그가 삭제되었습니다.");
    }
}
