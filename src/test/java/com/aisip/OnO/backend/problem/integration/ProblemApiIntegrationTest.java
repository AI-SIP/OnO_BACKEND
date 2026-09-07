package com.aisip.OnO.backend.problem.integration;

import com.aisip.OnO.backend.folder.entity.Folder;
import com.aisip.OnO.backend.problem.dto.AddProblemImageUrlsRequest;
import com.aisip.OnO.backend.problem.dto.ProblemDeleteRequestDto;
import com.aisip.OnO.backend.problem.dto.ProblemRegisterDto;
import com.aisip.OnO.backend.problem.dto.ProblemRegisterV2BatchDto;
import com.aisip.OnO.backend.problem.dto.ProblemRegisterV2Dto;
import com.aisip.OnO.backend.problem.dto.ProblemTagUpdateDto;
import com.aisip.OnO.backend.problem.entity.AnalysisStatus;
import com.aisip.OnO.backend.problem.entity.Problem;
import com.aisip.OnO.backend.problem.entity.ProblemImageType;
import com.aisip.OnO.backend.problem.reminder.ProblemReviewReminderRepository;
import com.aisip.OnO.backend.problem.reminder.ProblemReviewReminderStatus;
import com.aisip.OnO.backend.problem.support.ProblemTestSupport;
import com.aisip.OnO.backend.tag.entity.Tag;
import com.aisip.OnO.backend.user.entity.User;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 문제 도메인 API 시나리오 테스트.
 *
 * <p>단일 엔드포인트의 계약은 {@code ProblemControllerTest} 가 본다. 여기서는 앱이 실제로
 * 밟는 순서 — 등록 → 이미지 첨부 → 분석 → 조회 → 수정 → 삭제 — 를 이어서 돌리고,
 * 그 사이에 다른 사용자의 데이터가 절대 섞이지 않는지 확인한다.
 */
