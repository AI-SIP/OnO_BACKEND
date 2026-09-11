package com.aisip.OnO.backend.cosmetic.service;

import com.aisip.OnO.backend.common.exception.ApplicationException;
import com.aisip.OnO.backend.cosmetic.dto.CosmeticEquipResponseDto;
import com.aisip.OnO.backend.cosmetic.dto.CosmeticItemResponseDto;
import com.aisip.OnO.backend.cosmetic.dto.CosmeticListResponseDto;
import com.aisip.OnO.backend.cosmetic.dto.CosmeticSlotDto;
import com.aisip.OnO.backend.cosmetic.dto.UnlockedCosmeticDto;
import com.aisip.OnO.backend.cosmetic.entity.CosmeticItem;
import com.aisip.OnO.backend.cosmetic.entity.CosmeticSlot;
import com.aisip.OnO.backend.cosmetic.entity.UserCosmeticLoadout;
import com.aisip.OnO.backend.cosmetic.exception.CosmeticErrorCase;
import com.aisip.OnO.backend.cosmetic.repository.CosmeticItemRepository;
import com.aisip.OnO.backend.cosmetic.repository.UserCosmeticLoadoutRepository;
import com.aisip.OnO.backend.mission.entity.UserMissionStatus;
import com.aisip.OnO.backend.user.entity.User;
import com.aisip.OnO.backend.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * 꾸미기 조회와 장착.
 *
 * <p>보유 여부는 저장하지 않는다. {@code required_level <= 총 학습 레벨} 로 매번 계산한다.
 * 보유를 테이블로 두면 레벨이 오를 때마다 지급이 필요하고, 그 지급이 한 번 밀리면 사용자는
 * 레벨은 올랐는데 아이템은 안 열린 상태로 남는다. 계산으로 두면 그런 상태가 아예 없다.
 * 테마 해금이 같은 방식을 쓴다.
 *
 * <p>쓰기는 전부 {@code UserCosmeticLoadoutRepository} 의 네이티브 upsert 를 탄다.
 * {@code (user_id, slot)} 기본키가 한 슬롯에 하나만 들어가는 것을 DB 수준에서 보장하는데,
 * 엔티티를 읽어 고치는 방식으로 쓰면 그 보장이 무의미해진다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CosmeticService {

    /**
     * 개구리 본체를 가리키는 아이템 키. 슬롯이 {@link CosmeticSlot#BASE} 라 장착 대상이 아니다.
     *
     * <p>본체 이미지까지 데이터로 두는 이유는 아이템 이미지와 같다. S3 로 옮길 때
     * {@code image_url} 만 바꾸면 앱 배포 없이 전환돼야 하는데, 본체만 코드 상수면 그 한 장 때문에
     * 배포를 해야 한다.
     */
    private static final String BASE_ITEM_KEY = "BASE";

    /**
     * 본체 행이 없을 때 쓸 값. 시드가 항상 넣어 주므로 실제로는 쓰이지 않는다.
     *
     * <p>없다고 500 을 내면, 본체 행 하나가 빠졌을 뿐인데 꾸미기 화면 전체가 열리지 않는다.
     */
    private static final String FALLBACK_BASE_IMAGE_URL = "assets/Cosmetic/BASE.png";

    /** 미션 상태가 아직 없는 사용자의 레벨. {@code StudyRoomMapper} 와 같은 기준이다. */
    private static final long DEFAULT_STUDY_LEVEL = 1L;

    private final CosmeticItemRepository cosmeticItemRepository;
    private final UserCosmeticLoadoutRepository userCosmeticLoadoutRepository;
    private final UserRepository userRepository;

    /**
     * 아이템 목록과 현재 장착 상태.
     *
     * <p>잠긴 아이템도 {@code owned: false} 로 함께 내려간다. 화면에 "레벨 6 에 열려요" 를 보여주려면
     * 잠긴 것이 무엇인지도 알아야 한다.
     *
     * <p>읽기 전용이다. 여기서 장착 행을 만들지 않는다. 목록만 열어 본 사용자 수만큼 빈 행이 생기는 것을 피한다.
     */
    public CosmeticListResponseDto getCosmetics(Long userId) {
        long level = totalStudyLevelOf(userId);
        List<CosmeticItem> activeItems = cosmeticItemRepository.findAllByActiveTrue();

        List<CosmeticItemResponseDto> items = equippableItems(activeItems).stream()
                .map(item -> CosmeticItemResponseDto.of(item, level))
                .toList();

        return new CosmeticListResponseDto(
                baseImageUrl(activeItems),
                CosmeticSlot.BASE.getLayerOrder(),
                CosmeticSlot.equippableSlots().stream().map(CosmeticSlotDto::from).toList(),
                items,
                currentEquipped(userId, activeItems, level)
        );
    }

    /**
     * 슬롯 하나 장착. {@code itemKey} 가 null 이면 해제한다.
     *
     * <p>충돌하는 다른 슬롯의 아이템은 자동으로 벗기고, 벗긴 슬롯을 응답에 담는다.
     * 충돌을 사용자에게 떠넘기면 "왜 안 걸리지" 하는 상태가 되고, 조용히 벗기기만 하면
     * 사라진 이유를 알 수 없다. 벗기고 알려 준다.
     */
    @Transactional
    public CosmeticEquipResponseDto equip(Long userId, CosmeticSlot slot, String itemKey) {
        long level = totalStudyLevelOf(userId);
        List<CosmeticItem> activeItems = cosmeticItemRepository.findAllByActiveTrue();
        Map<String, CosmeticItem> itemsByKey = indexByKey(activeItems);

        CosmeticItem item = resolveEquipTarget(slot, itemKey, itemsByKey, level);

        materializePresetIfUntouched(userId, activeItems, level);

        // 쓸 내용을 먼저 다 모은 뒤 슬롯 이름 순서로 적용한다.
        //
        // 한 요청이 여러 행을 건드릴 수 있는데(장착 1 + 충돌 해제 N), 두 요청이 서로 다른 순서로
        // 같은 행들을 잠그면 교착이 난다. 모든 트랜잭션이 같은 순서로 잠그면 교착 자체가 성립하지 않는다.
        // 슬롯 이름은 기본키의 두 번째 컬럼이라 이 순서가 곧 인덱스 순서다.
        Map<CosmeticSlot, String> writes = orderedWrites();
        writes.put(slot, item == null ? UserCosmeticLoadout.NONE : item.getItemKey());

        List<CosmeticSlot> unequipped = conflictingSlots(userId, item, itemsByKey);
        unequipped.forEach(conflicting -> writes.put(conflicting, UserCosmeticLoadout.NONE));

        applyWrites(userId, writes);

        log.info("userId: {} equipped slot: {}, itemKey: {}, unequipped: {}",
                userId, slot, itemKey, unequipped);

        return new CosmeticEquipResponseDto(currentEquipped(userId, activeItems, level), unequipped);
    }

    /**
     * 세트 한 번에 장착.
     *
     * <p>하나라도 보유하지 않았으면 통째로 거절한다. 되는 것만 걸어 주면 사용자는 "졸업 세트를 걸었는데
     * 학사모만 있는" 어중간한 상태를 보게 되고, 그게 의도인지 버그인지 구별할 수 없다.
     */
    @Transactional
    public CosmeticEquipResponseDto equipSet(Long userId, String setId) {
        long level = totalStudyLevelOf(userId);
        List<CosmeticItem> setItems = cosmeticItemRepository.findAllBySetIdAndActiveTrueOrderByIdAsc(setId).stream()
                .filter(CosmeticItem::isEquippable)
                .toList();

        if (setItems.isEmpty()) {
            throw new ApplicationException(CosmeticErrorCase.COSMETIC_SET_NOT_FOUND);
        }
        // 검사를 먼저 전부 끝낸다. 걸다가 중간에 거절하면 앞의 몇 개만 걸린 채로 롤백을 믿어야 한다.
        boolean anyLocked = setItems.stream().anyMatch(item -> !item.isOwnedAt(level));
        if (anyLocked) {
            throw new ApplicationException(CosmeticErrorCase.COSMETIC_ITEM_NOT_OWNED);
        }

        List<CosmeticItem> activeItems = cosmeticItemRepository.findAllByActiveTrue();
        Map<String, CosmeticItem> itemsByKey = indexByKey(activeItems);

        materializePresetIfUntouched(userId, activeItems, level);

        Map<CosmeticSlot, String> writes = orderedWrites();
        setItems.forEach(item -> writes.put(item.getSlot(), item.getItemKey()));

        // 세트 안에서 슬롯이 겹치면 나중 것이 이긴다. 애초에 겹치게 시드하지 않지만,
        // 겹쳤을 때 행이 두 개 생기지 않는다는 것 자체는 기본키가 보장한다.
        List<CosmeticSlot> unequipped = new ArrayList<>();
        for (CosmeticItem item : setItems) {
            for (CosmeticSlot conflicting : conflictingSlots(userId, item, itemsByKey)) {
                // 세트가 채울 슬롯은 어차피 덮어쓰므로 "벗겼다" 고 알리지 않는다.
                if (!writes.containsKey(conflicting) && !unequipped.contains(conflicting)) {
                    unequipped.add(conflicting);
                }
            }
        }
        unequipped.forEach(conflicting -> writes.put(conflicting, UserCosmeticLoadout.NONE));

        applyWrites(userId, writes);

        log.info("userId: {} equipped set: {}, items: {}, unequipped: {}",
                userId, setId, setItems.stream().map(CosmeticItem::getItemKey).toList(), unequipped);

        return new CosmeticEquipResponseDto(currentEquipped(userId, activeItems, level), unequipped);
    }

    /**
     * 이번 레벨업으로 새로 열린 아이템. 미션 보상 수령 응답이 쓴다.
     *
     * <p>구간은 {@code (levelBefore, levelAfter]} 라, 레벨이 한 번에 여러 단계 오르면 그 사이 것이 전부 들어온다.
     * 레벨이 그대로면 빈 목록이다.
     */
    public List<UnlockedCosmeticDto> findUnlockedBetween(long levelBefore, long levelAfter) {
        if (levelAfter <= levelBefore) {
            return List.of();
        }
        return cosmeticItemRepository.findUnlockedBetween(levelBefore, levelAfter).stream()
                .filter(CosmeticItem::isEquippable)
                .map(UnlockedCosmeticDto::from)
                .toList();
    }

    // ─────────────────────────── 장착 대상 판정 ───────────────────────────

    /**
     * 요청이 가리키는 아이템. 해제 요청이면 null 을 준다.
     *
     * <p>없는 키와 비활성 아이템을 같은 에러로 답한다. 구분해 주면 키를 훑어서 아직 공개하지 않은
     * 2차 콘텐츠 목록을 알아낼 수 있다.
     */
    private CosmeticItem resolveEquipTarget(CosmeticSlot slot, String itemKey,
                                            Map<String, CosmeticItem> itemsByKey, long level) {
        if (!slot.isEquippable()) {
            throw new ApplicationException(CosmeticErrorCase.COSMETIC_SLOT_MISMATCH);
        }
        if (itemKey == null || itemKey.isBlank()) {
            return null;
        }

        CosmeticItem item = itemsByKey.get(itemKey);
        if (item == null || !item.isEquippable()) {
            throw new ApplicationException(CosmeticErrorCase.COSMETIC_ITEM_NOT_FOUND);
        }
        if (item.getSlot() != slot) {
            throw new ApplicationException(CosmeticErrorCase.COSMETIC_SLOT_MISMATCH);
        }
        if (!item.isOwnedAt(level)) {
            throw new ApplicationException(CosmeticErrorCase.COSMETIC_ITEM_NOT_OWNED);
        }
        return item;
    }

    /**
     * 새로 거는 아이템과 같이 쓸 수 없는, 지금 걸려 있는 슬롯들.
     *
     * <p>충돌은 <b>양쪽 모두</b> 본다. 후드 옷에 "모자와 충돌" 이라고만 적고 모자 쪽에는 안 적는 것이
     * 자연스러운데, 한쪽만 보면 모자를 나중에 거는 경우에 검사가 통째로 빠진다.
     * 어느 쪽에 적어도 동작해야 데이터를 채우는 사람이 실수하지 않는다.
     *
     * <p>같은 슬롯은 대상이 아니다. 어차피 덮어써진다.
     *
     * <p>카탈로그에 없는(비활성으로 내려간) 아이템이 걸려 있으면 건드리지 않는다. 충돌 여부를 알 수 없는데
     * 벗기면 사용자 입장에서는 이유 없이 사라진 것이다.
     */
    private List<CosmeticSlot> conflictingSlots(Long userId, CosmeticItem item, Map<String, CosmeticItem> itemsByKey) {
        if (item == null || (item.conflictKeys().isEmpty() && !anyoneConflictsWith(item, itemsByKey))) {
            return List.of();
        }

        List<CosmeticSlot> conflicting = new ArrayList<>();
        for (UserCosmeticLoadout row : userCosmeticLoadoutRepository.findAllByUserId(userId)) {
            if (row.isEmptySlot() || row.getSlot() == item.getSlot()) {
                continue;
            }
            CosmeticItem equipped = itemsByKey.get(row.getItemKey());
            if (equipped == null) {
                continue;
            }
            if (item.conflictKeys().contains(equipped.getItemKey())
                    || equipped.conflictKeys().contains(item.getItemKey())) {
                conflicting.add(row.getSlot());
            }
        }
        conflicting.sort(Comparator.comparing(Enum::name));
        return conflicting;
    }

    /** 이 아이템을 충돌 대상으로 지목한 아이템이 하나라도 있는지. 없으면 장착 행을 읽지 않고 끝낸다. */
    private boolean anyoneConflictsWith(CosmeticItem item, Map<String, CosmeticItem> itemsByKey) {
        return itemsByKey.values().stream()
                .anyMatch(other -> other.conflictKeys().contains(item.getItemKey()));
    }

    // ─────────────────────────── 장착 상태 ───────────────────────────

    /**
     * 지금 걸려 있는 것. 행이 하나도 없으면 기본 프리셋으로 답한다.
     *
     * <p>비활성으로 내려간 아이템과 레벨이 내려가 잠긴 아이템은 응답에서 뺀다. 행은 그대로 두고
     * 보여주기만 멈춘다. 프론트는 아이템 목록에 없는 키를 받으면 그릴 이미지가 없고,
     * "잠긴 것을 걸고 있다" 는 상태도 화면에서 설명할 방법이 없다.
     * 관리자가 레벨을 되돌리면 걸려 있던 것이 그대로 다시 보인다.
     */
    private Map<CosmeticSlot, String> currentEquipped(Long userId, List<CosmeticItem> activeItems, long level) {
        List<UserCosmeticLoadout> rows = userCosmeticLoadoutRepository.findAllByUserId(userId);
        if (rows.isEmpty()) {
            return defaultPreset(activeItems, level);
        }

        Map<String, CosmeticItem> itemsByKey = indexByKey(activeItems);
        Map<CosmeticSlot, String> equipped = new EnumMap<>(CosmeticSlot.class);
        for (UserCosmeticLoadout row : rows) {
            if (row.isEmptySlot()) {
                continue;
            }
            CosmeticItem item = itemsByKey.get(row.getItemKey());
            if (item == null || !item.isEquippable() || !item.isOwnedAt(level)) {
                continue;
            }
            equipped.put(row.getSlot(), row.getItemKey());
        }
        return equipped;
    }

    /**
     * 장착 행이 없는 사용자에게 보여줄 기본 차림.
     *
     * <p>슬롯마다 열린 것 중 {@code required_level} 이 가장 높은 것을 고른다.
     *
     * <p><b>이 프리셋이 있는 이유</b>: 지금 레벨 15 인 사용자는 다 자란 개구리를 보고 있다.
     * 꾸미기로 전환하면서 장착 데이터가 없다는 이유로 맨 개구리가 되면, 사용자 입장에서는
     * 아무것도 안 했는데 하향된 것이다. 프리셋이 그 순간을 막는다.
     * 마이그레이션으로 전 사용자 행을 만드는 방법도 있지만, 수십만 행을 미리 쓰고 나면
     * 나중에 프리셋 규칙을 바꿀 수 없다. 계산으로 두면 규칙만 고치면 된다.
     */
    private Map<CosmeticSlot, String> defaultPreset(List<CosmeticItem> activeItems, long level) {
        Map<CosmeticSlot, String> preset = new EnumMap<>(CosmeticSlot.class);
        for (CosmeticSlot slot : CosmeticSlot.equippableSlots()) {
            activeItems.stream()
                    .filter(item -> item.getSlot() == slot)
                    .filter(item -> item.isOwnedAt(level))
                    // 레벨이 높은 것이 나중에 얻은 것이다. 같은 레벨에 여러 개면 키 순서로 고정한다.
                    // 정렬 기준이 없으면 조회할 때마다 다른 것이 걸려 있는 것처럼 보인다.
                    // isOwnedAt 을 통과한 아이템은 requiredLevel 이 null 이 아니다.
                    .min(Comparator
                            .comparingInt((CosmeticItem item) -> -item.getRequiredLevel())
                            .thenComparing(CosmeticItem::getItemKey))
                    .ifPresent(item -> preset.put(slot, item.getItemKey()));
        }
        return preset;
    }

    /**
     * 한 번도 장착을 건드리지 않은 사용자의 프리셋을 실제 행으로 굳힌다.
     *
     * <p>이게 없으면 첫 장착이 재앙이 된다. 레벨 15 사용자가 모자만 바꾸는 순간 행이 하나 생기고,
     * 그때부터 프리셋을 타지 않으므로 배경·옷·가방이 통째로 사라진다. 사용자는 모자를 바꿨을 뿐인데
     * 개구리가 벗겨진다.
     *
     * <p>그래서 첫 변경 때 지금 보고 있던 차림을 그대로 행으로 옮겨 적고, 그 위에 변경을 얹는다.
     * 사용자가 보던 화면이 그대로 이어진다.
     *
     * <p>{@code insertIfAbsent} 라 이미 있는 행은 건드리지 않는다. 행이 하나라도 있으면
     * 이 메서드는 아무 일도 하지 않아야 하는데, 조건 검사와 쓰기 사이에 다른 요청이 끼어들어도
     * 그 성질이 유지된다.
     */
    private void materializePresetIfUntouched(Long userId, List<CosmeticItem> activeItems, long level) {
        if (userCosmeticLoadoutRepository.countByUserId(userId) > 0) {
            return;
        }

        Map<CosmeticSlot, String> preset = orderedWrites();
        preset.putAll(defaultPreset(activeItems, level));
        preset.forEach((slot, itemKey) ->
                userCosmeticLoadoutRepository.insertIfAbsent(userId, slot.name(), itemKey));
    }

    private void applyWrites(Long userId, Map<CosmeticSlot, String> writes) {
        writes.forEach((slot, itemKey) -> userCosmeticLoadoutRepository.equip(userId, slot.name(), itemKey));
    }

    /** 슬롯 이름 순으로 정렬되는 빈 맵. 기본키 순서대로 쓰기 위한 것이다. */
    private Map<CosmeticSlot, String> orderedWrites() {
        return new TreeMap<>(Comparator.comparing(Enum::name));
    }

    // ─────────────────────────── 공통 ───────────────────────────

    private List<CosmeticItem> equippableItems(List<CosmeticItem> activeItems) {
        return activeItems.stream()
                .filter(CosmeticItem::isEquippable)
                // 뒤에서 앞 순서로, 같은 슬롯 안에서는 먼저 열리는 것부터.
                // 잠긴 것은 해금 레벨을 모르므로 맨 뒤에 둔다.
                .sorted(Comparator
                        .comparingInt((CosmeticItem item) -> item.getSlot().getLayerOrder())
                        .thenComparing(item -> item.getRequiredLevel() == null
                                ? Integer.MAX_VALUE : item.getRequiredLevel())
                        .thenComparing(CosmeticItem::getItemKey))
                .toList();
    }

    private String baseImageUrl(List<CosmeticItem> activeItems) {
        return activeItems.stream()
                .filter(item -> BASE_ITEM_KEY.equals(item.getItemKey()))
                .findFirst()
                .map(CosmeticItem::getImageUrl)
                .orElse(FALLBACK_BASE_IMAGE_URL);
    }

    /** 같은 키가 두 번 나올 수 없다. uk_cosmetic_item_key 가 막는다. */
    private Map<String, CosmeticItem> indexByKey(List<CosmeticItem> items) {
        Map<String, CosmeticItem> byKey = new LinkedHashMap<>();
        items.forEach(item -> byKey.put(item.getItemKey(), item));
        return byKey;
    }

    private long totalStudyLevelOf(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApplicationException(CosmeticErrorCase.USER_NOT_FOUND));

        return Optional.ofNullable(user.getUserMissionStatus())
                .map(UserMissionStatus::getTotalStudyLevel)
                .orElse(DEFAULT_STUDY_LEVEL);
    }
}
