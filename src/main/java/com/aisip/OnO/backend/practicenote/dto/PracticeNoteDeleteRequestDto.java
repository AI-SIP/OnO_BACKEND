package com.aisip.OnO.backend.practicenote.dto;

import java.util.List;

public record PracticeNoteDeleteRequestDto(
        List<Long> deletePracticeIdList
) {
}
