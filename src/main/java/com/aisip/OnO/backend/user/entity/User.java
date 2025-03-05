package com.aisip.OnO.backend.user.entity;

import com.aisip.OnO.backend.common.entity.BaseEntity;
import com.aisip.OnO.backend.problem.entity.Folder;
import com.aisip.OnO.backend.problem.entity.Problem;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

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

    private String platform;

    private String password;

    @Enumerated(EnumType.STRING)
    private UserType type;

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<Problem> problems;

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<Folder> folders;
}

