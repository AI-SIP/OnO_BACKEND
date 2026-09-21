package com.aisip.OnO.backend.admin.controller;

import com.aisip.OnO.backend.admin.dto.AdminLearningDto.NoteProblemRow;
import com.aisip.OnO.backend.admin.dto.AdminLearningDto.NoteRow;
import com.aisip.OnO.backend.admin.dto.AdminLearningDto.NoteSummary;
import com.aisip.OnO.backend.admin.dto.AdminLearningDto.SolveRow;
import com.aisip.OnO.backend.admin.dto.AdminLearningDto.SolveSummary;
import com.aisip.OnO.backend.admin.support.AdminTestSupport;
import com.aisip.OnO.backend.folder.entity.Folder;
import com.aisip.OnO.backend.mission.entity.MissionType;
import com.aisip.OnO.backend.practicenote.entity.PracticeNote;
import com.aisip.OnO.backend.practicenote.entity.PracticeNotification;
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

@DisplayName("AdminPracticeNoteController")
class AdminPracticeNoteControllerTest extends AdminTestSupport {

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
    private <T> T attribute(MvcResult result, String name) {
        return (T) result.getModelAndView().getModel().get(name);
    }

    private ProblemSolve saveSolve(Problem problem, Long userId, AnswerStatus answerStatus, LocalDateTime practicedAt, Integer seconds) {
        return problemSolveRepository.save(ProblemSolve.create(
                problem, userId, practicedAt, answerStatus, null, null, seconds, null));
    }

    private void mapProblem(PracticeNote note, Problem problem) {
        jdbcTemplate.update("""
                INSERT INTO problem_practice_note_mapping (practice_note_id, problem_id, created_at, updated_at)
                VALUES (?, ?, NOW(), NOW())
                """, note.getId(), problem.getId());
    }

    @Nested
    @DisplayName("복습노트 목록")
    class PracticeNoteList {

        @Test
        @DisplayName("복습노트가 없어도 0 기반 집계를 돌려준다")
        void rendersZeroBasedResultWhenEmpty() throws Exception {
            MvcResult result = mockMvc.perform(get("/admin/practice-notes"))
                    .andExpect(status().isOk())
                    .andExpect(view().name("practice-notes"))
                    .andExpect(model().attribute("totalPracticeNotes", 0L))
                    .andExpect(model().attribute("noteTotalPages", 0))
                    .andExpect(model().attribute("notePage", 0))
                    .andReturn();

            assertThat(AdminPracticeNoteControllerTest.this.<List<NoteRow>>attribute(result, "practiceNotes")).isEmpty();
        }

        @Test
        @DisplayName("복습노트에 작성자 정보와 문제 수, 알림 설정을 붙여 보여준다")
        void showsOwnerProblemCountAndNotification() throws Exception {
            PracticeNote note = savePracticeNote(owner.getId(), "미적분 복습");
            note.updateNotification(new PracticeNotification(1, 21, 0, "weekly", List.of(3, 1)));
            practiceNoteRepository.save(note);
            mapProblem(note, saveProblem(owner.getId(), folder, "문제"));

            MvcResult result = mockMvc.perform(get("/admin/practice-notes"))
                    .andExpect(status().isOk())
                    .andExpect(model().attribute("totalPracticeNotes", 1L))
                    .andReturn();

            List<NoteRow> notes = attribute(result, "practiceNotes");
            assertThat(notes).singleElement().satisfies(row -> {
                assertThat(row.noteId()).isEqualTo(note.getId());
                assertThat(row.userId()).isEqualTo(owner.getId());
                assertThat(row.userName()).isEqualTo(owner.getName());
                assertThat(row.title()).isEqualTo("미적분 복습");
                assertThat(row.problemCount()).isEqualTo(1L);
                assertThat(row.practiceCount()).isZero();
                assertThat(row.notificationText())
                        .as("요일은 JPA 로 따로 읽어 붙이므로 여기서 빠지면 요일 없는 알림으로 보인다")
                        .isEqualTo("매주 월, 수 21:00");
            });
            NoteSummary summary = attribute(result, "summary");
            assertThat(summary.withNotification()).isEqualTo(1L);
        }

