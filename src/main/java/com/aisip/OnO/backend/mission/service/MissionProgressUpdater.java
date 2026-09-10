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
 * <p><b>호출자의 트랜잭션 안에서 돈다.</b> 한때 커밋 후 실행({@code AFTER_COMMIT}) + 새 트랜잭션
 * ({@code REQUIRES_NEW}) 으로 떼어냈다가 되돌렸다. 되돌린 이유와 그 대가를 남겨 둔다.
 *
 * <p><b>왜 떼어냈었나.</b> 진행도 갱신이 잠금 대기 시간을 넘기거나 교착의 희생자가 되면
 * 사용자가 방금 저장한 오답노트까지 함께 롤백되기 때문이다. 그 걱정 자체는 지금도 유효하다.
 *
 * <p><b>왜 되돌렸나.</b> 떼어내면 커밋 직후 잠깐 <b>요청 하나가 커넥션을 두 개</b> 잡는다.
 * 바깥 트랜잭션의 커넥션은 {@code afterCommit} 콜백이 끝나고 {@code cleanupAfterCompletion} 에서야
 * 반납되는데, 그 콜백 안에서 {@code REQUIRES_NEW} 가 새 커넥션을 또 얻기 때문이다.
 * 실측으로 동시 요청 8건에 커넥션 16개가 필요했다(풀 9면 전부 멈추고 16이면 통과). 정확히 두 배다.
 * 운영 Hikari 풀은 기본값 10이라 <b>미션을 건드리는 요청 다섯 건만 겹쳐도 서비스 전체가 멈춘다.</b>
 *
 * <p><b>무엇을 받아들였나.</b> 두 위험의 크기가 다르다. 롤백 전파는 같은 사용자가 두 요청을 동시에 보내고
 * 같은 진행도 행에서 잠금을 오래 기다려야 나는 일이라 드물고, 터져도 <b>요청 하나가 실패</b>한다.
 * 커넥션 고갈은 사용자가 누구든 다섯 건만 겹치면 나고, 터지면 <b>서비스 전체가 멈춘다.</b>
 * 드문 요청 실패가 전체 정지보다 낫다. 그래서 <b>진행도 갱신이 실패하면 본 작업도 함께 롤백된다는 것을
 * 알면서 이 방식을 고른 것이다.</b> 이 절충을 바꾸려면 커넥션을 두 배로 쓰지 않는 방법
 * (예: 별도 실행기로 요청 스레드에서 떼어내기)을 먼저 마련해야 한다.
 *
 * <p>덧붙여 이 방식은 "본 작업이 롤백되면 진행도도 남지 않는다"를 그대로 지킨다.
 * 하지도 않은 행동으로 보상을 받는 일은 없다.
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
