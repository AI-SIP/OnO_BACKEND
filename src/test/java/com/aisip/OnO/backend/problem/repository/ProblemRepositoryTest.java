package com.aisip.OnO.backend.problem.repository;

import com.aisip.OnO.backend.folder.entity.Folder;
import com.aisip.OnO.backend.problem.entity.AnalysisStatus;
import com.aisip.OnO.backend.problem.entity.Problem;
import com.aisip.OnO.backend.problem.entity.ProblemAnalysis;
import com.aisip.OnO.backend.problem.entity.ProblemImageType;
import com.aisip.OnO.backend.problem.service.ProblemService;
import com.aisip.OnO.backend.problem.support.ProblemTestSupport;
import com.aisip.OnO.backend.tag.entity.Tag;
import com.aisip.OnO.backend.user.entity.User;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

/**
 * ProblemRepository DB 연동 테스트.
 *
 * <p>fetch join 여부, 커서 조건, 소프트 딜리트 필터처럼 SQL 로 내려가야만 확인되는 것들을 본다.
 * 프로덕션과 같은 MySQL 위에서 돌기 때문에 콜레이션에 따른 대소문자 처리도 실제와 같다.
 */
@DisplayName("ProblemRepository")
class ProblemRepositoryTest extends ProblemTestSupport {

    @Autowired
    private ProblemService problemService;

    private User owner;
    private User intruder;
    private Folder ownerRoot;
    private Folder intruderRoot;

    @BeforeEach
    void setUpUsers() {
        owner = fixtures.createUser();
        intruder = fixtures.createOtherUser();
        ownerRoot = fixtures.createRootFolder(owner.getId());
        intruderRoot = fixtures.createRootFolder(intruder.getId());
    }

    // ════════════════════════════ 기본 조회 ════════════════════════════

    @Nested
    @DisplayName("사용자/폴더 기준 조회")
    class BasicLookup {

        @Test
        @DisplayName("사용자 문제 조회는 이미지까지 fetch join 으로 함께 읽는다")
        void fetchesImagesTogether() {
            Problem problem = saveProblem(owner.getId(), ownerRoot);
            saveImageData(problem, "https://s3/p1.png", ProblemImageType.PROBLEM_IMAGE);
            saveImageData(problem, "https://s3/p2.png", ProblemImageType.ANSWER_IMAGE);

            List<Problem> problems = problemRepository.findAllByUserId(owner.getId());

            assertThat(problems).hasSize(1);
            assertThat(problems.get(0).getProblemImageDataList())
                    .as("영속성 컨텍스트 밖에서도 이미지에 접근할 수 있어야 한다")
                    .hasSize(2);
        }

        @Test
        @DisplayName("사용자 문제 조회에 다른 사용자의 문제는 섞이지 않는다")
        void isUserScoped() {
            saveProblem(owner.getId(), ownerRoot);
            saveProblem(intruder.getId(), intruderRoot);

            assertThat(problemRepository.findAllByUserId(owner.getId())).hasSize(1);
            assertThat(problemRepository.findAllByUserId(intruder.getId())).hasSize(1);
        }

        @Test
        @DisplayName("폴더 기준 조회는 해당 폴더 문제만 반환한다")
        void findsByFolder() {
            Folder subFolder = fixtures.createFolder(owner.getId(), "하위", ownerRoot);
            saveProblem(owner.getId(), ownerRoot);
            saveProblem(owner.getId(), subFolder);

            assertThat(problemRepository.findAllByFolderId(subFolder.getId())).hasSize(1);
        }

        @Test
        @DisplayName("소프트 딜리트된 문제는 어떤 조회에도 나오지 않는다")
        void excludesSoftDeleted() {
            Problem problem = saveProblem(owner.getId(), ownerRoot);
            problemRepository.deleteById(problem.getId());

            assertThat(problemRepository.findById(problem.getId())).isEmpty();
            assertThat(problemRepository.findAllByUserId(owner.getId())).isEmpty();
            assertThat(problemRepository.findAllByFolderId(ownerRoot.getId())).isEmpty();
            assertThat(problemRepository.findProblemWithImageData(problem.getId())).isEmpty();
        }

        @Test
        @DisplayName("countByUserId 는 사용자별로 센다")
        void countsPerUser() {
            saveProblem(owner.getId(), ownerRoot);
            saveProblem(owner.getId(), ownerRoot);
            saveProblem(intruder.getId(), intruderRoot);

            assertThat(problemRepository.countByUserId(owner.getId())).isEqualTo(2L);
            assertThat(problemRepository.countByUserId(intruder.getId())).isEqualTo(1L);
        }
    }

    // ════════════════════════════ 커서 조회 ════════════════════════════

    @Nested
    @DisplayName("커서 기반 조회")
    class CursorQueries {

