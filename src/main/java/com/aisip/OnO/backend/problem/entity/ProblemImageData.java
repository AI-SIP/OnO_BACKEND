package com.aisip.OnO.backend.problem.entity;

import com.aisip.OnO.backend.common.entity.BaseEntity;
import com.aisip.OnO.backend.problem.dto.ProblemImageDataRegisterDto;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Getter
@Builder(access = AccessLevel.PRIVATE)
@AllArgsConstructor(access = AccessLevel.PROTECTED)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "image_data")
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

    public static ProblemImageData from(ProblemImageDataRegisterDto problemImageDataRegisterDto, Problem problem) {
        return ProblemImageData.builder()
                .problem(problem)
                .imageUrl(problemImageDataRegisterDto.imageUrl())
                .problemImageType(problemImageDataRegisterDto.problemImageType())
                .build();
    }
}
