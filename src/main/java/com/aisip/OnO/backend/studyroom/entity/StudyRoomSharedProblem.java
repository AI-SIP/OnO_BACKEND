package com.aisip.OnO.backend.studyroom.entity;

import com.aisip.OnO.backend.common.entity.BaseEntity;
import com.aisip.OnO.backend.problem.entity.Problem;
import com.aisip.OnO.backend.user.entity.User;
import jakarta.persistence.*;
import lombok.*;

@Getter
@Entity
@Builder(access = AccessLevel.PRIVATE)
@AllArgsConstructor(access = AccessLevel.PROTECTED)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "study_room_shared_problem", indexes = {
        @Index(name = "idx_study_room_shared_problem_room_created", columnList = "room_id, created_at"),
        @Index(name = "idx_study_room_shared_problem_problem", columnList = "problem_id")
})
public class StudyRoomSharedProblem extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id", nullable = false)
    private StudyRoom room;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shared_by_user_id", nullable = false)
    private User sharedByUser;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "problem_id", nullable = false)
    private Problem problem;

    @Column(length = 100)
    private String comment;

    public static StudyRoomSharedProblem create(StudyRoom room, User sharedByUser, Problem problem, String comment) {
        return StudyRoomSharedProblem.builder()
                .room(room)
                .sharedByUser(sharedByUser)
                .problem(problem)
                .comment(comment)
                .build();
    }
}
