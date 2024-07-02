package com.aisip.OnO.backend.converter;

import com.aisip.OnO.backend.Dto.User.UserResponseDto;
import com.aisip.OnO.backend.entity.User;

public class UserConverter {

    public static UserResponseDto convertToResponseDto(User user) {
        if(user == null){
            return null;
        }

        UserResponseDto dto = new UserResponseDto();
        dto.setUserId(user.getId());
        dto.setUserName(user.getUserName());

        return dto;
    }
}
