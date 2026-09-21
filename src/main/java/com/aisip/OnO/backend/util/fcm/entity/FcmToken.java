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
@Table(name = "fcm_token",
    uniqueConstraints = {
        @UniqueConstraint(name = "idx_fcm_token_user_token", columnNames = {"user_id", "token"})
    },
    // 유니크 인덱스는 선두가 user_id 라 token 단독 조회가 타지 못한다.
    // 등록 때마다 이전 소유자 행을 token 으로 찾으므로 보조 인덱스를 따로 둔다. (운영 반영은 V44)
    indexes = {
        @Index(name = "idx_fcm_token_token", columnList = "token")
    }
)
public class FcmToken extends BaseEntity {

    @Id
    @GeneratedValue
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private String token;

    public static FcmToken From(FcmTokenRequestDto fcmTokenRequestDto, Long userId) {
        return FcmToken.builder()
                .userId(userId)
                .token(fcmTokenRequestDto.token())
                .build();
    }
}
