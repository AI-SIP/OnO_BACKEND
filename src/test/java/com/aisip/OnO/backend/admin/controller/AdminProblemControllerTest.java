package com.aisip.OnO.backend.admin.controller;

import com.aisip.OnO.backend.admin.dto.AdminLearningDto.NoteRef;
import com.aisip.OnO.backend.admin.dto.AdminLearningDto.ProblemDetail;
import com.aisip.OnO.backend.admin.dto.AdminLearningDto.ProblemRow;
import com.aisip.OnO.backend.admin.dto.AdminLearningDto.ProblemSummary;
import com.aisip.OnO.backend.admin.dto.AdminLearningDto.SolveRow;
import com.aisip.OnO.backend.admin.dto.AdminPager;
import com.aisip.OnO.backend.admin.support.AdminTestSupport;
import com.aisip.OnO.backend.folder.entity.Folder;
import com.aisip.OnO.backend.practicenote.entity.PracticeNote;
import com.aisip.OnO.backend.problem.entity.Problem;
import com.aisip.OnO.backend.problemsolve.entity.AnswerStatus;
import com.aisip.OnO.backend.problemsolve.entity.ProblemSolve;
import com.aisip.OnO.backend.problemsolve.repository.ProblemSolveRepository;
import com.aisip.OnO.backend.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@DisplayName("AdminProblemController")
class AdminProblemControllerTest extends AdminTestSupport {

    @Autowired
    private ProblemSolveRepository problemSolveRepository;

    private User owner;
    private Folder folder;

    @BeforeEach
    void setUp() {
        authenticateAs(createAdminUser().getId(), "ROLE_ADMIN");
        owner = fixtures.createUser();
        folder = fixtures.createRootFolder(owner.getId());
    }

    @SuppressWarnings("unchecked")
    private List<ProblemRow> problemsOf(MvcResult result) {
        return (List<ProblemRow>) result.getModelAndView().getModel().get("problems");
    }

    private Object attribute(MvcResult result, String name) {
        return result.getModelAndView().getModel().get(name);
    }

    private void saveAnalysis(Problem problem, String status, String subject) {
        jdbcTemplate.update("""
                INSERT INTO problem_analysis (problem_id, status, subject, created_at, updated_at)
                VALUES (?, ?, ?, NOW(), NOW())
                """, problem.getId(), status, subject);
    }

    private ProblemSolve saveSolve(Problem problem, Long userId, AnswerStatus answerStatus, LocalDateTime practicedAt) {
        return problemSolveRepository.save(ProblemSolve.create(
                problem, userId, practicedAt, answerStatus, "다시 보니 부호를 틀렸다", "[\"FASTER_SOLVING\"]", 95, "sprout_growth"));
    }

    @Nested
    @DisplayName("문제 목록")
    class ProblemList {

        @Test
        @DisplayName("문제가 하나도 없어도 0 기반 집계를 돌려준다")
        void rendersZeroBasedResultWhenNoProblemExists() throws Exception {
            MvcResult result = mockMvc.perform(get("/admin/problems"))
                    .andExpect(status().isOk())
                    .andExpect(view().name("problems"))
                    .andExpect(model().attribute("totalProblems", 0L))
                    .andExpect(model().attribute("totalPages", 0))
                    .andExpect(model().attribute("currentPage", 0))
                    .andExpect(model().attribute("filtered", false))
                    .andReturn();

            assertThat(problemsOf(result)).isEmpty();
            assertThat(((ProblemSummary) attribute(result, "summary")).total()).isZero();
        }

        @Test
        @DisplayName("등록된 문제를 최신순으로 페이지 단위로 보여준다")
        void paginatesProblems() throws Exception {
            saveProblem(owner.getId(), folder, "문제1");
            saveProblem(owner.getId(), folder, "문제2");
            Problem latest = saveProblem(owner.getId(), folder, "문제3");

            MvcResult result = mockMvc.perform(get("/admin/problems").param("size", "2"))
                    .andExpect(status().isOk())
                    .andExpect(model().attribute("totalProblems", 3L))
                    .andExpect(model().attribute("totalPages", 2))
                    .andReturn();

            assertThat(problemsOf(result)).hasSize(2);
            assertThat(problemsOf(result).get(0).problemId()).isEqualTo(latest.getId());
        }

