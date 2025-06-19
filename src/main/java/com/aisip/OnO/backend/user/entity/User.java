package com.aisip.OnO.backend.user.entity;

import com.aisip.OnO.backend.common.entity.BaseEntity;
import com.aisip.OnO.backend.common.service.CryptoConverter;
import com.aisip.OnO.backend.mission.entity.MissionLog;
import com.aisip.OnO.backend.mission.entity.UserMissionStatus;
import com.aisip.OnO.backend.user.dto.UserRegisterDto;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import java.util.ArrayList;
import java.util.List;

@Getter
@Entity
@Builder(access = AccessLevel.PRIVATE)
@AllArgsConstructor(access = AccessLevel.PROTECTED)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@SQLDelete(sql = "UPDATE user SET deleted_at = now() WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
@Table(name = "user")
public class User extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String email;

    private String name;

    @Convert(converter = CryptoConverter.class)
    private String identifier;

    private String password;

    private String platform;

    @Embedded
    private UserMissionStatus userMissionStatus;

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL)
    private List<MissionLog> missionLogList = new ArrayList<>();

    public static User from(UserRegisterDto userRegisterDto) {
        return User.builder()
                .email(userRegisterDto.email())
                .name(userRegisterDto.name())
                .identifier(userRegisterDto.identifier())
                .platform(userRegisterDto.platform())
                .password(userRegisterDto.password())
                .userMissionStatus(new UserMissionStatus(1, 0))
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

        if (userRegisterDto.password() != null && !userRegisterDto.password().isBlank()) {
            this.password = userRegisterDto.password();
        }
    }
}

