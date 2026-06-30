package com.aisip.OnO.backend.feedback.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "user_feedback")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class UserFeedback {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "usage_purpose", length = 500)
    private String usagePurpose;

    @Column(name = "usage_frequency", length = 50)
    private String usageFrequency;

    @Column(name = "nps_score")
    private Integer npsScore;

    @Column(name = "registration_pain_points", length = 500)
    private String registrationPainPoints;

    @Column(name = "classification_method", length = 50)
    private String classificationMethod;

    @Column(name = "template_satisfaction")
    private Integer templateSatisfaction;

    @Column(name = "notification_effectiveness", length = 50)
    private String notificationEffectiveness;

    @Column(name = "review_interval_satisfaction")
    private Integer reviewIntervalSatisfaction;

    @Column(name = "practice_note_used")
    private Boolean practiceNoteUsed;

    @Column(name = "practice_note_usefulness")
    private Integer practiceNoteUsefulness;

    @Column(name = "review_set_non_usage_reason", length = 300)
    private String reviewSetNonUsageReason;

    @Column(name = "study_room_usage", length = 50)
    private String studyRoomUsage;

    @Column(name = "study_room_non_usage_reason", length = 300)
    private String studyRoomNonUsageReason;

    @Column(name = "challenge_motivation")
    private Integer challengeMotivation;

    @Column(name = "problem_sharing_usefulness")
    private Integer problemSharingUsefulness;

    @Column(name = "most_used_feature", length = 100)
    private String mostUsedFeature;

    @Column(name = "pain_points", columnDefinition = "TEXT")
    private String painPoints;

    @Column(name = "desired_features", columnDefinition = "TEXT")
    private String desiredFeatures;

    @Column(name = "ip_address", length = 50)
    private String ipAddress;

    @Column(name = "submitted_at")
    private LocalDateTime submittedAt;
}