        @Test
        @DisplayName("작성자 이름과 폴더, 복습 횟수를 한 행에 붙여 보여준다")
        void showsOwnerFolderAndSolveCount() throws Exception {
            Problem problem = saveProblem(owner.getId(), folder, "메모");
            saveSolve(problem, owner.getId(), AnswerStatus.CORRECT, LocalDateTime.now());
            saveSolve(problem, owner.getId(), AnswerStatus.WRONG, LocalDateTime.now());

            MvcResult result = mockMvc.perform(get("/admin/problems")).andExpect(status().isOk()).andReturn();

            assertThat(problemsOf(result)).singleElement().satisfies(row -> {
                assertThat(row.userId()).isEqualTo(owner.getId());
                assertThat(row.userName()).isEqualTo(owner.getName());
                assertThat(row.folderId()).isEqualTo(folder.getId());
                assertThat(row.solveCount())
                        .as("복습 횟수는 problem_solve 행 수다")
                        .isEqualTo(2L);
                assertThat(row.tagCount()).isZero();
                assertThat(row.analysisStatus())
                        .as("분석 행이 없으면 상태는 비어 있어야 한다")
                        .isNull();
            });
        }

        @Test
        @DisplayName("다른 사용자의 문제도 함께 보인다 - 관리자 화면은 전체 조회다")
        void showsProblemsOfEveryUser() throws Exception {
            User other = fixtures.createOtherUser();
            Folder otherFolder = fixtures.createRootFolder(other.getId());
            saveProblem(owner.getId(), folder, "내 문제");
            saveProblem(other.getId(), otherFolder, "남의 문제");

            MvcResult result = mockMvc.perform(get("/admin/problems").param("size", "50"))
                    .andExpect(status().isOk())
                    .andReturn();

            assertThat(problemsOf(result)).hasSize(2);
        }

        @Test
        @DisplayName("userId 로 한 사람의 문제만 거른다")
        void filtersByUser() throws Exception {
            User other = fixtures.createOtherUser();
            saveProblem(owner.getId(), folder, "내 문제");
            saveProblem(other.getId(), fixtures.createRootFolder(other.getId()), "남의 문제");

            MvcResult result = mockMvc.perform(get("/admin/problems").param("userId", String.valueOf(owner.getId())))
                    .andExpect(status().isOk())
                    .andExpect(model().attribute("totalProblems", 1L))
                    .andExpect(model().attribute("filtered", true))
                    .andExpect(model().attribute("filterUserName", owner.getName()))
                    .andReturn();

            assertThat(problemsOf(result)).extracting(ProblemRow::userId).containsOnly(owner.getId());
        }

        @Test
        @DisplayName("date 로 그날 등록한 문제만 거른다 - 통계 화면의 날짜별 링크가 쓰는 경로다")
        void filtersByCreatedDate() throws Exception {
            Problem old = saveProblem(owner.getId(), folder, "어제 문제");
            forceCreatedAt("problem", old.getId(), LocalDate.now().minusDays(1).atTime(23, 59));
            Problem today = saveProblem(owner.getId(), folder, "오늘 문제");

            MvcResult result = mockMvc.perform(get("/admin/problems").param("date", LocalDate.now().toString()))
                    .andExpect(status().isOk())
                    .andExpect(model().attribute("totalProblems", 1L))
                    .andReturn();

            assertThat(problemsOf(result)).extracting(ProblemRow::problemId).containsExactly(today.getId());
        }

        @Test
        @DisplayName("status 로 분석 상태를 거르고, 요약에는 실패 건수가 잡힌다")
        void filtersByAnalysisStatus() throws Exception {
            Problem failed = saveProblem(owner.getId(), folder, "실패");
            Problem completed = saveProblem(owner.getId(), folder, "완료");
            saveAnalysis(failed, "FAILED", null);
            saveAnalysis(completed, "COMPLETED", "수학");

            MvcResult result = mockMvc.perform(get("/admin/problems").param("status", "FAILED"))
                    .andExpect(status().isOk())
                    .andExpect(model().attribute("filterStatus", "FAILED"))
                    .andReturn();

            assertThat(problemsOf(result)).extracting(ProblemRow::problemId).containsExactly(failed.getId());
            ProblemSummary summary = (ProblemSummary) attribute(result, "summary");
            assertThat(summary.failed()).isEqualTo(1L);
            assertThat(summary.total())
                    .as("요약은 필터와 상관없이 전체 기준이다")
                    .isEqualTo(2L);
        }

