package com.aisip.OnO.backend.problemsolve.controller;

import com.aisip.OnO.backend.problem.entity.Problem;
import com.aisip.OnO.backend.problemsolve.ProblemSolveTestSupport;
import com.aisip.OnO.backend.problemsolve.entity.AnswerStatus;
import com.aisip.OnO.backend.problemsolve.entity.ProblemSolve;
import com.aisip.OnO.backend.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("복습 기록 API")
class ProblemSolveControllerTest extends ProblemSolveTestSupport {

    private User user;
    private User other;
    private Problem problem;
    private Problem othersProblem;

    @BeforeEach
    void setUpFixtures() {
        user = fixtures.createUser();
        other = fixtures.createOtherUser();
        problem = saveProblem(user.getId());
        othersProblem = saveProblem(other.getId());
    }

    private String json(Object body) throws Exception {
        return objectMapper.writeValueAsString(body);
    }

    private Map<String, Object> registerBody(Long problemId, String answerStatus) {
        Map<String, Object> body = new HashMap<>();
        body.put("problemId", problemId);
        body.put("practicedAt", "2026-01-10T09:30:00");
        body.put("answerStatus", answerStatus);
        body.put("reflection", "회고");
        body.put("improvements", List.of("FASTER_SOLVING"));
        body.put("timeSpentSeconds", 120);
        return body;
    }

    @Nested
    @DisplayName("POST /api/problem-solves")
    class CreateProblemSolve {

        @Test
        @DisplayName("복습 기록을 만들면 200과 생성된 id를 돌려준다")
        void createsSolve() throws Exception {

            mockMvc.perform(post("/api/problem-solves")
                            .contentType(APPLICATION_JSON)
                            .content(json(registerBody(problem.getId(), "CORRECT")))
                            .with(asUser(user.getId())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data").isNumber());

            assertThat(problemSolveRepository.countByProblemId(problem.getId())).isEqualTo(1L);
        }

        @Test
        @DisplayName("다른 사용자의 문제에 기록하려 하면 403이다")
        void rejectsOtherUsersProblem() throws Exception {

            mockMvc.perform(post("/api/problem-solves")
                            .contentType(APPLICATION_JSON)
                            .content(json(registerBody(othersProblem.getId(), "CORRECT")))
                            .with(asUser(user.getId())))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.errorCode").value(4002));

            assertThat(problemSolveRepository.countByProblemId(othersProblem.getId())).isZero();
        }

        @Test
        @DisplayName("없는 문제면 404다")
        void rejectsUnknownProblem() throws Exception {

            mockMvc.perform(post("/api/problem-solves")
                            .contentType(APPLICATION_JSON)
                            .content(json(registerBody(999_999L, "CORRECT")))
                            .with(asUser(user.getId())))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.errorCode").value(4001));
        }

        @Test
        @DisplayName("problemId 나 answerStatus 가 빠지면 500이 아니라 400이다")
        void rejectsMissingRequiredFields() throws Exception {

            mockMvc.perform(post("/api/problem-solves")
                            .contentType(APPLICATION_JSON)
                            .content(json(registerBody(null, "CORRECT")))
                            .with(asUser(user.getId())))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value(4023));

            mockMvc.perform(post("/api/problem-solves")
                            .contentType(APPLICATION_JSON)
                            .content(json(registerBody(problem.getId(), null)))
                            .with(asUser(user.getId())))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value(4023));
        }

        @Test
        @DisplayName("알 수 없는 채점 상태 문자열은 400이다")
        void rejectsUnknownAnswerStatus() throws Exception {

            mockMvc.perform(post("/api/problem-solves")
                            .contentType(APPLICATION_JSON)
                            .content(json(registerBody(problem.getId(), "NOT_A_STATUS")))
                            .with(asUser(user.getId())))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("인증 없이 요청하면 401이고 아무것도 저장되지 않는다")
        void requiresAuthentication() throws Exception {
            mockMvc.perform(post("/api/problem-solves")
                            .contentType(APPLICATION_JSON)
                            .content(json(registerBody(problem.getId(), "CORRECT"))))
                    .andExpect(status().isUnauthorized());

            assertThat(problemSolveRepository.countByProblemId(problem.getId())).isZero();
        }
    }

