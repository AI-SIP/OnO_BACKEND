package com.aisip.OnO.backend.problem.entity;

import com.aisip.OnO.backend.common.entity.BaseEntity;
import com.aisip.OnO.backend.problem.entity.Problem;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Getter
@Setter
@Table(name = "problem_repeat")
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProblemSolve extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "solve_image_url")
    private String solveImageUrl;

    @ManyToOne(fetch =  FetchType.LAZY)
    @JoinColumn(name = "problem_id")
    private Problem problem;
}
