package com.aisip.OnO.backend.tag.entity;

import com.aisip.OnO.backend.common.entity.BaseEntity;
import com.aisip.OnO.backend.problem.entity.Problem;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

@Entity
@Getter
@Builder(access = AccessLevel.PRIVATE)
@AllArgsConstructor(access = AccessLevel.PROTECTED)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@SQLDelete(sql = "UPDATE problem_tag_mapping SET deleted_at = now() WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
@Table(name = "problem_tag_mapping",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_problem_tag_mapping_problem_tag", columnNames = {"problem_id", "tag_id"})
        },
        indexes = {
                @Index(name = "idx_problem_tag_mapping_problem_id", columnList = "problem_id"),
                @Index(name = "idx_problem_tag_mapping_tag_id", columnList = "tag_id")
        })
public class ProblemTagMapping extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "problem_id", nullable = false)
    private Problem problem;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tag_id", nullable = false)
    private Tag tag;

    public static ProblemTagMapping from(Problem problem, Tag tag) {
        ProblemTagMapping mapping = ProblemTagMapping.builder()
                .problem(problem)
                .tag(tag)
                .build();
        problem.addTagMappingToProblem(mapping);
        return mapping;
    }
}
