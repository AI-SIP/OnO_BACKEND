package com.aisip.OnO.blackbox.scenario;

import com.aisip.OnO.blackbox.support.Api;
import com.aisip.OnO.blackbox.support.BlackboxStack;
import com.aisip.OnO.blackbox.support.BlackboxTest;
import com.aisip.OnO.blackbox.support.ErrorCodes;
import com.aisip.OnO.blackbox.support.Images;
import com.aisip.OnO.blackbox.support.TestUser;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 오답노트 등록(v1, v2, 배치), 조회와 검색, 수정, 이미지, 삭제, 다른 사용자 차단.
 */
class ProblemScenarioTest extends BlackboxTest {

    @Test
    void 이미지를_올려_오답노트를_등록하면_조회에_이미지와_폴더와_태그가_보인다() {
        TestUser user = TestUser.guest();
        long folder = Fixtures.createFolder(user, "영어", Fixtures.rootFolderId(user));
        long tag = Fixtures.createTag(user, "빈칸추론");
        String image = Images.uploadViaPresignedUrl(user);

        long problemId = Fixtures.registerProblem(user, "관계대명사 헷갈림", folder, List.of(image), List.of(tag));

        JsonNode problem = user.api().get("/api/problems/" + problemId).ok();
        assertThat(problem.path("memo").asText()).isEqualTo("관계대명사 헷갈림");
        assertThat(problem.path("folderId").asLong()).isEqualTo(folder);
        assertThat(problem.path("imageUrlList").get(0).path("imageUrl").asText()).isEqualTo(image);
        assertThat(problem.path("imageUrlList").get(0).path("problemImageType").asText()).isEqualTo("PROBLEM_IMAGE");
        assertThat(problem.path("tags").get(0).path("name").asText()).isEqualTo("빈칸추론");

        assertThat(user.api().get("/api/problems/problemCount").ok().asLong()).isEqualTo(1);
        assertThat(ids(user.api().get("/api/problems/user").ok())).containsExactly(problemId);
        assertThat(ids(user.api().get("/api/problems/folder/" + folder).ok())).containsExactly(problemId);
        assertThat(ids(user.api().get("/api/problems/folder/" + folder + "/V2").ok().path("content")))
                .containsExactly(problemId);
        assertThat(ids(user.api().get("/api/problems/tag/" + tag + "/V2").ok().path("content")))
                .containsExactly(problemId);
    }

    @Test
    void v1_로_등록한_문제도_같은_방식으로_조회된다() {
        TestUser user = TestUser.guest();
        Map<String, Object> body = new HashMap<>();
        body.put("memo", "옛 앱 등록");
        body.put("folderId", Fixtures.rootFolderId(user));
        body.put("solvedAt", Fixtures.now().toString());

        long problemId = user.api().post("/api/problems", body).success().asLong();

        assertThat(user.api().get("/api/problems/" + problemId).ok().path("memo").asText()).isEqualTo("옛 앱 등록");
    }

    @Test
    void 배치로_여러_문제를_한_번에_등록한다() {
        TestUser user = TestUser.guest();
        long root = Fixtures.rootFolderId(user);

        JsonNode ids = user.api(Fixtures.CURRENT_APP_VERSION).post("/api/problems/v2/batch", Map.of("problems", List.of(
                Fixtures.problemBody("첫째", root, List.of(), List.of()),
                Fixtures.problemBody("둘째", root, List.of(), List.of())))).success();

        assertThat(ids).hasSize(2);
        assertThat(user.api().get("/api/problems/problemCount").ok().asLong()).isEqualTo(2);
    }

    @Test
    void 출처_제목으로_검색하면_내_문제만_나온다() {
        // 앱의 "제목"은 reference(문제집, 출처) 필드다(ProblemRepositoryImpl.findProblemsByTitleWithCursor).
        TestUser me = TestUser.guest();
        TestUser other = TestUser.guest();
        long mine = registerWithReference(me, "쎈 수학 근의공식 단원");
        Fixtures.registerProblem(me, "검색에 안 걸릴 문제");
        registerWithReference(other, "쎈 수학 근의공식 단원");

        JsonNode result = me.api().get("/api/problems/title/V2", Map.of("query", "근의공식")).ok();

        assertThat(ids(result.path("content"))).containsExactly(mine);
    }

