package com.aisip.OnO.backend.cosmetic.entity;

import com.aisip.OnO.backend.mission.entity.MissionType.AbilityType;
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
import org.hibernate.annotations.ColumnDefault;

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
 * <p>해금 조건은 {@code requiredLevel} 과 {@code requiredAbility} 두 컬럼이 함께 정한다.
 * 자세한 것은 {@link #isOwnedBy(CosmeticUnlockLevels)} 에 있다.
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

    /**
     * {@code columnDefinition} 을 못 박는 이유는 V34 의 {@code slot VARCHAR(32)} 과 맞추기 위해서다.
     *
     * <p>비워 두면 Hibernate 는 MySQL 에서 이 컬럼을 네이티브 {@code ENUM(...)} 으로 만든다.
     * 그러면 운영 스키마(VARCHAR)와 테스트 스키마(ENUM)가 갈리고, 이 enum 에서 상수를 하나 지우는
     * 순간 그 값을 담고 있던 옛 마이그레이션이 테스트에서만 "Data truncated" 로 죽는다.
     * 운영에서는 VARCHAR 라 아무 일도 없는데 테스트만 터지는 차이는 만들지 않는다.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "slot", nullable = false, length = 32, columnDefinition = "varchar(32)")
    private CosmeticSlot slot;

    /**
     * 이 아이템만의 그리는 층. {@code null} 이면 자리의 기본값({@link CosmeticSlot#getLayerOrder()})을 쓴다.
     *
     * <p>{@link CosmeticSlot#BAG} 하나를 위해 생겼다. 등에 메는 가방과 앞으로 메는 가방은 같은 자리인데
     * 그리는 층이 다르다. 전자는 개구리 본체보다 뒤(200), 후자는 옷 위(450)다.
     * 층이 다르다는 이유만으로 자리를 둘로 나누면 아이템 두세 개짜리 탭이 하나 더 생긴다.
     *
     * <p>0 이나 -1 같은 마법값을 쓰지 않는다. {@code required_level} 과 같은 이유로,
     * "덮어쓰지 않는다" 와 "0층에 그린다" 는 다른 말이다.
     *
     * <p>서버는 이 값을 풀지 않고 그대로 내려보낸다. 합성은 프론트가 하고
     * {@code item.layerOrder ?? slot.layerOrder} 로 푼다.
     */
    @Column(name = "layer_order")
    private Integer layerOrder;

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

    /**
     * {@code required_level} 을 어느 레벨과 비교할지. {@code null} 이면 총 학습 레벨이다.
     *
     * <p>테마 해금이 이미 능력치별로 돌고 있어 같은 방식을 쓴다. 총 학습 레벨 하나로만 열면
     * 앱의 능력치 네 칸짜리 스탯 화면이 보상과 아무 관계가 없어지고, 사용자에게
     * "출석 Lv.5 달성" 같은 구체적인 조건을 보여 줄 수도 없다.
     *
     * <p>여기가 {@code null} 이면 "능력치 조건이 없다"는 뜻이지 "ATTENDANCE 를 본다"는 뜻이 아니다.
     * 기본값을 넣지 않는 이유는 {@code required_level} 에 마법값을 쓰지 않는 것과 같다.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "required_ability", length = 32)
    private AbilityType requiredAbility;

    /**
     * 소매와 바짓단까지 그려진 전신 의상인지.
     *
     * <p>앱이 이 옷을 입히면 개구리 본체를 머리만 있는 그림으로 바꿔 깐다. 그러지 않으면
     * 옷 밑으로 본체의 팔다리가 삐져나온다. 어느 옷이 전신인지는 그림을 봐야 알 수 있는 사실이라
     * 프론트가 item_key 목록을 코드에 박지 않도록 카탈로그가 들고 내려간다.
     *
     * <p>{@code @ColumnDefault} 가 V36 의 {@code DEFAULT 0} 과 짝을 이룬다. 이 값이 없으면
     * Hibernate 가 만드는 스키마에만 기본값이 빠져, 이 컬럼을 모르던 시절의 시드 INSERT(V35)를
     * 다시 돌릴 때 "doesn't have a default value" 로 막힌다. 운영에서는 V35 가 V36 보다 먼저 돌아
     * 드러나지 않는 차이라, 맞춰 두지 않으면 테스트에서만 나는 실패가 된다.
     */
    @ColumnDefault("0")
    @Column(name = "full_body", nullable = false)
    private boolean fullBody;

    @Column(name = "set_id", length = 64)
    private String setId;

    /**
     * 세트의 사람이 읽는 이름. 세트에 속하지 않으면 {@code null} 이다.
     *
     * <p>{@code set_id} 는 기계용 키라 화면에 그대로 쓸 수 없다. 이 컬럼이 없던 동안
     * 프론트가 세트 배너에 아이템 이름을 이어 붙여 썼다.
     *
     * <p>같은 {@code set_id} 를 가진 행끼리 값이 같아야 한다. 세트를 늘릴 때 한 행만 고치면
     * 배너 이름이 아이템마다 달라진다.
     */
    @Column(name = "set_name_ko", length = 64)
    private String setNameKo;

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
     * <p>비교 대상은 {@code requiredAbility} 가 정한다. 적혀 있으면 그 능력치 레벨, 비어 있으면
     * 총 학습 레벨이다. 갈림은 {@link CosmeticUnlockLevels#levelFor} 한 곳에만 있다.
     *
     * <p>경계는 포함이다. {@code required_level == 비교 대상 레벨} 이면 열린 것이다.
     * 문제 복습 4 짜리 비니는 문제 복습이 4 가 되는 순간 써야지 5 가 돼서 열리면 안 된다.
     */
    public boolean isOwnedBy(CosmeticUnlockLevels levels) {
        return requiredLevel != null && requiredLevel <= levels.levelFor(requiredAbility);
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
