package com.aisip.OnO.backend.tag.dto;

import java.util.List;

public record TagDeleteRequestDto(
        List<Long> deleteTagIdList
) {
}
