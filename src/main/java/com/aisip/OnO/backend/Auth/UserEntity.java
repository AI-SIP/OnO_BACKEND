package com.aisip.OnO.backend.Auth;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
public class UserEntity {
    @Id
    private String userId;
    private String email;
    private String name;
}

