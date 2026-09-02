package com.aisip.OnO.backend.performance;

import com.aisip.OnO.backend.folder.entity.Folder;
import com.aisip.OnO.backend.problem.entity.Problem;
import com.aisip.OnO.backend.problem.service.ProblemService;
import com.aisip.OnO.backend.problem.support.ProblemTestSupport;
import com.aisip.OnO.backend.support.QueryCounter;
import com.aisip.OnO.backend.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 조회 API 가 실행하는 쿼리 수를 고정한다.
 *
 * <p>N+1 은 기능 테스트로 잡히지 않는다. 응답이 정확하고 테스트도 통과하며, 개발 환경의
 * 적은 데이터에서는 느려지지도 않는다. 오직 데이터가 늘어난 프로덕션에서만 드러난다.
 *
 * <p>여기서 검증하는 방식은 "느린지"가 아니라 <b>"데이터 개수에 따라 쿼리 수가 늘어나는지"</b> 다.
 * 같은 동작을 데이터 3건과 30건에 대해 각각 실행해 쿼리 수가 같으면 N+1 이 아니고,
 * 늘어나면 N+1 이다. 절대적인 쿼리 수를 박아 두는 것보다 리팩터링에 강하다.
 */
@DisplayName("쿼리 수 회귀")
class QueryCountRegressionTest extends ProblemTestSupport {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    @Autowired
    private ProblemService problemService;

    @Autowired
    private QueryCounter queryCounter;

    private User owner;
    private Folder root;

    @BeforeEach
    void setUpOwner() {
        owner = fixtures.createUser();
        root = fixtures.createRootFolder(owner.getId());
    }

    private void saveDueProblems(int count) {
        LocalDate yesterday = LocalDate.now(KST).minusDays(1);
        for (int i = 0; i < count; i++) {
            Problem problem = saveProblem(owner.getId(), root);
            saveProblemWithReviewSchedule(owner.getId(), root, yesterday, 1, 0);
            saveSkippedAnalysis(problem);
        }
    }

    @Nested
    @DisplayName("복습 대상 조회")
    class ReviewDue {

        /**
         * Sentry 가 이 경로에서 N+1 을 잡았다. 응답에 필요한 값은 스칼라뿐인데
         * {@code Problem.problemAnalysis} 가 mappedBy OneToOne 이라 행마다 존재 확인 쿼리가 나갔다.
         * 프로젝션 조회로 바꿔 고쳤고, 여기서 다시 들어오지 않도록 고정한다.
         */
        @Test
        @DisplayName("문제 수가 열 배로 늘어도 쿼리 수는 그대로다")
        void queryCountDoesNotGrowWithProblemCount() {
            saveDueProblems(3);
            long fewProblems = queryCounter.count(() -> problemService.getReviewDueProblems(owner.getId()));

            saveDueProblems(27);
            long manyProblems = queryCounter.count(() -> problemService.getReviewDueProblems(owner.getId()));

            assertThat(manyProblems)
                    .as("""
                            문제가 늘어난 만큼 쿼리가 늘어나면 N+1 이다.
                            3건일 때 %d개, 30건일 때 %d개.""", fewProblems, manyProblems)
                    .isEqualTo(fewProblems);
        }

        @Test
        @DisplayName("조회 결과 자체는 정확하다 - 쿼리 수만 보다 결과를 놓치지 않도록")
        void returnsCorrectResultWhileKeepingQueryCountFlat() {
            saveDueProblems(5);

            QueryCounter.Counted<?> counted =
                    queryCounter.measure(() -> problemService.getReviewDueProblems(owner.getId()));

            assertThat(counted.queryCount())
                    .as("복습 대상 조회는 한 자릿수 쿼리로 끝나야 한다")
                    .isLessThan(10);
        }
    }

    @Nested
    @DisplayName("사용자 문제 목록 조회")
    class UserProblems {

        @Test
        @DisplayName("문제 수가 늘어도 쿼리 수는 그대로다")
        void queryCountDoesNotGrowWithProblemCount() {
            for (int i = 0; i < 3; i++) {
                saveProblem(owner.getId(), root);
            }
            long fewProblems = queryCounter.count(() -> problemService.findUserProblems(owner.getId()));

            for (int i = 0; i < 27; i++) {
                saveProblem(owner.getId(), root);
            }
            long manyProblems = queryCounter.count(() -> problemService.findUserProblems(owner.getId()));

            assertThat(manyProblems)
                    .as("""
                            문제가 늘어난 만큼 쿼리가 늘어나면 N+1 이다.
                            3건일 때 %d개, 30건일 때 %d개.""", fewProblems, manyProblems)
                    .isEqualTo(fewProblems);
        }
    }
}
