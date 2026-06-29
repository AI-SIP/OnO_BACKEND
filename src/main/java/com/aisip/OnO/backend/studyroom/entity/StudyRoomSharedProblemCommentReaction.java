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
@Table(name = "study_room_shared_problem_comment_reaction",
        uniqueConstraints = @UniqueConstraint(name = "uk_shared_problem_comment_reaction", columnNames = {"comment_id", "user_id", "emoji"}),
        indexes = {
                @Index(name = "idx_shared_problem_comment_reaction_comment", columnList = "comment_id"),
                @Index(name = "idx_shared_problem_comment_reaction_user", columnList = "user_id")
        })
public class StudyRoomSharedProblemCommentReaction extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "comment_id", nullable = false)
    private StudyRoomSharedProblemComment comment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 80)
    private String emoji;

    public static StudyRoomSharedProblemCommentReaction create(StudyRoomSharedProblemComment comment, User user, String emoji) {
        return StudyRoomSharedProblemCommentReaction.builder()
                .comment(comment)
                .user(user)
                .emoji(emoji)
                .build();
    }
}