    private static long registerWithReference(TestUser user, String reference) {
        Map<String, Object> body = Fixtures.problemBody("메모", Fixtures.rootFolderId(user), List.of(), List.of());
        body.put("reference", reference);
        return user.api(Fixtures.CURRENT_APP_VERSION).post("/api/problems/v2", body).success().asLong();
    }

    @Test
    void 메모와_폴더와_태그를_바꾸면_조회에_반영된다() {
        TestUser user = TestUser.guest();
        long root = Fixtures.rootFolderId(user);
        long target = Fixtures.createFolder(user, "옮길 곳", root);
        long tagA = Fixtures.createTag(user, "A");
        long tagB = Fixtures.createTag(user, "B");
        long problemId = Fixtures.registerProblem(user, "처음 메모", root, List.of(), List.of(tagA));

        user.api().patch("/api/problems/info", Map.of("problemId", problemId, "memo", "바뀐 메모")).ok();
        user.api().patch("/api/problems/path", Map.of("problemId", problemId, "folderId", target)).ok();
        user.api().patch("/api/problems/" + problemId + "/tags",
                Map.of("addTagIds", List.of(tagB), "removeTagIds", List.of(tagA))).ok();

        JsonNode problem = user.api().get("/api/problems/" + problemId).ok();
        assertThat(problem.path("memo").asText()).isEqualTo("바뀐 메모");
        assertThat(problem.path("folderId").asLong()).isEqualTo(target);
        List<Long> tagIds = new ArrayList<>();
        problem.path("tags").forEach(t -> tagIds.add(t.path("tagId").asLong()));
        assertThat(tagIds).containsExactly(tagB);
    }

    @Test
    void 이미지를_추가하고_하나씩_지우면_S3_파일도_따라_지워진다() {
        // 앱의 문제 수정 화면은 PATCH /imageData 로 새 이미지를 덧붙이고, 뺄 이미지는 DELETE /imageData 로 따로 지운다.
        TestUser user = TestUser.guest();
        long problemId = Fixtures.registerProblem(user, "이미지 바꾸기");
        String first = Images.uploadViaPresignedUrl(user);
        String second = Images.uploadViaPresignedUrl(user);

        user.api().post("/api/problems/" + problemId + "/imageData/urls", Map.of("imageDataList", List.of(
                Map.of("imageUrl", first, "problemImageType", "ANSWER_IMAGE")))).success();
        assertThat(imageUrls(user, problemId)).containsExactly(first);

        user.api().patch("/api/problems/imageData", Map.of("problemId", problemId, "imageDataDtoList", List.of(
                Map.of("imageUrl", second, "problemImageType", "PROBLEM_IMAGE")))).success();
        assertThat(imageUrls(user, problemId)).containsExactlyInAnyOrder(first, second);

        user.api().delete("/api/problems/imageData?imageUrl=" + encode(first)).ok();
        assertThat(imageUrls(user, problemId)).containsExactly(second);
        eventually(Duration.ofSeconds(15), () ->
                assertThat(BlackboxStack.get().objectExists(Images.objectKey(first))).isFalse());
        assertThat(BlackboxStack.get().objectExists(Images.objectKey(second))).isTrue();
    }

    @Test
    void 옛_앱처럼_멀티파트로_문제_이미지를_올리면_붙고_분석이_시작된다() {
        TestUser user = TestUser.guest();
        long problemId = Fixtures.registerProblem(user, "멀티파트 이미지");

        user.api().multipart("POST", "/api/problems/" + problemId + "/imageData",
                Map.of("problemImageTypes", "PROBLEM_IMAGE"), "problemImages", "q.png", "image/png", Images.PNG).ok();

        JsonNode problem = user.api().get("/api/problems/" + problemId).ok();
        assertThat(problem.path("imageUrlList")).hasSize(1);
        String key = Images.objectKey(problem.path("imageUrlList").get(0).path("imageUrl").asText());
        assertThat(BlackboxStack.get().objectExists(key)).isTrue();
        eventually(Duration.ofSeconds(60), () -> assertThat(
                user.api().get("/api/problems/" + problemId + "/analysis").ok().path("status").asText())
                .isEqualTo("COMPLETED"));
    }

