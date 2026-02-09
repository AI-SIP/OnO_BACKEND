package com.aisip.OnO.backend.config.rabbitmq;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ 설정
 * - Exchange, Queue, Binding 정의
 * - JSON 메시지 컨버터 설정
 * - 재시도 및 DLQ(Dead Letter Queue) 설정
 */
@Configuration
public class RabbitMQConfig {

    // ==================== Exchange 정의 ====================
    public static final String FILE_EXCHANGE = "file.exchange";
    public static final String ANALYSIS_EXCHANGE = "analysis.exchange";
    public static final String NOTIFICATION_EXCHANGE = "notification.exchange";

    // ==================== Queue 정의 ====================
    public static final String S3_DELETE_QUEUE = "s3.delete.queue";
    public static final String S3_DELETE_DLQ = "s3.delete.dlq"; // Dead Letter Queue

    public static final String GPT_ANALYSIS_QUEUE = "gpt.analysis.queue";
    public static final String GPT_ANALYSIS_DLQ = "gpt.analysis.dlq";

    public static final String FCM_NOTIFICATION_QUEUE = "fcm.notification.queue";
    public static final String FCM_NOTIFICATION_DLQ = "fcm.notification.dlq";

    // ==================== Routing Key 정의 ====================
    public static final String S3_DELETE_ROUTING_KEY = "s3.delete";
    public static final String GPT_ANALYSIS_ROUTING_KEY = "gpt.analysis";
    public static final String FCM_NOTIFICATION_ROUTING_KEY = "fcm.notification";

    /**
     * JSON 메시지 컨버터 (객체를 JSON으로 직렬화/역직렬화)
     */
    @Bean
    public MessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    /**
     * RabbitTemplate (메시지 전송용)
     */
    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
        rabbitTemplate.setMessageConverter(messageConverter());
        return rabbitTemplate;
    }

    /**
     * Listener Container Factory (메시지 수신 설정)
     */
    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            ConnectionFactory connectionFactory) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(messageConverter());
        factory.setConcurrentConsumers(3); // 동시 처리 스레드 수
        factory.setMaxConcurrentConsumers(10); // 최대 동시 처리 스레드 수
        factory.setPrefetchCount(1); // 한 번에 가져올 메시지 수
        return factory;
    }

    // ==================== File Exchange & Queue ====================

    /**
     * File 관련 작업 Exchange (Direct)
     */
    @Bean
    public DirectExchange fileExchange() {
        return new DirectExchange(FILE_EXCHANGE);
    }

    /**
     * S3 파일 삭제 Queue
     * - DLQ 설정 포함
     * - TTL: 10분 (600,000ms)
     */
    @Bean
    public Queue s3DeleteQueue() {
        return QueueBuilder.durable(S3_DELETE_QUEUE)
                .withArgument("x-dead-letter-exchange", "") // 기본 Exchange로 DLQ 라우팅
                .withArgument("x-dead-letter-routing-key", S3_DELETE_DLQ)
                .withArgument("x-message-ttl", 600000) // 10분 TTL
                .build();
    }

    /**
     * S3 삭제 실패 시 저장되는 DLQ (Dead Letter Queue)
     */
    @Bean
    public Queue s3DeleteDLQ() {
        return QueueBuilder.durable(S3_DELETE_DLQ).build();
    }

    /**
     * S3 삭제 Queue와 Exchange 바인딩
     */
    @Bean
    public Binding s3DeleteBinding() {
        return BindingBuilder
                .bind(s3DeleteQueue())
                .to(fileExchange())
                .with(S3_DELETE_ROUTING_KEY);
    }

    // ==================== Analysis Exchange & Queue ====================

    /**
     * AI 분석 관련 Exchange (Direct)
     */
    @Bean
    public DirectExchange analysisExchange() {
        return new DirectExchange(ANALYSIS_EXCHANGE);
    }

    /**
     * GPT 분석 Queue
     * - DLQ 설정 포함
     * - TTL: 30분 (1,800,000ms)
     */
    @Bean
    public Queue gptAnalysisQueue() {
        return QueueBuilder.durable(GPT_ANALYSIS_QUEUE)
                .withArgument("x-dead-letter-exchange", "")
                .withArgument("x-dead-letter-routing-key", GPT_ANALYSIS_DLQ)
                .withArgument("x-message-ttl", 1800000) // 30분 TTL
                .build();
    }

    /**
     * GPT 분석 실패 시 저장되는 DLQ
     */
    @Bean
    public Queue gptAnalysisDLQ() {
        return QueueBuilder.durable(GPT_ANALYSIS_DLQ).build();
    }

    /**
     * GPT 분석 Queue와 Exchange 바인딩
     */
    @Bean
    public Binding gptAnalysisBinding() {
        return BindingBuilder
                .bind(gptAnalysisQueue())
                .to(analysisExchange())
                .with(GPT_ANALYSIS_ROUTING_KEY);
    }

    // ==================== Notification Exchange & Queue ====================

    /**
     * 알림 관련 Exchange (Direct)
     */
    @Bean
    public DirectExchange notificationExchange() {
        return new DirectExchange(NOTIFICATION_EXCHANGE);
    }

    /**
     * FCM 푸시 알림 Queue
     * - DLQ 설정 포함
     * - TTL: 5분 (300,000ms) - 알림은 빠르게 전송되어야 함
     */
    @Bean
    public Queue fcmNotificationQueue() {
        return QueueBuilder.durable(FCM_NOTIFICATION_QUEUE)
                .withArgument("x-dead-letter-exchange", "")
                .withArgument("x-dead-letter-routing-key", FCM_NOTIFICATION_DLQ)
                .withArgument("x-message-ttl", 300000) // 5분 TTL
                .build();
    }

    /**
     * FCM 알림 실패 시 저장되는 DLQ
     */
    @Bean
    public Queue fcmNotificationDLQ() {
        return QueueBuilder.durable(FCM_NOTIFICATION_DLQ).build();
    }

    /**
     * FCM 알림 Queue와 Exchange 바인딩
     */
    @Bean
    public Binding fcmNotificationBinding() {
        return BindingBuilder
                .bind(fcmNotificationQueue())
                .to(notificationExchange())
                .with(FCM_NOTIFICATION_ROUTING_KEY);
    }
}