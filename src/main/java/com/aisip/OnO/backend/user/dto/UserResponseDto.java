package com.aisip.OnO.backend.user.dto;

import com.aisip.OnO.backend.user.entity.UserType;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.*;

import java.time.LocalDateTime;

@Data
@Builder
public class UserResponseDto {

    private Long userId;

    private String userName;

    private String userEmail;

    private String userIdentifier;

    @Enumerated(EnumType.STRING)
    private UserType userType;

    private boolean firstLogin;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
