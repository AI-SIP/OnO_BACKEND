package com.aisip.OnO.backend.concurrency;

import com.aisip.OnO.backend.problem.entity.Problem;
import com.aisip.OnO.backend.problemsolve.dto.ProblemSolveRegisterDto;
import com.aisip.OnO.backend.problemsolve.entity.AnswerStatus;
import com.aisip.OnO.backend.problemsolve.service.ProblemSolveService;
import com.aisip.OnO.backend.studyroom.entity.StudyRoom;
import com.aisip.OnO.backend.studyroom.entity.StudyRoomChallenge;
import com.aisip.OnO.backend.studyroom.entity.StudyRoomChallengeMetric;
import com.aisip.OnO.backend.studyroom.entity.StudyRoomChallengeStatus;
import com.aisip.OnO.backend.studyroom.entity.StudyRoomChallengeType;
import com.aisip.OnO.backend.studyroom.entity.StudyRoomFeedEventType;
import com.aisip.OnO.backend.studyroom.service.StudyRoomActivityListener;
import com.aisip.OnO.backend.studyroom.support.StudyRoomTestSupport;
import com.aisip.OnO.backend.user.entity.User;
import com.github.gavlyukovskiy.boot.jdbc.decorator.DecoratedDataSource;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import javax.sql.DataSource;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

/**
 * 복습 등록 뒤 스터디룸 피드와 챌린지 판정이 커넥션 풀을 바닥내지 않는지 본다. (#405)
 *
 * <p>예전에는 복습 등록이 커밋되면 같은 요청 스레드에서 {@code AFTER_COMMIT} + {@code REQUIRES_NEW} 로
 * 피드를 만들고, 그 안에서 다시 {@code REQUIRES_NEW} 로 챌린지를 판정했다. 바깥 트랜잭션의 커넥션은
 * {@code afterCommit} 콜백이 끝나야 반납되므로 요청 하나가 커넥션을 <b>세 개까지</b> 동시에 쥐었다.
 * 풀 크기만큼 등록이 겹치면 모두 첫 커넥션을 쥔 채 두 번째를 기다려 서로를 막고,
 * {@code connection-timeout}(30초) 뒤에 전부 실패했다.
 *
 * <p>지금은 커밋 뒤 작업을 전용 실행기로 넘기고, 실행기에서는 피드와 챌린지 판정을 차례로 각자의
 * 트랜잭션으로 돌린다. 요청 스레드는 자기 커넥션 하나만 쥐고, 뒤처리는 실행기 스레드 수만큼만 커넥션을 쓴다.
 *
 * <p>챌린지 달성 알림은 FCM 실발송 경로라 베이스의 목으로만 확인한다.
 */
@DisplayName("동시성 - 복습 등록 뒤처리와 커넥션 풀 (#405)")
class StudyRoomActivityConnectionPoolTest extends StudyRoomTestSupport {

    private static final Duration ASYNC_TIMEOUT = Duration.ofSeconds(15);

    @Autowired
    private ProblemSolveService problemSolveService;

    @Autowired
    private StudyRoomActivityListener activityListener;

    @Autowired
    private DataSource dataSource;

    private int poolSize;

    @BeforeEach
    void readPoolSize() {
        poolSize = hikariDataSource().getMaximumPoolSize();
    }

    @Test
    @DisplayName("풀 크기만큼 복습 등록이 동시에 들어와도 커넥션을 기다리다 멈추지 않는다")
    void concurrentSolvesDoNotExhaustPool() {
        List<Learner> learners = new ArrayList<>();
        for (int i = 0; i < poolSize; i++) {
            learners.add(learnerWithChallenge());
        }

        ConcurrentRunner.Outcome outcome = ConcurrentRunner.runConcurrently(poolSize, index -> {
            Learner learner = learners.get(index);
            problemSolveService.createProblemSolve(solveOf(learner.problem()), learner.user().getId());
        });

        assertThat(outcome.failures())
                .as("커넥션 대기로 실패한 등록이 있으면 안 된다. 풀: %s", poolStats())
                .isEmpty();
        assertThat(problemSolveRepository.count()).isEqualTo(poolSize);

        awaitActivityProcessed();
        assertThat(feedRepository.findAll())
                .as("뒤처리를 떼어 내도 방마다 복습 피드는 남아야 한다")
                .hasSize(poolSize)
                .allSatisfy(feed -> assertThat(feed.getEventType()).isEqualTo(StudyRoomFeedEventType.PRACTICE_COMPLETED));
        for (Learner learner : learners) {
            verify(fcmService, timeout(ASYNC_TIMEOUT.toMillis()))
                    .sendNotificationToAllUserDevice(eq(learner.user().getId()), any());
            assertThat(challengeRepository.findById(learner.challenge().getId()).orElseThrow().getStatus())
                    .isEqualTo(StudyRoomChallengeStatus.COMPLETED);
        }
    }

