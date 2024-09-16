package com.aisip.OnO.backend.Dto.User;

import com.aisip.OnO.backend.entity.User.UserType;
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

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
