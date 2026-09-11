package com.aisip.OnO.backend.cosmetic.entity;

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
import java.util.Arrays;
import java.util.List;

/**
 * 꾸미기 아이템 하나. 관리자 화면은 2차라 1차에서는 마이그레이션 시드로만 들어온다.
 *
 * <p>애플리케이션은 이 테이블을 읽기만 한다. 그래서 {@code BaseEntity} 를 상속하지 않는다.
 * 상속하면 쓰지도 않는 {@code deleted_at} 이 딸려 와 V34 스키마와 어긋난다.
 * {@code createdAt}/{@code updatedAt} 은 시드가 채우는 값을 그대로 읽기만 한다.
 *
 * <p>{@code imageUrl} 은 지금 번들 상대 경로({@code assets/Cosmetic/hat_beanie.png})다.
 * S3 로 옮길 때 이 컬럼만 {@code https://...} 로 바꾸면 앱 배포 없이 전환된다.
 * 프론트가 {@code http} 로 시작하는지로 갈라 처리한다.
 */
@Entity
@Getter
@AllArgsConstructor(access = AccessLevel.PROTECTED)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "cosmetic_item",
        uniqueConstraints = @UniqueConstraint(name = "uk_cosmetic_item_key", columnNames = "item_key"),
        indexes = {
                // 레벨업 해금 알림이 "levelBefore < required_level <= levelAfter 인 활성 아이템" 을 찾는다.
                @Index(name = "idx_cosmetic_item_active_level", columnList = "active, required_level"),
                @Index(name = "idx_cosmetic_item_set", columnList = "set_id")
        })
public class CosmeticItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "item_key", nullable = false, length = 64)
    private String itemKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "slot", nullable = false, length = 32)
    private CosmeticSlot slot;

    @Column(name = "name_ko", nullable = false, length = 64)
    private String nameKo;

    @Column(name = "image_url", nullable = false, length = 512)
    private String imageUrl;

    /**
     * 이 레벨부터 열린다. {@code null} 이면 레벨로는 열리지 않는다.
     *
     * <p>0 이나 -1 같은 마법값을 쓰지 않는다. "레벨 조건이 없다"와 "레벨 0 이면 된다"는 다른 말이고,
     * 마법값을 쓰면 {@code required_level <= level} 비교가 조용히 전부 참이 된다.
     */
    @Column(name = "required_level")
    private Integer requiredLevel;

    @Column(name = "set_id", length = 64)
    private String setId;

    /** 같이 걸 수 없는 아이템의 {@code item_key} 목록. 콤마 구분. 지금은 전부 비어 있다. */
    @Column(name = "conflicts_with", length = 512)
    private String conflictsWith;

    @Column(name = "active", nullable = false)
    private boolean active;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    /**
     * 보유 여부. 저장하지 않고 매번 계산한다.
     *
     * <p>보유를 테이블로 두면 레벨이 오를 때마다 지급 배치가 필요하고, 그 배치가 한 번 밀리면
     * 사용자는 레벨은 올랐는데 아이템이 안 열린 상태로 남는다. 계산으로 두면 그 상태 자체가 없다.
     * 테마 해금이 같은 방식을 쓴다.
     *
     * <p>경계는 포함이다. {@code required_level == totalStudyLevel} 이면 열린 것이다.
     * 레벨 6 짜리 비니는 레벨 6 이 되는 순간 써야지 7 이 돼서 열리면 안 된다.
     */
    public boolean isOwnedAt(long totalStudyLevel) {
        return requiredLevel != null && requiredLevel <= totalStudyLevel;
    }

    /**
     * 충돌 목록을 잘라서 돌려준다.
     *
     * <p>비어 있는 조각은 버린다. 운영에서 콤마를 하나 더 찍는 실수(`a,,b`, `a,`)로
     * 빈 문자열이 아이템 키로 취급되면 엉뚱한 슬롯이 해제될 수 있다.
     */
    public List<String> conflictKeys() {
        if (conflictsWith == null || conflictsWith.isBlank()) {
            return List.of();
        }
        return Arrays.stream(conflictsWith.split(","))
                .map(String::trim)
                .filter(key -> !key.isEmpty())
                .toList();
    }

    public boolean isEquippable() {
        return slot != null && slot.isEquippable();
    }
}