        @Test
        @DisplayName("모르는 status 값은 500 대신 필터 없이 보여준다")
        void ignoresUnknownStatus() throws Exception {
            saveProblem(owner.getId(), folder, "문제");

            mockMvc.perform(get("/admin/problems").param("status", "NOPE"))
                    .andExpect(status().isOk())
                    .andExpect(model().attribute("filterStatus", (Object) null))
                    .andExpect(model().attribute("totalProblems", 1L));
        }

        @Test
        @DisplayName("다음 페이지 링크에 걸어 둔 필터가 그대로 남는다")
        void keepsFilterInPagerLinks() throws Exception {
            saveProblem(owner.getId(), folder, "문제1");
            saveProblem(owner.getId(), folder, "문제2");

            // 페이지 링크는 실제 요청의 쿼리스트링으로 만들기 때문에 param() 이 아니라 주소에 직접 넣는다.
            MvcResult result = mockMvc.perform(get("/admin/problems?userId=" + owner.getId() + "&size=1"))
                    .andExpect(status().isOk())
                    .andReturn();

            AdminPager pager = (AdminPager) attribute(result, "pager");
            assertThat(pager.nextUrl())
                    .contains("userId=" + owner.getId())
                    .contains("page=1");
        }

        @Test
        @DisplayName("날짜 형식이 틀리면 400으로 거절한다")
        void rejectsMalformedDate() throws Exception {
            mockMvc.perform(get("/admin/problems").param("date", "2026-13-40"))
                    .andExpect(status().isBadRequest());
        }

        @ParameterizedTest(name = "page={0}")
        @ValueSource(ints = {-1, -50})
        @DisplayName("음수 page 는 0페이지로 보정한다")
        void clampsNegativePage(int page) throws Exception {
            saveProblem(owner.getId(), folder, "문제");

            mockMvc.perform(get("/admin/problems").param("page", String.valueOf(page)))
                    .andExpect(status().isOk())
                    .andExpect(model().attribute("currentPage", 0));
        }

        @ParameterizedTest(name = "size={0}")
        @ValueSource(ints = {0, -1})
        @DisplayName("0 이하 size 는 1로 보정한다")
        void clampsNonPositiveSize(int size) throws Exception {
            saveProblem(owner.getId(), folder, "문제");

            mockMvc.perform(get("/admin/problems").param("size", String.valueOf(size)))
                    .andExpect(status().isOk())
                    .andExpect(model().attribute("size", 1));
        }

        @Test
        @DisplayName("마지막 페이지를 넘는 page 는 마지막 페이지로 되돌려 빈 화면을 보여주지 않는다")
        void clampsPageBeyondLastPage() throws Exception {
            saveProblem(owner.getId(), folder, "문제1");
            saveProblem(owner.getId(), folder, "문제2");

            MvcResult result = mockMvc.perform(get("/admin/problems").param("page", "99").param("size", "1"))
                    .andExpect(status().isOk())
                    .andExpect(model().attribute("currentPage", 1))
                    .andReturn();

            assertThat(problemsOf(result)).hasSize(1);
        }

        @Test
        @DisplayName("size 가 전체 건수보다 커도 한 페이지로 묶어 준다")
        void allowsOversizedPageSize() throws Exception {
            saveProblem(owner.getId(), folder, "문제");

            mockMvc.perform(get("/admin/problems").param("size", "10000"))
                    .andExpect(status().isOk())
                    .andExpect(model().attribute("totalPages", 1));
        }
    }

    @Nested
    @DisplayName("문제 상세")
    class ProblemDetailPage {

