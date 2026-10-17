package com.aisip.OnO.backend.common.exception;

import com.aisip.OnO.backend.folder.entity.Folder;
import com.aisip.OnO.backend.folder.exception.FolderErrorCase;
import com.aisip.OnO.backend.practicenote.entity.PracticeNote;
import com.aisip.OnO.backend.practicenote.exception.PracticeNoteErrorCase;
import com.aisip.OnO.backend.practicenote.support.PracticeNoteTestSupport;
import com.aisip.OnO.backend.problem.entity.Problem;
import com.aisip.OnO.backend.problem.exception.ProblemErrorCase;
import com.aisip.OnO.backend.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 요청 값 검증이 빠져 400 대신 500 이 나가던 API 들의 회귀 테스트. (#400)
 *
 * <p>잘못된 요청은 공통 에러 응답(errorCode 400)으로 돌려주고, 서버 오류 알림(Discord)은 보내지 않는다.
 * 앱이 지금 보내는 정상 요청과 소유권 검증은 그대로인지도 같이 본다.
 */
@DisplayName("요청 값 검증 (#400)")
class RequestValidationApiTest extends PracticeNoteTestSupport {

    private Long userId;
    private Long otherUserId;
    private Folder root;
    private Folder otherRoot;

    @BeforeEach
    void setUpData() {
        User user = fixtures.createUser();
        User otherUser = fixtures.createOtherUser();
        userId = user.getId();
        otherUserId = otherUser.getId();
        root = fixtures.createRootFolder(userId);
        otherRoot = fixtures.createRootFolder(otherUserId);
        authenticateAs(userId);
    }

    private ResultActions expectBadRequest(MockHttpServletRequestBuilder request) throws Exception {
        ResultActions result = mockMvc.perform(request)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value(400));
        verify(discordWebhookNotificationService, never())
                .sendErrorNotification(anyString(), any(), anyString(), anyString());
        return result;
    }

    private MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder builder, String body) {
        return builder.contentType(MediaType.APPLICATION_JSON).content(body);
    }

    @Nested
    @DisplayName("커서 목록 API 의 size")
    class CursorSize {

        private List<String> cursorUrls() {
            return List.of(
                    "/api/folders/" + root.getId() + "/subfolders/V2",
                    "/api/folders/thumbnails/V2",
                    "/api/problems/folder/" + root.getId() + "/V2",
                    "/api/problems/tag/1/V2",
                    "/api/problems/title/V2?query=a",
                    "/api/practiceNotes/thumbnail/V2"
            );
        }

        @ParameterizedTest
        @ValueSource(strings = {"0", "-1", "101", "2147483647"})
        @DisplayName("범위를 벗어난 size 는 모든 V2 커서 API 에서 400 이다")
        void outOfRangeSizeIsBadRequest(String size) throws Exception {
            for (String url : cursorUrls()) {
                expectBadRequest(get(url).param("size", size));
            }
        }

        @ParameterizedTest
        @ValueSource(strings = {"1", "20", "100"})
        @DisplayName("1 이상 100 이하의 size 는 그대로 통과한다")
        void inRangeSizeIsOk(String size) throws Exception {
            mockMvc.perform(get("/api/problems/folder/{folderId}/V2", root.getId()).param("size", size))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.size").value(Integer.parseInt(size)));
        }

        @Test
        @DisplayName("size 를 빼면 기본값 20 으로 조회된다")
        void defaultSizeIsTwenty() throws Exception {
            mockMvc.perform(get("/api/folders/{folderId}/subfolders/V2", root.getId()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.size").value(20));
        }

        @Test
        @DisplayName("size=1 이면 한 건과 다음 커서를 준다")
        void sizeOneReturnsSingleItemWithCursor() throws Exception {
            saveProblems(userId, root, 2);

            mockMvc.perform(get("/api/problems/folder/{folderId}/V2", root.getId()).param("size", "1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.content.length()").value(1))
                    .andExpect(jsonPath("$.data.hasNext").value(true));
        }

        @Test
        @DisplayName("size 가 정상이어도 남의 폴더는 여전히 403 이다")
        void ownershipIsStillChecked() throws Exception {
            mockMvc.perform(get("/api/problems/folder/{folderId}/V2", otherRoot.getId()).param("size", "20"))
                    .andExpect(status().isForbidden());
            mockMvc.perform(get("/api/folders/{folderId}/subfolders/V2", otherRoot.getId()).param("size", "20"))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.errorCode").value(FolderErrorCase.FOLDER_USER_UNMATCHED.getErrorCode()));
        }
    }

    @Nested
    @DisplayName("PATCH /api/folders")
    class UpdateFolder {

        @Test
        @DisplayName("folderId 가 없으면 400")
        void missingFolderIdIsBadRequest() throws Exception {
            expectBadRequest(json(patch("/api/folders"),
                    "{\"folderName\":\"새 이름\",\"parentFolderId\":" + root.getId() + "}"))
                    .andExpect(jsonPath("$.message").value("folderId 는 필수입니다."));
        }

        @Test
        @DisplayName("앱처럼 folderId 를 보내면 수정된다")
        void updatesOwnFolder() throws Exception {
            Folder child = fixtures.createFolder(userId, "옛 이름", root);

            mockMvc.perform(json(patch("/api/folders"),
                            "{\"folderId\":" + child.getId() + ",\"folderName\":\"새 이름\",\"parentFolderId\":" + root.getId() + "}"))
                    .andExpect(status().isOk());

            assertThat(folderNameOf(child.getId())).isEqualTo("새 이름");
        }

        @Test
        @DisplayName("남의 폴더를 수정하면 403")
        void otherUsersFolderIsForbidden() throws Exception {
            Folder othersChild = fixtures.createFolder(otherUserId, "남의 폴더", otherRoot);

            mockMvc.perform(json(patch("/api/folders"),
                            "{\"folderId\":" + othersChild.getId() + ",\"folderName\":\"바꿈\"}"))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.errorCode").value(FolderErrorCase.FOLDER_USER_UNMATCHED.getErrorCode()));
        }

        @Test
        @DisplayName("같은 DTO 를 쓰는 폴더 생성은 folderId 없이 그대로 된다")
        void createFolderStillWorksWithoutFolderId() throws Exception {
            mockMvc.perform(json(post("/api/folders"),
                            "{\"folderName\":\"새 폴더\",\"parentFolderId\":" + root.getId() + "}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data").isNumber());
        }

        private String folderNameOf(Long folderId) {
            return transactionTemplate.execute(status -> entityManager.find(Folder.class, folderId).getName());
        }
    }

    @Nested
    @DisplayName("DELETE /api/folders")
    class DeleteFolders {

        @Test
        @DisplayName("deleteFolderIdList 가 없으면 400")
        void missingListIsBadRequest() throws Exception {
            expectBadRequest(json(delete("/api/folders"), "{}"));
        }

        @Test
        @DisplayName("목록에 null 이 섞이면 400")
        void nullElementIsBadRequest() throws Exception {
            expectBadRequest(json(delete("/api/folders"), "{\"deleteFolderIdList\":[null]}"));
        }
    }

    @Nested
    @DisplayName("PATCH /api/problems/info, /path")
    class UpdateProblem {

        @Test
        @DisplayName("info 에 problemId 가 없으면 400")
        void infoWithoutProblemIdIsBadRequest() throws Exception {
            expectBadRequest(json(patch("/api/problems/info"), "{\"memo\":\"메모\"}"))
                    .andExpect(jsonPath("$.message").value("problemId 는 필수입니다."));
        }

        @Test
        @DisplayName("path 에 problemId 가 없으면 400")
        void pathWithoutProblemIdIsBadRequest() throws Exception {
            expectBadRequest(json(patch("/api/problems/path"), "{\"folderId\":" + root.getId() + "}"));
        }

        @Test
        @DisplayName("앱처럼 problemId 를 보내면 메모와 폴더가 바뀐다")
        void updatesOwnProblem() throws Exception {
            Problem problem = saveProblem(userId, root);
            Folder target = fixtures.createFolder(userId, "옮길 곳", root);

            mockMvc.perform(json(patch("/api/problems/info"),
                            "{\"problemId\":" + problem.getId() + ",\"memo\":\"바뀐 메모\",\"reference\":\"출처\"}"))
                    .andExpect(status().isOk());
            mockMvc.perform(json(patch("/api/problems/path"),
                            "{\"problemId\":" + problem.getId() + ",\"folderId\":" + target.getId() + "}"))
                    .andExpect(status().isOk());

            transactionTemplate.executeWithoutResult(status -> {
                Problem reloaded = entityManager.find(Problem.class, problem.getId());
                assertThat(reloaded.getMemo()).isEqualTo("바뀐 메모");
                assertThat(reloaded.getFolder().getId()).isEqualTo(target.getId());
            });
        }

        @Test
        @DisplayName("남의 문제를 수정하면 403")
        void otherUsersProblemIsForbidden() throws Exception {
            Problem othersProblem = saveProblem(otherUserId, otherRoot);

            mockMvc.perform(json(patch("/api/problems/info"),
                            "{\"problemId\":" + othersProblem.getId() + ",\"memo\":\"바꿈\"}"))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.errorCode").value(ProblemErrorCase.PROBLEM_USER_UNMATCHED.getErrorCode()));
            mockMvc.perform(json(patch("/api/problems/path"),
                            "{\"problemId\":" + othersProblem.getId() + ",\"folderId\":" + root.getId() + "}"))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("같은 DTO 를 쓰는 문제 등록은 problemId 없이 그대로 된다")
        void registerStillWorksWithoutProblemId() throws Exception {
            mockMvc.perform(json(post("/api/problems"),
                            "{\"memo\":\"메모\",\"reference\":\"출처\",\"folderId\":" + root.getId() + "}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data").isNumber());
        }
    }

    @Nested
    @DisplayName("DELETE /api/problems")
    class DeleteProblems {

        @Test
        @DisplayName("deleteProblemIdList 가 없으면 400")
        void missingListIsBadRequest() throws Exception {
            expectBadRequest(json(delete("/api/problems"), "{}"));
        }

        @Test
        @DisplayName("목록에 null 이 섞이면 400")
        void nullElementIsBadRequest() throws Exception {
            expectBadRequest(json(delete("/api/problems"), "{\"deleteProblemIdList\":[null]}"));
        }

        @Test
        @DisplayName("앱처럼 id 목록을 보내면 지워진다")
        void deletesOwnProblems() throws Exception {
            Problem problem = saveProblem(userId, root);

            mockMvc.perform(json(delete("/api/problems"), "{\"deleteProblemIdList\":[" + problem.getId() + "]}"))
                    .andExpect(status().isOk());

            assertThat(problemRepository.findById(problem.getId())).isEmpty();
        }

        @Test
        @DisplayName("남의 문제를 지우면 403 이고 남아 있다")
        void otherUsersProblemIsForbidden() throws Exception {
            Problem othersProblem = saveProblem(otherUserId, otherRoot);

            mockMvc.perform(json(delete("/api/problems"), "{\"deleteProblemIdList\":[" + othersProblem.getId() + "]}"))
                    .andExpect(status().isForbidden());

            assertThat(problemRepository.findById(othersProblem.getId())).isPresent();
        }
    }

    @Nested
    @DisplayName("PATCH, DELETE /api/practiceNotes")
    class PracticeNotes {

        @Test
        @DisplayName("practiceNoteId 가 없으면 400")
        void missingPracticeNoteIdIsBadRequest() throws Exception {
            expectBadRequest(json(patch("/api/practiceNotes"),
                    "{\"practiceTitle\":\"제목\",\"addProblemIdList\":[],\"removeProblemIdList\":[]}"))
                    .andExpect(jsonPath("$.message").value("practiceNoteId 는 필수입니다."));
        }

        @Test
        @DisplayName("추가 목록에 null 이 섞이면 400")
        void nullElementInAddListIsBadRequest() throws Exception {
            PracticeNote note = savePracticeNote(userId, "복습", List.of());

            expectBadRequest(json(patch("/api/practiceNotes"),
                    "{\"practiceNoteId\":" + note.getId() + ",\"addProblemIdList\":[null],\"removeProblemIdList\":[]}"));
        }

        @Test
        @DisplayName("앱처럼 보내면 수정되고, 두 목록이 없어도 지금처럼 통과한다")
        void updatesOwnPracticeNote() throws Exception {
            Problem problem = saveProblem(userId, root);
            PracticeNote note = savePracticeNote(userId, "복습", List.of());

            mockMvc.perform(json(patch("/api/practiceNotes"),
                            "{\"practiceNoteId\":" + note.getId() + ",\"practiceTitle\":\"새 제목\","
                                    + "\"addProblemIdList\":[" + problem.getId() + "],\"removeProblemIdList\":[]}"))
                    .andExpect(status().isOk());
            mockMvc.perform(json(patch("/api/practiceNotes"),
                            "{\"practiceNoteId\":" + note.getId() + ",\"practiceTitle\":\"또 새 제목\"}"))
                    .andExpect(status().isOk());

            assertThat(practiceNoteRepository.findById(note.getId()).orElseThrow().getTitle()).isEqualTo("또 새 제목");
        }

        @Test
        @DisplayName("남의 복습노트를 수정하면 403")
        void otherUsersPracticeNoteIsForbidden() throws Exception {
            PracticeNote othersNote = savePracticeNote(otherUserId, "남의 복습", List.of());

            mockMvc.perform(json(patch("/api/practiceNotes"),
                            "{\"practiceNoteId\":" + othersNote.getId() + ",\"practiceTitle\":\"바꿈\"}"))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.errorCode").value(PracticeNoteErrorCase.PRACTICE_NOTE_USER_UNMATCHED.getErrorCode()));
        }

        @Test
        @DisplayName("deletePracticeIdList 가 없으면 400")
        void missingDeleteListIsBadRequest() throws Exception {
            expectBadRequest(json(delete("/api/practiceNotes"), "{}"));
        }

        @Test
        @DisplayName("앱처럼 id 목록을 보내면 지워진다")
        void deletesOwnPracticeNotes() throws Exception {
            PracticeNote note = savePracticeNote(userId, "복습", List.of());

            mockMvc.perform(json(delete("/api/practiceNotes"), "{\"deletePracticeIdList\":[" + note.getId() + "]}"))
                    .andExpect(status().isOk());

            assertThat(practiceNoteRepository.findById(note.getId())).isEmpty();
        }
    }

    @Nested
    @DisplayName("깨진 form 본문")
    class MalformedFormBody {

        private static final String BROKEN_FORM = "name=%zz";

        @Test
        @DisplayName("PATCH 의 퍼센트 인코딩이 깨져 있으면 400")
        void malformedFormOnPatchIsBadRequest() throws Exception {
            expectBadRequest(patch("/api/folders")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .content(BROKEN_FORM));
        }

        @Test
        @DisplayName("인증 없는 스캐너 요청(PUT /poc.jsp)도 500 이 아니라 400 이다")
        void malformedFormFromScannerIsBadRequest() throws Exception {
            clearAuthentication();

            expectBadRequest(put("/poc.jsp")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .content(BROKEN_FORM));
        }

        @Test
        @DisplayName("정상 form 본문은 필터를 그대로 지나간다")
        void wellFormedFormPassesThrough() throws Exception {
            clearAuthentication();

            int status = mockMvc.perform(put("/poc.jsp")
                            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                            .content("name=%ED%95%9C"))
                    .andReturn().getResponse().getStatus();

            // 깨진 본문과 달리 필터에서 400 으로 막히지 않고 시큐리티까지 가서 인증 실패로 끝난다.
            assertThat(status).isEqualTo(401);
        }
    }
}
