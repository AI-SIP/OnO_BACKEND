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
     * @throws org.springframework.amqp.AmqpException 브로커에 메시지를 넘기지 못했을 때.
     *         삼키면 한도는 이미 썼는데 상태가 PROCESSING 으로 영원히 남으므로, 호출한 쪽이 실패로 정리하게 던진다.
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
            throw e;
        }
    }
}
