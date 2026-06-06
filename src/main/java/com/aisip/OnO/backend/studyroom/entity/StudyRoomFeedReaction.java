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
@Table(name = "study_room_feed_reaction",
        uniqueConstraints = @UniqueConstraint(name = "uk_study_room_feed_reaction", columnNames = {"feed_id", "user_id", "emoji"}))
public class StudyRoomFeedReaction extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "feed_id", nullable = false)
    private StudyRoomFeed feed;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 16)
    private String emoji;

    public static StudyRoomFeedReaction create(StudyRoomFeed feed, User user, String emoji) {
        return StudyRoomFeedReaction.builder()
                .feed(feed)
                .user(user)
                .emoji(emoji)
                .build();
    }
}
