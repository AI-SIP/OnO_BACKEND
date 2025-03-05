package com.aisip.OnO.backend.practicenote.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PracticeNoteRegisterDto {

    private Long practiceId;

    private String practiceTitle;

    private List<Long> registerProblemIds;
}
