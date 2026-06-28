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
@Table(name = "study_room_shared_problem_comment", indexes = {
        @Index(name = "idx_shared_problem_comment_problem_id", columnList = "shared_problem_id, id"),
        @Index(name = "idx_shared_problem_comment_author", columnList = "author_id")
})
public class StudyRoomSharedProblemComment extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shared_problem_id", nullable = false)
    private StudyRoomSharedProblem sharedProblem;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "author_id", nullable = false)
    private User author;

    @Column(nullable = false, length = 1000)
    private String content;

    public static StudyRoomSharedProblemComment create(StudyRoomSharedProblem sharedProblem, User author, String content) {
        return StudyRoomSharedProblemComment.builder()
                .sharedProblem(sharedProblem)
                .author(author)
                .content(content)
                .build();
    }

    public void updateContent(String content) {
        this.content = content;
    }
}
