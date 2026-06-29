package com.aisip.OnO.backend.studyroom.entity;

import com.aisip.OnO.backend.common.entity.BaseEntity;
import com.aisip.OnO.backend.user.entity.User;
import jakarta.persistence.*;
import lombok.*;

import java.time.Duration;
import java.time.LocalDateTime;

@Getter
@Entity
@Builder(access = AccessLevel.PRIVATE)
@AllArgsConstructor(access = AccessLevel.PROTECTED)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "study_session", indexes = {
        @Index(name = "idx_study_session_room_ended", columnList = "room_id, ended_at"),
        @Index(name = "idx_study_session_user_ended", columnList = "user_id, ended_at")
})
public class StudySession extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id", nullable = false)
    private StudyRoom room;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    @Column(name = "ended_at")
    private LocalDateTime endedAt;

    @Column(name = "duration_minutes")
    private Integer durationMinutes;

    public static StudySession start(StudyRoom room, User user, LocalDateTime startedAt) {
        return StudySession.builder()
                .room(room)
                .user(user)
                .startedAt(startedAt)
                .build();
    }

    public void end(LocalDateTime endedAt) {
        this.endedAt = endedAt;
        this.durationMinutes = Math.toIntExact(Math.max(0, Duration.between(startedAt, endedAt).toMinutes()));
    }
}
