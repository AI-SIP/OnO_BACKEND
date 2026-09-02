package com.aisip.OnO.backend.tag.controller;

import com.aisip.OnO.backend.tag.TagTestSupport;
import com.aisip.OnO.backend.tag.entity.Tag;
import com.aisip.OnO.backend.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("태그 API")
class TagControllerTest extends TagTestSupport {

    private User user;
    private User other;

    @BeforeEach
    void setUpUsers() {
        user = fixtures.createUser();
        other = fixtures.createOtherUser();
    }

    private String json(Object body) throws Exception {
        return objectMapper.writeValueAsString(body);
    }

    @Nested
    @DisplayName("POST /api/tags")
    class CreateTag {

        @Test
        @DisplayName("태그를 만들면 200과 함께 생성된 태그를 돌려준다")
        void createsTag() throws Exception {

            mockMvc.perform(post("/api/tags")
                            .contentType(APPLICATION_JSON)
                            .content(json(Map.of("name", "발상 부족")))
                            .with(asUser(user.getId())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.tagId").isNumber())
                    .andExpect(jsonPath("$.data.name").value("발상 부족"));

            assertThat(tagNames(user.getId())).containsExactly("발상 부족");
        }

        @Test
        @DisplayName("같은 이름을 두 번 보내면 같은 태그 id를 돌려주고 태그는 하나만 남는다")
        void isIdempotentForSameName() throws Exception {
            String body = json(Map.of("name", "발상 부족"));

            String first = mockMvc.perform(post("/api/tags").contentType(APPLICATION_JSON).content(body)
                            .with(asUser(user.getId())))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();
            String second = mockMvc.perform(post("/api/tags").contentType(APPLICATION_JSON).content(body)
                            .with(asUser(user.getId())))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();

            assertThat(second)
                    .as("중복 생성 요청은 500이 아니라 기존 태그로 수렴해야 한다")
                    .isEqualTo(first);
            assertThat(tagNames(user.getId())).hasSize(1);
        }

        @Test
        @DisplayName("빈 이름은 400과 에러코드 9001로 거절한다")
        void rejectsEmptyName() throws Exception {

            mockMvc.perform(post("/api/tags")
                            .contentType(APPLICATION_JSON)
                            .content(json(Map.of("name", "   ")))
                            .with(asUser(user.getId())))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value(9001));
        }

        @Test
        @DisplayName("31자 이름은 400과 에러코드 9002로 거절한다")
        void rejectsTooLongName() throws Exception {

            mockMvc.perform(post("/api/tags")
                            .contentType(APPLICATION_JSON)
                            .content(json(Map.of("name", "가".repeat(31))))
                            .with(asUser(user.getId())))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value(9002));
        }

        @Test
        @DisplayName("본문이 없으면 500이 아니라 400이다")
        void rejectsMissingBody() throws Exception {

            mockMvc.perform(post("/api/tags").contentType(APPLICATION_JSON)
                            .with(asUser(user.getId())))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("인증 없이 요청하면 401이다")
        void requiresAuthentication() throws Exception {
            mockMvc.perform(post("/api/tags")
                            .contentType(APPLICATION_JSON)
                            .content(json(Map.of("name", "발상 부족"))))
                    .andExpect(status().isUnauthorized());

            assertThat(tagNames(user.getId())).isEmpty();
        }
    }

    @Nested
    @DisplayName("GET /api/tags")
    class GetUserTags {

