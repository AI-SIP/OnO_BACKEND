package com.aisip.OnO.backend.Dto.User;

import com.aisip.OnO.backend.entity.User.UserType;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.*;

@Data
@Builder
public class UserResponseDto {

    private Long userId;

    private String userName;

    private String userEmail;

    private String userIdentifier;

    @Enumerated(EnumType.STRING)
    private UserType userType;
}
