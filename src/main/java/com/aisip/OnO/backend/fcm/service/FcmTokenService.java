package com.aisip.OnO.backend.fcm.service;

import com.aisip.OnO.backend.common.exception.ApplicationException;
import com.aisip.OnO.backend.fcm.dto.FcmTokenRequestDto;
import com.aisip.OnO.backend.fcm.entity.FcmToken;
import com.aisip.OnO.backend.fcm.exception.FcmTokenErrorCase;
import com.aisip.OnO.backend.fcm.repository.FcmTokenRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class FcmTokenService {

    private final FcmTokenRepository fcmTokenRepository;

    public void registerToken(FcmTokenRequestDto fcmTokenRequestDto, Long userId) {
        FcmToken fcmToken = FcmToken.From(fcmTokenRequestDto, userId);
        fcmTokenRepository.save(fcmToken);
    }

    public void findToken(String token) {
        FcmToken fcmToken = fcmTokenRepository.findByToken(token)
                        .orElseThrow(() -> new ApplicationException(FcmTokenErrorCase.FCM_TOKEN_NOT_FOUND));
    }
}
