package com.aisip.OnO.backend.entity.Problem;

import com.aisip.OnO.backend.entity.BaseEntity;
import com.aisip.OnO.backend.entity.User.User;
import jakarta.persistence.*;
import lombok.*;

import java.util.List;

@Entity
@Getter
@Setter
@Table(name = "problem_practice")
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProblemPractice extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", referencedColumnName = "id")
    private User user;

    private String title;  // 복습 목록의 제목, 예: "시험 대비 복습", "중요 문제 모음" 등

    private Long practiceCount;

    @OneToMany(fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    @JoinTable(
            name = "problem_practice_problems", // 중간 테이블 이름
            joinColumns = @JoinColumn(name = "problem_practice_id"), // ProblemPractice 엔티티의 외래 키
            inverseJoinColumns = @JoinColumn(name = "problem_id") // Problem 엔티티의 외래 키
    )
    private List<Problem> problems;  // 복습에 포함된 문제 목록
}