    @Nested
    @DisplayName("GET /api/problem-solves/{problemSolveId}")
    class GetProblemSolve {

        @Test
        @DisplayName("자기 기록을 조회하면 200과 상세 정보를 돌려준다")
        void returnsOwnSolve() throws Exception {
            ProblemSolve solve = saveSolve(problem, user.getId(), PRACTICED_AT, AnswerStatus.PARTIAL);

            mockMvc.perform(get("/api/problem-solves/" + solve.getId())
                            .with(asUser(user.getId())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.problemSolveId").value(solve.getId()))
                    .andExpect(jsonPath("$.data.problemId").value(problem.getId()))
                    .andExpect(jsonPath("$.data.answerStatus").value("PARTIAL"))
                    .andExpect(jsonPath("$.data.practicedAt").value("2026-01-10 09:30:00"));
        }

        @Test
        @DisplayName("다른 사용자의 기록을 조회하면 403이다")
        void rejectsOtherUsersSolve() throws Exception {
            ProblemSolve othersSolve = saveSolve(othersProblem, other.getId(), PRACTICED_AT);

            mockMvc.perform(get("/api/problem-solves/" + othersSolve.getId())
                            .with(asUser(user.getId())))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.errorCode").value(4022));
        }

        @Test
        @DisplayName("없는 기록이면 404다")
        void rejectsUnknownSolve() throws Exception {

            mockMvc.perform(get("/api/problem-solves/999999")
                            .with(asUser(user.getId())))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.errorCode").value(4021));
        }

        @Test
        @DisplayName("숫자가 아닌 id 는 400이다")
        void rejectsNonNumericId() throws Exception {

            mockMvc.perform(get("/api/problem-solves/not-a-number")
                            .with(asUser(user.getId())))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("인증 없이 요청하면 401이다")
        void requiresAuthentication() throws Exception {
            ProblemSolve solve = saveSolve(problem, user.getId(), PRACTICED_AT);

            mockMvc.perform(get("/api/problem-solves/" + solve.getId()))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("목록·개수 조회")
    class ListAndCount {

        @Test
        @DisplayName("문제별 기록을 최근 순으로 돌려준다")
        void returnsSolvesOfProblem() throws Exception {
            saveSolve(problem, user.getId(), PRACTICED_AT);
            saveSolve(problem, user.getId(), PRACTICED_AT.plusDays(1));

            mockMvc.perform(get("/api/problem-solves/problem/" + problem.getId())
                            .with(asUser(user.getId())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.length()").value(2))
                    .andExpect(jsonPath("$.data[0].practicedAt").value("2026-01-11 09:30:00"));
        }

        @Test
        @DisplayName("다른 사용자의 문제 기록 목록은 403이다")
        void rejectsOtherUsersProblemSolves() throws Exception {
            saveSolve(othersProblem, other.getId(), PRACTICED_AT);

            mockMvc.perform(get("/api/problem-solves/problem/" + othersProblem.getId())
                            .with(asUser(user.getId())))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("사용자 기록 목록에는 자기 기록만 담긴다")
        void returnsOwnSolvesOnly() throws Exception {
            saveSolve(problem, user.getId(), PRACTICED_AT);
            saveSolve(othersProblem, other.getId(), PRACTICED_AT);

            mockMvc.perform(get("/api/problem-solves/user")
                            .with(asUser(user.getId())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.length()").value(1))
                    .andExpect(jsonPath("$.data[0].userId").value(user.getId()));
        }

        @Test
        @DisplayName("문제별 개수와 사용자 전체 개수를 돌려준다")
        void returnsCounts() throws Exception {
            saveSolve(problem, user.getId(), PRACTICED_AT);
            saveSolve(problem, user.getId(), PRACTICED_AT.plusDays(1));
            saveSolve(othersProblem, other.getId(), PRACTICED_AT);

            mockMvc.perform(get("/api/problem-solves/problem/" + problem.getId() + "/count")
                            .with(asUser(user.getId())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data").value(2));
            mockMvc.perform(get("/api/problem-solves/user/count")
                            .with(asUser(user.getId())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data").value(2));
        }

        @Test
        @DisplayName("다른 사용자의 문제 개수 조회는 403이다")
        void rejectsOtherUsersCount() throws Exception {

            mockMvc.perform(get("/api/problem-solves/problem/" + othersProblem.getId() + "/count")
                            .with(asUser(user.getId())))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("인증 없이 목록을 조회하면 401이다")
        void requiresAuthentication() throws Exception {
            mockMvc.perform(get("/api/problem-solves/user"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.errorCode").value(1007));
            mockMvc.perform(get("/api/problem-solves/user/count"))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("PATCH /api/problem-solves")
    class UpdateProblemSolve {

        @Test
        @DisplayName("자기 기록을 수정하면 200이고 값이 바뀐다")
        void updatesOwnSolve() throws Exception {
            ProblemSolve solve = saveSolve(problem, user.getId(), PRACTICED_AT, AnswerStatus.WRONG);

            mockMvc.perform(patch("/api/problem-solves")
                            .contentType(APPLICATION_JSON)
                            .content(json(Map.of(
                                    "problemSolveId", solve.getId(),
                                    "answerStatus", "CORRECT",
                                    "reflection", "이제 알겠다",
                                    "improvements", List.of("NO_REPEAT_MISTAKE"),
                                    "timeSpentSeconds", 30)))
                            .with(asUser(user.getId())))
                    .andExpect(status().isOk());

            mockMvc.perform(get("/api/problem-solves/" + solve.getId())
                            .with(asUser(user.getId())))
                    .andExpect(jsonPath("$.data.answerStatus").value("CORRECT"))
                    .andExpect(jsonPath("$.data.reflection").value("이제 알겠다"))
                    .andExpect(jsonPath("$.data.improvements[0]").value("NO_REPEAT_MISTAKE"));
        }

        @Test
        @DisplayName("다른 사용자의 기록 수정은 403이다")
        void rejectsOtherUsersSolve() throws Exception {
            ProblemSolve othersSolve = saveSolve(othersProblem, other.getId(), PRACTICED_AT, AnswerStatus.WRONG);

            mockMvc.perform(patch("/api/problem-solves")
                            .contentType(APPLICATION_JSON)
                            .content(json(Map.of("problemSolveId", othersSolve.getId(), "answerStatus", "CORRECT")))
                            .with(asUser(user.getId())))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.errorCode").value(4022));
        }

        @Test
        @DisplayName("id 가 빠지면 400이다")
        void rejectsMissingId() throws Exception {

            mockMvc.perform(patch("/api/problem-solves")
                            .contentType(APPLICATION_JSON)
                            .content(json(Map.of("answerStatus", "CORRECT")))
                            .with(asUser(user.getId())))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value(4023));
        }

        @Test
        @DisplayName("인증 없이 수정하면 401이다")
        void requiresAuthentication() throws Exception {
            ProblemSolve solve = saveSolve(problem, user.getId(), PRACTICED_AT, AnswerStatus.WRONG);

            mockMvc.perform(patch("/api/problem-solves")
                            .contentType(APPLICATION_JSON)
                            .content(json(Map.of("problemSolveId", solve.getId(), "answerStatus", "CORRECT"))))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("DELETE /api/problem-solves/{problemSolveId}")
    class DeleteProblemSolve {

        @Test
        @DisplayName("자기 기록을 지우면 200이고 목록에서 사라진다")
        void deletesOwnSolve() throws Exception {
            ProblemSolve solve = saveSolve(problem, user.getId(), PRACTICED_AT);

            mockMvc.perform(delete("/api/problem-solves/" + solve.getId())
                            .with(asUser(user.getId())))
                    .andExpect(status().isOk());

            assertThat(problemSolveRepository.countByUserId(user.getId())).isZero();
        }

        @Test
        @DisplayName("다른 사용자의 기록은 403이고 그대로 남는다")
        void rejectsOtherUsersSolve() throws Exception {
            ProblemSolve othersSolve = saveSolve(othersProblem, other.getId(), PRACTICED_AT);

            mockMvc.perform(delete("/api/problem-solves/" + othersSolve.getId())
                            .with(asUser(user.getId())))
                    .andExpect(status().isForbidden());

            assertThat(problemSolveRepository.countByUserId(other.getId())).isEqualTo(1L);
        }

        @Test
        @DisplayName("없는 기록이면 404다")
        void rejectsUnknownSolve() throws Exception {

            mockMvc.perform(delete("/api/problem-solves/999999")
                            .with(asUser(user.getId())))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("인증 없이 삭제하면 401이고 기록은 남는다")
        void requiresAuthentication() throws Exception {
            ProblemSolve solve = saveSolve(problem, user.getId(), PRACTICED_AT);

            mockMvc.perform(delete("/api/problem-solves/" + solve.getId()))
                    .andExpect(status().isUnauthorized());

            assertThat(problemSolveRepository.countByUserId(user.getId())).isEqualTo(1L);
        }
    }

    @Nested
    @DisplayName("이미지 업로드")
    class UploadImages {

        @Test
        @DisplayName("멀티파트로 올린 이미지가 기록에 붙는다")
        void uploadsImages() throws Exception {
            ProblemSolve solve = saveSolve(problem, user.getId(), PRACTICED_AT);
            given(fileUploadService.uploadFileToS3(any(MultipartFile.class)))
                    .willReturn("https://test-ono-bucket.s3.ap-northeast-2.amazonaws.com/a.png");

            mockMvc.perform(multipart("/api/problem-solves/" + solve.getId() + "/images")
                            .file(new MockMultipartFile("images", "a.png", "image/png", "a".getBytes()))
                            .with(asUser(user.getId())))
                    .andExpect(status().isOk());

            mockMvc.perform(get("/api/problem-solves/" + solve.getId())
                            .with(asUser(user.getId())))
                    .andExpect(jsonPath("$.data.imageUrls.length()").value(1));
        }

        @Test
        @DisplayName("다른 사용자의 기록에는 이미지를 올릴 수 없다")
        void rejectsOtherUsersSolve() throws Exception {
            ProblemSolve othersSolve = saveSolve(othersProblem, other.getId(), PRACTICED_AT);

            mockMvc.perform(multipart("/api/problem-solves/" + othersSolve.getId() + "/images")
                            .file(new MockMultipartFile("images", "a.png", "image/png", "a".getBytes()))
                            .with(asUser(user.getId())))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("presigned URL 목록을 붙이면 200이고 순서대로 저장된다")
        void addsImageUrls() throws Exception {
            ProblemSolve solve = saveSolve(problem, user.getId(), PRACTICED_AT);

            mockMvc.perform(post("/api/problem-solves/" + solve.getId() + "/image-urls")
                            .contentType(APPLICATION_JSON)
                            .content(json(Map.of("imageUrls", List.of(
                                    "https://test-ono-bucket.s3.ap-northeast-2.amazonaws.com/a.png",
                                    "https://test-ono-bucket.s3.ap-northeast-2.amazonaws.com/b.png"))))
                            .with(asUser(user.getId())))
                    .andExpect(status().isOk());

            mockMvc.perform(get("/api/problem-solves/" + solve.getId())
                            .with(asUser(user.getId())))
                    .andExpect(jsonPath("$.data.imageUrls[0]")
                            .value("https://test-ono-bucket.s3.ap-northeast-2.amazonaws.com/a.png"))
                    .andExpect(jsonPath("$.data.imageUrls[1]")
                            .value("https://test-ono-bucket.s3.ap-northeast-2.amazonaws.com/b.png"));
        }

        @Test
        @DisplayName("다른 사용자의 기록에는 URL 도 붙일 수 없다")
        void rejectsAddingUrlsToOtherUsersSolve() throws Exception {
            ProblemSolve othersSolve = saveSolve(othersProblem, other.getId(), PRACTICED_AT);

            mockMvc.perform(post("/api/problem-solves/" + othersSolve.getId() + "/image-urls")
                            .contentType(APPLICATION_JSON)
                            .content(json(Map.of("imageUrls", List.of(
                                    "https://test-ono-bucket.s3.ap-northeast-2.amazonaws.com/a.png"))))
                            .with(asUser(user.getId())))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("인증 없이 이미지 URL 을 붙이면 401이다")
        void requiresAuthentication() throws Exception {
            ProblemSolve solve = saveSolve(problem, user.getId(), PRACTICED_AT);

            mockMvc.perform(post("/api/problem-solves/" + solve.getId() + "/image-urls")
                            .contentType(APPLICATION_JSON)
                            .content(json(Map.of("imageUrls", List.of(
                                    "https://test-ono-bucket.s3.ap-northeast-2.amazonaws.com/a.png")))))
                    .andExpect(status().isUnauthorized());
        }
    }
}
