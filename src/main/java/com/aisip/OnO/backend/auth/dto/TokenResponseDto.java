package com.aisip.OnO.backend.auth.dto;

import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TokenResponseDto {

    private String accessToken;
    private String refreshToken;
}
