package com.aisip.OnO.backend.mission.service;

import com.aisip.OnO.backend.mission.entity.MissionMetric;
import com.aisip.OnO.backend.mission.event.MissionProgressEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.CannotAcquireLockException;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * 진행도 갱신 실패가 본 작업 밖으로 새어 나가지 않는지.
 *
 * <p>리스너는 커밋 콜백에서 실행된다. 여기서 예외를 흘리면 데이터는 이미 저장됐는데 사용자에게는
 * 500 이 나가는 최악의 조합이 된다. 잠금 대기 초과나 교착 희생자는 실제로 일어나는 일이라
 * 그때 오답노트 저장이 성공으로 끝나는지를 고정한다.
 */
@DisplayName("미션 진행도 이벤트 리스너")
class MissionProgressEventListenerTest {

    @Test
    @DisplayName("진행도 갱신이 실패해도 예외를 밖으로 내보내지 않는다")
    void swallowsFailure() {
        MissionProgressEventListener listener = new MissionProgressEventListener(
                throwingApplier(new CannotAcquireLockException("교착 희생자로 선택됐다")));

        assertThatCode(() -> listener.handleMissionProgress(
                new MissionProgressEvent(1L, MissionMetric.PROBLEM_CREATED, 1)))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("정상이면 받은 값 그대로 넘긴다")
    void delegatesEvent() {
        AtomicInteger appliedAmount = new AtomicInteger();
        MissionProgressApplier applier = new MissionProgressApplier(null, null) {
            @Override
            public void apply(Long userId, MissionMetric metric, int amount) {
                appliedAmount.set(amount);
            }
        };

        new MissionProgressEventListener(applier).handleMissionProgress(
                new MissionProgressEvent(1L, MissionMetric.PROBLEM_CREATED, 3));

        assertThat(appliedAmount).hasValue(3);
    }

    private MissionProgressApplier throwingApplier(RuntimeException failure) {
        return new MissionProgressApplier(null, null) {
            @Override
            public void apply(Long userId, MissionMetric metric, int amount) {
                throw failure;
            }
        };
    }
}
