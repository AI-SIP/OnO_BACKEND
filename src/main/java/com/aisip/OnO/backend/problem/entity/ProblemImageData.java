package com.aisip.OnO.backend.problem.entity;

import com.aisip.OnO.backend.common.entity.BaseEntity;
import com.aisip.OnO.backend.problem.dto.ProblemImageDataRegisterDto;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

@Entity
@Getter
@Builder(access = AccessLevel.PRIVATE)
@AllArgsConstructor(access = AccessLevel.PROTECTED)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "image_data")
@SQLDelete(sql = "UPDATE image_data SET deleted_at = now() WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
public class ProblemImageData extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "problem_id", nullable = false)
    private Problem problem;

    @Column(name = "image_url")
    private String imageUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "image_type")
    private ProblemImageType problemImageType;

    public static ProblemImageData from(ProblemImageDataRegisterDto problemImageDataRegisterDto) {
        return ProblemImageData.builder()
                .imageUrl(problemImageDataRegisterDto.imageUrl())
                .problemImageType(problemImageDataRegisterDto.problemImageType())
                .build();
    }

    public void updateProblem(Problem problem) {
        this.problem = problem;
        problem.addImageData(this);
    }
}
