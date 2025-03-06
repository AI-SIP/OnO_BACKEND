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

    @Column(name = "problem_id")
    private Long problemId;

    @Column(name = "image_url")
    private String imageUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "image_type")
    private ProblemImageType problemImageType;

    public static ProblemImageData from(ProblemImageDataRegisterDto problemImageDataRegisterDto) {
        return ProblemImageData.builder()
                .problemId(problemImageDataRegisterDto.problemId())
                .imageUrl(problemImageDataRegisterDto.imageUrl())
                .problemImageType(problemImageDataRegisterDto.problemImageType())
                .build();
    }
}
