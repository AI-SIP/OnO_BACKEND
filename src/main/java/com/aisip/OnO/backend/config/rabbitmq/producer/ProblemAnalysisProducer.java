package com.aisip.OnO.backend.config.rabbitmq.producer;

import com.aisip.OnO.backend.config.rabbitmq.RabbitMQConfig;
import com.aisip.OnO.backend.config.rabbitmq.message.ProblemAnalysisMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

/**
 * GPT 문제 분석 메시지 Producer
 * - 메시지를 RabbitMQ로 전송
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProblemAnalysisProducer {

    private final RabbitTemplate rabbitTemplate;

    /**
     * GPT 문제 분석 메시지 전송
     * @param problemId 분석할 문제 ID
     */
    public void sendAnalysisMessage(Long problemId) {
        try {
            ProblemAnalysisMessage message = new ProblemAnalysisMessage(problemId);

            rabbitTemplate.convertAndSend(
                    RabbitMQConfig.ANALYSIS_EXCHANGE,
                    RabbitMQConfig.GPT_ANALYSIS_ROUTING_KEY,
                    message
            );

            log.info("RabbitMQ message sent - exchange: {}, routingKey: {}, operation: {}, problemId: {}",
                    RabbitMQConfig.ANALYSIS_EXCHANGE, RabbitMQConfig.GPT_ANALYSIS_ROUTING_KEY,
                    "problem_analysis", problemId);
        } catch (Exception e) {
            log.error("RabbitMQ message send failed - exchange: {}, routingKey: {}, operation: {}, problemId: {}, error: {}",
                    RabbitMQConfig.ANALYSIS_EXCHANGE, RabbitMQConfig.GPT_ANALYSIS_ROUTING_KEY,
                    "problem_analysis", problemId, e.getMessage(), e);
            // 메시지 전송 실패해도 예외를 던지지 않음 (분석은 선택적 기능)
        }
    }
}