        @Test
        @DisplayName("userId 와 date 로 거른다")
        void filtersByUserAndDate() throws Exception {
            User other = fixtures.createOtherUser();
            PracticeNote mine = savePracticeNote(owner.getId(), "오늘 내 노트");
            PracticeNote old = savePracticeNote(owner.getId(), "어제 내 노트");
            forceCreatedAt("practice_note", old.getId(), LocalDate.now().minusDays(1).atTime(12, 0));
            savePracticeNote(other.getId(), "남의 노트");

            MvcResult result = mockMvc.perform(get("/admin/practice-notes")
                            .param("userId", String.valueOf(owner.getId()))
                            .param("date", LocalDate.now().toString()))
                    .andExpect(status().isOk())
                    .andExpect(model().attribute("totalPracticeNotes", 1L))
                    .andExpect(model().attribute("filtered", true))
                    .andReturn();

            assertThat(AdminPracticeNoteControllerTest.this.<List<NoteRow>>attribute(result, "practiceNotes"))
                    .extracting(NoteRow::noteId)
                    .containsExactly(mine.getId());
        }

        @Test
        @DisplayName("notePage 로 페이지를 넘길 수 있다")
        void paginatesByNotePage() throws Exception {
            savePracticeNote(owner.getId(), "노트1");
            savePracticeNote(owner.getId(), "노트2");
            savePracticeNote(owner.getId(), "노트3");

            MvcResult result = mockMvc.perform(get("/admin/practice-notes")
                            .param("notePage", "1")
                            .param("size", "2"))
                    .andExpect(status().isOk())
                    .andExpect(model().attribute("notePage", 1))
                    .andExpect(model().attribute("noteTotalPages", 2))
                    .andReturn();

            assertThat(AdminPracticeNoteControllerTest.this.<List<NoteRow>>attribute(result, "practiceNotes")).hasSize(1);
        }

        @ParameterizedTest(name = "notePage={0}")
        @ValueSource(ints = {-1, -30})
        @DisplayName("음수 notePage 는 0페이지로 보정한다")
        void clampsNegativeNotePage(int notePage) throws Exception {
            mockMvc.perform(get("/admin/practice-notes").param("notePage", String.valueOf(notePage)))
                    .andExpect(status().isOk())
                    .andExpect(model().attribute("notePage", 0));
        }

        @ParameterizedTest(name = "size={0}")
        @ValueSource(ints = {0, -1})
        @DisplayName("0 이하 size 는 1로 보정한다")
        void clampsNonPositiveSize(int size) throws Exception {
            mockMvc.perform(get("/admin/practice-notes").param("size", String.valueOf(size)))
                    .andExpect(status().isOk())
                    .andExpect(model().attribute("size", 1));
        }

        @Test
        @DisplayName("size 가 과도하게 커도 500을 내지 않는다")
        void allowsOversizedPageSize() throws Exception {
            savePracticeNote(owner.getId(), "노트");

            mockMvc.perform(get("/admin/practice-notes").param("size", "100000"))
                    .andExpect(status().isOk())
                    .andExpect(model().attribute("noteTotalPages", 1));
        }
    }

    @Nested
    @DisplayName("복습노트 상세")
    class PracticeNoteDetail {

