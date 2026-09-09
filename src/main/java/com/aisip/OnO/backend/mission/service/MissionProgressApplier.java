package com.aisip.OnO.backend.mission.service;

import com.aisip.OnO.backend.mission.entity.MissionDefinition;
import com.aisip.OnO.backend.mission.entity.MissionMetric;
import com.aisip.OnO.backend.mission.repository.MissionDefinitionRepository;
import com.aisip.OnO.backend.mission.repository.MissionProgressRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * 진행도를 실제로 갱신한다. 본 작업과 분리된 자기 트랜잭션에서 돈다.
 *
 * <p>증가는 리포지토리의 upsert 한 문장으로만 한다. 조회한 뒤 값을 계산해 저장하면
 * 같은 사용자의 요청이 겹칠 때 증가분이 사라진다.
 */
@Component
@RequiredArgsConstructor
public class MissionProgressApplier {

    private final MissionDefinitionRepository missionDefinitionRepository;
    private final MissionProgressRepository missionProgressRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void apply(Long userId, MissionMetric metric, int amount) {
        LocalDate today = MissionPeriodKey.today();
        List<MissionDefinition> definitions =
                missionDefinitionRepository.findAllByMetricAndActiveTrueOrderByIdAsc(metric);

        for (MissionDefinition definition : definitions) {
            String periodKey = MissionPeriodKey.of(definition.getCategory(), today);
            missionProgressRepository.increaseValue(
                    userId, definition.getId(), periodKey, amount, definition.getTarget());
            missionProgressRepository.markCompleted(userId, definition.getId(), periodKey);
        }
    }
}
