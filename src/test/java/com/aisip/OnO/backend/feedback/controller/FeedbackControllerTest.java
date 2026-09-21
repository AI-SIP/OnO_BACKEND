package com.aisip.OnO.backend.feedback.controller;

import com.aisip.OnO.backend.feedback.entity.UserFeedback;
import com.aisip.OnO.backend.feedback.repository.UserFeedbackRepository;
import com.aisip.OnO.backend.support.IntegrationTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@DisplayName("FeedbackController")
class FeedbackControllerTest extends IntegrationTestSupport {

    @Autowired
    private UserFeedbackRepository feedbackRepository;

    @BeforeEach
    void submitAnonymously() {
        // 설문은 로그인하지 않은 사용자도 응답할 수 있어야 한다.
        clearAuthentication();
    }

    private MockHttpServletRequestBuilder submit() {
        return post("/feedback").param("npsScore", "8");
    }

    @Nested
    @DisplayName("설문 화면")
    class Pages {

        @Test
        @DisplayName("설문 폼은 로그인 없이 열린다")
        void formIsPublic() throws Exception {
            mockMvc.perform(get("/feedback"))
                    .andExpect(status().isOk())
                    .andExpect(view().name("feedback"));
        }

        @Test
        @DisplayName("제출 완료 화면도 로그인 없이 열린다")
        void completePageIsPublic() throws Exception {
            mockMvc.perform(get("/feedback/complete"))
                    .andExpect(status().isOk())
                    .andExpect(view().name("feedback-complete"));
        }
    }

    @Nested
    @DisplayName("설문 제출")
    class Submit {

        @Test
        @DisplayName("제출하면 저장하고 완료 화면으로 리다이렉트한다")
        void savesAndRedirects() throws Exception {
            mockMvc.perform(submit()
                            .param("usagePurpose", "시험 대비")
                            .param("usagePurpose", "숙제")
                            .param("usageFrequency", "매일")
                            .param("painPoints", "검색이 느려요"))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/feedback/complete"));

            assertThat(feedbackRepository.findAll()).singleElement().satisfies(saved -> {
                assertThat(saved.getNpsScore()).isEqualTo(8);
                assertThat(saved.getUsagePurpose()).isEqualTo("시험 대비,숙제");
                assertThat(saved.getPainPoints()).isEqualTo("검색이 느려요");
            });
        }

        @Test
        @DisplayName("아무 항목도 채우지 않아도 제출된다")
        void acceptsEmptySubmission() throws Exception {
            mockMvc.perform(post("/feedback"))
                    .andExpect(status().is3xxRedirection());

            assertThat(feedbackRepository.count()).isEqualTo(1);
        }

        @Test
        @DisplayName("같은 사용자가 연달아 제출해도 두 건 모두 저장된다")
        void allowsRepeatedSubmission() throws Exception {
            mockMvc.perform(submit()).andExpect(status().is3xxRedirection());
            mockMvc.perform(submit()).andExpect(status().is3xxRedirection());

            assertThat(feedbackRepository.count()).isEqualTo(2);
        }

        @Test
        @DisplayName("X-Forwarded-For 의 첫 번째 주소를 접속 IP 로 남긴다")
        void usesFirstAddressOfForwardedForHeader() throws Exception {
            mockMvc.perform(submit().header("X-Forwarded-For", " 203.0.113.7 , 10.0.0.1 , 10.0.0.2 "))
                    .andExpect(status().is3xxRedirection());

            assertThat(feedbackRepository.findAll()).singleElement()
                    .satisfies(saved -> assertThat(saved.getIpAddress()).isEqualTo("203.0.113.7"));
        }

        @Test
        @DisplayName("X-Forwarded-For 가 없으면 요청의 원격 주소를 남긴다")
        void fallsBackToRemoteAddress() throws Exception {
            mockMvc.perform(submit()).andExpect(status().is3xxRedirection());

            assertThat(feedbackRepository.findAll()).singleElement()
                    .satisfies(saved -> assertThat(saved.getIpAddress()).isNotBlank());
        }

        @Test
        @DisplayName("조작된 긴 X-Forwarded-For 때문에 응답이 버려지지 않는다")
        void survivesForgedForwardedForHeader() throws Exception {
            mockMvc.perform(submit().header("X-Forwarded-For", "9".repeat(300)))
                    .andExpect(status().is3xxRedirection());

            assertThat(feedbackRepository.count())
                    .as("헤더 하나로 설문 제출을 막을 수 있으면 안 된다")
                    .isEqualTo(1);
        }

