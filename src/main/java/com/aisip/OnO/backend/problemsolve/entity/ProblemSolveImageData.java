package com.aisip.OnO.backend.problemsolve.entity;

import com.aisip.OnO.backend.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

@Entity
@Getter
@Builder(access = AccessLevel.PRIVATE)
@AllArgsConstructor(access = AccessLevel.PROTECTED)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "problem_solve_image_data", indexes = {
        @Index(name = "idx_solve_image_data_solve_id", columnList = "problem_solve_id")
})
@SQLDelete(sql = "UPDATE problem_solve_image_data SET deleted_at = now() WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
public class ProblemSolveImageData extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "problem_solve_id", nullable = false)
    private ProblemSolve problemSolve;

    @Column(nullable = false)
    private String imageUrl;

    @Column(nullable = false)
    private Integer imageOrder;

    public static ProblemSolveImageData create(String imageUrl, Integer imageOrder) {
        return ProblemSolveImageData.builder()
                .imageUrl(imageUrl)
                .imageOrder(imageOrder)
                .build();
    }

    public void updateProblemSolve(ProblemSolve problemSolve) {
        this.problemSolve = problemSolve;
    }
}