        @Test
        @DisplayName("노트에 든 문제와, 노트 주인이 그 문제들에 남긴 복습 기록을 보여준다")
        void showsProblemsAndRecentSolves() throws Exception {
            PracticeNote note = savePracticeNote(owner.getId(), "세트");
            Problem inNote = saveProblem(owner.getId(), folder, "노트 속 문제");
            Problem outside = saveProblem(owner.getId(), folder, "노트 밖 문제");
            mapProblem(note, inNote);
            saveSolve(inNote, owner.getId(), AnswerStatus.WRONG, LocalDateTime.now().minusHours(2), 30);
            ProblemSolve last = saveSolve(inNote, owner.getId(), AnswerStatus.CORRECT, LocalDateTime.now(), 40);
            saveSolve(outside, owner.getId(), AnswerStatus.CORRECT, LocalDateTime.now(), 10);

            MvcResult result = mockMvc.perform(get("/admin/practice-notes/{noteId}", note.getId()))
                    .andExpect(status().isOk())
                    .andExpect(view().name("admin-practice-note-detail"))
                    .andReturn();

            List<NoteProblemRow> problems = attribute(result, "problems");
            assertThat(problems).singleElement().satisfies(row -> {
                assertThat(row.problemId()).isEqualTo(inNote.getId());
                assertThat(row.solveCount()).isEqualTo(2L);
                assertThat(row.lastAnswerStatus())
                        .as("마지막 결과는 가장 최근 복습 기록의 결과다")
                        .isEqualTo("CORRECT");
            });
            List<SolveRow> solves = attribute(result, "recentSolves");
            assertThat(solves)
                    .as("노트에 없는 문제의 복습 기록은 섞이면 안 된다")
                    .extracting(SolveRow::problemId)
                    .containsOnly(inNote.getId());
            assertThat(solves.get(0).solveId()).isEqualTo(last.getId());
        }

        @Test
        @DisplayName("문제가 없는 노트도 빈 목록으로 보여준다")
        void rendersEmptyNote() throws Exception {
            PracticeNote note = savePracticeNote(owner.getId(), "빈 세트");

            MvcResult result = mockMvc.perform(get("/admin/practice-notes/{noteId}", note.getId()))
                    .andExpect(status().isOk())
                    .andReturn();

            assertThat(AdminPracticeNoteControllerTest.this.<List<SolveRow>>attribute(result, "recentSolves")).isEmpty();
        }

