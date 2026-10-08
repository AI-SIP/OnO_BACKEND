package com.aisip.OnO.backend.mcp.tool;

import com.aisip.OnO.backend.feedback.dto.FeedbackResponseDto;
import com.aisip.OnO.backend.feedback.service.FeedbackService;
import com.aisip.OnO.backend.mcp.dto.McpAdminDtos.Feedback;
import com.aisip.OnO.backend.mcp.dto.McpAdminDtos.FeedbackList;
import lombok.RequiredArgsConstructor;
import org.springaicommunity.mcp.annotation.McpTool;
import org.springaicommunity.mcp.annotation.McpToolParam;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/** 관리자 피드백 화면(/admin/feedbacks)을 MCP 로 연다. 제출자 IP 는 돌려주지 않는다. */
@Component
@RequiredArgsConstructor
public class AdminFeedbackMcpTools {

    private final FeedbackService feedbackService;
    private final McpToolAudit audit;

    @McpTool(name = "list_feedbacks",
            description = "앱 안 설문 피드백을 최근 제출 순으로 봅니다. NPS 점수, 사용 목적, 불편한 점, 원하는 기능 같은 응답과 "
                    + "전체 건수, 평균 NPS 를 돌려줍니다. 불편한 점과 원하는 기능은 사용자가 직접 쓴 글입니다.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true, openWorldHint = false))
    public FeedbackList listFeedbacks(
            @McpToolParam(description = "가져올 개수. 기본 20, 최대 50", required = false) Integer limit,
            @McpToolParam(description = "0부터 시작하는 페이지", required = false) Integer page) {
        Map<String, Object> arguments = new LinkedHashMap<>();
        arguments.put("limit", limit);
        arguments.put("page", page);
        return audit.record("list_feedbacks", arguments, () -> {
            Page<FeedbackResponseDto> feedbacks = feedbackService.findAll(
                    McpToolArguments.page(page), McpToolArguments.limit(limit));
            return new FeedbackList(
                    feedbackService.count(),
                    feedbackService.averageNps(),
                    McpPage.of(feedbacks.getTotalElements(),
                            feedbacks.getContent().stream().map(AdminFeedbackMcpTools::toFeedback).toList()));
        });
    }

    private static Feedback toFeedback(FeedbackResponseDto f) {
        return new Feedback(f.getId(), f.getNpsScore(), f.getUsagePurpose(), f.getUsageFrequency(),
                f.getMostUsedFeature(), f.getPainPoints(), f.getDesiredFeatures(), f.getRegistrationPainPoints(),
                f.getClassificationMethod(), f.getTemplateSatisfaction(), f.getPracticeNoteUsed(),
                f.getNotificationEffectiveness(), f.getReviewSetNonUsageReason(), f.getStudyRoomUsage(),
                f.getStudyRoomNonUsageReason(), f.getChallengeMotivation(), f.getProblemSharingUsefulness(),
                f.getSubmittedAt());
    }
}
