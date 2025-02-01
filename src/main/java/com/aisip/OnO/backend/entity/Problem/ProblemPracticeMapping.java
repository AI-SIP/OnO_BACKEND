package com.aisip.OnO.backend.entity.Problem;

import com.aisip.OnO.backend.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Getter
@Setter
@Table(name = "problem_practice_mapping")
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProblemPracticeMapping extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "problem_practice_id", referencedColumnName = "id", nullable = false)
    private Practice practice;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "problem_id", referencedColumnName = "id", nullable = false)
    private Problem problem;

    // 중간 테이블이므로 추가적인 컬럼을 넣을 수도 있음
    private Long practiceCount;
}