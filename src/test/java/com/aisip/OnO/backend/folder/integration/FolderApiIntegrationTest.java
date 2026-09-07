package com.aisip.OnO.backend.folder.integration;

import com.aisip.OnO.backend.folder.dto.FolderDeleteRequestDto;
import com.aisip.OnO.backend.folder.dto.FolderRegisterDto;
import com.aisip.OnO.backend.folder.entity.Folder;
import com.aisip.OnO.backend.folder.exception.FolderErrorCase;
import com.aisip.OnO.backend.folder.support.FolderTestSupport;
import com.aisip.OnO.backend.problem.entity.Problem;
import com.aisip.OnO.backend.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("폴더 API")
class FolderApiIntegrationTest extends FolderTestSupport {

    private Long userId;
    private Long otherUserId;
    private FolderTree tree;

    @BeforeEach
    void setUpFolders() {
        User user = fixtures.createUser();
        User otherUser = fixtures.createOtherUser();
        userId = user.getId();
        otherUserId = otherUser.getId();
        tree = createFolderTree(userId);
        authenticateAs(userId);
    }

    @Nested
    @DisplayName("GET /api/folders/root")
    class GetRootFolder {

        @Test
        @DisplayName("루트 폴더와 하위 폴더 썸네일을 돌려준다")
        void returnsRootFolder() throws Exception {
            saveProblems(userId, tree.root(), 2);
            saveProblems(userId, tree.notebookA(), 1);

            mockMvc.perform(get("/api/folders/root"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.folderId").value(tree.root().getId()))
                    .andExpect(jsonPath("$.data.folderName").value(tree.root().getName()))
                    .andExpect(jsonPath("$.data.parentFolder").isEmpty())
                    .andExpect(jsonPath("$.data.subFolderList.length()").value(2))
                    .andExpect(jsonPath("$.data.problemIdList.length()").value(2))
                    .andExpect(jsonPath("$.data.subFolderList[?(@.folderId == " + tree.notebookA().getId() + ")].problemCount")
                            .value(1));
        }

        @Test
        @DisplayName("루트 폴더가 없으면 404 와 폴더 없음 코드를 준다")
        void returns404WhenRootFolderMissing() throws Exception {
            authenticateAs(otherUserId);

            mockMvc.perform(get("/api/folders/root"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.errorCode").value(FolderErrorCase.FOLDER_NOT_FOUND.getErrorCode()));
        }

        @Test
        @DisplayName("인증 없이 요청하면 401")
        void returns401WithoutAuthentication() throws Exception {
            clearAuthentication();

            mockMvc.perform(get("/api/folders/root"))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("GET /api/folders/{folderId}")
    class GetFolder {

        @Test
        @DisplayName("본인 폴더는 부모·하위 폴더·문제 목록과 함께 조회된다")
        void returnsOwnFolder() throws Exception {
            List<Problem> problems = saveProblems(userId, tree.notebookA(), 2);

            mockMvc.perform(get("/api/folders/{folderId}", tree.notebookA().getId()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.folderId").value(tree.notebookA().getId()))
                    .andExpect(jsonPath("$.data.parentFolder.folderId").value(tree.root().getId()))
                    .andExpect(jsonPath("$.data.subFolderList.length()").value(2))
                    .andExpect(jsonPath("$.data.problemIdList.length()").value(problems.size()));
        }

        @Test
        @DisplayName("다른 사용자의 폴더를 조회하면 403")
        void returns403ForOtherUserFolder() throws Exception {
            authenticateAs(otherUserId);

            mockMvc.perform(get("/api/folders/{folderId}", tree.notebookA().getId()))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.errorCode").value(FolderErrorCase.FOLDER_USER_UNMATCHED.getErrorCode()));
        }

        @Test
        @DisplayName("존재하지 않는 폴더를 조회하면 404")
        void returns404ForMissingFolder() throws Exception {
            mockMvc.perform(get("/api/folders/{folderId}", nonExistentFolderId()))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.errorCode").value(FolderErrorCase.FOLDER_NOT_FOUND.getErrorCode()));
        }

        @Test
        @DisplayName("인증 없이 요청하면 401")
        void returns401WithoutAuthentication() throws Exception {
            clearAuthentication();

            mockMvc.perform(get("/api/folders/{folderId}", tree.notebookA().getId()))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("폴더 목록 조회")
    class GetFolderList {

        @Test
        @DisplayName("썸네일 목록에는 본인 폴더만 담긴다")
        void thumbnailsContainOnlyOwnFolders() throws Exception {
            Folder otherUserFolder = fixtures.createRootFolder(otherUserId);

            mockMvc.perform(get("/api/folders/thumbnails"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.length()").value(tree.all().size()))
                    .andExpect(jsonPath("$.data[?(@.folderId == " + otherUserFolder.getId() + ")]").isEmpty());
        }

        @Test
        @DisplayName("상세 목록은 부모·하위 폴더 정보를 함께 준다")
        void detailsContainParentAndSubFolders() throws Exception {
            mockMvc.perform(get("/api/folders"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.length()").value(tree.all().size()))
                    .andExpect(jsonPath("$.data[0].folderId").value(tree.root().getId()))
                    .andExpect(jsonPath("$.data[0].parentFolder").isEmpty())
                    .andExpect(jsonPath("$.data[0].subFolderList.length()").value(2));
        }

        @Test
        @DisplayName("커서 기반 썸네일 조회는 다음 커서를 함께 준다")
        void thumbnailsWithCursor() throws Exception {
            ResultActions firstPage = mockMvc.perform(get("/api/folders/thumbnails/V2")
                            .param("size", "4"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.content.length()").value(4))
                    .andExpect(jsonPath("$.data.hasNext").value(true));

            Number nextCursor = (Number) com.jayway.jsonpath.JsonPath.read(
                    firstPage.andReturn().getResponse().getContentAsString(), "$.data.nextCursor");

            mockMvc.perform(get("/api/folders/thumbnails/V2")
                            .param("cursor", String.valueOf(nextCursor.longValue()))
                            .param("size", "4"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.content.length()").value(2))
                    .andExpect(jsonPath("$.data.hasNext").value(false))
                    .andExpect(jsonPath("$.data.nextCursor").isEmpty());
        }

        @Test
        @DisplayName("커서 기반 하위 폴더 조회는 소유자가 아니면 403")
        void subFoldersWithCursorRejectsOtherUser() throws Exception {
            authenticateAs(otherUserId);

            mockMvc.perform(get("/api/folders/{folderId}/subfolders/V2", tree.notebookA().getId()))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.errorCode").value(FolderErrorCase.FOLDER_USER_UNMATCHED.getErrorCode()));
        }

        @Test
        @DisplayName("커서 기반 하위 폴더 조회는 하위 폴더만 담는다")
        void subFoldersWithCursor() throws Exception {
            mockMvc.perform(get("/api/folders/{folderId}/subfolders/V2", tree.notebookA().getId())
                            .param("size", "10"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.content.length()").value(2))
                    .andExpect(jsonPath("$.data.hasNext").value(false));
        }

        @Test
        @DisplayName("인증 없이 목록을 요청하면 401")
        void returns401WithoutAuthentication() throws Exception {
            clearAuthentication();

            mockMvc.perform(get("/api/folders/thumbnails")).andExpect(status().isUnauthorized());
            mockMvc.perform(get("/api/folders")).andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("POST /api/folders")
    class CreateFolder {

        @Test
        @DisplayName("폴더를 만들고 생성된 id 를 돌려준다")
        void createsFolder() throws Exception {
            String body = objectMapper.writeValueAsString(
                    new FolderRegisterDto("새 공책", null, tree.root().getId()));

            String response = mockMvc.perform(post("/api/folders")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data").isNumber())
                    .andReturn().getResponse().getContentAsString();

            Number createdId = com.jayway.jsonpath.JsonPath.read(response, "$.data");
            Folder created = folderRepository.findById(createdId.longValue()).orElseThrow();
            assertThat(created.getName()).isEqualTo("새 공책");
            assertThat(created.getUserId()).isEqualTo(userId);
        }

        @Test
        @DisplayName("다른 사용자의 폴더 아래에는 만들 수 없다")
        void rejectsOtherUserParentFolder() throws Exception {
            Folder otherUserFolder = fixtures.createRootFolder(otherUserId);
            String body = objectMapper.writeValueAsString(
                    new FolderRegisterDto("침입", null, otherUserFolder.getId()));

            mockMvc.perform(post("/api/folders")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.errorCode").value(FolderErrorCase.FOLDER_USER_UNMATCHED.getErrorCode()));

            assertThat(folderRepository.findAllByUserId(otherUserId)).hasSize(1);
        }

        @Test
        @DisplayName("부모 폴더 id 가 없으면 404 로 응답한다")
        void rejectsMissingParentFolderId() throws Exception {
            String body = objectMapper.writeValueAsString(new FolderRegisterDto("부모 없음", null, null));

            mockMvc.perform(post("/api/folders")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.errorCode").value(FolderErrorCase.FOLDER_NOT_FOUND.getErrorCode()));
        }

        @Test
        @DisplayName("인증 없이 요청하면 401")
        void returns401WithoutAuthentication() throws Exception {
            clearAuthentication();
            String body = objectMapper.writeValueAsString(
                    new FolderRegisterDto("새 공책", null, tree.root().getId()));

            mockMvc.perform(post("/api/folders")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("PATCH /api/folders")
    class UpdateFolder {

        @Test
        @DisplayName("폴더 이름을 바꾼다")
        void updatesFolderName() throws Exception {
            String body = objectMapper.writeValueAsString(
                    new FolderRegisterDto("바뀐 이름", tree.notebookA().getId(), null));

            mockMvc.perform(patch("/api/folders")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isOk());

            assertThat(folderRepository.findById(tree.notebookA().getId()).orElseThrow().getName())
                    .isEqualTo("바뀐 이름");
        }

        @Test
        @DisplayName("루트 폴더 수정 요청은 400 으로 막는다")
        void rejectsRootFolderUpdate() throws Exception {
            String body = objectMapper.writeValueAsString(
                    new FolderRegisterDto("루트 이름", tree.root().getId(), null));

            mockMvc.perform(patch("/api/folders")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value(FolderErrorCase.ROOT_FOLDER_CANNOT_UPDATE.getErrorCode()));
        }

        @Test
        @DisplayName("폴더를 다른 폴더 아래로 옮긴다")
        void movesFolder() throws Exception {
            String body = objectMapper.writeValueAsString(
                    new FolderRegisterDto(null, tree.leafA1().getId(), tree.notebookB().getId()));

            mockMvc.perform(patch("/api/folders")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isOk());

            assertThat(folderRepository.findFolderWithDetailsByFolderId(tree.leafA1().getId()).orElseThrow()
                    .getParentFolder().getId())
                    .isEqualTo(tree.notebookB().getId());
        }

        @Test
        @DisplayName("자기 자신을 부모로 지정하면 400")
        void rejectsSelfParent() throws Exception {
            Long folderId = tree.notebookA().getId();
            String body = objectMapper.writeValueAsString(new FolderRegisterDto(null, folderId, folderId));

            mockMvc.perform(patch("/api/folders")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value(FolderErrorCase.INVALID_PARENT_FOLDER.getErrorCode()));
        }

        @Test
        @DisplayName("다른 사용자의 폴더는 수정할 수 없다")
        void rejectsOtherUserFolder() throws Exception {
            authenticateAs(otherUserId);
            String body = objectMapper.writeValueAsString(
                    new FolderRegisterDto("남의 폴더", tree.notebookA().getId(), null));

            mockMvc.perform(patch("/api/folders")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.errorCode").value(FolderErrorCase.FOLDER_USER_UNMATCHED.getErrorCode()));

            assertThat(folderRepository.findById(tree.notebookA().getId()).orElseThrow().getName())
                    .as("이름이 바뀌지 않아야 한다")
                    .isEqualTo("공책 A");
        }

        @Test
        @DisplayName("인증 없이 요청하면 401")
        void returns401WithoutAuthentication() throws Exception {
            clearAuthentication();
            String body = objectMapper.writeValueAsString(
                    new FolderRegisterDto("바뀐 이름", tree.notebookA().getId(), null));

            mockMvc.perform(patch("/api/folders")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("DELETE /api/folders")
    class DeleteFolders {

        @Test
        @DisplayName("폴더 하나를 지우면 하위 폴더까지 사라진다")
        void deletesSingleFolderWithSubTree() throws Exception {
            saveProblems(userId, tree.leafA1(), 2);
            String body = objectMapper.writeValueAsString(
                    new FolderDeleteRequestDto(List.of(tree.notebookA().getId())));

            mockMvc.perform(delete("/api/folders")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isOk());

            assertThat(folderRepository.findAllByUserId(userId))
                    .extracting(Folder::getId)
                    .containsExactlyInAnyOrder(tree.root().getId(), tree.notebookB().getId(), tree.leafB1().getId());
            assertThat(problemRepository.findAllByUserId(userId))
                    .as("폴더와 함께 문제도 지워진다")
                    .isEmpty();
        }

        @Test
        @DisplayName("최상위 폴더를 모두 지우면 루트만 남는다")
        void deletesAllTopLevelFolders() throws Exception {
            String body = objectMapper.writeValueAsString(new FolderDeleteRequestDto(
                    List.of(tree.notebookA().getId(), tree.notebookB().getId())));

            mockMvc.perform(delete("/api/folders")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isOk());

            assertThat(folderRepository.findAllByUserId(userId))
                    .extracting(Folder::getId)
                    .containsExactly(tree.root().getId());
        }

        @Test
        @DisplayName("루트 폴더 삭제 요청은 400 으로 막는다")
        void rejectsRootFolderDelete() throws Exception {
            String body = objectMapper.writeValueAsString(
                    new FolderDeleteRequestDto(List.of(tree.root().getId())));

            mockMvc.perform(delete("/api/folders")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value(FolderErrorCase.ROOT_FOLDER_CANNOT_REMOVE.getErrorCode()));

            assertThat(folderRepository.findAllByUserId(userId)).hasSize(6);
        }

        @Test
        @DisplayName("존재하지 않는 폴더 삭제 요청은 404")
        void rejectsMissingFolder() throws Exception {
            String body = objectMapper.writeValueAsString(
                    new FolderDeleteRequestDto(List.of(nonExistentFolderId())));

            mockMvc.perform(delete("/api/folders")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.errorCode").value(FolderErrorCase.FOLDER_NOT_FOUND.getErrorCode()));
        }

        @Test
        @DisplayName("다른 사용자의 폴더는 삭제할 수 없다")
        void rejectsOtherUserFolder() throws Exception {
            authenticateAs(otherUserId);
            String body = objectMapper.writeValueAsString(
                    new FolderDeleteRequestDto(List.of(tree.notebookA().getId())));

            mockMvc.perform(delete("/api/folders")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.errorCode").value(FolderErrorCase.FOLDER_USER_UNMATCHED.getErrorCode()));

            assertThat(folderRepository.findAllByUserId(userId)).hasSize(6);
        }

        @Test
        @DisplayName("전체 삭제는 본인 폴더만 지운다")
        void deletesAllUserFolders() throws Exception {
            Folder otherUserFolder = fixtures.createRootFolder(otherUserId);

            mockMvc.perform(delete("/api/folders/all"))
                    .andExpect(status().isOk());

            assertThat(folderRepository.findAllByUserId(userId)).isEmpty();
            assertThat(folderRepository.findAllByUserId(otherUserId))
                    .extracting(Folder::getId)
                    .containsExactly(otherUserFolder.getId());
        }

        @Test
        @DisplayName("인증 없이 요청하면 401")
        void returns401WithoutAuthentication() throws Exception {
            clearAuthentication();
            String body = objectMapper.writeValueAsString(
                    new FolderDeleteRequestDto(List.of(tree.notebookA().getId())));

            mockMvc.perform(delete("/api/folders")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isUnauthorized());
            mockMvc.perform(delete("/api/folders/all"))
                    .andExpect(status().isUnauthorized());
        }
    }
}
