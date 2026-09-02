package com.aisip.OnO.backend.problemsolve.repository;

import com.aisip.OnO.backend.problem.entity.Problem;
import com.aisip.OnO.backend.problemsolve.ProblemSolveTestSupport;
import com.aisip.OnO.backend.problemsolve.entity.AnswerStatus;
import com.aisip.OnO.backend.problemsolve.entity.ProblemSolve;
import com.aisip.OnO.backend.problemsolve.entity.ProblemSolveImageData;
import com.aisip.OnO.backend.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ProblemSolveRepository 쿼리")
class ProblemSolveRepositoryTest extends ProblemSolveTestSupport {

    private User user;
    private User other;
    private Problem problem;
    private Problem othersProblem;

    @BeforeEach
    void setUpFixtures() {
        user = fixtures.createUser();
        other = fixtures.createOtherUser();
        problem = saveProblem(user.getId());
        othersProblem = saveProblem(other.getId());
    }

    @Nested
    @DisplayName("findByIdWithImages")
    class FindByIdWithImages {

        @Test
        @DisplayName("이미지를 함께 가져와 트랜잭션 밖에서도 접근할 수 있다")
        void fetchesImagesEagerly() {
            ProblemSolve solve = saveSolve(problem, user.getId(), PRACTICED_AT);
            saveImage(solve, "https://bucket.s3.amazonaws.com/a.png", 0);
            saveImage(solve, "https://bucket.s3.amazonaws.com/b.png", 1);

            ProblemSolve found = problemSolveRepository.findByIdWithImages(solve.getId()).orElseThrow();

            assertThat(found.getImages())
                    .as("fetch join 이 빠지면 응답 변환에서 LazyInitializationException 이 난다")
                    .extracting(ProblemSolveImageData::getImageUrl)
                    .containsExactlyInAnyOrder("https://bucket.s3.amazonaws.com/a.png",
                            "https://bucket.s3.amazonaws.com/b.png");
        }

        @Test
        @DisplayName("이미지가 없어도 기록 자체는 조회된다")
        void findsSolveWithoutImages() {
            ProblemSolve solve = saveSolve(problem, user.getId(), PRACTICED_AT);

            assertThat(problemSolveRepository.findByIdWithImages(solve.getId()))
                    .as("LEFT JOIN 이 아니면 이미지 없는 기록이 통째로 사라진다")
                    .isPresent();
        }

        @Test
        @DisplayName("없는 id 나 소프트 삭제된 기록은 비어 있다")
        void returnsEmptyForUnknownOrDeleted() {
            ProblemSolve solve = saveSolve(problem, user.getId(), PRACTICED_AT);
            problemSolveRepository.delete(solve);

            assertThat(problemSolveRepository.findByIdWithImages(solve.getId())).isEmpty();
            assertThat(problemSolveRepository.findByIdWithImages(999_999L)).isEmpty();
        }
    }

    @Nested
    @DisplayName("문제 기준 조회")
    class ByProblem {

        @Test
        @DisplayName("최근에 푼 순서로 정렬하고 이미지가 여러 장이어도 중복 없이 돌려준다")
        void returnsDistinctSolvesOrderedByPracticedAtDesc() {
            ProblemSolve older = saveSolve(problem, user.getId(), PRACTICED_AT);
            ProblemSolve newer = saveSolve(problem, user.getId(), PRACTICED_AT.plusDays(1));
            saveImage(newer, "https://bucket.s3.amazonaws.com/a.png", 0);
            saveImage(newer, "https://bucket.s3.amazonaws.com/b.png", 1);

            assertThat(problemSolveRepository.findAllByProblemIdWithImages(problem.getId()))
                    .extracting(ProblemSolve::getId)
                    .as("DISTINCT 가 없으면 이미지 수만큼 같은 기록이 중복된다")
                    .containsExactly(newer.getId(), older.getId());
        }

        @Test
        @DisplayName("다른 문제의 기록은 섞이지 않는다")
        void doesNotMixOtherProblems() {
            saveSolve(problem, user.getId(), PRACTICED_AT);
            saveSolve(othersProblem, other.getId(), PRACTICED_AT);

            assertThat(problemSolveRepository.findAllByProblemId(problem.getId())).hasSize(1);
        }

