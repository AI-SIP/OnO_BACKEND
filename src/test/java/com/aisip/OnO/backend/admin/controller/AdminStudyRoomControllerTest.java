package com.aisip.OnO.backend.admin.controller;

import com.aisip.OnO.backend.admin.dto.AdminStudyRoomDetailDto;
import com.aisip.OnO.backend.admin.dto.AdminStudyRoomSummaryDto;
import com.aisip.OnO.backend.admin.support.AdminTestSupport;
import com.aisip.OnO.backend.studyroom.entity.StudyRoom;
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

@DisplayName("AdminStudyRoomController")
class AdminStudyRoomControllerTest extends AdminTestSupport {

    private User host;

    @BeforeEach
    void setUp() {
        authenticateAs(createAdminUser().getId(), "ROLE_ADMIN");
        host = fixtures.createUser();
    }

    @SuppressWarnings("unchecked")
    private List<AdminStudyRoomSummaryDto> roomsOf(MvcResult result) {
        return (List<AdminStudyRoomSummaryDto>) result.getModelAndView().getModel().get("rooms");
    }

    @Nested
    @DisplayName("스터디룸 목록")
    class RoomList {

        @Test
        @DisplayName("스터디룸이 없어도 0 기반 집계를 돌려준다")
        void rendersZeroBasedResultWhenEmpty() throws Exception {
            MvcResult result = mockMvc.perform(get("/admin/study-rooms"))
                    .andExpect(status().isOk())
                    .andExpect(view().name("admin-study-rooms"))
                    .andExpect(model().attribute("totalCount", 0L))
                    .andExpect(model().attribute("totalPages", 0))
                    .andExpect(model().attribute("pageBlockStart", 0))
                    .andExpect(model().attribute("pageBlockEnd", 0))
                    .andExpect(model().attribute("hasPreviousBlock", false))
                    .andReturn();

            assertThat(roomsOf(result)).isEmpty();
        }

        @Test
        @DisplayName("방마다 멤버 수와 공유 문제 수를 집계해 보여준다")
        void aggregatesMemberAndSharedProblemCount() throws Exception {
            StudyRoom room = saveStudyRoom("고3 수학방", host);

            MvcResult result = mockMvc.perform(get("/admin/study-rooms"))
                    .andExpect(status().isOk())
                    .andExpect(model().attribute("totalCount", 1L))
                    .andReturn();

            assertThat(roomsOf(result)).singleElement().satisfies(dto -> {
                assertThat(dto.getId()).isEqualTo(room.getId());
                assertThat(dto.getName()).isEqualTo("고3 수학방");
                assertThat(dto.getMemberCount()).isEqualTo(1L);
                assertThat(dto.getSharedProblemCount())
                        .as("공유 문제가 없는 방은 null 이 아니라 0 이어야 한다")
                        .isEqualTo(0L);
            });
        }

        @Test
        @DisplayName("size 로 페이지를 끊는다")
        void paginatesRooms() throws Exception {
            saveStudyRoom("방1", host);
            saveStudyRoom("방2", host);
            saveStudyRoom("방3", host);

            MvcResult result = mockMvc.perform(get("/admin/study-rooms").param("size", "2"))
                    .andExpect(status().isOk())
                    .andExpect(model().attribute("totalPages", 2))
                    .andReturn();

            assertThat(roomsOf(result)).hasSize(2);
        }

        @ParameterizedTest(name = "page={0}")
        @ValueSource(ints = {-1, -20})
        @DisplayName("음수 page 는 500이 아니라 0페이지로 보정한다")
        void clampsNegativePage(int page) throws Exception {
            saveStudyRoom("방", host);

            mockMvc.perform(get("/admin/study-rooms").param("page", String.valueOf(page)))
                    .andExpect(status().isOk())
                    .andExpect(model().attribute("currentPage", 0));
        }

        @ParameterizedTest(name = "size={0}")
        @ValueSource(ints = {0, -1})
        @DisplayName("0 이하 size 는 500이 아니라 1로 보정한다")
        void clampsNonPositiveSize(int size) throws Exception {
            saveStudyRoom("방", host);

            mockMvc.perform(get("/admin/study-rooms").param("size", String.valueOf(size)))
                    .andExpect(status().isOk())
                    .andExpect(model().attribute("size", 1));
        }

        @Test
        @DisplayName("size 가 과도하게 커도 500을 내지 않는다")
        void allowsOversizedPageSize() throws Exception {
            saveStudyRoom("방", host);

            mockMvc.perform(get("/admin/study-rooms").param("size", "100000"))
                    .andExpect(status().isOk())
                    .andExpect(model().attribute("totalPages", 1));
        }
    }

    @Nested
    @DisplayName("스터디룸 상세")
    class RoomDetail {

        @Test
        @DisplayName("방장과 멤버 목록을 보여준다")
        void showsMembers() throws Exception {
            StudyRoom room = saveStudyRoom("고3 수학방", host);

            MvcResult result = mockMvc.perform(get("/admin/study-rooms/{id}", room.getId()))
                    .andExpect(status().isOk())
                    .andExpect(view().name("admin-study-room-detail"))
                    .andReturn();

            AdminStudyRoomDetailDto dto =
                    (AdminStudyRoomDetailDto) result.getModelAndView().getModel().get("room");
            assertThat(dto.getId()).isEqualTo(room.getId());
            assertThat(dto.getHostUserId()).isEqualTo(host.getId());
            assertThat(dto.getMembers())
                    .extracting(AdminStudyRoomDetailDto.MemberInfo::getUserId)
                    .containsExactly(host.getId());
            assertThat(dto.getChallenges()).isEmpty();
            assertThat(dto.getSharedProblemCount()).isZero();
        }

        @Test
        @DisplayName("없는 스터디룸을 조회하면 500이 아니라 404로 응답한다")
        void returnsNotFoundForUnknownRoom() throws Exception {
            mockMvc.perform(get("/admin/study-rooms/{id}", 999_999L))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("id 가 숫자가 아니면 400으로 거절한다")
        void rejectsNonNumericId() throws Exception {
            mockMvc.perform(get("/admin/study-rooms/{id}", "abc"))
                    .andExpect(status().isBadRequest());
        }
    }
}
