package com.aisip.OnO.backend.mission.service;

import com.aisip.OnO.backend.mission.entity.MissionDefinition;
import com.aisip.OnO.backend.mission.entity.MissionMetric;
import com.aisip.OnO.backend.mission.repository.MissionDefinitionRepository;
import com.aisip.OnO.backend.mission.repository.MissionProgressRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * 다른 도메인이 부르는 진행도 증가 진입점.
 *
 * <p><b>호출자의 트랜잭션 안에서 돈다.</b> 별도 이벤트 리스너를 두거나 {@code REQUIRES_NEW} 로 떼어내지 않는다.
 * 오답노트 등록이 롤백됐는데 "오늘의 오답" 진행도만 남으면 사용자는 하지도 않은 일로 보상을 받는다.
 * {@code ProblemReviewReminderService} 가 {@code AFTER_COMMIT} + {@code REQUIRES_NEW} 를 쓰는 것은
 * FCM 이라는 외부 연동이 실패해도 본 작업을 막으면 안 되기 때문이고, 진행도는 DB 만 건드리므로 경우가 다르다.
 *
 * <p>증가는 리포지토리의 upsert 한 문장으로만 한다. 여기서 조회한 뒤 값을 계산해 저장하면
 * 같은 사용자의 요청이 겹칠 때 증가분이 사라진다.
 */
@Component
@RequiredArgsConstructor
public class MissionProgressUpdater {

    private final MissionDefinitionRepository missionDefinitionRepository;
    private final MissionProgressRepository missionProgressRepository;

    /**
     * 애노테이션이 여기에도 붙어 있어야 한다. 이 오버로드는 같은 빈의 3인자 메서드를 직접 부르는데,
     * 자기 호출은 프록시를 타지 않아 저쪽의 {@code @Transactional} 이 적용되지 않는다.
     * 호출자에게 트랜잭션이 없으면 그대로 트랜잭션 없이 실행돼 flush 에서 터진다.
     */
    @Transactional
    public void increase(Long userId, MissionMetric metric) {
        increase(userId, metric, 1);
    }

    /**
     * @param amount 한 번에 오를 양. 오답노트를 여러 장 등록하면 장수만큼 오른다.
     */
    @Transactional
    public void increase(Long userId, MissionMetric metric, int amount) {
        if (userId == null || metric == null || amount <= 0) {
            return;
        }

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
