package com.aisip.OnO.backend.Dto.User;

import lombok.*;

@Data
@Builder
public class UserResponseDto {

    private Long userId;

    private String userName;

    private String userEmail;
}
