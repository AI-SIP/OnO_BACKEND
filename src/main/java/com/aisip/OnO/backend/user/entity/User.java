package com.aisip.OnO.backend.user.entity;

import com.aisip.OnO.backend.common.entity.BaseEntity;
import com.aisip.OnO.backend.user.dto.UserRegisterDto;
import jakarta.persistence.*;
import lombok.*;

@Getter
@Entity
@Builder(access = AccessLevel.PRIVATE)
@AllArgsConstructor(access = AccessLevel.PROTECTED)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "user")
public class User extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String email;

    private String name;

    private String identifier;

    private String platform;

    @Enumerated(EnumType.STRING)
    private UserType userType;

    public static User from(UserRegisterDto userRegisterDto) {
        return User.builder()
                .email(userRegisterDto.email())
                .name(userRegisterDto.name())
                .identifier(userRegisterDto.identifier())
                .platform(userRegisterDto.platform())
                .build();
    }

    public void updateUser(UserRegisterDto userRegisterDto) {
        if (userRegisterDto.email() != null && !userRegisterDto.email().isBlank()) {
            this.email = userRegisterDto.email();
        }

        if (userRegisterDto.name() != null && !userRegisterDto.name().isBlank()) {
            this.name = userRegisterDto.name();
        }

        if (userRegisterDto.identifier() != null && !userRegisterDto.identifier().isBlank()) {
            this.identifier = userRegisterDto.identifier();
        }

        if (userRegisterDto.platform() != null && !userRegisterDto.platform().isBlank()) {
            this.platform = userRegisterDto.platform();
        }

        if (userRegisterDto.userType() != null) {
            this.userType = userRegisterDto.userType();
        }
    }
}

