package com.aisip.OnO.backend.feedback.service;

import com.aisip.OnO.backend.common.exception.ApplicationException;
import com.aisip.OnO.backend.feedback.dto.FeedbackRequestDto;
import com.aisip.OnO.backend.feedback.dto.FeedbackResponseDto;
import com.aisip.OnO.backend.feedback.entity.UserFeedback;
import com.aisip.OnO.backend.feedback.exception.FeedbackErrorCase;
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

    /** {@code UserFeedback.ipAddress} 컬럼 길이(varchar(50))와 맞춘다. */
    private static final int IP_ADDRESS_MAX_LENGTH = 50;

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
                .ipAddress(truncate(ipAddress, IP_ADDRESS_MAX_LENGTH))
                .submittedAt(LocalDateTime.now())
                .build();

        feedbackRepository.save(feedback);
        notifyDiscord(feedback);
    }

    /**
     * 관리자 화면의 페이지네이션 입력은 그대로 신뢰할 수 없다.
     * page 가 음수이거나 size 가 0 이하이면 {@link PageRequest#of}가 IllegalArgumentException 을 던져
     * 관리자 화면이 통째로 500 이 됐다. 잘못된 파라미터는 유효한 범위로 보정한다.
     */
    public Page<FeedbackResponseDto> findAll(int page, int size) {
        return feedbackRepository
                .findAllByOrderBySubmittedAtDesc(PageRequest.of(Math.max(page, 0), Math.max(size, 1)))
                .map(FeedbackResponseDto::from);
    }

    public FeedbackResponseDto findById(Long id) {
        return feedbackRepository.findById(id)
                .map(FeedbackResponseDto::from)
                // IllegalArgumentException 은 GlobalExceptionHandler 의 마지막 Exception 핸들러로 떨어져
                // 500 + Discord 에러 알림이 됐다. 없는 리소스 조회는 404 다.
                .orElseThrow(() -> new ApplicationException(FeedbackErrorCase.FEEDBACK_NOT_FOUND));
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

    /**
     * IP 는 사용자가 {@code X-Forwarded-For} 헤더로 얼마든지 길게 조작할 수 있는 값이다.
     * {@code ip_address} 컬럼은 varchar(50) 이므로 그대로 넣으면 Data too long 으로 저장이 실패하고,
     * 설문 응답 자체가 버려진다. 응답 내용과 달리 IP 는 부가 정보이므로 잘라서 담는다.
     */
    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