        @Test
        @DisplayName("npsScore 가 숫자가 아니면 400으로 거절한다")
        void rejectsNonNumericNpsScore() throws Exception {
            mockMvc.perform(post("/feedback").param("npsScore", "열점"))
                    .andExpect(status().isBadRequest());

            assertThat(feedbackRepository.count()).isZero();
        }
    }

    @Nested
    @DisplayName("컬럼 길이 초과 입력")
    class OverlongInput {

        /**
         * 프로덕션에서 컬럼 길이 초과가 500으로 나가면서 에러 알림까지 발송된 이력이 있다.
         * 길이 초과는 클라이언트 입력 문제이므로 반드시 4xx 여야 한다.
         */
        @ParameterizedTest(name = "{0} 은 {1}자까지 허용하고 그 이상은 400")
        @CsvSource({
                "usagePurpose,               500",
                "registrationPainPoints,     500",
                "mostUsedFeature,            100",
                "usageFrequency,              50",
                "classificationMethod,        50"
        })
        @DisplayName("한도를 넘는 입력은 500이 아니라 400으로 거절한다")
        void rejectsOverlongInputWithBadRequest(String field, int maxLength) throws Exception {
            mockMvc.perform(post("/feedback").param(field, "가".repeat(maxLength)))
                    .andExpect(status().is3xxRedirection());
            assertThat(feedbackRepository.count())
                    .as("%s 는 %d자까지는 반드시 저장돼야 한다", field, maxLength)
                    .isEqualTo(1);

            MvcResult result = mockMvc.perform(post("/feedback").param(field, "가".repeat(maxLength + 1)))
                    .andReturn();

            assertThat(result.getResponse().getStatus())
                    .as("%s 가 %d자를 넘으면 500이 아니라 400이어야 한다", field, maxLength + 1)
                    .isEqualTo(400);
            assertThat(feedbackRepository.count())
                    .as("거절했으면 잘린 값이 남아서도 안 된다")
                    .isEqualTo(1);
        }

        @Test
        @DisplayName("복습 세트 미사용 이유가 300자를 넘으면 400으로 거절한다")
        void rejectsOverlongReviewSetNonUsageReason() throws Exception {
            mockMvc.perform(post("/feedback")
                            .param("practiceNoteUsed", "false")
                            .param("reviewSetNonUsageReason", "가".repeat(300)))
                    .andExpect(status().is3xxRedirection());

            mockMvc.perform(post("/feedback")
                            .param("practiceNoteUsed", "false")
                            .param("reviewSetNonUsageReason", "가".repeat(301)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("스터디룸 미사용 이유가 300자를 넘으면 400으로 거절한다")
        void rejectsOverlongStudyRoomNonUsageReason() throws Exception {
            mockMvc.perform(post("/feedback")
                            .param("studyRoomUsage", "사용하지 않음")
                            .param("studyRoomNonUsageReason", "가".repeat(300)))
                    .andExpect(status().is3xxRedirection());

            mockMvc.perform(post("/feedback")
                            .param("studyRoomUsage", "사용하지 않음")
                            .param("studyRoomNonUsageReason", "가".repeat(301)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("'기타' 텍스트까지 합쳐 한도를 넘어도 400으로 거절한다")
        void rejectsWhenOtherTextPushesValueOverLimit() throws Exception {
            mockMvc.perform(post("/feedback")
                            .param("usagePurpose", "가".repeat(480))
                            .param("usagePurpose", "기타")
                            .param("usagePurposeOther", "나".repeat(30)))
                    .andExpect(status().isBadRequest());

            assertThat(feedbackRepository.count()).isZero();
        }

        @Test
        @DisplayName("자유 입력은 TEXT 컬럼이라 아주 길어도 저장된다")
        void acceptsVeryLongFreeText() throws Exception {
            String longText = "다".repeat(5000);

            mockMvc.perform(post("/feedback")
                            .param("painPoints", longText)
                            .param("desiredFeatures", longText))
                    .andExpect(status().is3xxRedirection());

            List<UserFeedback> saved = feedbackRepository.findAll();
            assertThat(saved).singleElement()
                    .satisfies(f -> assertThat(f.getPainPoints()).hasSize(5000));
        }
    }
}