        @Test
        @DisplayName("폴더 커서 조회는 hasNext 판단을 위해 size+1 건까지 읽는다")
        void readsOneMoreThanSize() {
            for (int i = 0; i < 5; i++) {
                saveProblem(owner.getId(), ownerRoot, "문제" + i, null);
            }

            assertThat(problemRepository.findProblemsByFolderWithCursor(ownerRoot.getId(), null, 3))
                    .as("size 3 이면 다음 페이지 존재 여부 확인용으로 4건을 읽는다")
                    .hasSize(4);
        }

        @Test
        @DisplayName("커서 이후의 문제만 ID 오름차순으로 반환한다")
        void returnsRowsAfterCursor() {
            List<Problem> problems = List.of(
                    saveProblem(owner.getId(), ownerRoot, "1", null),
                    saveProblem(owner.getId(), ownerRoot, "2", null),
                    saveProblem(owner.getId(), ownerRoot, "3", null)
            );

            List<Problem> page = problemRepository.findProblemsByFolderWithCursor(
                    ownerRoot.getId(), problems.get(0).getId(), 10);

            assertThat(page)
                    .extracting(Problem::getId)
                    .containsExactly(problems.get(1).getId(), problems.get(2).getId());
        }

        @Test
        @DisplayName("태그 커서 조회는 태그와 사용자 두 조건을 모두 만족하는 문제만 반환한다")
        void tagCursorIsUserScoped() {
            Tag ownerTag = saveTag(owner.getId(), "공통태그");
            Problem mine = saveProblem(owner.getId(), ownerRoot);
            saveTagMapping(mine, ownerTag);

            assertThat(problemRepository.findProblemsByTagWithCursor(ownerTag.getId(), owner.getId(), null, 10))
                    .hasSize(1);
            assertThat(problemRepository.findProblemsByTagWithCursor(ownerTag.getId(), intruder.getId(), null, 10))
                    .as("태그 ID 를 알아내도 남의 문제는 못 본다")
                    .isEmpty();
        }

        @Test
        @DisplayName("태그가 여러 개 붙어도 문제는 중복 없이 한 번만 나온다")
        void tagCursorDeduplicates() {
            Problem problem = saveProblem(owner.getId(), ownerRoot);
            Tag tag = saveTag(owner.getId(), "중복확인");
            saveTagMapping(problem, tag);

            assertThat(problemRepository.findProblemsByTagWithCursor(tag.getId(), owner.getId(), null, 10))
                    .hasSize(1);
        }

        @Test
        @DisplayName("제목 검색은 대소문자를 가리지 않는 부분 일치다")
        void titleSearchIsCaseInsensitiveContains() {
            saveProblem(owner.getId(), ownerRoot, "메모", "Calculus Chapter 3");

            assertThat(problemRepository.findProblemsByTitleWithCursor("CALCULUS", owner.getId(), null, 10))
                    .hasSize(1);
            assertThat(problemRepository.findProblemsByTitleWithCursor("chapter", owner.getId(), null, 10))
                    .hasSize(1);
            assertThat(problemRepository.findProblemsByTitleWithCursor("없는단어", owner.getId(), null, 10))
                    .isEmpty();
        }

        @Test
        @DisplayName("제목 검색도 사용자 범위를 벗어나지 않는다")
        void titleSearchIsUserScoped() {
            saveProblem(intruder.getId(), intruderRoot, "메모", "남의 제목");

            assertThat(problemRepository.findProblemsByTitleWithCursor("남의", owner.getId(), null, 10)).isEmpty();
        }
    }

    // ════════════════════════════ 복습 대상 ════════════════════════════

    @Nested
    @DisplayName("복습 대상 조회")
    class ReviewDueQueries {

        @Test
        @DisplayName("오늘 이하의 복습 예정일만 nextReviewAt 오름차순으로 반환한다")
        void returnsDueProblemsOrdered() {
            LocalDate today = LocalDate.now();
            saveProblemWithReviewSchedule(owner.getId(), ownerRoot, today, 1, 0);
            saveProblemWithReviewSchedule(owner.getId(), ownerRoot, today.minusDays(5), 2, 1);
            saveProblemWithReviewSchedule(owner.getId(), ownerRoot, today.plusDays(1), 4, 2);

            List<ReviewDueProblemProjection> due = problemRepository.findReviewDueProblems(owner.getId(), today);

            assertThat(due).hasSize(2);
            assertThat(due)
                    .extracting(ReviewDueProblemProjection::nextReviewAt)
                    .containsExactly(today.minusDays(5), today);
        }

        @Test
        @DisplayName("nextReviewAt 이 null 인 문제는 대상이 아니다")
        void excludesNullSchedule() {
            saveProblemWithReviewSchedule(owner.getId(), ownerRoot, null, 8, 3);

            assertThat(problemRepository.findReviewDueProblems(owner.getId(), LocalDate.now())).isEmpty();
        }

        @Test
        @DisplayName("다른 사용자의 복습 대상은 반환하지 않는다")
        void isUserScoped() {
            saveProblemWithReviewSchedule(intruder.getId(), intruderRoot, LocalDate.now(), 1, 0);

            assertThat(problemRepository.findReviewDueProblems(owner.getId(), LocalDate.now())).isEmpty();
        }

