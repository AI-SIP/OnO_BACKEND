package com.aisip.OnO.backend.entity.SocialLogin;

import com.aisip.OnO.backend.entity.User;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "social_login")
public class SocialLogin {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String socialId;  // 소셜 서비스에서 제공하는 고유 ID

    @Enumerated(EnumType.STRING)
    private SocialLoginType socialLoginType;  // Enum 타입의 소셜 서비스 제공자

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;  // 연결된 사용자 엔티티

    private LocalDate linkedDate;  // 계정 연결 날짜
}
