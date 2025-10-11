package com.aisip.OnO.backend.practicenote.dto;

import java.util.List;

public record PracticeNoteUpdateDto (
        Long practiceNoteId,

        String practiceTitle,

        List<Long> addProblemIdList,

        List<Long> removeProblemIdList,

        PracticeNotificationRegisterDto practiceNotification
){}
