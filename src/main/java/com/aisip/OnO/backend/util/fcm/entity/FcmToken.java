package com.aisip.OnO.backend.util.fcm.entity;

import com.aisip.OnO.backend.common.entity.BaseEntity;
import com.aisip.OnO.backend.util.fcm.dto.FcmTokenRequestDto;
import jakarta.persistence.*;
import lombok.*;

@Getter
@Entity
@Builder(access = AccessLevel.PRIVATE)
@AllArgsConstructor(access = AccessLevel.PROTECTED)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "fcm_token")
public class FcmToken extends BaseEntity {

    @Id
    @GeneratedValue
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false, unique = true)
    private String token;

    public static FcmToken From(FcmTokenRequestDto fcmTokenRequestDto, Long userId) {
        return FcmToken.builder()
                .userId(userId)
                .token(fcmTokenRequestDto.token())
                .build();
    }
}