        @Test
        @DisplayName("복습 대상 조회는 문제 개수와 무관하게 쿼리 한 번으로 끝난다 (N+1 방지)")
        void doesNotTriggerNPlusOneQueries() {
            // 서비스는 Asia/Seoul 기준으로 오늘을 계산하므로 픽스처도 같은 기준을 쓴다.
            LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));
            for (int i = 0; i < 10; i++) {
                Problem problem = saveProblemWithReviewSchedule(owner.getId(), ownerRoot, today, 1, 0);
                saveSkippedAnalysis(problem);
            }

            SessionFactory sessionFactory = entityManager.getEntityManagerFactory().unwrap(SessionFactory.class);
            Statistics statistics = sessionFactory.getStatistics();
            boolean wasEnabled = statistics.isStatisticsEnabled();
            statistics.setStatisticsEnabled(true);
            statistics.clear();

            try {
                assertThat(problemService.getReviewDueProblems(owner.getId()).dueCount()).isEqualTo(10);

                assertThat(statistics.getPrepareStatementCount())
                        .as("엔티티로 읽으면 mappedBy OneToOne(problemAnalysis) 때문에 행마다 select 가 한 번씩 더 나간다")
                        .isEqualTo(1L);
            } finally {
                statistics.setStatisticsEnabled(wasEnabled);
            }
        }

        @Test
        @DisplayName("복습 대상 요약은 사용자별 due 개수를 집계한다")
        void summarizesDueCountPerUser() {
            LocalDate today = LocalDate.now();
            saveProblemWithReviewSchedule(owner.getId(), ownerRoot, today, 1, 0);
            saveProblemWithReviewSchedule(owner.getId(), ownerRoot, today.minusDays(1), 1, 0);
            saveProblemWithReviewSchedule(intruder.getId(), intruderRoot, today, 1, 0);

            List<ReviewDueSummary> summaries = problemRepository.findReviewDueSummaryByDate(today);

            assertThat(summaries)
                    .extracting(ReviewDueSummary::getUserId, ReviewDueSummary::getDueCount)
                    .containsExactlyInAnyOrder(
                            tuple(owner.getId(), 2L),
                            tuple(intruder.getId(), 1L)
                    );
        }
    }

    // ════════════════════════════ 통계 ════════════════════════════

    @Nested
    @DisplayName("관리자 통계 쿼리")
    class StatisticsQueries {

        @Test
        @DisplayName("관리자 목록은 최신순으로 페이지를 만든다")
        void paginatesAdminProblems() {
            for (int i = 0; i < 3; i++) {
                saveProblem(owner.getId(), ownerRoot, "관리자" + i, null);
            }

            var page = problemRepository.findAdminProblems(PageRequest.of(0, 2));

            assertThat(page.getContent()).hasSize(2);
            assertThat(page.getTotalElements()).isEqualTo(3);
        }

        @Test
        @DisplayName("삭제된 문제의 분석은 집계에서 제외된다")
        void excludesDeletedProblemAnalyses() {
            Problem kept = saveProblem(owner.getId(), ownerRoot);
            saveSkippedAnalysis(kept);
            Problem deleted = saveProblem(owner.getId(), ownerRoot);
            saveSkippedAnalysis(deleted);
            problemRepository.deleteById(deleted.getId());

            assertThat(problemRepository.countProblemAnalysesForActiveProblems()).isEqualTo(1L);
            assertThat(problemRepository.countProblemAnalysesByStatusForActiveProblems())
                    .containsEntry(AnalysisStatus.NOT_STARTED, 1L);
        }

        @Test
        @DisplayName("상태별 집계는 상태가 섞여 있어도 각각 센다")
        void countsByStatus() {
            Problem notStarted = saveProblem(owner.getId(), ownerRoot);
            saveSkippedAnalysis(notStarted);

            Problem completed = saveProblem(owner.getId(), ownerRoot);
            saveSkippedAnalysis(completed);
            inTransaction(() -> {
                ProblemAnalysis analysis = problemAnalysisRepository.findByProblemId(completed.getId()).orElseThrow();
                analysis.updateWithSuccess("수학", "계산", "[]", "풀이", "실수", "팁");
                problemAnalysisRepository.save(analysis);
            });

            assertThat(problemRepository.countProblemAnalysesByStatusForActiveProblems())
                    .containsEntry(AnalysisStatus.NOT_STARTED, 1L)
                    .containsEntry(AnalysisStatus.COMPLETED, 1L);
        }

        @Test
        @DisplayName("일자별 등록 수는 요청 구간의 모든 날짜를 0으로라도 채운다")
        void fillsEveryDateInRange() {
            saveProblem(owner.getId(), ownerRoot);
            LocalDate today = LocalDate.now();

            var counts = problemRepository.countDailyProblems(today.minusDays(3), today);

            assertThat(counts).hasSize(4);
            assertThat(counts.get(today)).isEqualTo(1L);
            assertThat(counts.get(today.minusDays(3))).isZero();
        }
    }
}
