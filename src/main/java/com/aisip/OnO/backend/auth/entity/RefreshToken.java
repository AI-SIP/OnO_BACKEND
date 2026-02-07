package com.aisip.OnO.backend.auth.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Getter
@NoArgsConstructor
@Builder(access = AccessLevel.PRIVATE)
@AllArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "refresh_token", indexes = {
        @Index(name = "idx_refresh_token_user_id", columnList = "user_id")
})
public class RefreshToken {

    @Id
    @GeneratedValue(generator = "UUID")
    private UUID id;

    @Column(nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private Authority authority;

    @Column(nullable = false)
    private String refreshToken;

    public static RefreshToken from(Long userId, Authority authority, String refreshToken) {
        return RefreshToken.builder()
                .userId(userId)
                .authority(authority)
                .refreshToken(refreshToken)
                .build();
    }
}
