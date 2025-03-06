package com.aisip.OnO.backend.user.dto;

import com.aisip.OnO.backend.user.entity.UserType;

public record UserRegisterDto(
    String email,
    String name,
    String identifier,
    String platform,
    UserType userType
) {}