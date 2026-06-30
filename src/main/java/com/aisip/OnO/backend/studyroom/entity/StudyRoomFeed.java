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
@Table(name = "study_room_feed", indexes = {
        @Index(name = "idx_study_room_feed_room_created", columnList = "room_id, created_at"),
        @Index(name = "idx_study_room_feed_room_user_type_created", columnList = "room_id, user_id, event_type, created_at")
})
public class StudyRoomFeed extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id", nullable = false)
    private StudyRoom room;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 40)
    private StudyRoomFeedEventType eventType;

    @Column(name = "metadata_json", columnDefinition = "TEXT")
    private String metadataJson;

    public static StudyRoomFeed create(StudyRoom room, User user, StudyRoomFeedEventType eventType, String metadataJson) {
        return StudyRoomFeed.builder()
                .room(room)
                .user(user)
                .eventType(eventType)
                .metadataJson(metadataJson)
                .build();
    }

    public void updateMetadataJson(String metadataJson) {
        this.metadataJson = metadataJson;
    }
}