        @Test
        @DisplayName("문제와 함께 작성자, 폴더, 복습 기록, 들어 있는 복습노트를 모아 보여준다")
        void showsProblemWithRelatedData() throws Exception {
            Problem problem = saveProblem(owner.getId(), folder, "삼각함수 실수");
            saveSolve(problem, owner.getId(), AnswerStatus.WRONG, LocalDateTime.now().minusDays(1));
            ProblemSolve latest = saveSolve(problem, owner.getId(), AnswerStatus.CORRECT, LocalDateTime.now());
            PracticeNote note = savePracticeNote(owner.getId(), "삼각함수 세트");
            jdbcTemplate.update("""
                    INSERT INTO problem_practice_note_mapping (practice_note_id, problem_id, created_at, updated_at)
                    VALUES (?, ?, NOW(), NOW())
                    """, note.getId(), problem.getId());

            MvcResult result = mockMvc.perform(get("/admin/problem/{problemId}", problem.getId()))
                    .andExpect(status().isOk())
                    .andExpect(view().name("problem"))
                    .andExpect(model().attribute("problemSolveCount", 2))
                    .andReturn();

            ProblemDetail detail = (ProblemDetail) attribute(result, "problem");
            assertThat(detail.problemId()).isEqualTo(problem.getId());
            assertThat(detail.folderId()).isEqualTo(folder.getId());
            assertThat(detail.userId())
                    .as("작성자를 잘못 짚으면 관리자가 엉뚱한 사용자를 보게 된다")
                    .isEqualTo(owner.getId());
            assertThat(detail.userDeleted()).isFalse();

            @SuppressWarnings("unchecked")
            List<SolveRow> solves = (List<SolveRow>) attribute(result, "problemSolves");
            assertThat(solves.get(0).solveId())
                    .as("복습 기록은 최근 것부터 보여야 한다")
                    .isEqualTo(latest.getId());
            assertThat(solves.get(0).improvementTexts()).containsExactly("풀이 시간이 단축됐어요");
            assertThat(solves.get(0).timeSpentText()).isEqualTo("1분 35초");

            @SuppressWarnings("unchecked")
            List<NoteRef> notes = (List<NoteRef>) attribute(result, "practiceNotes");
            assertThat(notes).extracting(NoteRef::noteId).containsExactly(note.getId());
        }

        @Test
        @DisplayName("다른 문제에 남긴 복습 기록은 섞이지 않는다")
        void showsOnlySolvesOfThatProblem() throws Exception {
            Problem problem = saveProblem(owner.getId(), folder, "내 문제");
            Problem otherProblem = saveProblem(owner.getId(), folder, "다른 문제");
            saveSolve(otherProblem, owner.getId(), AnswerStatus.CORRECT, LocalDateTime.now());

            mockMvc.perform(get("/admin/problem/{problemId}", problem.getId()))
                    .andExpect(status().isOk())
                    .andExpect(model().attribute("problemSolveCount", 0));
        }

        @Test
        @DisplayName("핵심 포인트가 줄바꿈 평문이면 줄마다 나눠 보여준다")
        void splitsPlainKeyPoints() throws Exception {
            Problem problem = saveProblem(owner.getId(), folder, "메모");
            jdbcTemplate.update("""
                    INSERT INTO problem_analysis (problem_id, status, key_points, created_at, updated_at)
                    VALUES (?, 'COMPLETED', ?, NOW(), NOW())
                    """, problem.getId(), "• 판별식\n• 실근 조건");

            MvcResult result = mockMvc.perform(get("/admin/problem/{problemId}", problem.getId()))
                    .andExpect(status().isOk())
                    .andReturn();

            assertThat(attribute(result, "keyPoints")).isEqualTo(List.of("판별식", "실근 조건"));
        }

        @Test
        @DisplayName("없는 문제를 조회하면 500이 아니라 404로 응답한다")
        void returnsNotFoundForUnknownProblem() throws Exception {
            mockMvc.perform(get("/admin/problem/{problemId}", 999_999L))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("problemId 가 숫자가 아니면 400으로 거절한다")
        void rejectsNonNumericProblemId() throws Exception {
            mockMvc.perform(get("/admin/problem/{problemId}", "abc"))
                    .andExpect(status().isBadRequest());
        }
    }
}
