package com.aisip.OnO.backend.admin.controller;

import com.aisip.OnO.backend.admin.dto.AdminPracticeLogResponseDto;
import com.aisip.OnO.backend.admin.dto.AdminPracticeNoteResponseDto;
import com.aisip.OnO.backend.admin.support.AdminTestSupport;
import com.aisip.OnO.backend.mission.entity.MissionType;
import com.aisip.OnO.backend.practicenote.entity.PracticeNote;
import com.aisip.OnO.backend.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@DisplayName("AdminPracticeNoteController")
class AdminPracticeNoteControllerTest extends AdminTestSupport {

    private User owner;

    @BeforeEach
    void setUp() {
        authenticateAs(createAdminUser().getId(), "ROLE_ADMIN");
        owner = fixtures.createUser();
    }

    @SuppressWarnings("unchecked")
    private <T> List<T> attribute(MvcResult result, String name) {
        return (List<T>) result.getModelAndView().getModel().get(name);
    }

    @Nested
    @DisplayName("복습노트 목록")
    class PracticeNoteList {

        @Test
        @DisplayName("복습노트가 없어도 0 기반 집계를 돌려준다")
        void rendersZeroBasedResultWhenEmpty() throws Exception {
            mockMvc.perform(get("/admin/practice-notes"))
                    .andExpect(status().isOk())
                    .andExpect(view().name("practice-notes"))
                    .andExpect(model().attribute("totalPracticeNotes", 0L))
                    .andExpect(model().attribute("noteTotalPages", 0))
                    .andExpect(model().attribute("notePageBlockStart", 0))
                    .andExpect(model().attribute("notePageBlockEnd", 0))
                    .andExpect(model().attribute("hasPreviousNoteBlock", false))
                    .andExpect(model().attribute("hasNextNoteBlock", false));
        }

        @Test
        @DisplayName("복습노트에 작성자 정보와 문제 수를 붙여 보여준다")
        void showsOwnerAndProblemCount() throws Exception {
            PracticeNote note = savePracticeNote(owner.getId(), "미적분 복습");

            MvcResult result = mockMvc.perform(get("/admin/practice-notes"))
                    .andExpect(status().isOk())
                    .andExpect(model().attribute("totalPracticeNotes", 1L))
                    .andReturn();

            List<AdminPracticeNoteResponseDto> notes = attribute(result, "practiceNotes");
            assertThat(notes).singleElement().satisfies(dto -> {
                assertThat(dto.practiceNoteId()).isEqualTo(note.getId());
                assertThat(dto.userId()).isEqualTo(owner.getId());
                assertThat(dto.userName()).isEqualTo(owner.getName());
                assertThat(dto.practiceTitle()).isEqualTo("미적분 복습");
                assertThat(dto.problemCount())
                        .as("문제가 없는 복습노트는 null 이 아니라 0 이어야 한다")
                        .isEqualTo(0L);
                assertThat(dto.practiceCount()).isEqualTo(0L);
            });
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

            assertThat(attribute(result, "practiceNotes")).hasSize(1);
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
    @DisplayName("복습 기록 목록")
    class PracticeLogList {

        @Test
        @DisplayName("복습 기록이 없어도 0 기반 집계를 돌려준다")
        void rendersZeroBasedResultWhenEmpty() throws Exception {
            mockMvc.perform(get("/admin/practice-logs"))
                    .andExpect(status().isOk())
                    .andExpect(view().name("practice-logs"))
                    .andExpect(model().attribute("totalPracticeLogs", 0L))
                    .andExpect(model().attribute("logTotalPages", 0))
                    .andExpect(model().attribute("hasPreviousLogBlock", false))
                    .andExpect(model().attribute("hasNextLogBlock", false));
        }

        @Test
        @DisplayName("NOTE_PRACTICE 미션 기록만 복습 기록으로 집계한다")
        void countsOnlyNotePracticeMissions() throws Exception {
            PracticeNote note = savePracticeNote(owner.getId(), "복습노트");
            saveMissionLog(owner, MissionType.NOTE_PRACTICE, note.getId());
            saveMissionLog(owner, MissionType.USER_LOGIN, null);
            saveMissionLog(owner, MissionType.PROBLEM_WRITE, null);

            MvcResult result = mockMvc.perform(get("/admin/practice-logs"))
                    .andExpect(status().isOk())
                    .andExpect(model().attribute("totalPracticeLogs", 1L))
                    .andReturn();

            List<AdminPracticeLogResponseDto> logs = attribute(result, "practiceLogs");
            assertThat(logs).singleElement().satisfies(dto -> {
                assertThat(dto.userId()).isEqualTo(owner.getId());
                assertThat(dto.practiceNoteId()).isEqualTo(note.getId());
                assertThat(dto.practiceTitle()).isEqualTo("복습노트");
                assertThat(dto.point()).isEqualTo(MissionType.NOTE_PRACTICE.getPoint());
            });
        }

        @Test
        @DisplayName("참조된 복습노트가 사라졌어도 500 없이 '-' 로 표시한다")
        void showsDashWhenReferencedNoteIsGone() throws Exception {
            saveMissionLog(owner, MissionType.NOTE_PRACTICE, 999_999L);

            MvcResult result = mockMvc.perform(get("/admin/practice-logs"))
                    .andExpect(status().isOk())
                    .andReturn();

            List<AdminPracticeLogResponseDto> logs = attribute(result, "practiceLogs");
            assertThat(logs).singleElement()
                    .satisfies(dto -> assertThat(dto.practiceTitle()).isEqualTo("-"));
        }

        @ParameterizedTest(name = "logPage={0}")
        @ValueSource(ints = {-1, -7})
        @DisplayName("음수 logPage 는 0페이지로 보정한다")
        void clampsNegativeLogPage(int logPage) throws Exception {
            mockMvc.perform(get("/admin/practice-logs").param("logPage", String.valueOf(logPage)))
                    .andExpect(status().isOk())
                    .andExpect(model().attribute("logPage", 0));
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
