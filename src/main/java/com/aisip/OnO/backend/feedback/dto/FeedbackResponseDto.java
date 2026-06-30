package com.aisip.OnO.backend.feedback.dto;

import com.aisip.OnO.backend.feedback.entity.UserFeedback;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class FeedbackResponseDto {

    private Long id;
    private String usagePurpose;
    private String usageFrequency;
    private Integer npsScore;
    private String registrationPainPoints;
    private String classificationMethod;
    private Integer templateSatisfaction;
    private Boolean practiceNoteUsed;
    private String notificationEffectiveness;
    private String reviewSetNonUsageReason;
    private String studyRoomUsage;
    private String studyRoomNonUsageReason;
    private Integer challengeMotivation;
    private Integer problemSharingUsefulness;
    private String mostUsedFeature;
    private String painPoints;
    private String desiredFeatures;
    private String ipAddress;
    private LocalDateTime submittedAt;

    public static FeedbackResponseDto from(UserFeedback f) {
        return FeedbackResponseDto.builder()
                .id(f.getId())
                .usagePurpose(f.getUsagePurpose())
                .usageFrequency(f.getUsageFrequency())
                .npsScore(f.getNpsScore())
                .registrationPainPoints(f.getRegistrationPainPoints())
                .classificationMethod(f.getClassificationMethod())
                .templateSatisfaction(f.getTemplateSatisfaction())
                .practiceNoteUsed(f.getPracticeNoteUsed())
                .notificationEffectiveness(f.getNotificationEffectiveness())
                .reviewSetNonUsageReason(f.getReviewSetNonUsageReason())
                .studyRoomUsage(f.getStudyRoomUsage())
                .studyRoomNonUsageReason(f.getStudyRoomNonUsageReason())
                .challengeMotivation(f.getChallengeMotivation())
                .problemSharingUsefulness(f.getProblemSharingUsefulness())
                .mostUsedFeature(f.getMostUsedFeature())
                .painPoints(f.getPainPoints())
                .desiredFeatures(f.getDesiredFeatures())
                .ipAddress(f.getIpAddress())
                .submittedAt(f.getSubmittedAt())
                .build();
    }
}
