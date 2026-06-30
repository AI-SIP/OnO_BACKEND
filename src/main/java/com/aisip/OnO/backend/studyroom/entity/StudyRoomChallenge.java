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
@Table(name = "study_room_challenge", indexes = {
        @Index(name = "idx_study_room_challenge_room_status_end", columnList = "room_id, status, end_at")
})
public class StudyRoomChallenge extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id", nullable = false)
    private StudyRoom room;

    @Column(nullable = false, length = 40)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StudyRoomChallengeType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private StudyRoomChallengeMetric metric;

    @Enumerated(EnumType.STRING)
    @Column(name = "period", length = 20)
    private StudyRoomChallengePeriod period;

    @Column(name = "period_days")
    private Integer periodDays;

    @Column(name = "target_value", nullable = false)
    private Integer targetValue;

    @Column(name = "start_at", nullable = false)
    private LocalDateTime startAt;

    @Column(name = "end_at", nullable = false)
    private LocalDateTime endAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StudyRoomChallengeStatus status;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    public static StudyRoomChallenge create(StudyRoom room, String title, StudyRoomChallengeType type,
                                            StudyRoomChallengeMetric metric, StudyRoomChallengePeriod period,
                                            Integer periodDays, Integer targetValue,
                                            LocalDateTime startAt, LocalDateTime endAt) {
        return StudyRoomChallenge.builder()
                .room(room)
                .title(title)
                .type(type)
                .metric(metric)
                .period(period)
                .periodDays(periodDays)
                .targetValue(targetValue)
                .startAt(startAt)
                .endAt(endAt)
                .status(StudyRoomChallengeStatus.IN_PROGRESS)
                .build();
    }

    public void updateStatus(StudyRoomChallengeStatus status) {
        this.status = status;
    }
}
