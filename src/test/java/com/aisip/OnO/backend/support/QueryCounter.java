package com.aisip.OnO.backend.support;

import jakarta.persistence.EntityManagerFactory;
import lombok.RequiredArgsConstructor;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.springframework.stereotype.Component;

import java.util.function.Supplier;

/**
 * 특정 동작이 실행하는 SQL 문 개수를 센다.
 *
 * <p>N+1 은 기능적으로는 정상 동작하기 때문에 일반적인 단언으로는 절대 잡히지 않는다.
 * 응답 내용이 같고 테스트도 통과하며, 데이터가 적은 개발 환경에서는 성능 차이도 느껴지지 않는다.
 * 사용자가 늘어난 뒤 프로덕션에서 느려지고 나서야 드러난다.
 *
 * <p>실제로 {@code GET /api/problems/review-due} 가 이 상태였다. 응답에 필요한 값은 스칼라뿐인데
 * mappedBy OneToOne 연관 때문에 행마다 존재 확인 쿼리가 한 번씩 더 나갔고, Sentry 의 N+1 감지에
 * 걸리고 나서야 알았다. 고친 뒤 다시 들어오지 않도록 쿼리 수를 단언으로 고정한다.
 *
 * <p>측정 대상은 자기 트랜잭션을 여는 서비스 메서드다. 테스트 메서드 자체에는 트랜잭션이 없으므로
 * 영속성 컨텍스트를 건드리지 않고 Hibernate 통계만 읽는다. 매번 새 세션이 열리기 때문에
 * 1차 캐시가 결과를 가리는 일도 없다.
 */
@Component
@RequiredArgsConstructor
public class QueryCounter {

    private final EntityManagerFactory entityManagerFactory;

    /** 주어진 동작이 실행한 SQL 문 개수를 돌려준다. */
    public long count(Runnable action) {
        return measure(() -> {
            action.run();
            return null;
        }).queryCount();
    }

    /** 반환값과 쿼리 수를 함께 돌려준다. */
    public <T> Counted<T> measure(Supplier<T> action) {
        Statistics statistics = statistics();
        boolean wasEnabled = statistics.isStatisticsEnabled();

        statistics.setStatisticsEnabled(true);
        statistics.clear();
        try {
            T result = action.get();
            return new Counted<>(result, statistics.getPrepareStatementCount());
        } finally {
            statistics.setStatisticsEnabled(wasEnabled);
        }
    }

    private Statistics statistics() {
        return entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
    }

    public record Counted<T>(T result, long queryCount) {
    }
}
