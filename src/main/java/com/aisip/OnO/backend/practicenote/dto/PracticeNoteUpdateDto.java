package com.aisip.OnO.backend.practicenote.dto;

import jakarta.validation.constraints.NotNull;

import java.util.List;

public record PracticeNoteUpdateDto (
        @NotNull(message = "practiceNoteId 는 필수입니다.")
        Long practiceNoteId,

        String practiceTitle,

        // 리스트 자체가 없으면 서비스가 빈 리스트로 본다. 안의 id 가 비어 있는 것만 막는다.
        List<@NotNull(message = "addProblemIdList 에 빈 id 가 있습니다.") Long> addProblemIdList,

        List<@NotNull(message = "removeProblemIdList 에 빈 id 가 있습니다.") Long> removeProblemIdList,

        PracticeNotificationRegisterDto practiceNotification
){}