        @Test
        @DisplayName("개수는 소프트 삭제된 기록을 빼고 센다")
        void countExcludesSoftDeleted() {
            ProblemSolve deleted = saveSolve(problem, user.getId(), PRACTICED_AT);
            saveSolve(problem, user.getId(), PRACTICED_AT.plusDays(1));
            problemSolveRepository.delete(deleted);

            assertThat(problemSolveRepository.countByProblemId(problem.getId())).isEqualTo(1L);
            assertThat(countRowsIncludingDeleted(problem.getId())).isEqualTo(2);
        }

        @Test
        @DisplayName("기록이 없으면 개수는 0이다")
        void countIsZeroWithoutSolves() {
            assertThat(problemSolveRepository.countByProblemId(problem.getId())).isZero();
        }
    }

    @Nested
    @DisplayName("사용자 기준 조회")
    class ByUser {

        @Test
        @DisplayName("자기 기록만 최근에 푼 순서로 돌려준다")
        void returnsOwnSolvesOrderedByPracticedAtDesc() {
            ProblemSolve first = saveSolve(problem, user.getId(), PRACTICED_AT);
            ProblemSolve second = saveSolve(problem, user.getId(), PRACTICED_AT.plusDays(3));
            saveSolve(othersProblem, other.getId(), PRACTICED_AT.plusDays(10));

            assertThat(problemSolveRepository.findAllByUserId(user.getId()))
                    .extracting(ProblemSolve::getId)
                    .containsExactly(second.getId(), first.getId());
        }

        @Test
        @DisplayName("개수도 사용자별로 세고 소프트 삭제된 기록은 빠진다")
        void countsPerUserExcludingDeleted() {
            ProblemSolve deleted = saveSolve(problem, user.getId(), PRACTICED_AT);
            saveSolve(problem, user.getId(), PRACTICED_AT.plusDays(1));
            saveSolve(othersProblem, other.getId(), PRACTICED_AT);
            problemSolveRepository.delete(deleted);

            assertThat(problemSolveRepository.countByUserId(user.getId())).isEqualTo(1L);
            assertThat(problemSolveRepository.countByUserId(other.getId())).isEqualTo(1L);
        }
    }

    @Nested
    @DisplayName("마지막 복습 시각")
    class LastSolvedAt {

        @Test
        @DisplayName("가장 최근 복습 시각을 돌려준다")
        void returnsLatestPracticedAt() {
            saveSolve(problem, user.getId(), PRACTICED_AT);
            saveSolve(problem, user.getId(), PRACTICED_AT.plusDays(2));

            assertThat(problemSolveRepository.findLastSolvedAtByProblemId(problem.getId()))
                    .isEqualTo(PRACTICED_AT.plusDays(2));
        }

        @Test
        @DisplayName("기록이 없으면 null 이다")
        void returnsNullWithoutSolves() {
            assertThat(problemSolveRepository.findLastSolvedAtByProblemId(problem.getId())).isNull();
        }
    }

    @Nested
    @DisplayName("복습 요약 집계")
    class SolveSummaries {

        @Test
        @DisplayName("문제별 복습 횟수와 마지막 복습 시각을 한 번에 집계한다")
        void aggregatesPerProblem() {
            Problem anotherProblem = saveProblem(user.getId());
            saveSolve(problem, user.getId(), PRACTICED_AT);
            saveSolve(problem, user.getId(), PRACTICED_AT.plusDays(1));
            saveSolve(anotherProblem, user.getId(), PRACTICED_AT.plusDays(5));

            Map<Long, ProblemSolveSummary> summaries = problemSolveRepository
                    .findSolveSummariesByProblemIds(List.of(problem.getId(), anotherProblem.getId()))
                    .stream()
                    .collect(Collectors.toMap(ProblemSolveSummary::getProblemId, Function.identity()));

            assertThat(summaries.get(problem.getId()).getSolveCount()).isEqualTo(2L);
            assertThat(summaries.get(problem.getId()).getLastSolvedAt()).isEqualTo(PRACTICED_AT.plusDays(1));
            assertThat(summaries.get(anotherProblem.getId()).getSolveCount()).isEqualTo(1L);
        }

        @Test
        @DisplayName("기록이 없는 문제는 결과에 아예 나오지 않는다")
        void omitsProblemsWithoutSolves() {
            assertThat(problemSolveRepository.findSolveSummariesByProblemIds(List.of(problem.getId())))
                    .as("호출부는 결과 없음을 0회로 해석해야 한다")
                    .isEmpty();
        }

