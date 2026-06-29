package com.aisip.OnO.backend.studyroom.entity;

import com.aisip.OnO.backend.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Getter
@Entity
@Builder(access = AccessLevel.PRIVATE)
@AllArgsConstructor(access = AccessLevel.PROTECTED)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "study_room_invite_code",
        uniqueConstraints = @UniqueConstraint(name = "uk_study_room_invite_code", columnNames = "code"),
        indexes = @Index(name = "idx_study_room_invite_room", columnList = "room_id"))
public class StudyRoomInviteCode extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id", nullable = false)
    private StudyRoom room;

    @Column(nullable = false, length = 6)
    private String code;

    @Column(name = "expired_at", nullable = false)
    private LocalDateTime expiredAt;

    public static StudyRoomInviteCode create(StudyRoom room, String code, LocalDateTime expiredAt) {
        return StudyRoomInviteCode.builder()
                .room(room)
                .code(code)
                .expiredAt(expiredAt)
                .build();
    }

    public boolean isExpired(LocalDateTime now) {
        return !expiredAt.isAfter(now);
    }
}
