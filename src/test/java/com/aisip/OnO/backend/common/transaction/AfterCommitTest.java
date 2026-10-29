package com.aisip.OnO.backend.common.transaction;

import com.aisip.OnO.backend.support.IntegrationTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * 커밋 후 실행 유틸.
 *
 * <p>실제 트랜잭션 매니저로 커밋과 롤백을 일으켜야 afterCommit 이 도는 시점을 확인할 수 있다.
 * 새 빈을 띄우지 않도록 공통 통합 테스트 컨텍스트를 그대로 쓴다.
 */
@DisplayName("커밋 후 실행")
class AfterCommitTest extends IntegrationTestSupport {

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Test
    @DisplayName("트랜잭션이 없으면 바로 실행한다")
    void runsImmediatelyWithoutTransaction() {
        AtomicBoolean ran = new AtomicBoolean(false);

        AfterCommit.run("테스트", () -> ran.set(true));

        assertThat(ran).isTrue();
    }

    @Test
    @DisplayName("트랜잭션 안에서는 커밋이 끝난 뒤에 실행한다")
    void runsAfterCommit() {
        AtomicBoolean ran = new AtomicBoolean(false);

        transactionTemplate.executeWithoutResult(status -> {
            AfterCommit.run("테스트", () -> ran.set(true));
            assertThat(ran)
                    .as("커밋 전에 돌면 그 사이 조회가 옛 데이터로 캐시를 다시 채운다")
                    .isFalse();
        });

        assertThat(ran).isTrue();
    }

    @Test
    @DisplayName("롤백되면 실행하지 않는다")
    void skipsOnRollback() {
        AtomicBoolean ran = new AtomicBoolean(false);

        transactionTemplate.executeWithoutResult(status -> {
            AfterCommit.run("테스트", () -> ran.set(true));
            status.setRollbackOnly();
        });

        assertThat(ran).isFalse();
    }

    @Test
    @DisplayName("트랜잭션 없이 실행한 작업이 실패해도 예외를 밖으로 내보내지 않는다")
    void swallowsFailureWithoutTransaction() {
        assertThatCode(() -> AfterCommit.run("테스트", () -> {
            throw new IllegalStateException("redis down");
        })).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("커밋 후 작업이 실패해도 요청은 성공하고 뒤에 등록한 작업도 실행된다")
    void failureDoesNotBreakCommitOrLaterTasks() {
        List<String> ran = new ArrayList<>();

        assertThatCode(() -> transactionTemplate.executeWithoutResult(status -> {
            AfterCommit.run("실패하는 작업", () -> {
                throw new IllegalStateException("redis down");
            });
            AfterCommit.run("뒤 작업", () -> ran.add("뒤 작업"));
        }))
                .as("이미 커밋된 요청이 캐시 삭제 실패로 500 이 되면 안 된다")
                .doesNotThrowAnyException();

        assertThat(ran).containsExactly("뒤 작업");
    }
}
