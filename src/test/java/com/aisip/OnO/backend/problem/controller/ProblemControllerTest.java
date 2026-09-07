package com.aisip.OnO.backend.problem.controller;

import com.aisip.OnO.backend.folder.entity.Folder;
import com.aisip.OnO.backend.problem.dto.AddProblemImageUrlsRequest;
import com.aisip.OnO.backend.problem.dto.ProblemDeleteRequestDto;
import com.aisip.OnO.backend.problem.dto.ProblemRegisterDto;
import com.aisip.OnO.backend.problem.dto.ProblemRegisterV2BatchDto;
import com.aisip.OnO.backend.problem.dto.ProblemRegisterV2Dto;
import com.aisip.OnO.backend.problem.dto.ProblemTagUpdateDto;
import com.aisip.OnO.backend.problem.dto.UpdateProblemImageDataRequest;
import com.aisip.OnO.backend.problem.entity.Problem;
import com.aisip.OnO.backend.problem.entity.ProblemImageType;
import com.aisip.OnO.backend.problem.support.ProblemTestSupport;
import com.aisip.OnO.backend.tag.entity.Tag;
import com.aisip.OnO.backend.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * ProblemController HTTP 계약 테스트.
 *
 * <p>서비스를 목으로 대체하지 않고 실제 빈을 그대로 쓴다. 목으로 바꾸면 컨텍스트가 갈라지기도 하고,
 * "컨트롤러가 서비스를 호출한다"는 사실만 확인하게 되어 응답 코드·바디가 실제로 맞는지 알 수 없다.
 */
@DisplayName("ProblemController")
class ProblemControllerTest extends ProblemTestSupport {

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

    // ════════════════════════════ 인증 ════════════════════════════

    @Nested
    @DisplayName("인증되지 않은 요청")
    class Unauthenticated {

        @BeforeEach
        void removeAuthentication() {
            clearAuthentication();
        }

