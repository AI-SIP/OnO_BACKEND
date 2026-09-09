package com.aisip.OnO.backend.mission.service;

import com.aisip.OnO.backend.mission.entity.MissionMetric;
import com.aisip.OnO.backend.mission.event.MissionProgressEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * 다른 도메인이 부르는 진행도 증가 진입점. 실제 갱신은 하지 않고 사실만 알린다.
 *
 * <p><b>왜 호출자의 트랜잭션에 합류하지 않는가.</b> 요구는 두 가지인데 방향이 다르다.
 *
 * <p>하나는 "본 작업이 실패하면 진행도도 없어야 한다"이다. 오답노트 등록이 롤백됐는데
 * "오늘의 오답" 진행도만 남으면 사용자는 하지도 않은 일로 보상을 받는다.
 *
 * <p>다른 하나는 "진행도가 실패해도 본 작업은 살아야 한다"이다. 진행도 갱신은 다른 트랜잭션이 잡은
 * 행에서 대기하다 잠금 대기 시간을 넘기거나 교착의 희생자가 될 수 있다. 같은 트랜잭션에 묶어 두면
 * 그때 사용자가 방금 저장한 오답노트가 통째로 사라지고 500 이 나간다.
 * 보너스 카운터 하나 때문에 본 기능을 잃는 거래는 성립하지 않는다.
 *
 * <p>커밋 후 실행({@code AFTER_COMMIT})에 새 트랜잭션({@code REQUIRES_NEW})이면 둘 다 선다.
 * 본 작업이 롤백되면 이벤트가 발행되지 않아 진행도도 남지 않고, 진행도가 실패해도 본 작업은 이미 커밋돼 있다.
 * 판단 기준은 외부 연동이냐 DB 냐가 아니라 <b>본 작업의 성패에 그 결과가 필요한가</b>이고,
 * 미션 진행도는 필요 없다. {@code ProblemReviewReminderService} 가 같은 이유로 같은 형태를 쓴다.
 */
@Component
@RequiredArgsConstructor
public class MissionProgressUpdater {

    private final ApplicationEventPublisher eventPublisher;

    public void increase(Long userId, MissionMetric metric) {
        increase(userId, metric, 1);
    }

    /**
     * @param amount 한 번에 오를 양. 오답노트를 여러 장 등록하면 장수만큼 오른다.
     */
    public void increase(Long userId, MissionMetric metric, int amount) {
        if (userId == null || metric == null || amount <= 0) {
            return;
        }
        eventPublisher.publishEvent(new MissionProgressEvent(userId, metric, amount));
    }
}
