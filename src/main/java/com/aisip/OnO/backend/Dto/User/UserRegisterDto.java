package com.aisip.OnO.backend.Dto.User;

import com.aisip.OnO.backend.entity.User.UserType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserRegisterDto {

    private String platform;

    private String email;

    private String name;

    private String identifier;

    @Enumerated(EnumType.STRING)
    private UserType type;
}
