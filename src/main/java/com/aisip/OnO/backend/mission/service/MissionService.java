package com.aisip.OnO.backend.mission.service;

import com.aisip.OnO.backend.common.exception.ApplicationException;
import com.aisip.OnO.backend.cosmetic.dto.UnlockedCosmeticDto;
import com.aisip.OnO.backend.cosmetic.service.CosmeticService;
import com.aisip.OnO.backend.mission.dto.MissionClaimHistoryItemDto;
import com.aisip.OnO.backend.mission.dto.MissionClaimHistoryResponseDto;
import com.aisip.OnO.backend.mission.dto.MissionClaimResponseDto;
import com.aisip.OnO.backend.mission.dto.MissionListResponseDto;
import com.aisip.OnO.backend.mission.dto.MissionResponseDto;
import com.aisip.OnO.backend.mission.entity.MissionCategory;
import com.aisip.OnO.backend.mission.entity.MissionDefinition;
import com.aisip.OnO.backend.mission.entity.MissionProgress;
import com.aisip.OnO.backend.mission.exception.MissionErrorCase;
import com.aisip.OnO.backend.mission.repository.MissionDefinitionRepository;
import com.aisip.OnO.backend.mission.repository.MissionProgressRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 미션 조회와 보상 받기.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MissionService {

    /**
     * 지난 기간 미수령 보상을 얼마나 거슬러 보여줄지.
     *
     * <p>상한이 없으면 오래 쓴 사용자일수록 매 조회에 몇 년치 미수령이 딸려 온다.
     * 한 달이면 "지난 주에 받는 걸 깜빡했다"는 실제 상황은 모두 덮는다.
     */
    private static final int EXPIRED_LOOKBACK_DAYS = 30;

    /** 기록 조회 한 페이지 최대 건수. 요청이 아무리 커도 여기서 자른다. */
    private static final int MAX_HISTORY_SIZE = 50;

    private final MissionDefinitionRepository missionDefinitionRepository;
    private final MissionProgressRepository missionProgressRepository;
    private final MissionRewardGranter missionRewardGranter;
    private final CosmeticService cosmeticService;

    /**
     * 활성 미션 전부와 이번 기간의 진행도.
     *
     * <p>아직 손대지 않은 미션도 {@code current: 0} 으로 내려간다. 조회는 읽기 전용이라
     * 여기서 진행도 행을 만들지 않는다. 화면에 미션 목록만 띄운 사용자 수만큼 빈 행이 생기는 것을 피한다.
     */
    public MissionListResponseDto getMissions(Long userId) {
        LocalDate today = MissionPeriodKey.today();
        String dailyKey = MissionPeriodKey.daily(today);
        String weeklyKey = MissionPeriodKey.weekly(today);

        List<MissionDefinition> definitions = missionDefinitionRepository.findAllByActiveTrueOrderBySortOrderAscIdAsc();

        // 소유권: 조회 대상은 요청한 사용자의 진행도로만 한정한다.
        Map<String, MissionProgress> progressByKey = new HashMap<>();
        missionProgressRepository.findAllByUserIdAndPeriodKeyIn(userId, List.of(dailyKey, weeklyKey))
                .forEach(progress -> progressByKey.put(cacheKey(progress.getMissionId(), progress.getPeriodKey()), progress));

        List<MissionResponseDto> daily = new ArrayList<>();
        List<MissionResponseDto> weekly = new ArrayList<>();
        for (MissionDefinition definition : definitions) {
            boolean isWeekly = definition.getCategory() == MissionCategory.WEEKLY;
            String periodKey = isWeekly ? weeklyKey : dailyKey;
            MissionResponseDto dto = MissionResponseDto.from(
                    definition, progressByKey.get(cacheKey(definition.getId(), periodKey)), periodKey);
            if (isWeekly) {
                weekly.add(dto);
            } else {
                daily.add(dto);
            }
        }

        return new MissionListResponseDto(
                new MissionListResponseDto.MissionSectionDto(dailyKey, List.copyOf(daily)),
                new MissionListResponseDto.MissionSectionDto(weeklyKey, List.copyOf(weekly)),
                // 여러 기간이 섞이므로 묶음 단위의 기간 키가 없다. 기간은 항목마다 실려 간다.
                new MissionListResponseDto.MissionSectionDto(null, expiredMissions(userId, dailyKey, weeklyKey))
        );
    }

    /**
     * 지난 기간에 완료했지만 받지 않은 보상.
     *
     * <p>정의가 그 사이 비활성화됐어도 이미 완료한 보상은 받을 수 있어야 하므로 active 조건을 걸지 않는다.
     * 정의 자체가 사라진 경우에만 건너뛴다.
     */
    private List<MissionResponseDto> expiredMissions(Long userId, String dailyKey, String weeklyKey) {
        List<MissionProgress> expired = missionProgressRepository.findUnclaimedFromPastPeriods(
                userId,
                List.of(dailyKey, weeklyKey),
                MissionPeriodKey.now().minusDays(EXPIRED_LOOKBACK_DAYS)
        );
        if (expired.isEmpty()) {
            return List.of();
        }

        Map<Long, MissionDefinition> definitionsById = missionDefinitionRepository
                .findAllById(expired.stream().map(MissionProgress::getMissionId).distinct().toList())
                .stream()
                .collect(Collectors.toMap(MissionDefinition::getId, definition -> definition));

        return expired.stream()
                .filter(progress -> definitionsById.containsKey(progress.getMissionId()))
                // 최근에 놓친 것을 먼저 보여주고, 시각이 같으면 목록과 같은 순서를 쓴다.
                //
                // 기간 키 문자열로 정렬하면 안 된다. "2026-W37" 과 "2026-09-09" 는 여섯 번째 글자
                // 'W'(0x57) 대 '0'(0x30) 에서 갈려 주간이 언제나 일일보다 앞선다.
                // 어제 놓친 일일 미션이 3주 전 주간 미션보다 아래로 밀린다.
                .sorted(Comparator
                        .comparing(MissionProgress::getCompletedAt, Comparator.reverseOrder())
                        .thenComparing(progress -> definitionsById.get(progress.getMissionId()).getSortOrder()))
                .map(progress -> MissionResponseDto.from(
                        definitionsById.get(progress.getMissionId()), progress, progress.getPeriodKey()))
                .toList();
    }

    /**
     * 보상 획득 기록. 받은 시각 역순, 커서 페이지네이션.
     *
     * <p>새 테이블을 두지 않는다. {@code claimed_at} 이 붙은 진행도 행이 곧 기록이다.
     *
     * <p>정의가 비활성화된 미션도 그대로 내려간다. 이미 받은 건 받은 것이다.
     * 정의 자체가 사라진 경우에만 건너뛴다.
     *
     * @param cursor 앞 페이지 마지막 항목의 {@code progressId}. 첫 페이지에서는 null.
     */
    public MissionClaimHistoryResponseDto getClaimHistory(Long userId, Long cursor, int size) {
        int safeSize = Math.min(Math.max(size, 1), MAX_HISTORY_SIZE);

        LocalDateTime cursorClaimedAt = null;
        if (cursor != null) {
            // 남의 id 를 커서로 넘기면 여기서 걸린다. 못 찾은 커서는 "끝난 목록"으로 본다.
            MissionProgress cursorProgress = missionProgressRepository.findByIdAndUserId(cursor, userId).orElse(null);
            if (cursorProgress == null || cursorProgress.getClaimedAt() == null) {
                return MissionClaimHistoryResponseDto.nextPage(List.of(), null, false, safeSize);
            }
            cursorClaimedAt = cursorProgress.getClaimedAt();
        }

        // 다음 페이지가 있는지 알려면 한 건 더 읽어 보는 수밖에 없다. 전체를 세는 것보다 싸다.
        List<MissionProgress> claimed = missionProgressRepository.findClaimedPage(
                userId, cursorClaimedAt, cursor, PageRequest.of(0, safeSize + 1));
        boolean hasNext = claimed.size() > safeSize;
        List<MissionProgress> pageContent = hasNext ? claimed.subList(0, safeSize) : claimed;

        Map<Long, MissionDefinition> definitionsById = missionDefinitionRepository
                .findAllById(pageContent.stream().map(MissionProgress::getMissionId).distinct().toList())
                .stream()
                .collect(Collectors.toMap(MissionDefinition::getId, definition -> definition));

        List<MissionClaimHistoryItemDto> content = pageContent.stream()
                .filter(progress -> definitionsById.containsKey(progress.getMissionId()))
                .map(progress -> MissionClaimHistoryItemDto.from(
                        definitionsById.get(progress.getMissionId()), progress))
                .toList();

        Long nextCursor = hasNext && !pageContent.isEmpty()
                ? pageContent.get(pageContent.size() - 1).getId()
                : null;

        if (cursor != null) {
            return MissionClaimHistoryResponseDto.nextPage(content, nextCursor, hasNext, safeSize);
        }
        return MissionClaimHistoryResponseDto.firstPage(
                content, nextCursor, hasNext, safeSize,
                missionProgressRepository.sumClaimedXp(userId),
                missionProgressRepository.countByUserIdAndClaimedAtIsNotNull(userId)
        );
    }

    /**
     * 보상 받기.
     *
     * <p>남의 진행도는 "없다"로 답한다. 존재 여부를 알려주면 id 를 훑어 다른 사용자의 미션 진행 상황을
     * 알아낼 수 있기 때문이다.
     *
     * <p>이미 받았는지는 조회로 한 번 걸러 400 을 주고, 실제 지급은 {@code claimed_at IS NULL} 조건부
     * UPDATE 가 성공한 경우에만 한다. 조회만으로 판단하면 버튼을 두 번 빠르게 누른 두 요청이 모두
     * 통과해 XP 가 두 번 들어간다.
     */
    @Transactional
    public MissionClaimResponseDto claim(Long userId, Long progressId) {
        MissionProgress progress = missionProgressRepository.findById(progressId)
                .orElseThrow(() -> new ApplicationException(MissionErrorCase.MISSION_PROGRESS_NOT_FOUND));

        if (!progress.isOwnedBy(userId)) {
            throw new ApplicationException(MissionErrorCase.MISSION_PROGRESS_NOT_FOUND);
        }
        if (!progress.isCompleted()) {
            throw new ApplicationException(MissionErrorCase.MISSION_NOT_COMPLETED);
        }
        if (progress.isClaimed()) {
            throw new ApplicationException(MissionErrorCase.MISSION_ALREADY_CLAIMED);
        }

        MissionDefinition definition = missionDefinitionRepository.findById(progress.getMissionId())
                .orElseThrow(() -> new ApplicationException(MissionErrorCase.MISSION_PROGRESS_NOT_FOUND));

        // 진행도 행을 잠그기 전에 사용자 행을 먼저 잠근다. 잠금 순서를 기존 적립 경로와 맞춰 교착을 막는다.
        missionRewardGranter.lockUser(userId);

        // 보상 종류와 값을 받는 순간 박아 둔다. 나중에 정의를 바꿔도 이 기록의 값은 그대로다.
        if (missionProgressRepository.markClaimed(
                progressId, userId, definition.getRewardType().name(), definition.getRewardValue()) == 0) {
            throw new ApplicationException(MissionErrorCase.MISSION_ALREADY_CLAIMED);
        }

        MissionRewardGranter.GrantResult grantResult = missionRewardGranter.grant(
                userId, definition.getMetric(), definition.getRewardType(), definition.getRewardValue());

        // 해금 조회는 지급이 끝난 뒤에 한다. 사용자 행 잠금을 들고 있는 구간을 늘리지 않기 위해서다.
        // 총 학습 레벨 구간과 이번에 XP 가 들어간 능력치 레벨 구간을 함께 본다. 능력치 레벨로 열리는
        // 아이템이 대부분이라, 총 학습 레벨만 보면 레벨업 알림이 거의 늘 비어 있게 된다.
        // cosmetic_item 은 애플리케이션이 읽기만 하는 카탈로그라 이 조회가 새 잠금 순서를 만들지 않는다.
        List<UnlockedCosmeticDto> unlockedCosmetics = cosmeticService.findUnlockedBetween(
                grantResult.levelBefore(), grantResult.totalStudyLevel(),
                grantResult.abilityType(), grantResult.abilityLevelBefore(), grantResult.abilityLevelAfter());

        log.info("userId: {} claimed missionProgressId: {}, code: {}, reward: {} {}, unlocked: {}",
                userId, progressId, definition.getCode(), definition.getRewardType(), definition.getRewardValue(),
                unlockedCosmetics.stream().map(UnlockedCosmeticDto::itemKey).toList());

        return new MissionClaimResponseDto(
                progressId,
                definition.getRewardType(),
                definition.getRewardValue(),
                grantResult.totalStudyLevel(),
                grantResult.leveledUp(),
                unlockedCosmetics
        );
    }

    private String cacheKey(Long missionId, String periodKey) {
        return missionId + "@" + periodKey;
    }
}
