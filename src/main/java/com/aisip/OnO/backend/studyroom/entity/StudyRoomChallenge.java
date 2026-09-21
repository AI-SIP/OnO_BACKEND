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

    /**
     * 챌린지를 만든 사용자.
     *
     * <p>{@code StudyRoom.hostUserId} 와 같이 FK 없는 식별자로 둔다. 사용자 탈퇴는 소프트 삭제라
     * 행이 남지만, 방장 식별자와 같은 방식을 쓰는 편이 이 도메인 안에서 일관된다.
     * 기존 행은 V46 마이그레이션에서 방장으로 채웠다(그때까지 방장만 지울 수 있었다).
     */
    @Column(name = "created_by_user_id", nullable = false)
    private Long createdByUserId;

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

    public static StudyRoomChallenge create(StudyRoom room, Long createdByUserId, String title,
                                            StudyRoomChallengeType type,
                                            StudyRoomChallengeMetric metric, StudyRoomChallengePeriod period,
                                            Integer periodDays, Integer targetValue,
                                            LocalDateTime startAt, LocalDateTime endAt) {
        return StudyRoomChallenge.builder()
                .room(room)
                .createdByUserId(createdByUserId)
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

    /** 이 챌린지를 만든 사용자인지 확인한다. */
    public boolean isCreatedBy(Long userId) {
        return userId != null && userId.equals(createdByUserId);
    }

    public void updateStatus(StudyRoomChallengeStatus status) {
        this.status = status;
    }

    /**
     * 완료 상태로 전이하면서 완료 시각까지 함께 채운다.
     *
     * <p>완료 전이는 중복 알림을 막으려고 {@code tryTransitionFromInProgress} 벌크 UPDATE 로
     * 먼저 DB 를 바꾼다. 벌크 UPDATE 는 영속성 컨텍스트를 우회하므로, 이어서
     * {@code updateStatus} 만 호출하면 커밋 시점의 더티 체킹 UPDATE 가 메모리에 남아 있던
     * {@code completedAt = null} 을 그대로 덮어써 완료 시각이 사라진다.
     * 두 필드를 함께 맞춰 두어야 벌크 UPDATE 결과가 유지된다.
     */
    public void markCompleted(LocalDateTime completedAt) {
        this.status = StudyRoomChallengeStatus.COMPLETED;
        this.completedAt = completedAt;
    }
}
