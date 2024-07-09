package com.aisip.OnO.backend.entity;

import com.aisip.OnO.backend.entity.Image.ImageData;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Getter
@Setter
@Table(name = "problem")
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Problem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    private String memo;

    private String reference;

    private LocalDateTime solvedAt;

    private LocalDateTime createdAt;

    private LocalDateTime updateAt;

    @OneToMany(mappedBy = "problem", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<ImageData> images;
}