    @Test
    void 이미지_없이_저장한_문제는_분석_상태가_NO_IMAGE_가_된다() {
        TestUser user = TestUser.guest();
        long problemId = Fixtures.registerProblem(user, "이미지 없음");

        user.api().patch("/api/problems/" + problemId + "/no-image").ok();

        assertThat(user.api().get("/api/problems/" + problemId + "/analysis").ok().path("status").asText())
                .isEqualTo("NO_IMAGE");
        TestUser.guest().api().patch("/api/problems/" + problemId + "/no-image").expectDenied();
    }

    @Test
    void 지운_문제는_404_이고_이미지_파일도_지워진다() {
        TestUser user = TestUser.guest();
        String image = Images.uploadViaPresignedUrl(user);
        long problemId = Fixtures.registerProblem(user, "지울 문제", Fixtures.rootFolderId(user), List.of(image), List.of());

        user.api().delete("/api/problems", Map.of("deleteProblemIdList", List.of(problemId))).ok();

        user.api().get("/api/problems/" + problemId).expectError(404, ErrorCodes.PROBLEM_NOT_FOUND);
        eventually(Duration.ofSeconds(15), () ->
                assertThat(BlackboxStack.get().objectExists(Images.objectKey(image))).isFalse());
    }

    @Test
    void 전체_삭제하면_문제_수가_0_이_된다() {
        TestUser user = TestUser.guest();
        Fixtures.registerProblem(user, "하나");
        Fixtures.registerProblem(user, "둘");

        user.api().delete("/api/problems/all").ok();

        assertThat(user.api().get("/api/problems/problemCount").ok().asLong()).isZero();
    }

    @Test
    void 복습할_문제_조회는_빈_사용자에게도_올바른_모양으로_온다() {
        TestUser user = TestUser.guest();

        JsonNode due = user.api().get("/api/problems/review-due").ok();

        assertThat(due.path("dueCount").asLong()).isZero();
        assertThat(due.path("problems").isArray()).isTrue();
    }

    @Test
    void 남의_문제는_조회_수정_이동_삭제할_수_없다() {
        TestUser owner = TestUser.guest();
        TestUser other = TestUser.guest();
        long problemId = Fixtures.registerProblem(owner, "주인 문제");
        long otherFolder = Fixtures.rootFolderId(other);

        other.api().get("/api/problems/" + problemId).expectDenied();
        other.api().patch("/api/problems/info", Map.of("problemId", problemId, "memo", "탈취")).expectDenied();
        other.api().patch("/api/problems/path", Map.of("problemId", problemId, "folderId", otherFolder)).expectDenied();
        other.api().patch("/api/problems/" + problemId + "/tags", Map.of("addTagIds", List.of())).expectDenied();
        other.api().delete("/api/problems", Map.of("deleteProblemIdList", List.of(problemId))).expectDenied();
        other.api().get("/api/problems/" + problemId + "/analysis").expectDenied();

        JsonNode problem = owner.api().get("/api/problems/" + problemId).ok();
        assertThat(problem.path("memo").asText()).isEqualTo("주인 문제");
    }

    @Test
    void 남의_폴더에_문제를_등록하거나_옮길_수_없다() {
        TestUser owner = TestUser.guest();
        TestUser other = TestUser.guest();
        long ownersRoot = Fixtures.rootFolderId(owner);
        long mine = Fixtures.registerProblem(other, "내 문제");

        other.api(Fixtures.CURRENT_APP_VERSION)
                .post("/api/problems/v2", Fixtures.problemBody("침입", ownersRoot, List.of(), List.of()))
                .expectDenied();
        other.api().patch("/api/problems/path", Map.of("problemId", mine, "folderId", ownersRoot)).expectDenied();
        other.api().get("/api/problems/folder/" + ownersRoot + "/V2").expectDenied();
    }

