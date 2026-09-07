package com.aisip.OnO.backend.admin.controller;

import com.aisip.OnO.backend.admin.support.AdminTestSupport;
import com.aisip.OnO.backend.feedback.dto.FeedbackResponseDto;
import com.aisip.OnO.backend.feedback.entity.UserFeedback;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@DisplayName("AdminFeedbackController")
class AdminFeedbackControllerTest extends AdminTestSupport {

    @BeforeEach
    void loginAsAdmin() {
        authenticateAs(createAdminUser().getId(), "ROLE_ADMIN");
    }

    @SuppressWarnings("unchecked")
    private List<FeedbackResponseDto> feedbacksOf(MvcResult result) {
        return (List<FeedbackResponseDto>) result.getModelAndView().getModel().get("feedbacks");
    }

    @Nested
    @DisplayName("피드백 목록")
    class FeedbackList {

        @Test
        @DisplayName("피드백이 없어도 0 기반 집계를 돌려주고 평균 NPS 는 null 이다")
        void rendersZeroBasedResultWhenEmpty() throws Exception {
            MvcResult result = mockMvc.perform(get("/admin/feedbacks"))
                    .andExpect(status().isOk())
                    .andExpect(view().name("admin-feedback"))
                    .andExpect(model().attribute("totalCount", 0L))
                    .andExpect(model().attribute("totalPages", 0))
                    .andExpect(model().attribute("hasPreviousBlock", false))
                    .andExpect(model().attribute("hasNextBlock", false))
                    .andReturn();

            assertThat(feedbacksOf(result)).isEmpty();
            assertThat(result.getModelAndView().getModel().get("averageNps"))
                    .as("표본이 없을 때 평균을 0으로 꾸며내면 실제 NPS 0점과 구분되지 않는다")
                    .isNull();
        }

        @Test
        @DisplayName("제출 시각 내림차순으로 보여준다")
        void ordersBySubmittedAtDesc() throws Exception {
            UserFeedback older = userFeedbackRepository.save(UserFeedback.builder()
                    .npsScore(3)
                    .submittedAt(LocalDateTime.now().minusDays(2))
                    .build());
            UserFeedback newer = userFeedbackRepository.save(UserFeedback.builder()
                    .npsScore(9)
                    .submittedAt(LocalDateTime.now())
                    .build());

            MvcResult result = mockMvc.perform(get("/admin/feedbacks"))
                    .andExpect(status().isOk())
                    .andExpect(model().attribute("totalCount", 2L))
                    .andReturn();

            assertThat(feedbacksOf(result))
                    .extracting(FeedbackResponseDto::getId)
                    .containsExactly(newer.getId(), older.getId());
        }

        @Test
        @DisplayName("NPS 평균은 점수가 있는 응답만으로 계산한다")
        void averagesOnlyScoredFeedback() throws Exception {
            saveFeedback(10, "시험 대비");
            saveFeedback(6, "숙제");
            userFeedbackRepository.save(UserFeedback.builder()
                    .submittedAt(LocalDateTime.now())
                    .build());

            MvcResult result = mockMvc.perform(get("/admin/feedbacks"))
                    .andExpect(status().isOk())
                    .andExpect(model().attribute("totalCount", 3L))
                    .andReturn();

            assertThat((Double) result.getModelAndView().getModel().get("averageNps"))
                    .as("NPS 를 남기지 않은 응답까지 분모에 넣으면 평균이 낮게 왜곡된다")
                    .isEqualTo(8.0);
        }

        @Test
        @DisplayName("size 로 페이지를 끊는다")
        void paginatesFeedback() throws Exception {
            saveFeedback(1, "a");
            saveFeedback(2, "b");
            saveFeedback(3, "c");

            MvcResult result = mockMvc.perform(get("/admin/feedbacks").param("size", "2"))
                    .andExpect(status().isOk())
                    .andExpect(model().attribute("totalPages", 2))
                    .andReturn();

            assertThat(feedbacksOf(result)).hasSize(2);
        }

        @ParameterizedTest(name = "page={0}")
        @ValueSource(ints = {-1, -20})
        @DisplayName("음수 page 는 500이 아니라 0페이지로 보정한다")
        void clampsNegativePage(int page) throws Exception {
            saveFeedback(5, "목적");

            mockMvc.perform(get("/admin/feedbacks").param("page", String.valueOf(page)))
                    .andExpect(status().isOk())
                    .andExpect(model().attribute("currentPage", 0));
        }

        @ParameterizedTest(name = "size={0}")
        @ValueSource(ints = {0, -1})
        @DisplayName("0 이하 size 는 500이 아니라 1로 보정한다")
        void clampsNonPositiveSize(int size) throws Exception {
            saveFeedback(5, "목적");

            mockMvc.perform(get("/admin/feedbacks").param("size", String.valueOf(size)))
                    .andExpect(status().isOk())
                    .andExpect(model().attribute("size", 1));
        }

        @Test
        @DisplayName("size 가 과도하게 커도 500을 내지 않는다")
        void allowsOversizedPageSize() throws Exception {
            saveFeedback(5, "목적");

            mockMvc.perform(get("/admin/feedbacks").param("size", "100000"))
                    .andExpect(status().isOk())
                    .andExpect(model().attribute("totalPages", 1));
        }
    }

    @Nested
    @DisplayName("피드백 상세")
    class FeedbackDetail {

        @Test
        @DisplayName("저장된 응답 내용을 그대로 보여준다")
        void showsStoredAnswers() throws Exception {
            UserFeedback feedback = saveFeedback(9, "시험 대비,숙제");

            MvcResult result = mockMvc.perform(get("/admin/feedbacks/{id}", feedback.getId()))
                    .andExpect(status().isOk())
                    .andExpect(view().name("admin-feedback-detail"))
                    .andReturn();

            FeedbackResponseDto dto = (FeedbackResponseDto) result.getModelAndView().getModel().get("feedback");
            assertThat(dto.getId()).isEqualTo(feedback.getId());
            assertThat(dto.getNpsScore()).isEqualTo(9);
            assertThat(dto.getUsagePurpose()).isEqualTo("시험 대비,숙제");
        }

        @Test
        @DisplayName("없는 피드백을 조회하면 500이 아니라 404로 응답한다")
        void returnsNotFoundForUnknownId() throws Exception {
            mockMvc.perform(get("/admin/feedbacks/{id}", 999_999L))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("id 가 숫자가 아니면 400으로 거절한다")
        void rejectsNonNumericId() throws Exception {
            mockMvc.perform(get("/admin/feedbacks/{id}", "abc"))
                    .andExpect(status().isBadRequest());
        }
    }
}