@DisplayName("문제 API 시나리오")
class ProblemApiIntegrationTest extends ProblemTestSupport {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    @Autowired
    private ProblemReviewReminderRepository reminderRepository;

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
        authenticateAs(owner.getId());
    }

    private String json(Object body) throws Exception {
        return objectMapper.writeValueAsString(body);
    }

    private JsonNode dataOf(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8)).get("data");
    }

    private Long registerProblem(String memo, Long folderId) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/problems")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new ProblemRegisterDto(null, memo, "출처", folderId, null))))
                .andExpect(status().isOk())
                .andReturn();
        return dataOf(result).asLong();
    }

    // ════════════════════════════ 전체 흐름 ════════════════════════════

    @Nested
    @DisplayName("등록부터 삭제까지")
    class FullLifecycle {

        @Test
        @DisplayName("등록 → 이미지 첨부 → 분석 요청 → 조회 → 수정 → 삭제가 이어서 동작한다")
        void registerAttachAnalyzeUpdateDelete() throws Exception {
            Long problemId = registerProblem("처음 메모", ownerRoot.getId());

            mockMvc.perform(post("/api/problems/{id}/imageData/urls", problemId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(new AddProblemImageUrlsRequest(List.of(
                                    new AddProblemImageUrlsRequest.ImageUrlItem("https://s3/p.png", "PROBLEM_IMAGE"),
                                    new AddProblemImageUrlsRequest.ImageUrlItem("https://s3/a.png", "ANSWER_IMAGE")
                            )))))
                    .andExpect(status().isOk());

            mockMvc.perform(get("/api/problems/{id}", problemId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.memo").value("처음 메모"))
                    .andExpect(jsonPath("$.data.imageUrlList.length()").value(2));

            mockMvc.perform(patch("/api/problems/info")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(new ProblemRegisterDto(problemId, "수정 메모", null, null, null))))
                    .andExpect(status().isOk());

            mockMvc.perform(get("/api/problems/{id}", problemId))
                    .andExpect(jsonPath("$.data.memo").value("수정 메모"));

            mockMvc.perform(delete("/api/problems")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(new ProblemDeleteRequestDto(List.of(problemId)))))
                    .andExpect(status().isOk());

            mockMvc.perform(get("/api/problems/{id}", problemId))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("문제 이미지를 붙이면 분석이 PROCESSING 으로, 없으면 NO_IMAGE 로 정리된다")
        void analysisStatusFollowsImagePresence() throws Exception {
            Long withImageId = registerProblem("이미지 있음", ownerRoot.getId());
            mockMvc.perform(post("/api/problems/{id}/imageData/urls", withImageId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(new AddProblemImageUrlsRequest(List.of(
                                    new AddProblemImageUrlsRequest.ImageUrlItem("https://s3/p.png", "PROBLEM_IMAGE")
                            )))))
                    .andExpect(status().isOk());

            Long withoutImageId = registerProblem("이미지 없음", ownerRoot.getId());
            mockMvc.perform(post("/api/problems/{id}/analysis", withoutImageId))
                    .andExpect(status().isOk());

            mockMvc.perform(get("/api/problems/{id}/analysis", withImageId))
                    .andExpect(jsonPath("$.data.status").value(AnalysisStatus.PROCESSING.name()));
            mockMvc.perform(get("/api/problems/{id}/analysis", withoutImageId))
                    .andExpect(jsonPath("$.data.status").value(AnalysisStatus.NO_IMAGE.name()));
        }

        @Test
        @DisplayName("등록하면 복습 알림 5건이 예약되고 삭제하면 모두 취소된다")
        void reminderLifecycleFollowsProblem() throws Exception {
            Long problemId = registerProblem("복습 대상", ownerRoot.getId());

            assertThat(reminderRepository.findAll())
                    .filteredOn(reminder -> reminder.getProblemId().equals(problemId))
                    .hasSize(5)
                    .allMatch(reminder -> reminder.getStatus() == ProblemReviewReminderStatus.SCHEDULED);

            mockMvc.perform(delete("/api/problems")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(new ProblemDeleteRequestDto(List.of(problemId)))))
                    .andExpect(status().isOk());

            assertThat(reminderRepository.findAll())
                    .filteredOn(reminder -> reminder.getProblemId().equals(problemId))
                    .allMatch(reminder -> reminder.getStatus() == ProblemReviewReminderStatus.CANCELED);
        }

        @Test
        @DisplayName("배치 등록 후 폴더 목록·개수·복습 대상이 모두 일관되게 보인다")
        void batchRegisterIsVisibleEverywhere() throws Exception {
            List<ProblemRegisterV2Dto> dtos = new ArrayList<>();
            for (int i = 0; i < 12; i++) {
                dtos.add(new ProblemRegisterV2Dto(
                        null, "배치" + i, null, ownerRoot.getId(), null, List.of("https://s3/p" + i + ".png"), null));
            }

            mockMvc.perform(post("/api/problems/v2/batch")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(new ProblemRegisterV2BatchDto(dtos))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.length()").value(12));

            mockMvc.perform(get("/api/problems/problemCount"))
                    .andExpect(jsonPath("$.data").value(12));
            mockMvc.perform(get("/api/problems/folder/{folderId}", ownerRoot.getId()))
                    .andExpect(jsonPath("$.data.length()").value(12));
            mockMvc.perform(get("/api/problems/review-due"))
                    .andExpect(jsonPath("$.data.dueCount").value(12))
                    .andExpect(jsonPath("$.data.overdueCount").value(0));
        }
    }

    // ════════════════════════════ 페이징 ════════════════════════════

    @Nested
    @DisplayName("커서 페이징 흐름")
    class CursorPaging {

        @Test
        @DisplayName("커서를 따라가면 모든 문제를 중복 없이 정확히 한 번씩 받는다")
        void walksThroughAllPages() throws Exception {
            List<Long> registered = new ArrayList<>();
            for (int i = 0; i < 7; i++) {
                registered.add(registerProblem("페이징" + i, ownerRoot.getId()));
            }

            List<Long> collected = new ArrayList<>();
            Long cursor = null;
            boolean hasNext = true;
            while (hasNext) {
                MvcResult result = mockMvc.perform(get("/api/problems/folder/{folderId}/V2", ownerRoot.getId())
                                .param("size", "3")
                                .param("cursor", cursor == null ? "" : String.valueOf(cursor)))
                        .andExpect(status().isOk())
                        .andReturn();

                JsonNode data = dataOf(result);
                data.get("content").forEach(node -> collected.add(node.get("problemId").asLong()));
                hasNext = data.get("hasNext").asBoolean();
                cursor = hasNext ? data.get("nextCursor").asLong() : null;
            }

            assertThat(collected)
                    .as("커서 페이징이 건너뛰거나 겹치면 앱 무한 스크롤이 깨진다")
                    .containsExactlyElementsOf(registered);
        }

        @Test
        @DisplayName("태그 커서 조회도 태그가 붙은 문제만 순서대로 준다")
        void walksThroughTaggedProblems() throws Exception {
            Tag tag = saveTag(owner.getId(), "페이징태그");
            List<Long> tagged = new ArrayList<>();
            for (int i = 0; i < 4; i++) {
                Long problemId = registerProblem("태그" + i, ownerRoot.getId());
                mockMvc.perform(patch("/api/problems/{id}/tags", problemId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json(new ProblemTagUpdateDto(List.of(tag.getId()), null))))
                        .andExpect(status().isOk());
                tagged.add(problemId);
            }
            registerProblem("태그 없음", ownerRoot.getId());

            MvcResult result = mockMvc.perform(get("/api/problems/tag/{tagId}/V2", tag.getId())
                            .param("size", "10"))
                    .andExpect(status().isOk())
                    .andReturn();

            List<Long> collected = new ArrayList<>();
            dataOf(result).get("content").forEach(node -> collected.add(node.get("problemId").asLong()));
            assertThat(collected).containsExactlyElementsOf(tagged);
        }
    }

    // ════════════════════════════ 사용자 격리 ════════════════════════════

    @Nested
    @DisplayName("사용자 격리")
    class UserIsolation {

        @Test
        @DisplayName("같은 데이터를 두 사용자가 조회하면 각자 자기 것만 본다")
        void eachUserSeesOwnDataOnly() throws Exception {
            registerProblem("내 문제", ownerRoot.getId());

            authenticateAs(intruder.getId());
            registerProblem("남의 문제", intruderRoot.getId());

            mockMvc.perform(get("/api/problems/user"))
                    .andExpect(jsonPath("$.data.length()").value(1))
                    .andExpect(jsonPath("$.data[0].memo").value("남의 문제"));

            authenticateAs(owner.getId());
            mockMvc.perform(get("/api/problems/user"))
                    .andExpect(jsonPath("$.data.length()").value(1))
                    .andExpect(jsonPath("$.data[0].memo").value("내 문제"));
        }

        @Test
        @DisplayName("전체 삭제는 요청한 사용자의 문제에만 적용된다")
        void deleteAllIsScopedToRequester() throws Exception {
            registerProblem("내 문제", ownerRoot.getId());

            authenticateAs(intruder.getId());
            registerProblem("남의 문제", intruderRoot.getId());

            mockMvc.perform(delete("/api/problems/all")).andExpect(status().isOk());

            assertThat(problemRepository.findAllByUserId(intruder.getId())).isEmpty();
            assertThat(problemRepository.findAllByUserId(owner.getId()))
                    .as("남이 전체 삭제를 눌러도 내 오답노트는 남아야 한다")
                    .hasSize(1);
        }

        @Test
        @DisplayName("남의 문제 ID 를 알아내도 조회·수정·이동·삭제가 모두 막힌다")
        void everyMutationOnForeignProblemIsRejected() throws Exception {
            Problem theirs = saveProblem(intruder.getId(), intruderRoot, "남의 문제", null);
            Folder myFolder = fixtures.createFolder(owner.getId(), "내 폴더", ownerRoot);

            mockMvc.perform(get("/api/problems/{id}", theirs.getId()))
                    .andExpect(status().isForbidden());
            mockMvc.perform(get("/api/problems/{id}/analysis", theirs.getId()))
                    .andExpect(status().isForbidden());
            mockMvc.perform(patch("/api/problems/info")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(new ProblemRegisterDto(theirs.getId(), "탈취", null, null, null))))
                    .andExpect(status().isForbidden());
            mockMvc.perform(patch("/api/problems/path")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(new ProblemRegisterDto(theirs.getId(), null, null, myFolder.getId(), null))))
                    .andExpect(status().isForbidden());
            mockMvc.perform(patch("/api/problems/{id}/tags", theirs.getId())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(new ProblemTagUpdateDto(List.of(), List.of()))))
                    .andExpect(status().isForbidden());
            mockMvc.perform(delete("/api/problems")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(new ProblemDeleteRequestDto(List.of(theirs.getId())))))
                    .andExpect(status().isForbidden());
            mockMvc.perform(post("/api/problems/{id}/analysis", theirs.getId()))
                    .andExpect(status().isForbidden());

            Problem reloaded = problemRepository.findProblemWithImageData(theirs.getId()).orElseThrow();
            assertThat(reloaded.getMemo()).isEqualTo("남의 문제");
            assertThat(reloaded.getFolder().getId()).isEqualTo(intruderRoot.getId());
        }

        @Test
        @DisplayName("복습 대상 조회에도 남의 문제는 들어오지 않는다")
        void reviewDueIsScoped() throws Exception {
            LocalDate today = LocalDate.now(SEOUL);
            saveProblemWithReviewSchedule(intruder.getId(), intruderRoot, today.minusDays(3), 1, 0);
            saveProblemWithReviewSchedule(owner.getId(), ownerRoot, today, 1, 0);

            mockMvc.perform(get("/api/problems/review-due"))
                    .andExpect(jsonPath("$.data.dueCount").value(1))
                    .andExpect(jsonPath("$.data.overdueCount").value(0));
        }

        @Test
        @DisplayName("제목 검색으로도 남의 문제가 노출되지 않는다")
        void titleSearchIsScoped() throws Exception {
            saveProblem(intruder.getId(), intruderRoot, "메모", "비밀 노트");

            mockMvc.perform(get("/api/problems/title/V2").param("query", "비밀"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.content.length()").value(0));
        }
    }

    // ════════════════════════════ 입력 경계 ════════════════════════════

    @Nested
    @DisplayName("입력 경계값")
    class InputBoundaries {

        @Test
        @DisplayName("메모 0자 / 1000자는 통과하고 1001자는 400 이다")
        void memoBoundaries() throws Exception {
            mockMvc.perform(post("/api/problems")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(new ProblemRegisterDto(null, "", null, ownerRoot.getId(), null))))
                    .andExpect(status().isOk());

            mockMvc.perform(post("/api/problems")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(new ProblemRegisterDto(
                                    null, "가".repeat(1000), null, ownerRoot.getId(), null))))
                    .andExpect(status().isOk());

            mockMvc.perform(post("/api/problems")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(new ProblemRegisterDto(
                                    null, "가".repeat(1001), null, ownerRoot.getId(), null))))
                    .andExpect(status().isBadRequest());

            assertThat(problemRepository.findAllByUserId(owner.getId())).hasSize(2);
        }

        @Test
        @DisplayName("저장된 1000자 메모는 잘리지 않고 그대로 조회된다")
        void longMemoRoundTrips() throws Exception {
            String memo = "가".repeat(1000);
            Long problemId = registerProblem(memo, ownerRoot.getId());

            MvcResult result = mockMvc.perform(get("/api/problems/{id}", problemId))
                    .andExpect(status().isOk())
                    .andReturn();

            assertThat(dataOf(result).get("memo").asText())
                    .as("MySQL varchar 길이가 모자라면 여기서 잘린 값이 돌아온다")
                    .isEqualTo(memo);
        }

        @Test
        @DisplayName("빈 이미지 리스트와 빈 태그 리스트로도 등록된다")
        void emptyCollectionsAreAccepted() throws Exception {
            MvcResult result = mockMvc.perform(post("/api/problems/v2")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(new ProblemRegisterV2Dto(
                                    null, "메모", null, ownerRoot.getId(), null,
                                    List.of(), List.of(), List.of()))))
                    .andExpect(status().isOk())
                    .andReturn();

            Long problemId = dataOf(result).asLong();
            assertThat(problemImageDataRepository.findAllByProblemId(problemId)).isEmpty();
            assertThat(problemTagMappingRepository.findAllByProblemId(problemId)).isEmpty();
        }

        @Test
        @DisplayName("SOLVE_IMAGE 는 하루에 한 번만 등록된다")
        void solveImageIsOncePerDay() throws Exception {
            Long problemId = registerProblem("복습 기록", ownerRoot.getId());

            mockMvc.perform(post("/api/problems/{id}/imageData/urls", problemId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(new AddProblemImageUrlsRequest(List.of(
                                    new AddProblemImageUrlsRequest.ImageUrlItem("https://s3/s1.png", "SOLVE_IMAGE")
                            )))))
                    .andExpect(status().isOk());

            mockMvc.perform(post("/api/problems/{id}/imageData/urls", problemId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(new AddProblemImageUrlsRequest(List.of(
                                    new AddProblemImageUrlsRequest.ImageUrlItem("https://s3/s2.png", "SOLVE_IMAGE")
                            )))))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value(4003));

            assertThat(problemImageDataRepository.findAllByProblemId(problemId))
                    .filteredOn(image -> image.getProblemImageType() == ProblemImageType.SOLVE_IMAGE)
                    .hasSize(1);
        }
    }
}
