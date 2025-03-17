package com.aisip.OnO.backend.practicenote.dto;

import java.util.List;

public record PracticeNoteRegisterDto (
        Long practiceNoteId,

        String practiceTitle,

        List<Long> problemIdList
){}
