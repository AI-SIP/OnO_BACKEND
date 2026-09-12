package com.aisip.OnO.backend.cosmetic.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 사용자가 슬롯별로 무엇을 걸고 있는지. (userId, slot) 하나당 한 행이다.
 *
 * <p><b>(user_id, slot) 복합 기본키가 이 설계의 핵심이다.</b> 한 슬롯에 두 개가 들어가는 것을
 * 애플리케이션 검사가 아니라 DB 가 막는다. 같은 사용자가 같은 슬롯에 두 아이템을 동시에 걸어도
 * 중복 행이 생길 수 없다. 그래서 장착을
 * {@code INSERT ... ON DUPLICATE KEY UPDATE} 한 문장으로 처리할 수 있고, 읽고 나서 쓰는
 * check-then-act 도, 유니크 충돌 예외를 잡아 UPDATE 로 넘어가는 경로도 필요 없다.
 *
 * <p>이 엔티티는 <b>읽기 전용으로만</b> 쓴다. 쓰기는 전부 리포지토리의 네이티브 upsert/삭제를 탄다.
 * 엔티티를 고쳐 더티 체킹에 맡기면 위 보장이 사라진다.
 *
 * <p>user 와 cosmetic_item 에 연관관계를 걸지 않고 식별자만 들고 있다. mission_progress 와 같은
 * 이유로, 외래키를 걸면 INSERT 마다 부모 사용자 행에 공유 잠금이 붙어 이미 사용자 행을 배타 잠금으로
 * 잡는 미션 보상 지급 경로와 잠금 순서가 엇갈릴 수 있다.
 */
@Entity
@Getter
@IdClass(UserCosmeticLoadoutId.class)
@AllArgsConstructor(access = AccessLevel.PROTECTED)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "user_cosmetic_loadout")
public class UserCosmeticLoadout {

    /**
     * "이 슬롯을 일부러 비웠다"는 표시.
     *
     * <p>해제를 행 삭제로 처리하면 모든 슬롯을 벗은 사용자가 "한 번도 안 건드린 사용자"와
     * 구별되지 않는다. 그러면 다음 조회에서 기본 프리셋이 되살아나 벗은 것이 되돌아온다.
     * 맨 개구리를 보고 싶어서 하나씩 벗은 사용자가 앱을 껐다 켜면 다시 옷을 입고 있는 셈이다.
     *
     * <p>그래서 해제는 삭제가 아니라 이 값으로 덮어쓴다. 조회할 때 이 값이 든 행은 장착 맵에서 빠진다.
     * 언더스코어로 시작해 실제 아이템 키({@code hat_beanie} 같은 소문자 단어)와 겹칠 수 없고,
     * 장착 요청은 {@code cosmetic_item} 에 있는 키만 받으므로 클라이언트가 이 값을 넣을 수도 없다.
     */
    public static final String NONE = "__none__";

    @Id
    @Column(name = "user_id", nullable = false)
    private Long userId;

    /**
     * V34 의 {@code slot VARCHAR(32)} 과 맞춘다. {@code columnDefinition} 이 없으면 Hibernate 가
     * MySQL 에서 네이티브 {@code ENUM(...)} 으로 만들어, 운영에는 들어갈 수 있는 값(옛 배포가 남긴
     * 슬롯 이름)이 테스트 스키마에는 아예 못 들어간다. 그 차이를 두면 자리를 없애는 변경의
     * 마이그레이션을 테스트로 재현할 수 없다.
     */
    @Id
    @Enumerated(EnumType.STRING)
    @Column(name = "slot", nullable = false, length = 32, columnDefinition = "varchar(32)")
    private CosmeticSlot slot;

    @Column(name = "item_key", nullable = false, length = 64)
    private String itemKey;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    /** 일부러 비워 둔 슬롯인지. 장착 맵을 만들 때 걸러낸다. */
    public boolean isEmptySlot() {
        return NONE.equals(itemKey);
    }
}