        @Test
        @DisplayName("자기 태그만 이름순으로 돌려준다")
        void returnsOwnTagsOnly() throws Exception {
            saveTag(user.getId(), "나중태그");
            saveTag(user.getId(), "가장먼저");
            saveTag(other.getId(), "남의태그");

            mockMvc.perform(get("/api/tags")
                            .with(asUser(user.getId())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.length()").value(2))
                    .andExpect(jsonPath("$.data[0].name").value("가장먼저"))
                    .andExpect(jsonPath("$.data[1].name").value("나중태그"));
        }

        @Test
        @DisplayName("태그가 없으면 빈 배열이다")
        void returnsEmptyArray() throws Exception {

            mockMvc.perform(get("/api/tags")
                            .with(asUser(user.getId())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.length()").value(0));
        }

        @Test
        @DisplayName("인증 없이 요청하면 401이다")
        void requiresAuthentication() throws Exception {
            mockMvc.perform(get("/api/tags"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.errorCode").value(1007));
        }
    }

    @Nested
    @DisplayName("DELETE /api/tags/{tagId}")
    class DeleteTag {

        @Test
        @DisplayName("자기 태그를 지우면 200이고 목록에서 사라진다")
        void deletesOwnTag() throws Exception {
            Tag tag = saveTag(user.getId(), "지울태그");

            mockMvc.perform(delete("/api/tags/" + tag.getId())
                            .with(asUser(user.getId())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data").value("태그가 삭제되었습니다."));

            assertThat(tagNames(user.getId())).isEmpty();
        }

        @Test
        @DisplayName("다른 사용자의 태그를 지우려 하면 403이고 태그는 그대로다")
        void rejectsOtherUsersTag() throws Exception {
            Tag othersTag = saveTag(other.getId(), "남의태그");

            mockMvc.perform(delete("/api/tags/" + othersTag.getId())
                            .with(asUser(user.getId())))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.errorCode").value(9004));

            assertThat(tagNames(other.getId())).containsExactly("남의태그");
        }

        @Test
        @DisplayName("없는 태그를 지우려 하면 404다")
        void rejectsUnknownTag() throws Exception {

            mockMvc.perform(delete("/api/tags/999999")
                            .with(asUser(user.getId())))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.errorCode").value(9003));
        }

        @Test
        @DisplayName("인증 없이 요청하면 401이고 태그는 남는다")
        void requiresAuthentication() throws Exception {
            Tag tag = saveTag(user.getId(), "지울태그");

            mockMvc.perform(delete("/api/tags/" + tag.getId()))
                    .andExpect(status().isUnauthorized());

            assertThat(tagNames(user.getId())).containsExactly("지울태그");
        }
    }

    @Nested
    @DisplayName("DELETE /api/tags")
    class DeleteTags {

        @Test
        @DisplayName("여러 태그를 한 번에 지운다")
        void deletesMultipleTags() throws Exception {
            Tag first = saveTag(user.getId(), "첫번째");
            Tag second = saveTag(user.getId(), "두번째");
            saveTag(user.getId(), "남을태그");

            mockMvc.perform(delete("/api/tags")
                            .contentType(APPLICATION_JSON)
                            .content(json(Map.of("deleteTagIdList", List.of(first.getId(), second.getId()))))
                            .with(asUser(user.getId())))
                    .andExpect(status().isOk());

            assertThat(tagNames(user.getId())).containsExactly("남을태그");
        }

        @Test
        @DisplayName("빈 목록이면 404다")
        void rejectsEmptyList() throws Exception {

            mockMvc.perform(delete("/api/tags")
                            .contentType(APPLICATION_JSON)
                            .content(json(Map.of("deleteTagIdList", List.of())))
                            .with(asUser(user.getId())))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.errorCode").value(9003));
        }

        @Test
        @DisplayName("남의 태그가 섞여 있으면 403이고 하나도 지워지지 않는다")
        void rejectsMixedOwnership() throws Exception {
            Tag mine = saveTag(user.getId(), "내태그");
            Tag theirs = saveTag(other.getId(), "남의태그");

            mockMvc.perform(delete("/api/tags")
                            .contentType(APPLICATION_JSON)
                            .content(json(Map.of("deleteTagIdList", List.of(mine.getId(), theirs.getId()))))
                            .with(asUser(user.getId())))
                    .andExpect(status().isForbidden());

            assertThat(tagNames(user.getId())).containsExactly("내태그");
            assertThat(tagNames(other.getId())).containsExactly("남의태그");
        }

        @Test
        @DisplayName("인증 없이 요청하면 401이다")
        void requiresAuthentication() throws Exception {
            Tag tag = saveTag(user.getId(), "내태그");

            mockMvc.perform(delete("/api/tags")
                            .contentType(APPLICATION_JSON)
                            .content(json(Map.of("deleteTagIdList", List.of(tag.getId())))))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("POST /api/tags/recommend")
    class RecommendTags {

        @Test
        @DisplayName("추천 태그를 최대 5개까지 돌려준다")
        void returnsAtMostFiveTags() throws Exception {
            for (int i = 1; i <= 7; i++) {
                saveTag(user.getId(), "태그" + i);
            }

            mockMvc.perform(post("/api/tags/recommend")
                            .contentType(APPLICATION_JSON)
                            .content(json(Map.of("imageUrls", List.of("https://example.com/a.png"))))
                            .with(asUser(user.getId())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.length()").value(5));
        }

        @Test
        @DisplayName("본문 없이 호출해도 200으로 동작한다")
        void worksWithoutBody() throws Exception {
            saveTag(user.getId(), "내태그");

            mockMvc.perform(post("/api/tags/recommend")
                            .with(asUser(user.getId())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data[0].name").value("내태그"));
        }

        @Test
        @DisplayName("다른 사용자의 태그는 추천되지 않는다")
        void neverRecommendsOtherUsersTags() throws Exception {
            saveTag(other.getId(), "남의태그");

            mockMvc.perform(post("/api/tags/recommend")
                            .with(asUser(user.getId())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.length()").value(0));
        }

        @Test
        @DisplayName("인증 없이 요청하면 401이다")
        void requiresAuthentication() throws Exception {
            mockMvc.perform(post("/api/tags/recommend"))
                    .andExpect(status().isUnauthorized());
        }
    }
}
