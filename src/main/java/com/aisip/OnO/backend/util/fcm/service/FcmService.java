package com.aisip.OnO.backend.util.fcm.service;

import com.aisip.OnO.backend.common.exception.ApplicationException;
import com.aisip.OnO.backend.config.rabbitmq.producer.FcmNotificationProducer;
import com.aisip.OnO.backend.util.fcm.dto.FcmTokenRequestDto;
import com.aisip.OnO.backend.util.fcm.dto.FcmTokenResponseDto;
import com.aisip.OnO.backend.util.fcm.dto.NotificationRequestDto;
import com.aisip.OnO.backend.util.fcm.entity.FcmToken;
import com.aisip.OnO.backend.util.fcm.exception.FcmErrorCase;
import com.aisip.OnO.backend.util.fcm.repository.FcmTokenRepository;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.Notification;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class FcmService {

    private final FcmTokenRepository fcmTokenRepository;

    private final FirebaseMessaging firebaseMessaging;

    private final FcmNotificationProducer fcmNotificationProducer;

    public void registerToken(FcmTokenRequestDto fcmTokenRequestDto, Long userId) {
        if(!fcmTokenRepository.existsByUserIdAndToken(userId, fcmTokenRequestDto.token())){
            FcmToken fcmToken = FcmToken.From(fcmTokenRequestDto, userId);
            fcmTokenRepository.save(fcmToken);
        }
    }

    public FcmTokenResponseDto findToken(String token) {
        FcmToken fcmToken = fcmTokenRepository.findByToken(token)
                        .orElseThrow(() -> new ApplicationException(FcmErrorCase.FCM_TOKEN_NOT_FOUND));

        return FcmTokenResponseDto.from(fcmToken);
    }

    public List<FcmTokenResponseDto> findUserTokens(Long userId) {
        List<FcmToken> userFcmTokenList = fcmTokenRepository.findAllByUserId(userId);
        return userFcmTokenList.stream().map(FcmTokenResponseDto::from).collect(Collectors.toList());
    }

    public void sendNotification(NotificationRequestDto dto) {
        Message msg = Message.builder()
                .setToken(dto.token())
                .setNotification(Notification.builder()
                        .setTitle(dto.title())
                        .setBody(dto.body())
                        .build())
                .putAllData(dto.data())
                .build();

        try {
            String messageId = firebaseMessaging.send(msg);
            log.info("FCM 메시지 전송 성공: {}", messageId);
        } catch (FirebaseMessagingException e) {
            log.error("FCM 전송 실패", e);
            throw new ApplicationException(FcmErrorCase.FCM_SEND_FAILED);
        }
    }

    /**
     * 사용자의 모든 디바이스로 푸시 알림 전송 (RabbitMQ 비동기 방식)
     * - 기존 동기 방식에서 RabbitMQ 비동기 방식으로 변경
     * - Quartz Job이나 API에서 호출 시 즉시 반환
     */
    public void sendNotificationToAllUserDevice(Long userId, NotificationRequestDto notificationRequestDto) {
        // RabbitMQ Producer로 메시지 전송 (비동기)
        fcmNotificationProducer.sendNotificationMessage(
                userId,
                notificationRequestDto.title(),
                notificationRequestDto.body(),
                notificationRequestDto.data()
        );

        log.info("FCM 알림 메시지 큐 전송 완료 - userId: {}, title: {}", userId, notificationRequestDto.title());
    }

    /**
     * [DEPRECATED] 동기 방식 알림 전송 (테스트용으로만 사용)
     * @deprecated RabbitMQ 방식(sendNotificationToAllUserDevice)을 사용하세요
     */
    @Deprecated
    public void sendNotificationToAllUserDeviceSync(Long userId, NotificationRequestDto notificationRequestDto) {
        List<FcmToken> userFcmTokenList = fcmTokenRepository.findAllByUserId(userId);

        userFcmTokenList.forEach(fcmToken -> {
            try {
                sendNotification(new NotificationRequestDto(
                        fcmToken.getToken(),
                        notificationRequestDto.title(),
                        notificationRequestDto.body(),
                        notificationRequestDto.data()
                ));
            } catch (ApplicationException e) {
                log.warn("FCM 전송 실패 (userId: {}, token: {}): {}", userId, fcmToken.getToken(), e.getMessage());
            }
        });
    }
}
