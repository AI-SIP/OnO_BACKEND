package com.aisip.OnO.backend.converter;

import com.aisip.OnO.backend.Dto.User.UserResponseDto;
import com.aisip.OnO.backend.entity.User.User;

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
