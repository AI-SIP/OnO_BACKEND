package com.aisip.OnO.backend.entity.User;

import com.aisip.OnO.backend.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
public class User extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String email;

    private String name;

    private String identifier;

    private String password;

    @Enumerated(EnumType.STRING)
    private UserType type;
}

