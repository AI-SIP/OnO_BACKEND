package com.aisip.OnO.backend.entity.Problem;

import com.aisip.OnO.backend.entity.BaseEntity;
import com.aisip.OnO.backend.entity.User.User;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Getter
@Setter
@Table(name = "problem_practice")
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Practice extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", referencedColumnName = "id")
    private User user;

    private String title;  // 복습 목록의 제목, 예: "시험 대비 복습", "중요 문제 모음" 등

    private Long practiceCount;

    private LocalDateTime lastSolvedAt;

    @OneToMany(mappedBy = "problemPractice", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ProblemPracticeMapping> problemPracticeMappings;
}
