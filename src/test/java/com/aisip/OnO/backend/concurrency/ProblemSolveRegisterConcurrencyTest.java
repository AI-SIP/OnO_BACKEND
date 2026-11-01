package com.aisip.OnO.backend.concurrency;

import com.aisip.OnO.backend.mission.entity.MissionType;
import com.aisip.OnO.backend.problem.entity.Problem;
import com.aisip.OnO.backend.problemsolve.ProblemSolveTestSupport;
import com.aisip.OnO.backend.problemsolve.dto.ProblemSolveRegisterDto;
import com.aisip.OnO.backend.problemsolve.entity.AnswerStatus;
import com.aisip.OnO.backend.problemsolve.service.ProblemSolveService;
import com.aisip.OnO.backend.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 같은 사용자의 복습 기록 등록이 동시에 들어올 때 (#401).
 *
 * <p>등록 트랜잭션은 복습 기록 INSERT, 미션 적립(사용자 행 FOR UPDATE), 문제의 복습 일정 UPDATE 순으로 돈다.
 * problem_solve 는 problem 을 FK 로 참조하므로 INSERT 때 InnoDB 가 문제 행에 공유 잠금을 건다.
 * 같은 문제에 두 요청이 겹치면 이렇게 된다.
 *
 * <ol>
 *   <li>A, B 둘 다 문제 행 공유 잠금을 잡는다 (복습 기록 INSERT).</li>
 *   <li>A 가 사용자 행 배타 잠금을 잡고, B 는 그 뒤에서 기다린다.</li>
 *   <li>A 가 커밋 직전 문제 행을 UPDATE 하려면 배타 잠금이 필요한데, B 의 공유 잠금에 막힌다.</li>
 * </ol>
 *
 * <p>A 는 B 의 문제 행 잠금을, B 는 A 의 사용자 행 잠금을 기다리므로 InnoDB 가 B 를 교착 희생자로 고른다.
 * B 가 멈춰 있던 자리가 사용자 행 FOR UPDATE 라 Sentry 스택에는 {@code MissionLogService.lockUser} 가 찍혔다.
 *
 * <p>여기서 고정하는 것은 두 가지다. 동시 등록이 500 으로 나가지 않는다는 것,
 * 그리고 같은 문제의 복습 미션 기록이 하루 한 건을 넘지 않는다는 것이다.
 */
@DisplayName("동시성 - 복습 기록 등록")
class ProblemSolveRegisterConcurrencyTest extends ProblemSolveTestSupport {

    /**
     * 다른 동시성 테스트(8)보다 적게 둔다.
     *
     * <p>등록이 커밋되면 {@code StudyRoomFeedService.handleActivity} 가 AFTER_COMMIT 에서 REQUIRES_NEW 로,
     * 그 안의 챌린지 확인이 다시 REQUIRES_NEW 로 커넥션을 더 가져간다. 원래 커넥션을 놓기 전이라
     * 요청 하나가 커넥션을 최대 3개 쥔다. 8개를 띄우면 기다리는 요청들이 기본 풀 10개를 먼저 다 쥐어
     * 교착과 무관하게 커넥션 대기로 멈춘다. 4개면 모두 막혀 있어도 쥔 커넥션이 8개라 풀이 마르지 않는다.
     * 교착은 두 요청만 겹쳐도 나므로 재현에는 4개로 충분하다(수정 전 4개 중 3개가 교착으로 실패했다).
     */
    private static final int THREAD_COUNT = 4;

    @Autowired
    private ProblemSolveService problemSolveService;

    private User user;

    @BeforeEach
    void setUpUser() {
        user = fixtures.createUser();
    }

    @Test
    @DisplayName("같은 문제의 복습 기록을 4번 동시에 등록해도 모두 성공하고 미션 기록은 한 건이다")
    void concurrentSolvesOnSameProblemAllSucceed() {
        Problem problem = saveProblem(user.getId());

        ConcurrentRunner.Outcome outcome = ConcurrentRunner.runConcurrently(
                THREAD_COUNT,
                () -> problemSolveService.createProblemSolve(registerDto(problem.getId()), user.getId()));

        assertThat(outcome.serverErrors())
                .as("문제 행 공유 잠금과 사용자 행 배타 잠금이 엇갈리면 교착으로 500 이 난다")
                .isEmpty();
        assertThat(countRowsIncludingDeleted(problem.getId()))
                .as("요청마다 복습 기록이 하나씩 남아야 한다")
                .isEqualTo(THREAD_COUNT);
        assertThat(countMissionLogs(MissionType.PROBLEM_PRACTICE))
                .as("같은 문제의 복습 미션은 하루 한 건이다")
                .isEqualTo(1);
    }

    @Test
    @DisplayName("서로 다른 문제의 복습 기록을 4번 동시에 등록해도 모두 성공한다")
    void concurrentSolvesOnDifferentProblemsAllSucceed() {
        List<Problem> problems = IntStream.range(0, THREAD_COUNT)
                .mapToObj(i -> saveProblem(user.getId()))
                .toList();

        ConcurrentRunner.Outcome outcome = ConcurrentRunner.runConcurrently(
                THREAD_COUNT,
                index -> problemSolveService.createProblemSolve(
                        registerDto(problems.get(index).getId()), user.getId()));

        assertThat(outcome.serverErrors()).isEmpty();
        assertThat(countMissionLogs(MissionType.PROBLEM_PRACTICE))
                .as("문제마다 한 건씩")
                .isEqualTo(THREAD_COUNT);
    }

    private ProblemSolveRegisterDto registerDto(Long problemId) {
        return new ProblemSolveRegisterDto(problemId, PRACTICED_AT, AnswerStatus.CORRECT, "회고", List.of(), 60, null);
    }

    /** mission_type 은 {@code @Enumerated} 없이 매핑돼 순번(ordinal)으로 저장된다. */
    private long countMissionLogs(MissionType missionType) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM mission_log WHERE user_id = ? AND mission_type = ?",
                Long.class, user.getId(), missionType.ordinal());
        return count == null ? 0 : count;
    }
}
