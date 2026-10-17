package com.aisip.OnO.backend.practicenote.dto;

import jakarta.validation.constraints.NotNull;

import java.util.List;

public record PracticeNoteDeleteRequestDto(
        @NotNull(message = "deletePracticeIdList 는 필수입니다.")
        List<@NotNull(message = "deletePracticeIdList 에 빈 id 가 있습니다.") Long> deletePracticeIdList
) {
}
