package com.aisip.OnO.backend.user;

import com.aisip.OnO.backend.user.dto.UserResponseDto;
import com.aisip.OnO.backend.user.entity.User;

public class UserConverter {

    public static UserResponseDto convertToResponseDto(User user) {
        return convertToResponseDto(user, false);
    }

    public static UserResponseDto convertToResponseDto(User user, boolean firstLogin) {

        if (user == null) {
            return null;
        }

        return UserResponseDto.builder()
                .userId(user.getId())
                .userName(user.getName())
                .userEmail(user.getEmail())
                .userIdentifier(user.getIdentifier())
                .userType(user.getType())
                .firstLogin(firstLogin)
                .updatedAt(user.getUpdatedAt())
                .createdAt(user.getCreatedAt())
                .build();
    }
}
