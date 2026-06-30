package com.aisip.OnO.backend.feedback.service;

import com.aisip.OnO.backend.feedback.dto.FeedbackRequestDto;
import com.aisip.OnO.backend.feedback.dto.FeedbackResponseDto;
import com.aisip.OnO.backend.feedback.entity.UserFeedback;
import com.aisip.OnO.backend.feedback.repository.UserFeedbackRepository;
import com.aisip.OnO.backend.util.webhook.DiscordWebhookNotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FeedbackService {

    private final UserFeedbackRepository feedbackRepository;
    private final DiscordWebhookNotificationService discordWebhookNotificationService;

    @Transactional
    public void save(FeedbackRequestDto dto, String ipAddress) {
        boolean reviewSetUsed = Boolean.TRUE.equals(dto.getPracticeNoteUsed());
        boolean studyRoomUsed = "사용 중".equals(dto.getStudyRoomUsage());

        UserFeedback feedback = UserFeedback.builder()
                .usagePurpose(multiSelectWithOther(dto.getUsagePurpose(), dto.getUsagePurposeOther()))
                .usageFrequency(dto.getUsageFrequency())
                .npsScore(dto.getNpsScore())
                .registrationPainPoints(multiSelectWithOther(dto.getRegistrationPainPoints(), dto.getRegistrationPainPointsOther()))
                .classificationMethod(dto.getClassificationMethod())
                .templateSatisfaction(dto.getTemplateSatisfaction())
                .practiceNoteUsed(dto.getPracticeNoteUsed())
                .notificationEffectiveness(reviewSetUsed ? dto.getNotificationEffectiveness() : null)
                .reviewSetNonUsageReason(!reviewSetUsed ? multiSelectWithOther(dto.getReviewSetNonUsageReason(), dto.getReviewSetNonUsageReasonOther()) : null)
                .studyRoomUsage(dto.getStudyRoomUsage())
                .studyRoomNonUsageReason(!studyRoomUsed ? multiSelectWithOther(dto.getStudyRoomNonUsageReason(), dto.getStudyRoomNonUsageReasonOther()) : null)
                .challengeMotivation(studyRoomUsed ? dto.getChallengeMotivation() : null)
                .problemSharingUsefulness(studyRoomUsed ? dto.getProblemSharingUsefulness() : null)
                .mostUsedFeature(dto.getMostUsedFeature())
                .painPoints(nullIfBlank(dto.getPainPoints()))
                .desiredFeatures(nullIfBlank(dto.getDesiredFeatures()))
                .ipAddress(ipAddress)
                .submittedAt(LocalDateTime.now())
                .build();

        feedbackRepository.save(feedback);
        notifyDiscord(feedback);
    }

    public Page<FeedbackResponseDto> findAll(int page, int size) {
        return feedbackRepository
                .findAllByOrderBySubmittedAtDesc(PageRequest.of(page, size))
                .map(FeedbackResponseDto::from);
    }

    public FeedbackResponseDto findById(Long id) {
        return feedbackRepository.findById(id)
                .map(FeedbackResponseDto::from)
                .orElseThrow(() -> new IllegalArgumentException("피드백을 찾을 수 없습니다: " + id));
    }

    public long count() {
        return feedbackRepository.count();
    }

    public Double averageNps() {
        return feedbackRepository.findAverageNpsScore();
    }

    private void notifyDiscord(UserFeedback f) {
        try {
            StringBuilder sb = new StringBuilder();
            appendField(sb, "NPS",      f.getNpsScore() != null ? f.getNpsScore() + " / 10" : null);
            appendField(sb, "사용 목적",  f.getUsagePurpose());
            appendField(sb, "사용 빈도",  f.getUsageFrequency());
            appendField(sb, "복습 세트",  f.getPracticeNoteUsed() == null ? null
                    : (f.getPracticeNoteUsed() ? "사용 중" : "미사용"));
            appendField(sb, "스터디룸",   f.getStudyRoomUsage());
            appendField(sb, "불편한 점",  f.getPainPoints());
            appendField(sb, "원하는 기능", f.getDesiredFeatures());
            discordWebhookNotificationService.sendMessage("📋 새 유저 피드백 도착", sb.toString().trim());
        } catch (Exception e) {
            // 알림 실패가 저장 트랜잭션에 영향을 주지 않도록 로그만 남김
            log.warn("Discord 피드백 알림 전송 실패: {}", e.getMessage());
        }
    }

    private void appendField(StringBuilder sb, String label, String value) {
        if (value != null && !value.isBlank()) {
            sb.append("**").append(label).append("**: ").append(value).append("\n");
        }
    }

    private String multiSelectWithOther(List<String> items, String otherText) {
        boolean hasOther = otherText != null && !otherText.isBlank();
        if (CollectionUtils.isEmpty(items)) {
            return hasOther ? "기타: " + otherText.trim() : null;
        }
        List<String> result = new ArrayList<>(items);
        if (hasOther) {
            result.remove("기타");
            result.add("기타: " + otherText.trim());
        }
        return String.join(",", result);
    }

    private String nullIfBlank(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }
}
