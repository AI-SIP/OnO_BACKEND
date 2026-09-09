package com.aisip.OnO.backend.mission.service;

import com.aisip.OnO.backend.common.exception.ApplicationException;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 미션 조회와 보상 받기.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MissionService {

    private final MissionDefinitionRepository missionDefinitionRepository;
    private final MissionProgressRepository missionProgressRepository;
    private final MissionRewardGranter missionRewardGranter;

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
                    definition, progressByKey.get(cacheKey(definition.getId(), periodKey)));
            if (isWeekly) {
                weekly.add(dto);
            } else {
                daily.add(dto);
            }
        }

        return new MissionListResponseDto(
                new MissionListResponseDto.MissionSectionDto(dailyKey, List.copyOf(daily)),
                new MissionListResponseDto.MissionSectionDto(weeklyKey, List.copyOf(weekly))
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

        if (missionProgressRepository.markClaimed(progressId, userId) == 0) {
            throw new ApplicationException(MissionErrorCase.MISSION_ALREADY_CLAIMED);
        }

        MissionRewardGranter.GrantResult grantResult = missionRewardGranter.grant(
                userId, definition.getMetric(), definition.getRewardType(), definition.getRewardValue());

        log.info("userId: {} claimed missionProgressId: {}, code: {}, reward: {} {}",
                userId, progressId, definition.getCode(), definition.getRewardType(), definition.getRewardValue());

        return new MissionClaimResponseDto(
                progressId,
                definition.getRewardType(),
                definition.getRewardValue(),
                grantResult.totalStudyLevel(),
                grantResult.leveledUp()
        );
    }

    private String cacheKey(Long missionId, String periodKey) {
        return missionId + "@" + periodKey;
    }
}
