package com.aisip.OnO.backend.cosmetic.repository;

import com.aisip.OnO.backend.cosmetic.entity.CosmeticItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CosmeticItemRepository extends JpaRepository<CosmeticItem, Long> {

    /**
     * 화면에 내려갈 아이템 전부.
     *
     * <p>{@code BASE}(개구리 본체)까지 함께 온다. 슬롯이 장착 대상인지는 호출부가 가른다.
     * 여기서 미리 걸러 두 번 조회하면 목록 한 번에 끝날 것을 두 번 읽게 된다.
     */
    List<CosmeticItem> findAllByActiveTrue();

    Optional<CosmeticItem> findByItemKey(String itemKey);

    /** 세트 장착용. 비활성 아이템은 세트에 끼워 주지 않는다. */
    List<CosmeticItem> findAllBySetIdAndActiveTrueOrderByIdAsc(String setId);

    /**
     * 이번 레벨업으로 새로 열린 아이템.
     *
     * <p>구간은 {@code (levelBefore, levelAfter]} 다. 앞은 열림, 뒤는 닫힘.
     * 레벨이 한 번에 여러 단계 오르면 그 사이 것이 전부 들어온다.
     * {@code required_level} 이 null 인 아이템은 레벨로 열리지 않으므로 비교에서 저절로 빠진다.
     */
    @Query("""
            SELECT i FROM CosmeticItem i
            WHERE i.active = true
              AND i.requiredLevel > :levelBefore
              AND i.requiredLevel <= :levelAfter
            ORDER BY i.requiredLevel ASC, i.itemKey ASC
            """)
    List<CosmeticItem> findUnlockedBetween(
            @Param("levelBefore") long levelBefore,
            @Param("levelAfter") long levelAfter
    );
}
