package com.aisip.OnO.backend.entity.Problem;

import com.aisip.OnO.backend.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Getter
@Setter
@Table(name = "problem_repeat")
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProblemRepeat extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch =  FetchType.LAZY)
    @JoinColumn(name = "problem_id")
    private Problem problem;
}
