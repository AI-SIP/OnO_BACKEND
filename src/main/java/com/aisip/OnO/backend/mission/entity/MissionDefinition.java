package com.aisip.OnO.backend.mission.entity;

import com.aisip.OnO.backend.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 미션 정의. 관리자 화면은 2차라 1차에서는 마이그레이션 시드로만 들어온다.
 *
 * <p>정의를 코드가 아니라 DB 에 두는 이유는 목표치나 보상을 바꿀 때 배포 없이 고치기 위해서다.
 * 다만 진행 중이던 사용자가 바뀐 목표에 휘말리면 안 되므로, 진행도 행은 생성 시점의
 * {@code target} 을 {@code target_snapshot} 으로 복사해 두고 그 값으로 완료를 판정한다.
 */
@Entity
@Getter
@Builder(access = AccessLevel.PRIVATE)
@AllArgsConstructor(access = AccessLevel.PROTECTED)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "mission_definition",
        uniqueConstraints = @UniqueConstraint(name = "uk_mission_definition_code", columnNames = "code"))
public class MissionDefinition extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "code", nullable = false, length = 60)
    private String code;

    @Column(name = "title", nullable = false, length = 60)
    private String title;

    @Column(name = "description", nullable = false, length = 200)
    private String description;

    @Column(name = "icon_key", nullable = false, length = 40)
    private String iconKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 20)
    private MissionCategory category;

    @Enumerated(EnumType.STRING)
    @Column(name = "metric", nullable = false, length = 40)
    private MissionMetric metric;

    @Column(name = "target", nullable = false)
    private int target;

    @Enumerated(EnumType.STRING)
    @Column(name = "reward_type", nullable = false, length = 20)
    private MissionRewardType rewardType;

    @Column(name = "reward_value", nullable = false)
    private int rewardValue;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "active", nullable = false)
    private boolean active;
}
