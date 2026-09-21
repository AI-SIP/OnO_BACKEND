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

    /**
     * 원본 JWT 를 그대로 담는다. 현재 발급 형태(authority·sub·iat·jti·exp)만으로 최대 244자라
     * 기본 길이(varchar 255)로는 클레임이 하나만 늘어도 넘친다.
     * 저장이 잘리면 이후 갱신 요청이 세션을 찾지 못해 REFRESH_TOKEN_NOT_FOUND(1002) 가 된다.
     */
    @Column(nullable = false, length = 512)
    private String refreshToken;

    public static RefreshToken from(Long userId, Authority authority, String refreshToken) {
        return RefreshToken.builder()
                .userId(userId)
                .authority(authority)
                .refreshToken(refreshToken)
                .build();
    }

    public void updateToken(Authority authority, String refreshToken) {
        this.authority = authority;
        this.refreshToken = refreshToken;
    }
}
