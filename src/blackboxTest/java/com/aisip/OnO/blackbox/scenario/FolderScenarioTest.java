package com.aisip.OnO.blackbox.scenario;

import com.aisip.OnO.blackbox.support.Api;
import com.aisip.OnO.blackbox.support.BlackboxTest;
import com.aisip.OnO.blackbox.support.ErrorCodes;
import com.aisip.OnO.blackbox.support.TestUser;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 폴더 트리 만들기, 이동, 이름 바꾸기, 삭제와 다른 사용자 차단.
 */
class FolderScenarioTest extends BlackboxTest {

    @Test
    void 하위_폴더를_만들고_이름을_바꾸고_조회하면_트리에_반영된다() {
        TestUser user = TestUser.guest();
        long root = Fixtures.rootFolderId(user);

        long math = Fixtures.createFolder(user, "수학", root);
        long algebra = Fixtures.createFolder(user, "대수", math);
        user.api().patch("/api/folders", Map.of("folderId", algebra, "folderName", "대수학")).ok();

        JsonNode mathFolder = user.api().get("/api/folders/" + math).ok();
        assertThat(mathFolder.path("parentFolder").path("folderId").asLong()).isEqualTo(root);
        assertThat(names(mathFolder.path("subFolderList"))).containsExactly("대수학");

        JsonNode subFolders = user.api().get("/api/folders/" + math + "/subfolders/V2").ok();
        assertThat(names(subFolders.path("content"))).containsExactly("대수학");

        List<String> all = names(user.api().get("/api/folders").ok());
        assertThat(all).contains("수학", "대수학");
        assertThat(names(user.api().get("/api/folders/thumbnails").ok())).contains("수학", "대수학");
    }

    @Test
    void 폴더_썸네일_커서_페이지를_끝까지_넘기면_모든_폴더가_한_번씩_나온다() {
        TestUser user = TestUser.guest();
        long root = Fixtures.rootFolderId(user);
        List<Long> created = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            created.add(Fixtures.createFolder(user, "폴더" + i, root));
        }

        List<Long> seen = new ArrayList<>();
        Long cursor = null;
        for (int page = 0; page < 10; page++) {
            var query = new java.util.HashMap<String, Object>();
            query.put("size", 2);
            if (cursor != null) {
                query.put("cursor", cursor);
            }
            JsonNode body = user.api().get("/api/folders/thumbnails/V2", query).ok();
            body.path("content").forEach(f -> seen.add(f.path("folderId").asLong()));
            if (!body.path("hasNext").asBoolean()) {
                break;
            }
            cursor = body.path("nextCursor").asLong();
        }

        assertThat(seen).doesNotHaveDuplicates();
        assertThat(seen).containsAll(created);
    }

    @Test
    void 폴더를_지우면_안의_오답노트도_같이_사라진다() {
        TestUser user = TestUser.guest();
        long folder = Fixtures.createFolder(user, "지울 폴더", Fixtures.rootFolderId(user));
        long problem = Fixtures.registerProblem(user, "폴더 안 문제", folder, List.of(), List.of());

        user.api().delete("/api/folders", Map.of("deleteFolderIdList", List.of(folder))).ok();

        user.api().get("/api/folders/" + folder).expectError(404, ErrorCodes.FOLDER_NOT_FOUND);
        user.api().get("/api/problems/" + problem).expectError(404, ErrorCodes.PROBLEM_NOT_FOUND);
    }

    @Test
    void 루트_폴더는_지우거나_이름을_바꿀_수_없다() {
        TestUser user = TestUser.guest();
        long root = Fixtures.rootFolderId(user);

        user.api().delete("/api/folders", Map.of("deleteFolderIdList", List.of(root)))
                .expectError(400, ErrorCodes.ROOT_FOLDER_CANNOT_REMOVE);
        user.api().patch("/api/folders", Map.of("folderId", root, "folderName", "루트 이름"))
                .expectError(400, ErrorCodes.ROOT_FOLDER_CANNOT_UPDATE);
    }

    @Test
    void 폴더를_자기_하위_폴더_아래로_옮길_수_없다() {
        TestUser user = TestUser.guest();
        long parent = Fixtures.createFolder(user, "부모", Fixtures.rootFolderId(user));
        long child = Fixtures.createFolder(user, "자식", parent);

        user.api().patch("/api/folders", Map.of("folderId", parent, "folderName", "부모", "parentFolderId", child))
                .expectError(400, ErrorCodes.INVALID_PARENT_FOLDER);
    }

    /**
     * 현재 동작을 고정한다. 전체 삭제는 루트 폴더까지 지워서 그 뒤로 루트 조회가 404 가 되고 루트에 문제를 넣을 수 없다.
     * 앱(OnO_FRONT FolderService.deleteUserFolders)은 이 API 를 정의만 하고 부르지 않는다. 앱이 쓰기 시작하면 다시 본다.
     */
    @Test
    void 폴더_전체_삭제는_루트까지_지워서_루트_조회가_404_가_된다() {
        TestUser user = TestUser.guest();
        long folder = Fixtures.createFolder(user, "임시", Fixtures.rootFolderId(user));
        long problem = Fixtures.registerProblem(user, "폴더 안 문제", folder, List.of(), List.of());

        user.api().delete("/api/folders/all").ok();

        user.api().get("/api/folders/root").expectError(404, ErrorCodes.FOLDER_NOT_FOUND);
        user.api().get("/api/problems/" + problem).expectError(404, ErrorCodes.PROBLEM_NOT_FOUND);
    }

    @Test
    void 남의_폴더는_조회_수정_삭제할_수_없고_그_아래에_만들_수도_없다() {
        TestUser owner = TestUser.guest();
        TestUser other = TestUser.guest();
        long folder = Fixtures.createFolder(owner, "주인 폴더", Fixtures.rootFolderId(owner));

        other.api().get("/api/folders/" + folder).expectDenied();
        other.api().get("/api/folders/" + folder + "/subfolders/V2").expectDenied();
        other.api().patch("/api/folders", Map.of("folderId", folder, "folderName", "탈취")).expectDenied();
        other.api().delete("/api/folders", Map.of("deleteFolderIdList", List.of(folder))).expectDenied();
        other.api().post("/api/folders", Map.of("folderName", "침입", "parentFolderId", folder)).expectDenied();

        assertThat(owner.api().get("/api/folders/" + folder).ok().path("folderName").asText()).isEqualTo("주인 폴더");
    }

    @Test
    void 잘못된_요청은_400() {
        TestUser user = TestUser.guest();

        user.api().delete("/api/folders", Map.of()).expectStatus(400);
        user.api().patch("/api/folders", Map.of("folderName", "id 없음")).expectStatus(400);
        user.api().get("/api/folders/thumbnails/V2", Map.of("size", 0)).expectStatus(400);
        user.api().get("/api/folders/abc").expectStatus(400);
    }

    @Test
    void 폴더_API_는_토큰이_없으면_401() {
        Api.anonymous().get("/api/folders").expectStatus(401);
        Api.anonymous().post("/api/folders", Map.of("folderName", "x")).expectStatus(401);
    }

    private static List<String> names(JsonNode folders) {
        List<String> names = new ArrayList<>();
        folders.forEach(f -> names.add(f.path("folderName").asText()));
        return names;
    }
}
