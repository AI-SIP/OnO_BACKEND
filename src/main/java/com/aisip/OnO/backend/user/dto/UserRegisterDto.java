package com.aisip.OnO.backend.user.dto;

import lombok.Builder;

@Builder
public record UserRegisterDto(
    String email,
    String name,
    String identifier,
    String platform,

    String password
) {}