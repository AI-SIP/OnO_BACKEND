package com.aisip.OnO.backend.Dto.User;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserRegisterDto {

    private String googleId;
    private String appleId;
    private String email;
    private String userName;
    private String socialLoginType;

    // 소셜 ID를 반환하는 메서드
    public String getSocialId() {
        if ("GOOGLE".equalsIgnoreCase(socialLoginType)) {
            return googleId;
        } else if ("APPLE".equalsIgnoreCase(socialLoginType)) {
            return appleId;
        }
        return null;
    }
}