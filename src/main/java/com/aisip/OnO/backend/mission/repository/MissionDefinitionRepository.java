package com.aisip.OnO.backend.mission.repository;

import com.aisip.OnO.backend.mission.entity.MissionDefinition;
import com.aisip.OnO.backend.mission.entity.MissionMetric;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MissionDefinitionRepository extends JpaRepository<MissionDefinition, Long> {

    /** 목록 조회용. 정렬은 sortOrder 오름차순이고, 같은 값이면 id 로 흔들림을 없앤다. */
    List<MissionDefinition> findAllByActiveTrueOrderBySortOrderAscIdAsc();

    /**
     * 진행도를 올릴 대상.
     *
     * <p>id 오름차순으로 고정한다. 한 번의 행동이 일일/주간 두 미션을 함께 올리는데,
     * 동시에 들어온 요청들이 서로 다른 순서로 행을 잠그면 교착이 난다.
     * 모든 트랜잭션이 같은 순서로 잠그면 교착 자체가 성립하지 않는다.
     */
    List<MissionDefinition> findAllByMetricAndActiveTrueOrderByIdAsc(MissionMetric metric);

    Optional<MissionDefinition> findByCode(String code);
}
