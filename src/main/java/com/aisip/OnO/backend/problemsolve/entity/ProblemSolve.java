package com.aisip.OnO.backend.problemsolve.entity;

import com.aisip.OnO.backend.common.entity.BaseEntity;
import com.aisip.OnO.backend.problem.entity.Problem;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

@Entity
@Table(name = "problem_solve")
@Getter
@Builder(access = AccessLevel.PRIVATE)
@AllArgsConstructor(access = AccessLevel.PROTECTED)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@SQLDelete(sql = "UPDATE problem_solve SET deleted_at = now() WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
public class ProblemSolve extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "problem_id", nullable = false)
    private Problem problem;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private LocalDateTime practicedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AnswerStatus answerStatus;

    @Column(columnDefinition = "TEXT")
    private String reflection;

    @Column(columnDefinition = "TEXT")
    private String improvements;

    private Integer timeSpentSeconds;

    @Column(nullable = false)
    @Builder.Default
    private Boolean migratedFromLegacy = false;

    @OneToMany(mappedBy = "problemSolve", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<ProblemSolveImageData> images = new ArrayList<>();

    public static ProblemSolve create(Problem problem, Long userId, LocalDateTime practicedAt,
                                      AnswerStatus answerStatus, String reflection, String improvements,
                                      Integer timeSpentSeconds) {
        return ProblemSolve.builder()
                .problem(problem)
                .userId(userId)
                .practicedAt(practicedAt)
                .answerStatus(answerStatus)
                .reflection(reflection)
                .improvements(improvements)
                .timeSpentSeconds(timeSpentSeconds)
                .migratedFromLegacy(false)
                .build();
    }

    public static ProblemSolve createFromLegacy(Problem problem, Long userId, LocalDateTime practicedAt) {
        return ProblemSolve.builder()
                .problem(problem)
                .userId(userId)
                .practicedAt(practicedAt)
                .answerStatus(AnswerStatus.UNKNOWN)
                .migratedFromLegacy(true)
                .build();
    }

    public void addImage(ProblemSolveImageData image) {
        this.images.add(image);
        image.updateProblemSolve(this);
    }

    public void updateSolve(AnswerStatus answerStatus, String reflection, String improvements,
                            Integer timeSpentSeconds) {
        this.answerStatus = answerStatus;
        this.reflection = reflection;
        this.improvements = improvements;
        this.timeSpentSeconds = timeSpentSeconds;
    }
}
