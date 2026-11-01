package com.aisip.OnO.backend.studyroom.service;

import com.aisip.OnO.backend.studyroom.entity.StudyRoomFeedEventType;
import com.aisip.OnO.backend.studyroom.event.StudyRoomActivityEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;

/**
 * 활동 뒤처리의 실패 격리. 피드 생성과 챌린지 판정은 서로의 실패에 영향을 받지 않아야 한다.
 * 실패를 만들려면 서비스를 터뜨려야 해서 목으로 조립한다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("스터디룸 활동 뒤처리 실패 격리")
class StudyRoomActivityListenerTest {

    private static final StudyRoomActivityEvent EVENT =
            new StudyRoomActivityEvent(1L, StudyRoomFeedEventType.PRACTICE_COMPLETED, Map.of());

    @Mock
    private StudyRoomFeedService feedService;

    @Mock
    private StudyRoomChallengeService challengeService;

    private StudyRoomActivityListener listener;

    @BeforeEach
    void setUp() {
        listener = new StudyRoomActivityListener(feedService, challengeService);
    }

    @AfterEach
    void tearDown() {
        listener.destroy();
    }

    @Test
    @DisplayName("피드 생성이 실패해도 챌린지 판정은 돈다")
    void challengeRunsEvenIfFeedFails() {
        willThrow(new IllegalStateException("피드 실패"))
                .given(feedService).createFeedsForUserRooms(anyLong(), any(), any());

        assertThatCode(() -> listener.process(EVENT)).doesNotThrowAnyException();

        verify(challengeService).checkAndNotifyChallengeCompletionForUser(1L);
    }

    @Test
    @DisplayName("챌린지 판정이 실패해도 예외가 밖으로 새지 않는다")
    void challengeFailureIsSwallowed() {
        willThrow(new IllegalStateException("판정 실패"))
                .given(challengeService).checkAndNotifyChallengeCompletionForUser(1L);

        assertThatCode(() -> listener.process(EVENT)).doesNotThrowAnyException();

        verify(feedService).createFeedsForUserRooms(1L, StudyRoomFeedEventType.PRACTICE_COMPLETED, Map.of());
    }

    @Test
    @DisplayName("이벤트를 받으면 호출한 스레드가 아니라 실행기 스레드에서 처리한다")
    void processesOnExecutorThread() {
        Thread caller = Thread.currentThread();
        Thread[] worker = new Thread[1];
        willAnswer(invocation -> {
            worker[0] = Thread.currentThread();
            return null;
        }).given(feedService).createFeedsForUserRooms(anyLong(), any(), any());

        listener.onActivity(EVENT);

        assertThat(listener.awaitIdle(Duration.ofSeconds(5))).isTrue();
        assertThat(worker[0]).isNotNull().isNotSameAs(caller);
        assertThat(worker[0].getName()).startsWith("study-room-activity-");
    }

    @Test
    @DisplayName("같은 사용자의 활동은 한 스레드에서 들어온 순서대로 처리해 피드 누적이 겹치지 않는다")
    void sameUserRunsSequentiallyOnOneThread() {
        Set<String> threads = ConcurrentHashMap.newKeySet();
        List<Integer> order = new CopyOnWriteArrayList<>();
        AtomicInteger running = new AtomicInteger();
        AtomicInteger maxRunning = new AtomicInteger();
        willAnswer(invocation -> {
            threads.add(Thread.currentThread().getName());
            maxRunning.accumulateAndGet(running.incrementAndGet(), Math::max);
            Thread.sleep(20);
            Map<String, Object> metadata = invocation.getArgument(2);
            order.add((Integer) metadata.get("seq"));
            running.decrementAndGet();
            return null;
        }).given(feedService).createFeedsForUserRooms(anyLong(), any(), any());

        for (int i = 0; i < 6; i++) {
            listener.onActivity(new StudyRoomActivityEvent(7L, StudyRoomFeedEventType.PROBLEM_REGISTERED, Map.of("seq", i)));
        }

        assertThat(listener.awaitIdle(Duration.ofSeconds(5))).isTrue();
        assertThat(threads).hasSize(1);
        assertThat(maxRunning.get()).as("같은 사용자의 뒤처리가 동시에 돌면 피드가 갈라지거나 개수를 잃는다").isEqualTo(1);
        assertThat(order).containsExactly(0, 1, 2, 3, 4, 5);
    }

    @Test
    @DisplayName("다른 사용자의 활동은 다른 스레드로 나뉘어 함께 처리될 수 있다")
    void differentUsersCanRunOnDifferentThreads() {
        Set<String> threads = ConcurrentHashMap.newKeySet();
        willAnswer(invocation -> {
            threads.add(Thread.currentThread().getName());
            return null;
        }).given(feedService).createFeedsForUserRooms(anyLong(), any(), any());

        listener.onActivity(new StudyRoomActivityEvent(1L, StudyRoomFeedEventType.PRACTICE_COMPLETED, Map.of()));
        listener.onActivity(new StudyRoomActivityEvent(2L, StudyRoomFeedEventType.PRACTICE_COMPLETED, Map.of()));

        assertThat(listener.awaitIdle(Duration.ofSeconds(5))).isTrue();
        assertThat(threads).hasSize(StudyRoomActivityListener.WORKERS);
    }
}
