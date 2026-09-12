package com.aisip.OnO.backend.cosmetic.repository;

import com.aisip.OnO.backend.cosmetic.entity.UserCosmeticLoadout;
import com.aisip.OnO.backend.cosmetic.entity.UserCosmeticLoadoutId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface UserCosmeticLoadoutRepository extends JpaRepository<UserCosmeticLoadout, UserCosmeticLoadoutId> {

    /** 소유권: 장착 상태는 언제나 요청한 사용자 것만 읽는다. */
    List<UserCosmeticLoadout> findAllByUserId(Long userId);

    long countByUserId(Long userId);

    /**
     * 슬롯 하나를 한 문장으로 걸거나 바꾼다.
     *
     * <p>"없으면 넣고 있으면 바꾼다"를 애플리케이션에서 갈라 쓰면 같은 사용자가 같은 슬롯에
     * 동시에 장착할 때 둘 다 "없다"를 읽고 INSERT 해 기본키 충돌이 난다. JPA 에서 제약 위반은
     * 트랜잭션을 rollback-only 로 만들기 때문에 잡아서 UPDATE 로 넘어갈 수도 없다.
     *
     * <p>{@code INSERT ... ON DUPLICATE KEY UPDATE} 는 중복 키를 만나면 MySQL 이 그 행에
     * 배타 잠금을 걸고 UPDATE 로 바꿔 실행한다. 예외 경로도 없고 잠금 승격으로 인한 교착도 없다.
     * 뒤에 온 요청이 이기고, 어느 쪽이 이기든 행은 하나다.
     *
     * <p>해제도 이 메서드를 쓴다. {@code itemKey} 에 {@code UserCosmeticLoadout.NONE} 을 넣으면
     * "일부러 비운 슬롯" 행이 된다. 삭제하지 않는 이유는 엔티티 주석에 있다.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            INSERT INTO user_cosmetic_loadout (user_id, slot, item_key, updated_at)
            VALUES (:userId, :slot, :itemKey, NOW(6))
            ON DUPLICATE KEY UPDATE
                item_key = VALUES(item_key),
                updated_at = NOW(6)
            """, nativeQuery = true)
    int equip(
            @Param("userId") Long userId,
            @Param("slot") String slot,
            @Param("itemKey") String itemKey
    );

    /**
     * 행이 없을 때만 넣는다. 이미 있으면 아무것도 하지 않는다.
     *
     * <p>기본 프리셋을 실제 행으로 굳힐 때 쓴다. {@code ON DUPLICATE KEY UPDATE user_id = user_id}
     * 는 "이미 있으면 그대로 둔다"는 뜻이다. 사용자가 방금 고른 값을 프리셋이 덮어쓰면 안 되고,
     * 같은 요청이 동시에 두 번 들어와도 결과가 같아야 한다.
     *
     * <p>{@code INSERT IGNORE} 를 쓰지 않는다. 그쪽은 키 충돌만이 아니라 길이 초과·타입 불일치 같은
     * 진짜 오류까지 경고로 삼켜 버린다.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            INSERT INTO user_cosmetic_loadout (user_id, slot, item_key, updated_at)
            VALUES (:userId, :slot, :itemKey, NOW(6))
            ON DUPLICATE KEY UPDATE user_id = user_id
            """, nativeQuery = true)
    int insertIfAbsent(
            @Param("userId") Long userId,
            @Param("slot") String slot,
            @Param("itemKey") String itemKey
    );
}
