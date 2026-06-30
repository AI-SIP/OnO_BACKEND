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
@Table(name = "study_room_member",
        uniqueConstraints = @UniqueConstraint(name = "uk_study_room_member_room_user", columnNames = {"room_id", "user_id"}),
        indexes = {
                @Index(name = "idx_study_room_member_user", columnList = "user_id"),
                @Index(name = "idx_study_room_member_room", columnList = "room_id")
        })
public class StudyRoomMember extends BaseEntity {

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
    @Column(nullable = false, length = 20)
    private StudyRoomMemberRole role;

    @Column(name = "weekly_goal")
    private Integer weeklyGoal;

    public static StudyRoomMember create(User user, StudyRoomMemberRole role) {
        return StudyRoomMember.builder()
                .user(user)
                .role(role)
                .build();
    }

    public void updateRoom(StudyRoom room) {
        this.room = room;
    }

    public void updateWeeklyGoal(Integer weeklyGoal) {
        this.weeklyGoal = weeklyGoal;
    }

    public void promoteToHost() {
        this.role = StudyRoomMemberRole.HOST;
    }
}