        @Test
        @DisplayName("소프트 삭제된 기록은 집계에서 빠진다")
        void excludesSoftDeleted() {
            ProblemSolve deleted = saveSolve(problem, user.getId(), PRACTICED_AT.plusDays(3));
            saveSolve(problem, user.getId(), PRACTICED_AT);
            problemSolveRepository.delete(deleted);

            List<ProblemSolveSummary> summaries =
                    problemSolveRepository.findSolveSummariesByProblemIds(List.of(problem.getId()));

            assertThat(summaries).hasSize(1);
            assertThat(summaries.get(0).getSolveCount()).isEqualTo(1L);
            assertThat(summaries.get(0).getLastSolvedAt()).isEqualTo(PRACTICED_AT);
        }
    }

    @Nested
    @DisplayName("문제별 일괄 삭제")
    class DeleteAllByProblemId {

        @Test
        @Transactional
        @DisplayName("해당 문제의 기록만 지운다")
        void deletesOnlyGivenProblemSolves() {
            saveSolve(problem, user.getId(), PRACTICED_AT);
            saveSolve(problem, user.getId(), PRACTICED_AT.plusDays(1));
            saveSolve(othersProblem, other.getId(), PRACTICED_AT);

            problemSolveRepository.deleteAllByProblemId(problem.getId());

            assertThat(problemSolveRepository.countByProblemId(problem.getId())).isZero();
            assertThat(problemSolveRepository.countByProblemId(othersProblem.getId())).isEqualTo(1L);
        }
    }

    @Nested
    @DisplayName("ProblemSolveImageDataRepository")
    class ImageData {

        @Test
        @DisplayName("복습 기록에 달린 이미지를 모두 찾는다")
        void findsImagesOfSolve() {
            ProblemSolve solve = saveSolve(problem, user.getId(), PRACTICED_AT);
            ProblemSolve another = saveSolve(problem, user.getId(), PRACTICED_AT.plusDays(1));
            saveImage(solve, "https://bucket.s3.amazonaws.com/a.png", 0);
            saveImage(another, "https://bucket.s3.amazonaws.com/b.png", 0);

            assertThat(problemSolveImageDataRepository.findAllByProblemSolveId(solve.getId()))
                    .extracting(ProblemSolveImageData::getImageUrl)
                    .containsExactly("https://bucket.s3.amazonaws.com/a.png");
        }

        @Test
        @DisplayName("URL 로 이미지를 찾고, 없으면 빈 값이다")
        void findsImageByUrl() {
            ProblemSolve solve = saveSolve(problem, user.getId(), PRACTICED_AT);
            saveImage(solve, "https://bucket.s3.amazonaws.com/a.png", 0);

            assertThat(problemSolveImageDataRepository.findByImageUrl("https://bucket.s3.amazonaws.com/a.png")).isPresent();
            assertThat(problemSolveImageDataRepository.findByImageUrl("https://bucket.s3.amazonaws.com/none.png")).isEmpty();
        }

        @Test
        @Transactional
        @DisplayName("URL 로 이미지를 지우면 더 이상 조회되지 않는다")
        void deletesImageByUrl() {
            ProblemSolve solve = saveSolve(problem, user.getId(), PRACTICED_AT);
            saveImage(solve, "https://bucket.s3.amazonaws.com/a.png", 0);

            problemSolveImageDataRepository.deleteByImageUrl("https://bucket.s3.amazonaws.com/a.png");

            assertThat(problemSolveImageDataRepository.findByImageUrl("https://bucket.s3.amazonaws.com/a.png")).isEmpty();
        }
    }

    @Nested
    @DisplayName("레거시 마이그레이션 기록")
    class LegacySolves {

        @Test
        @DisplayName("레거시에서 옮겨온 기록은 UNKNOWN 상태로 표시된다")
        void marksLegacySolves() {
            ProblemSolve legacy = problemSolveRepository.save(
                    ProblemSolve.createFromLegacy(problem, user.getId(), LocalDateTime.of(2025, 5, 1, 0, 0)));

            ProblemSolve found = problemSolveRepository.findByIdWithImages(legacy.getId()).orElseThrow();
            assertThat(found.getAnswerStatus()).isEqualTo(AnswerStatus.UNKNOWN);
            assertThat(found.getMigratedFromLegacy()).isTrue();
        }
    }
}
