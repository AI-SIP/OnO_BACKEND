package com.aisip.OnO.backend.tag.dto;

import com.aisip.OnO.backend.tag.entity.Tag;

public record TagResponseDto(
        Long tagId,
        String name
) {
    public static TagResponseDto from(Tag tag) {
        return new TagResponseDto(tag.getId(), tag.getName());
    }
}
