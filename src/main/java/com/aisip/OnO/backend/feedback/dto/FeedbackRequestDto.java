package com.aisip.OnO.backend.feedback.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class FeedbackRequestDto {

    // Q1: 사용 목적 (다중 선택 + 기타 텍스트)
    private List<String> usagePurpose;
    private String usagePurposeOther;

    // Q2: 사용 빈도
    private String usageFrequency;

    // Q3: NPS
    private Integer npsScore;

    // Q4: 등록·조회 불편 (다중 선택 + 기타 텍스트)
    private List<String> registrationPainPoints;
    private String registrationPainPointsOther;

    // Q5: 분류 방식
    private String classificationMethod;

    // Q6: 템플릿 적합도
    private Integer templateSatisfaction;

    // Q_복습세트 사용 여부 (true=사용 중, false=사용 안 함)
    private Boolean practiceNoteUsed;

    // 복습 세트 사용 중일 때 → Q7: 알림 효과
    private String notificationEffectiveness;

    // 복습 세트 미사용 이유 (복수 선택 + 기타 텍스트)
    private List<String> reviewSetNonUsageReason;
    private String reviewSetNonUsageReasonOther;

    // Q10: 스터디룸 사용 여부 ("사용 중" / "사용하지 않음")
    private String studyRoomUsage;

    // 스터디룸 미사용 이유 (복수 선택 + 기타 텍스트)
    private List<String> studyRoomNonUsageReason;
    private String studyRoomNonUsageReasonOther;

    // 스터디룸 사용 중일 때
    // Q11: 챌린지 동기 부여
    private Integer challengeMotivation;

    // Q12: 오답 공유 유용도
    private Integer problemSharingUsefulness;

    // Q13: 자주 쓰는 기능
    private String mostUsedFeature;

    // Q14: 불편한 점
    private String painPoints;

    // Q15: 원하는 기능
    private String desiredFeatures;
}