        @Test
        @DisplayName("없는 복습노트는 404 로 응답한다")
        void returnsNotFoundForUnknownNote() throws Exception {
            mockMvc.perform(get("/admin/practice-notes/{noteId}", 999_999L))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("noteId 가 숫자가 아니면 400으로 거절한다")
        void rejectsNonNumericNoteId() throws Exception {
            mockMvc.perform(get("/admin/practice-notes/{noteId}", "abc"))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("복습 기록 목록")
    class PracticeLogList {

        @Test
        @DisplayName("복습 기록이 없어도 0 기반 집계를 돌려준다")
        void rendersZeroBasedResultWhenEmpty() throws Exception {
            MvcResult result = mockMvc.perform(get("/admin/practice-logs"))
                    .andExpect(status().isOk())
                    .andExpect(view().name("practice-logs"))
                    .andExpect(model().attribute("totalSolves", 0L))
                    .andExpect(model().attribute("totalPages", 0))
                    .andExpect(model().attribute("todaySolveCount", 0L))
                    .andReturn();

            SolveSummary summary = attribute(result, "summary");
            assertThat(summary.correctRate()).isZero();
            assertThat(summary.averageTimeSeconds()).isNull();
        }

        @Test
        @DisplayName("미션 기록이 아니라 problem_solve 를 복습 기록으로 센다")
        void countsProblemSolvesNotMissionLogs() throws Exception {
            Problem problem = saveProblem(owner.getId(), folder, "문제");
            PracticeNote note = savePracticeNote(owner.getId(), "노트");
            // 복습노트 첫 완료 때 한 번만 쌓이는 미션 기록. 이것만 세던 예전 화면이 실제보다 훨씬 적게 보였다.
            saveMissionLog(owner, MissionType.NOTE_PRACTICE, note.getId());
            saveSolve(problem, owner.getId(), AnswerStatus.CORRECT, LocalDateTime.now(), 60);
            saveSolve(problem, owner.getId(), AnswerStatus.WRONG, LocalDateTime.now(), 120);
            saveSolve(problem, owner.getId(), AnswerStatus.PARTIAL, LocalDateTime.now(), null);

            MvcResult result = mockMvc.perform(get("/admin/practice-logs"))
                    .andExpect(status().isOk())
                    .andExpect(model().attribute("totalSolves", 3L))
                    .andExpect(model().attribute("todaySolveCount", 3L))
                    .andReturn();

            List<SolveRow> solves = attribute(result, "solves");
            assertThat(solves).hasSize(3).allSatisfy(row -> {
                assertThat(row.userId()).isEqualTo(owner.getId());
                assertThat(row.problemId()).isEqualTo(problem.getId());
            });
            SolveSummary summary = attribute(result, "summary");
            assertThat(summary.correct()).isEqualTo(1L);
            assertThat(summary.correctRate()).isCloseTo(33.3, org.assertj.core.data.Offset.offset(0.1));
            assertThat(summary.averageTimeSeconds())
                    .as("소요 시간을 남기지 않은 기록은 평균에서 빠진다")
                    .isEqualTo(90.0);
        }

        @Test
        @DisplayName("결과를 모르는 이관 기록은 정답률 분모에서 뺀다")
        void excludesUnknownFromCorrectRate() throws Exception {
            Problem problem = saveProblem(owner.getId(), folder, "문제");
            problemSolveRepository.save(ProblemSolve.createFromLegacy(problem, owner.getId(), LocalDateTime.now()));
            saveSolve(problem, owner.getId(), AnswerStatus.CORRECT, LocalDateTime.now(), null);

            MvcResult result = mockMvc.perform(get("/admin/practice-logs")).andExpect(status().isOk()).andReturn();

            SolveSummary summary = attribute(result, "summary");
            assertThat(summary.unknown()).isEqualTo(1L);
            assertThat(summary.correctRate()).isEqualTo(100.0);
        }

        @Test
        @DisplayName("date, userId, answerStatus 로 거른다")
        void filtersByDateUserAndStatus() throws Exception {
            User other = fixtures.createOtherUser();
            Problem problem = saveProblem(owner.getId(), folder, "문제");
            ProblemSolve target = saveSolve(problem, owner.getId(), AnswerStatus.WRONG, LocalDateTime.now(), null);
            saveSolve(problem, owner.getId(), AnswerStatus.CORRECT, LocalDateTime.now(), null);
            saveSolve(problem, owner.getId(), AnswerStatus.WRONG, LocalDateTime.now().minusDays(2), null);
            saveSolve(problem, other.getId(), AnswerStatus.WRONG, LocalDateTime.now(), null);

            MvcResult result = mockMvc.perform(get("/admin/practice-logs")
                            .param("date", LocalDate.now().toString())
                            .param("userId", String.valueOf(owner.getId()))
                            .param("answerStatus", "wrong"))
                    .andExpect(status().isOk())
                    .andExpect(model().attribute("totalSolves", 1L))
                    .andExpect(model().attribute("filterStatus", "WRONG"))
                    .andReturn();

            assertThat(AdminPracticeNoteControllerTest.this.<List<SolveRow>>attribute(result, "solves"))
                    .extracting(SolveRow::solveId)
                    .containsExactly(target.getId());
        }

        @Test
        @DisplayName("지운 복습 기록은 세지 않는다")
        void excludesSoftDeletedSolves() throws Exception {
            Problem problem = saveProblem(owner.getId(), folder, "문제");
            ProblemSolve deleted = saveSolve(problem, owner.getId(), AnswerStatus.CORRECT, LocalDateTime.now(), null);
            problemSolveRepository.delete(deleted);

            mockMvc.perform(get("/admin/practice-logs"))
                    .andExpect(status().isOk())
                    .andExpect(model().attribute("totalSolves", 0L));
        }

        @ParameterizedTest(name = "page={0}")
        @ValueSource(ints = {-1, -7})
        @DisplayName("음수 page 는 0페이지로 보정한다")
        void clampsNegativePage(int page) throws Exception {
            mockMvc.perform(get("/admin/practice-logs").param("page", String.valueOf(page)))
                    .andExpect(status().isOk())
                    .andExpect(model().attribute("currentPage", 0));
        }

        @ParameterizedTest(name = "size={0}")
        @ValueSource(ints = {0, -1})
        @DisplayName("0 이하 size 는 1로 보정한다")
        void clampsNonPositiveSize(int size) throws Exception {
            mockMvc.perform(get("/admin/practice-logs").param("size", String.valueOf(size)))
                    .andExpect(status().isOk())
                    .andExpect(model().attribute("size", 1));
        }
    }
}
