package com.aisip.OnO.backend.mission.service;

import com.aisip.OnO.backend.mission.event.MissionProgressEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 본 작업이 커밋된 뒤에 진행도를 올린다.
 *
 * <p>실패는 <b>삼키고 로그만 남긴다.</b> 여기서 예외가 새어 나가면 커밋 콜백을 타고 요청 처리부까지 올라가,
 * 데이터는 이미 저장됐는데 사용자에게는 500 이 나가는 최악의 조합이 된다.
 * 진행도는 다음 행동에서 다시 오르므로 한 번 놓쳐도 회복 가능하다.
 *
 * <p>{@code @Transactional} 을 이 메서드에 직접 붙이지 않는다. 붙이면 여기서 예외를 잡는 순간
 * 트랜잭션이 롤백되지 않고 중간까지 반영된 채로 커밋된다. 새 트랜잭션 경계는
 * {@link MissionProgressApplier} 에 두어, 실패하면 그 트랜잭션만 통째로 롤백되고
 * 예외는 프록시 밖의 이 자리에서 잡힌다.
 *
 * <p>{@code fallbackExecution} 은 트랜잭션 없이 불린 경우에도 진행도를 올리기 위한 것이다.
 * 운영 경로는 모두 트랜잭션 안이지만, 없을 때 조용히 사라지는 것보다 즉시 실행하는 편이 안전하다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MissionProgressEventListener {

    private final MissionProgressApplier missionProgressApplier;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void handleMissionProgress(MissionProgressEvent event) {
        try {
            missionProgressApplier.apply(event.userId(), event.metric(), event.amount());
        } catch (Exception e) {
            log.error("[Mission] 진행도 갱신 실패 - userId: {}, metric: {}, amount: {}",
                    event.userId(), event.metric(), event.amount(), e);
        }
    }
}
