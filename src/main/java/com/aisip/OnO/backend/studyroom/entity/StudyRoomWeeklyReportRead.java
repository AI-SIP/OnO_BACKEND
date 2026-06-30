package com.aisip.OnO.backend.studyroom.entity;

import com.aisip.OnO.backend.common.entity.BaseEntity;
import com.aisip.OnO.backend.user.entity.User;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Getter
@Entity
@Builder(access = AccessLevel.PRIVATE)
@AllArgsConstructor(access = AccessLevel.PROTECTED)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "study_room_weekly_report_read",
        uniqueConstraints = @UniqueConstraint(name = "uk_study_room_weekly_report_read", columnNames = {"report_id", "user_id"}))
public class StudyRoomWeeklyReportRead extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "report_id", nullable = false)
    private StudyRoomWeeklyReport report;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "read_at", nullable = false)
    private LocalDateTime readAt;

    public static StudyRoomWeeklyReportRead create(StudyRoomWeeklyReport report, User user, LocalDateTime readAt) {
        return StudyRoomWeeklyReportRead.builder()
                .report(report)
                .user(user)
                .readAt(readAt)
                .build();
    }
}
