package com.aisip.OnO.backend.auth.dto;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TokenRequestDto {
    private String idToken;
    private String accessToken;
    private String refreshToken;
}