    @Test
    void 남의_태그를_내_문제에_붙일_수_없다() {
        TestUser owner = TestUser.guest();
        TestUser other = TestUser.guest();
        long ownersTag = Fixtures.createTag(owner, "주인 태그");

        other.api(Fixtures.CURRENT_APP_VERSION)
                .post("/api/problems/v2", Fixtures.problemBody("남의 태그", Fixtures.rootFolderId(other), List.of(), List.of(ownersTag)))
                .expectClientError();
        long mine = Fixtures.registerProblem(other, "내 문제");
        other.api().patch("/api/problems/" + mine + "/tags", Map.of("addTagIds", List.of(ownersTag)))
                .expectClientError();
        other.api().get("/api/problems/tag/" + ownersTag + "/V2").expectDenied();
    }

    @Test
    void 남이_올린_이미지_URL_로_문제를_등록할_수_없다() {
        TestUser owner = TestUser.guest();
        TestUser other = TestUser.guest();
        String ownersImage = Images.uploadViaPresignedUrl(owner);

        other.api(Fixtures.CURRENT_APP_VERSION)
                .post("/api/problems/v2", Fixtures.problemBody("남의 이미지", Fixtures.rootFolderId(other), List.of(ownersImage), List.of()))
                .expectError(403, ErrorCodes.IMAGE_USER_UNMATCHED);
        other.api().delete("/api/problems/imageData?imageUrl=" + encode(ownersImage))
                .expectClientError();
        assertThat(BlackboxStack.get().objectExists(Images.objectKey(ownersImage))).isTrue();
    }

    @Test
    void 잘못된_입력은_400() {
        TestUser user = TestUser.guest();
        long root = Fixtures.rootFolderId(user);

        // v1 은 폴더가 필수다. v2 는 폴더가 없으면 루트에 넣는다(ProblemService.registerProblemsV2).
        Map<String, Object> noFolder = new HashMap<>();
        noFolder.put("memo", "폴더 없음");
        user.api().post("/api/problems", noFolder).expectError(400, ErrorCodes.PROBLEM_FOLDER_ID_REQUIRED);
        Map<String, Object> v2NoFolder = Fixtures.problemBody("폴더 없음", root, List.of(), List.of());
        v2NoFolder.remove("folderId");
        long toRoot = user.api(Fixtures.CURRENT_APP_VERSION).post("/api/problems/v2", v2NoFolder).success().asLong();
        assertThat(user.api().get("/api/problems/" + toRoot).ok().path("folderId").asLong()).isEqualTo(root);

        user.api(Fixtures.CURRENT_APP_VERSION)
                .post("/api/problems/v2", Fixtures.problemBody("가".repeat(1001), root, List.of(), List.of()))
                .expectError(400, ErrorCodes.PROBLEM_MEMO_TOO_LONG);

        List<Long> sixTags = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            sixTags.add(Fixtures.createTag(user, "태그" + i));
        }
        user.api(Fixtures.CURRENT_APP_VERSION)
                .post("/api/problems/v2", Fixtures.problemBody("태그 6개", root, List.of(), sixTags))
                .expectStatus(400);

        user.api().patch("/api/problems/info", Map.of("memo", "id 없음")).expectStatus(400);
        user.api().delete("/api/problems", Map.of()).expectStatus(400);
        user.api().get("/api/problems/title/V2").expectStatus(400);
        user.api().post("/api/problems/v2", "{not json").expectStatus(400);
    }

    @Test
    void 없는_문제는_404() {
        TestUser user = TestUser.guest();

        user.api().get("/api/problems/999999999").expectError(404, ErrorCodes.PROBLEM_NOT_FOUND);
    }

    @Test
    void 오답노트_API_는_토큰이_없으면_401() {
        Api.anonymous().get("/api/problems/user").expectStatus(401);
        Api.anonymous().post("/api/problems/v2", Map.of()).expectStatus(401);
        Api.anonymous().delete("/api/problems/all").expectStatus(401);
    }

    private static String encode(String value) {
        return java.net.URLEncoder.encode(value, java.nio.charset.StandardCharsets.UTF_8);
    }

    private static List<String> imageUrls(TestUser user, long problemId) {
        List<String> urls = new ArrayList<>();
        user.api().get("/api/problems/" + problemId).ok().path("imageUrlList")
                .forEach(i -> urls.add(i.path("imageUrl").asText()));
        return urls;
    }

    private static List<Long> ids(JsonNode problems) {
        List<Long> ids = new ArrayList<>();
        problems.forEach(p -> ids.add(p.path("problemId").asLong()));
        return ids;
    }
}