    @Test
    @DisplayName("커밋된 복습은 피드를 남기고 달성한 챌린지를 완료 처리한다")
    void committedSolveCreatesFeedAndCompletesChallenge() {
        Learner learner = learnerWithChallenge();

        problemSolveService.createProblemSolve(solveOf(learner.problem()), learner.user().getId());

        awaitActivityProcessed();
        assertThat(feedRepository.findAll()).singleElement()
                .satisfies(feed -> assertThat(feed.getEventType()).isEqualTo(StudyRoomFeedEventType.PRACTICE_COMPLETED));
        assertThat(challengeRepository.findById(learner.challenge().getId()).orElseThrow().getStatus())
                .isEqualTo(StudyRoomChallengeStatus.COMPLETED);
        verify(fcmService, timeout(ASYNC_TIMEOUT.toMillis()))
                .sendNotificationToAllUserDevice(eq(learner.user().getId()), any());
    }

    @Test
    @DisplayName("복습 등록이 롤백되면 피드도 챌린지 판정도 돌지 않는다")
    void rolledBackSolveTriggersNothing() {
        Learner learner = learnerWithChallenge();

        transactionTemplate.executeWithoutResult(status -> {
            problemSolveService.createProblemSolve(solveOf(learner.problem()), learner.user().getId());
            status.setRollbackOnly();
        });

        awaitActivityProcessed();
        assertThat(problemSolveRepository.count()).isZero();
        assertThat(feedRepository.count()).isZero();
        assertThat(challengeRepository.findById(learner.challenge().getId()).orElseThrow().getStatus())
                .isEqualTo(StudyRoomChallengeStatus.IN_PROGRESS);
        verify(fcmService, never()).sendNotificationToAllUserDevice(any(), any());
    }

    private Learner learnerWithChallenge() {
        User user = fixtures.createUser("learner");
        StudyRoom room = createRoom(user);
        StudyRoomChallenge challenge = saveChallenge(room, "복습 1번", StudyRoomChallengeType.INDIVIDUAL,
                StudyRoomChallengeMetric.PRACTICE_COUNT, null, null, 1,
                LocalDateTime.now().minusHours(1), LocalDateTime.now().plusDays(7));
        return new Learner(user, saveProblem(user.getId()), challenge);
    }

    private static ProblemSolveRegisterDto solveOf(Problem problem) {
        return new ProblemSolveRegisterDto(problem.getId(), null, AnswerStatus.CORRECT, null, null, 60, null);
    }

    private void awaitActivityProcessed() {
        assertThat(activityListener.awaitIdle(ASYNC_TIMEOUT))
                .as("커밋 뒤 작업이 %s 안에 끝나지 않았다", ASYNC_TIMEOUT)
                .isTrue();
    }

    private String poolStats() {
        var pool = hikariDataSource().getHikariPoolMXBean();
        return "최대 %d, 사용중 %d, 유휴 %d, 대기 %d".formatted(poolSize, pool.getActiveConnections(),
                pool.getIdleConnections(), pool.getThreadsAwaitingConnection());
    }

    /** p6spy 가 DataSource 를 감싸고 있어서 원본을 한 겹 벗겨야 Hikari 가 나온다. */
    private HikariDataSource hikariDataSource() {
        DataSource candidate = dataSource instanceof DecoratedDataSource decorated
                ? decorated.getRealDataSource()
                : dataSource;
        if (candidate instanceof HikariDataSource hikari) {
            return hikari;
        }
        throw new IllegalStateException("HikariDataSource 를 꺼내지 못했다: " + dataSource.getClass());
    }

    private record Learner(User user, Problem problem, StudyRoomChallenge challenge) {
    }
}
