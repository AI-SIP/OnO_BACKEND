package com.aisip.OnO.backend.user.dto;

public record UserRegisterDto(
    String email,
    String name,
    String identifier,
    String platform,

    String password
) {}