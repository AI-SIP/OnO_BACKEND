package com.aisip.OnO.backend.problem.entity;

import com.aisip.OnO.backend.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "image_data")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProblemImageData extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "image_url")
    private String imageUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "image_type")
    private ProblemImageType problemImageType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "problem_id")
    private Problem problem;
}
