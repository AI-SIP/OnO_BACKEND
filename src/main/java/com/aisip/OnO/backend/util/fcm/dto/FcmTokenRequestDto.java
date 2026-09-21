package com.aisip.OnO.backend.util.fcm.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * FCM 토큰 등록 요청.
 *
 * <p>예전에는 검증이 전혀 없어서 null 토큰은 {@code fcm_token.token} 의 NOT NULL 제약에 걸려
 * DataIntegrityViolationException 으로, 255자를 넘는 토큰은 컬럼 길이 초과로 각각 DB 까지 간 뒤에야
 * 거절됐다. 빈 문자열은 아예 걸러지지 않아 푸시를 보낼 수 없는 쓰레기 행이 그대로 쌓였다.
 * 입력 검증은 DB 가 아니라 여기서 끝낸다.
 */
public record FcmTokenRequestDto(
        @NotBlank(message = "FCM 토큰은 필수입니다.")
        @Size(max = 255, message = "FCM 토큰 길이가 허용 범위를 초과했습니다.")
        String token
){
}
