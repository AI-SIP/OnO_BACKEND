package com.aisip.OnO.backend.mission.entity;

import com.aisip.OnO.backend.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 사용자별 미션 진행도. (userId, missionId, periodKey) 하나당 한 행이다.
 *
 * <p>진행도 증가는 이 엔티티를 통하지 않는다. 읽고 나서 쓰면 같은 사용자의 요청이 겹칠 때
 * 증가분이 사라지므로, {@code MissionProgressRepository} 의 upsert 한 문장으로만 올린다.
 * 이 클래스는 조회와 받기 판정에서만 쓴다.
 *
 * <p>user 와 mission_definition 에 연관관계를 걸지 않고 식별자만 들고 있다. 외래키를 걸면
 * INSERT 마다 부모 행에 공유 잠금이 붙어, 이미 사용자 행을 배타 잠금으로 잡고 있는
 * 기존 미션 적립 경로와 잠금 순서가 엇갈릴 수 있다.
 */
@Entity
@Getter
@AllArgsConstructor(access = AccessLevel.PROTECTED)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "mission_progress",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_mission_progress",
                columnNames = {"user_id", "mission_id", "period_key"}),
        indexes = {
                @Index(name = "idx_mission_progress_lookup", columnList = "user_id, period_key"),
                // 보상 획득 기록은 받은 시각 역순으로만 읽는다. 정렬 키를 뒤에 둬 정렬을 생략시킨다.
                @Index(name = "idx_mission_progress_claimed", columnList = "user_id, claimed_at")
        })
public class MissionProgress extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "mission_id", nullable = false)
    private Long missionId;

    @Column(name = "period_key", nullable = false, length = 20)
    private String periodKey;

    @Column(name = "current_value", nullable = false)
    private int currentValue;

    @Column(name = "target_snapshot", nullable = false)
    private int targetSnapshot;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "claimed_at")
    private LocalDateTime claimedAt;

    /**
     * 받은 시점의 보상 종류와 값.
     *
     * <p>기록 조회가 현재 정의를 읽으면 운영 중에 보상을 바꿨을 때 예전 기록까지 새 값으로 보인다.
     * {@code targetSnapshot} 과 같은 이유로 받는 순간의 값을 박아 둔다.
     * 이 기능 이전에 받은 행이 있을 수 있어 nullable 이고, 비어 있으면 현재 정의로 폴백한다.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "reward_type_snapshot", length = 20)
    private MissionRewardType rewardTypeSnapshot;

    @Column(name = "reward_value_snapshot")
    private Integer rewardValueSnapshot;

    public boolean isOwnedBy(Long userId) {
        return this.userId != null && this.userId.equals(userId);
    }

    public boolean isCompleted() {
        return completedAt != null;
    }

    public boolean isClaimed() {
        return claimedAt != null;
    }

    /** 받은 시점의 보상 종류. 스냅샷이 없는 옛 행은 현재 정의 값으로 답한다. */
    public MissionRewardType rewardTypeOr(MissionRewardType current) {
        return rewardTypeSnapshot != null ? rewardTypeSnapshot : current;
    }

    /** 받은 시점의 보상 값. 스냅샷이 없는 옛 행은 현재 정의 값으로 답한다. */
    public int rewardValueOr(int current) {
        return rewardValueSnapshot != null ? rewardValueSnapshot : current;
    }
}
