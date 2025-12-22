package com.aisip.OnO.backend.util.ai;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class ProblemAnalysisResult {
    private String subject;
    private String problemType;
    private List<String> keyPoints;
    private String solution;
    private String commonMistakes;
    private String studyTips;
}