        @Test
        @DisplayName("문제 조회는 401 로 막힌다")
        void rejectsGet() throws Exception {
            mockMvc.perform(get("/api/problems/1"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("문제 등록은 401 로 막힌다")
        void rejectsPost() throws Exception {
            mockMvc.perform(post("/api/problems")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(new ProblemRegisterDto(null, "메모", null, ownerRoot.getId(), null))))
                    .andExpect(status().isUnauthorized());

            assertThat(problemRepository.findAllByUserId(owner.getId()))
                    .as("인증 없이 데이터가 만들어지면 안 된다")
                    .isEmpty();
        }

        @Test
        @DisplayName("문제 삭제는 401 로 막힌다")
        void rejectsDelete() throws Exception {
            mockMvc.perform(delete("/api/problems/all"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("복습 대상 조회도 401 로 막힌다")
        void rejectsReviewDue() throws Exception {
            mockMvc.perform(get("/api/problems/review-due"))
                    .andExpect(status().isUnauthorized());
        }
    }

    // ════════════════════════════ 조회 ════════════════════════════

    @Nested
    @DisplayName("GET 조회 엔드포인트")
    class GetEndpoints {

        @Test
        @DisplayName("GET /api/problems/{id} - 본인 문제는 200 과 문제 내용을 반환한다")
        void getProblem() throws Exception {
            Problem problem = saveProblem(owner.getId(), ownerRoot, "조회 메모", "조회 출처");

            mockMvc.perform(get("/api/problems/{id}", problem.getId()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.problemId").value(problem.getId()))
                    .andExpect(jsonPath("$.data.memo").value("조회 메모"))
                    .andExpect(jsonPath("$.data.reference").value("조회 출처"))
                    .andExpect(jsonPath("$.data.folderId").value(ownerRoot.getId()));
        }

        @Test
        @DisplayName("GET /api/problems/{id} - 다른 사용자의 문제는 403")
        void getOtherUsersProblemIsForbidden() throws Exception {
            Problem othersProblem = saveProblem(intruder.getId(), intruderRoot);

            mockMvc.perform(get("/api/problems/{id}", othersProblem.getId()))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.errorCode").value(4002));
        }

        @Test
        @DisplayName("GET /api/problems/{id} - 없는 문제는 404")
        void getUnknownProblemIsNotFound() throws Exception {
            mockMvc.perform(get("/api/problems/{id}", 999_999L))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.errorCode").value(4001));
        }

        @Test
        @DisplayName("GET /api/problems/{id} - 숫자가 아닌 ID 는 400")
        void getWithNonNumericIdIsBadRequest() throws Exception {
            mockMvc.perform(get("/api/problems/{id}", "abc"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("GET /api/problems/user - 본인 문제만 반환한다")
        void getUserProblems() throws Exception {
            saveProblem(owner.getId(), ownerRoot, "내 문제", null);
            saveProblem(intruder.getId(), intruderRoot, "남의 문제", null);

            mockMvc.perform(get("/api/problems/user"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.length()").value(1))
                    .andExpect(jsonPath("$.data[0].memo").value("내 문제"));
        }

        @Test
        @DisplayName("GET /api/problems/folder/{folderId} - 남의 폴더는 403")
        void getOtherUsersFolderIsForbidden() throws Exception {
            mockMvc.perform(get("/api/problems/folder/{folderId}", intruderRoot.getId()))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.errorCode").value(5002));
        }

        @Test
        @DisplayName("GET /api/problems/folder/{folderId}/V2 - 커서 페이지를 반환한다")
        void getFolderProblemsWithCursor() throws Exception {
            for (int i = 0; i < 3; i++) {
                saveProblem(owner.getId(), ownerRoot, "커서" + i, null);
            }

            mockMvc.perform(get("/api/problems/folder/{folderId}/V2", ownerRoot.getId())
                            .param("size", "2"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.content.length()").value(2))
                    .andExpect(jsonPath("$.data.hasNext").value(true))
                    .andExpect(jsonPath("$.data.nextCursor").isNumber());
        }

        @Test
        @DisplayName("GET /api/problems/tag/{tagId}/V2 - 남의 태그는 403")
        void getOtherUsersTagIsForbidden() throws Exception {
            Tag othersTag = saveTag(intruder.getId(), "남의태그");

            mockMvc.perform(get("/api/problems/tag/{tagId}/V2", othersTag.getId()))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.errorCode").value(9004));
        }

        @Test
        @DisplayName("GET /api/problems/title/V2 - 제목 검색 결과를 반환한다")
        void searchByTitle() throws Exception {
            saveProblem(owner.getId(), ownerRoot, "메모", "미적분 3단원");

            mockMvc.perform(get("/api/problems/title/V2").param("query", "미적분"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.content.length()").value(1))
                    .andExpect(jsonPath("$.data.content[0].reference").value("미적분 3단원"));
        }

        @Test
        @DisplayName("GET /api/problems/title/V2 - query 파라미터가 없으면 400")
        void searchWithoutQueryIsBadRequest() throws Exception {
            mockMvc.perform(get("/api/problems/title/V2"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("GET /api/problems/problemCount - 본인 문제 개수를 반환한다")
        void getProblemCount() throws Exception {
            saveProblem(owner.getId(), ownerRoot);
            saveProblem(owner.getId(), ownerRoot);
            saveProblem(intruder.getId(), intruderRoot);

            mockMvc.perform(get("/api/problems/problemCount"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data").value(2));
        }

        @Test
        @DisplayName("GET /api/problems/{id}/analysis - 분석 레코드가 없으면 NOT_STARTED 로 응답한다")
        void getAnalysisWithoutRow() throws Exception {
            Problem problem = saveProblem(owner.getId(), ownerRoot);

            mockMvc.perform(get("/api/problems/{id}/analysis", problem.getId()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status").value("NOT_STARTED"));
        }

        @Test
        @DisplayName("GET /api/problems/{id}/analysis - 남의 문제 분석은 403")
        void getOtherUsersAnalysisIsForbidden() throws Exception {
            Problem othersProblem = saveProblem(intruder.getId(), intruderRoot);

            mockMvc.perform(get("/api/problems/{id}/analysis", othersProblem.getId()))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("GET /api/problems/review-due - 오늘 복습 대상과 밀린 개수를 반환한다")
        void getReviewDue() throws Exception {
            LocalDate today = LocalDate.now(java.time.ZoneId.of("Asia/Seoul"));
            saveProblemWithReviewSchedule(owner.getId(), ownerRoot, today.minusDays(2), 1, 0);
            saveProblemWithReviewSchedule(owner.getId(), ownerRoot, today, 1, 0);

            mockMvc.perform(get("/api/problems/review-due"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.dueCount").value(2))
                    .andExpect(jsonPath("$.data.overdueCount").value(1))
                    .andExpect(jsonPath("$.data.problems.length()").value(2));
        }
    }

    // ════════════════════════════ 등록 ════════════════════════════

    @Nested
    @DisplayName("등록 엔드포인트")
    class RegisterEndpoints {

        @Test
        @DisplayName("POST /api/problems - 생성된 problemId 를 반환한다")
        void registerProblem() throws Exception {
            mockMvc.perform(post("/api/problems")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(new ProblemRegisterDto(null, "메모", "출처", ownerRoot.getId(), null))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data").isNumber());

            assertThat(problemRepository.findAllByUserId(owner.getId())).hasSize(1);
        }

        @Test
        @DisplayName("POST /api/problems - folderId 가 없으면 500 이 아니라 400")
        void registerWithoutFolderIdIsBadRequest() throws Exception {
            mockMvc.perform(post("/api/problems")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(new ProblemRegisterDto(null, "메모", null, null, null))))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value(4008));
        }

        @Test
        @DisplayName("POST /api/problems - 1001자 메모는 400")
        void registerWithTooLongMemoIsBadRequest() throws Exception {
            mockMvc.perform(post("/api/problems")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(new ProblemRegisterDto(
                                    null, "가".repeat(1001), null, ownerRoot.getId(), null))))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value(4006));
        }

        @Test
        @DisplayName("POST /api/problems - 1000자 메모는 200")
        void registerWithMaxMemoSucceeds() throws Exception {
            mockMvc.perform(post("/api/problems")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(new ProblemRegisterDto(
                                    null, "가".repeat(1000), null, ownerRoot.getId(), null))))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("POST /api/problems - 남의 폴더에 등록하면 403")
        void registerIntoOtherUsersFolderIsForbidden() throws Exception {
            mockMvc.perform(post("/api/problems")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(new ProblemRegisterDto(null, "메모", null, intruderRoot.getId(), null))))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.errorCode").value(5002));
        }

        @Test
        @DisplayName("POST /api/problems - 잘못된 JSON 은 400")
        void registerWithMalformedJsonIsBadRequest() throws Exception {
            mockMvc.perform(post("/api/problems")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{ this is not json"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("POST /api/problems/v2 - 이미지 URL 과 함께 등록된다")
        void registerProblemV2() throws Exception {
            mockMvc.perform(post("/api/problems/v2")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(new ProblemRegisterV2Dto(
                                    null, "메모", null, ownerRoot.getId(), null,
                                    List.of("https://s3/p.png"), List.of("https://s3/a.png")))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data").isNumber());

            Problem saved = problemRepository.findAllByUserId(owner.getId()).get(0);
            assertThat(problemImageDataRepository.findAllByProblemId(saved.getId())).hasSize(2);
        }

        @Test
        @DisplayName("POST /api/problems/v2/batch - 여러 건을 한 번에 등록한다")
        void registerProblemsV2Batch() throws Exception {
            ProblemRegisterV2BatchDto batch = new ProblemRegisterV2BatchDto(List.of(
                    new ProblemRegisterV2Dto(null, "배치1", null, ownerRoot.getId(), null, null, null),
                    new ProblemRegisterV2Dto(null, "배치2", null, ownerRoot.getId(), null, null, null)
            ));

            mockMvc.perform(post("/api/problems/v2/batch")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(batch)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.length()").value(2));
        }

        @Test
        @DisplayName("POST /api/problems/v2/batch - 빈 배치는 빈 배열을 반환한다")
        void registerEmptyBatch() throws Exception {
            mockMvc.perform(post("/api/problems/v2/batch")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(new ProblemRegisterV2BatchDto(List.of()))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.length()").value(0));
        }
    }

    // ════════════════════════════ 이미지 ════════════════════════════

    @Nested
    @DisplayName("이미지 엔드포인트")
    class ImageEndpoints {

        @Test
        @DisplayName("POST /{id}/imageData - 멀티파트 업로드 후 이미지가 저장된다")
        void uploadImages() throws Exception {
            Problem problem = saveProblem(owner.getId(), ownerRoot);
            saveSkippedAnalysis(problem);
            org.mockito.BDDMockito.given(fileUploadService.uploadFileToS3(org.mockito.ArgumentMatchers.any()))
                    .willReturn("https://s3/uploaded.png");

            mockMvc.perform(MockMvcRequestBuilders.multipart("/api/problems/{id}/imageData", problem.getId())
                            .file(new MockMultipartFile("problemImages", "p.png", "image/png", "img".getBytes()))
                            .param("problemImageTypes", "PROBLEM_IMAGE"))
                    .andExpect(status().isOk());

            assertThat(problemImageDataRepository.findAllByProblemId(problem.getId())).hasSize(1);
        }

        @Test
        @DisplayName("POST /{id}/imageData/urls - URL 로 이미지를 추가한다")
        void addImageUrls() throws Exception {
            Problem problem = saveProblem(owner.getId(), ownerRoot);
            saveSkippedAnalysis(problem);

            mockMvc.perform(post("/api/problems/{id}/imageData/urls", problem.getId())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(new AddProblemImageUrlsRequest(List.of(
                                    new AddProblemImageUrlsRequest.ImageUrlItem("https://s3/p.png", "PROBLEM_IMAGE")
                            )))))
                    .andExpect(status().isOk());

            assertThat(problemImageDataRepository.findAllByProblemId(problem.getId())).hasSize(1);
        }

        @Test
        @DisplayName("POST /{id}/imageData/urls - imageDataList 가 없으면 400")
        void addImageUrlsWithoutListIsBadRequest() throws Exception {
            Problem problem = saveProblem(owner.getId(), ownerRoot);

            mockMvc.perform(post("/api/problems/{id}/imageData/urls", problem.getId())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("POST /{id}/imageData/urls - 남의 문제에는 403")
        void addImageUrlsToOtherUsersProblemIsForbidden() throws Exception {
            Problem othersProblem = saveProblem(intruder.getId(), intruderRoot);

            mockMvc.perform(post("/api/problems/{id}/imageData/urls", othersProblem.getId())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(new AddProblemImageUrlsRequest(List.of(
                                    new AddProblemImageUrlsRequest.ImageUrlItem("https://s3/p.png", "PROBLEM_IMAGE")
                            )))))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("PATCH /api/problems/imageData - 이미지 목록을 갱신한다")
        void updateImageData() throws Exception {
            Problem problem = saveProblem(owner.getId(), ownerRoot);
            saveSkippedAnalysis(problem);

            mockMvc.perform(patch("/api/problems/imageData")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(new UpdateProblemImageDataRequest(problem.getId(), List.of(
                                    new UpdateProblemImageDataRequest.ImageItem("https://s3/new.png", "PROBLEM_IMAGE")
                            )))))
                    .andExpect(status().isOk());

            assertThat(problemImageDataRepository.findAllByProblemId(problem.getId()))
                    .extracting(image -> image.getImageUrl())
                    .containsExactly("https://s3/new.png");
        }

        @Test
        @DisplayName("DELETE /api/problems/imageData - 본인 이미지를 삭제한다")
        void deleteImageData() throws Exception {
            Problem problem = saveProblem(owner.getId(), ownerRoot);
            saveImageData(problem, "https://s3/mine.png", ProblemImageType.PROBLEM_IMAGE);

            mockMvc.perform(delete("/api/problems/imageData").param("imageUrl", "https://s3/mine.png"))
                    .andExpect(status().isOk());

            assertThat(problemImageDataRepository.findByImageUrl("https://s3/mine.png")).isEmpty();
        }

        @Test
        @DisplayName("DELETE /api/problems/imageData - 남의 이미지는 403")
        void deleteOtherUsersImageIsForbidden() throws Exception {
            Problem othersProblem = saveProblem(intruder.getId(), intruderRoot);
            saveImageData(othersProblem, "https://s3/theirs.png", ProblemImageType.PROBLEM_IMAGE);

            mockMvc.perform(delete("/api/problems/imageData").param("imageUrl", "https://s3/theirs.png"))
                    .andExpect(status().isForbidden());

            assertThat(problemImageDataRepository.findByImageUrl("https://s3/theirs.png")).isPresent();
        }

        @Test
        @DisplayName("DELETE /api/problems/imageData - imageUrl 파라미터가 없으면 400")
        void deleteImageWithoutUrlIsBadRequest() throws Exception {
            mockMvc.perform(delete("/api/problems/imageData"))
                    .andExpect(status().isBadRequest());
        }
    }

    // ════════════════════════════ 분석 ════════════════════════════

    @Nested
    @DisplayName("분석 엔드포인트")
    class AnalysisEndpoints {

        @Test
        @DisplayName("POST /{id}/analysis - 이미지가 없으면 NO_IMAGE 로 마무리된다")
        void requestAnalysisWithoutImage() throws Exception {
            Problem problem = saveProblem(owner.getId(), ownerRoot);
            saveSkippedAnalysis(problem);

            mockMvc.perform(post("/api/problems/{id}/analysis", problem.getId()))
                    .andExpect(status().isOk());

            assertThat(problemAnalysisRepository.findByProblemId(problem.getId()).orElseThrow().getStatus())
                    .isEqualTo(com.aisip.OnO.backend.problem.entity.AnalysisStatus.NO_IMAGE);
        }

        @Test
        @DisplayName("POST /{id}/analysis - 남의 문제는 403")
        void requestAnalysisOnOtherUsersProblemIsForbidden() throws Exception {
            Problem othersProblem = saveProblem(intruder.getId(), intruderRoot);
            saveSkippedAnalysis(othersProblem);

            mockMvc.perform(post("/api/problems/{id}/analysis", othersProblem.getId()))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("PATCH /{id}/no-image - 분석 상태를 NO_IMAGE 로 바꾼다")
        void updateToNoImage() throws Exception {
            Problem problem = saveProblem(owner.getId(), ownerRoot);
            saveSkippedAnalysis(problem);

            mockMvc.perform(patch("/api/problems/{id}/no-image", problem.getId()))
                    .andExpect(status().isOk());

            assertThat(problemAnalysisRepository.findByProblemId(problem.getId()).orElseThrow().getStatus())
                    .isEqualTo(com.aisip.OnO.backend.problem.entity.AnalysisStatus.NO_IMAGE);
        }
    }

    // ════════════════════════════ 수정/삭제 ════════════════════════════

    @Nested
    @DisplayName("수정 · 삭제 엔드포인트")
    class MutationEndpoints {

        @Test
        @DisplayName("PATCH /api/problems/info - 메모를 수정한다")
        void updateInfo() throws Exception {
            Problem problem = saveProblem(owner.getId(), ownerRoot, "이전", null);

            mockMvc.perform(patch("/api/problems/info")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(new ProblemRegisterDto(problem.getId(), "이후", null, null, null))))
                    .andExpect(status().isOk());

            assertThat(problemRepository.findById(problem.getId()).orElseThrow().getMemo()).isEqualTo("이후");
        }

        @Test
        @DisplayName("PATCH /api/problems/info - 남의 문제 수정은 403")
        void updateOtherUsersInfoIsForbidden() throws Exception {
            Problem othersProblem = saveProblem(intruder.getId(), intruderRoot, "남의 메모", null);

            mockMvc.perform(patch("/api/problems/info")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(new ProblemRegisterDto(othersProblem.getId(), "탈취", null, null, null))))
                    .andExpect(status().isForbidden());

            assertThat(problemRepository.findById(othersProblem.getId()).orElseThrow().getMemo())
                    .isEqualTo("남의 메모");
        }

        @Test
        @DisplayName("PATCH /api/problems/path - 폴더를 이동한다")
        void updatePath() throws Exception {
            Folder target = fixtures.createFolder(owner.getId(), "대상", ownerRoot);
            Problem problem = saveProblem(owner.getId(), ownerRoot);

            mockMvc.perform(patch("/api/problems/path")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(new ProblemRegisterDto(problem.getId(), null, null, target.getId(), null))))
                    .andExpect(status().isOk());

            assertThat(problemRepository.findProblemWithImageData(problem.getId()).orElseThrow()
                    .getFolder().getId())
                    .isEqualTo(target.getId());
        }

        @Test
        @DisplayName("PATCH /api/problems/path - 남의 폴더로 이동하면 403")
        void movingIntoOtherUsersFolderIsForbidden() throws Exception {
            Problem problem = saveProblem(owner.getId(), ownerRoot);

            mockMvc.perform(patch("/api/problems/path")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(new ProblemRegisterDto(
                                    problem.getId(), null, null, intruderRoot.getId(), null))))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("PATCH /{id}/tags - 태그를 추가한다")
        void updateTags() throws Exception {
            Problem problem = saveProblem(owner.getId(), ownerRoot);
            Tag tag = saveTag(owner.getId(), "추가태그");

            mockMvc.perform(patch("/api/problems/{id}/tags", problem.getId())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(new ProblemTagUpdateDto(List.of(tag.getId()), null))))
                    .andExpect(status().isOk());

            assertThat(problemTagMappingRepository.findAllByProblemId(problem.getId())).hasSize(1);
        }

        @Test
        @DisplayName("PATCH /{id}/tags - 남의 태그를 붙이면 404")
        void addingOtherUsersTagIsNotFound() throws Exception {
            Problem problem = saveProblem(owner.getId(), ownerRoot);
            Tag othersTag = saveTag(intruder.getId(), "남의태그");

            mockMvc.perform(patch("/api/problems/{id}/tags", problem.getId())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(new ProblemTagUpdateDto(List.of(othersTag.getId()), null))))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.errorCode").value(9003));
        }

        @Test
        @DisplayName("DELETE /api/problems - 지정한 문제를 삭제한다")
        void deleteProblems() throws Exception {
            Problem problem = saveProblem(owner.getId(), ownerRoot);

            mockMvc.perform(delete("/api/problems")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(new ProblemDeleteRequestDto(List.of(problem.getId())))))
                    .andExpect(status().isOk());

            assertThat(problemRepository.findById(problem.getId())).isEmpty();
        }

        @Test
        @DisplayName("DELETE /api/problems - 남의 문제 ID 를 넣으면 403 이고 아무것도 지워지지 않는다")
        void deletingOtherUsersProblemIsForbidden() throws Exception {
            Problem mine = saveProblem(owner.getId(), ownerRoot);
            Problem theirs = saveProblem(intruder.getId(), intruderRoot);

            mockMvc.perform(delete("/api/problems")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(new ProblemDeleteRequestDto(List.of(mine.getId(), theirs.getId())))))
                    .andExpect(status().isForbidden());

            assertThat(problemRepository.findById(mine.getId())).isPresent();
            assertThat(problemRepository.findById(theirs.getId())).isPresent();
        }

        @Test
        @DisplayName("DELETE /api/problems/all - 본인 문제만 전부 삭제한다")
        void deleteAllUserProblems() throws Exception {
            saveProblem(owner.getId(), ownerRoot);
            Problem theirs = saveProblem(intruder.getId(), intruderRoot);

            mockMvc.perform(delete("/api/problems/all"))
                    .andExpect(status().isOk());

            assertThat(problemRepository.findAllByUserId(owner.getId())).isEmpty();
            assertThat(problemRepository.findById(theirs.getId())).isPresent();
        }
    }
}
