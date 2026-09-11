package com.aisip.OnO.backend.cosmetic.entity;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * {@link UserCosmeticLoadout} 의 복합 기본키 (user_id, slot).
 *
 * <p>JPA 는 복합키를 별도 클래스로 요구하고, 그 클래스는 직렬화 가능하며 equals/hashCode 가
 * 값 기준이어야 한다. 둘 중 하나라도 빠지면 같은 키를 두 번 조회할 때 영속성 컨텍스트가
 * 다른 행으로 착각한다.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@EqualsAndHashCode
public class UserCosmeticLoadoutId implements Serializable {

    private Long userId;

    private CosmeticSlot slot;
}
