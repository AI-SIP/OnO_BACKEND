package com.aisip.OnO.backend.config.rabbitmq.consumer;

import com.aisip.OnO.backend.common.exception.ApplicationException;
import com.aisip.OnO.backend.config.rabbitmq.RabbitMQConfig;
import com.aisip.OnO.backend.config.rabbitmq.message.ProblemAnalysisMessage;
import com.aisip.OnO.backend.problem.exception.ProblemErrorCase;
import com.aisip.OnO.backend.problem.service.ProblemAnalysisService;
import com.aisip.OnO.backend.util.ai.NonRetryableAnalysisException;
import com.aisip.OnO.backend.util.webhook.DiscordWebhookNotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;

/**
 * GPT 문제 분석 메시지 Consumer
 * - RabbitMQ에서 메시지를 받아 GPT 분석 처리
 * - 실패 시 자동으로 DLQ로 전송 (RabbitMQ가 처리)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProblemAnalysisConsumer {

    private final ProblemAnalysisService analysisService;
    private final DiscordWebhookNotificationService discordWebhookNotificationService;

    /**
     * GPT 문제 분석 메시지 수신 및 처리
     * - concurrency: 동시 처리 개수 (1-3개, OpenAI Rate Limit 고려)
     * - 예외 발생 시 자동으로 재시도 → 실패하면 DLQ로 이동
     */
    @RabbitListener(queues = RabbitMQConfig.GPT_ANALYSIS_QUEUE, concurrency = "1-2")
    public void handleAnalysisMessage(ProblemAnalysisMessage message) {
        log.info("RabbitMQ message received - queue: {}, operation: {}, problemId: {}, messageRetryCount: {}",
                RabbitMQConfig.GPT_ANALYSIS_QUEUE, "problem_analysis", message.getProblemId(), message.getRetryCount());

        try {
            // 실제 GPT 분석 수행
            analysisService.analyzeProblemSync(message.getProblemId());

            log.info("RabbitMQ message processed - queue: {}, operation: {}, outcome: {}, problemId: {}",
                    RabbitMQConfig.GPT_ANALYSIS_QUEUE, "problem_analysis", "success", message.getProblemId());

        } catch (NonRetryableAnalysisException e) {
            log.warn("RabbitMQ message skipped - queue: {}, operation: {}, outcome: {}, problemId: {}, messageRetryCount: {}, reason: {}",
                    RabbitMQConfig.GPT_ANALYSIS_QUEUE, "problem_analysis", "non_retryable_failure",
                    message.getProblemId(), message.getRetryCount(), e.getMessage());
            // 상태는 Service에서 FAILED로 업데이트됨. 재큐잉 없이 종료(ACK)
            return;
        } catch (ApplicationException e) {
            if (e.getErrorCase() == ProblemErrorCase.PROBLEM_NOT_FOUND) {
                log.warn("RabbitMQ message skipped - queue: {}, operation: {}, outcome: {}, problemId: {}, messageRetryCount: {}",
                        RabbitMQConfig.GPT_ANALYSIS_QUEUE, "problem_analysis", "resource_not_found",
                        message.getProblemId(), message.getRetryCount());
                return;
            }

            log.error("RabbitMQ message failed - queue: {}, operation: {}, outcome: {}, problemId: {}, messageRetryCount: {}, error: {}",
                    RabbitMQConfig.GPT_ANALYSIS_QUEUE, "problem_analysis", "failure",
                    message.getProblemId(), message.getRetryCount(), e.getMessage());

            // 예외를 던지면 RabbitMQ가 자동으로 재시도 or DLQ로 전송
            throw new RuntimeException("GPT 문제 분석 실패: " + message.getProblemId(), e);
        } catch (Exception e) {
            log.error("RabbitMQ message failed - queue: {}, operation: {}, outcome: {}, problemId: {}, messageRetryCount: {}, error: {}",
                    RabbitMQConfig.GPT_ANALYSIS_QUEUE, "problem_analysis", "failure",
                    message.getProblemId(), message.getRetryCount(), e.getMessage());

            // 예외를 던지면 RabbitMQ가 자동으로 재시도 or DLQ로 전송
            throw new RuntimeException("GPT 문제 분석 실패: " + message.getProblemId(), e);
        }
    }

    /**
     * DLQ 모니터링 및 알림 전송
     * - 모든 재시도 실패 후 DLQ로 들어온 메시지 처리
     * - Discord 알림 전송하여 관리자에게 수동 처리 요청
     */
    @RabbitListener(queues = RabbitMQConfig.GPT_ANALYSIS_DLQ)
    public void handleAnalysisDLQ(ProblemAnalysisMessage message) {
        log.error("RabbitMQ message moved to DLQ - queue: {}, operation: {}, outcome: {}, problemId: {}, messageRetryCount: {}",
                RabbitMQConfig.GPT_ANALYSIS_DLQ, "problem_analysis", "dlq",
                message.getProblemId(), message.getRetryCount());

        // Discord 알림 전송
        String errorTitle = String.format("🚨 GPT 문제 분석 최종 실패 (DLQ)");
        String errorDetails = String.format(
                "**Queue:** %s\n**Operation:** %s\n**Problem ID:** %d\n**Message Retry Count:** %d\n\n" +
                "모든 재시도가 실패했습니다. 문제를 확인하고 수동으로 재분석을 요청해주세요.",
                RabbitMQConfig.GPT_ANALYSIS_DLQ,
                "problem_analysis",
                message.getProblemId(),
                message.getRetryCount()
        );

        try {
            discordWebhookNotificationService.sendErrorNotification(
                    "RabbitMQ DLQ - GPT Analysis",
                    errorDetails,
                    "ERROR",
                    errorTitle
            );
            log.info("RabbitMQ DLQ notification sent - queue: {}, operation: {}, problemId: {}",
                    RabbitMQConfig.GPT_ANALYSIS_DLQ, "problem_analysis", message.getProblemId());
        } catch (Exception e) {
            log.error("RabbitMQ DLQ notification failed - queue: {}, operation: {}, problemId: {}, error: {}",
                    RabbitMQConfig.GPT_ANALYSIS_DLQ, "problem_analysis", message.getProblemId(), e.getMessage());
        }
    }
}
