package com.aisip.OnO.backend.studyroom.entity;

import com.aisip.OnO.backend.common.entity.BaseEntity;
import com.aisip.OnO.backend.user.entity.User;
import jakarta.persistence.*;
import lombok.*;

@Getter
@Entity
@Builder(access = AccessLevel.PRIVATE)
@AllArgsConstructor(access = AccessLevel.PROTECTED)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "study_room_shared_problem_reaction",
        uniqueConstraints = @UniqueConstraint(name = "uk_study_room_shared_problem_reaction", columnNames = {"shared_problem_id", "user_id", "emoji"}))
public class StudyRoomSharedProblemReaction extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shared_problem_id", nullable = false)
    private StudyRoomSharedProblem sharedProblem;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 16)
    private String emoji;

    public static StudyRoomSharedProblemReaction create(StudyRoomSharedProblem sharedProblem, User user, String emoji) {
        return StudyRoomSharedProblemReaction.builder()
                .sharedProblem(sharedProblem)
                .user(user)
                .emoji(emoji)
                .build();
    }
}
