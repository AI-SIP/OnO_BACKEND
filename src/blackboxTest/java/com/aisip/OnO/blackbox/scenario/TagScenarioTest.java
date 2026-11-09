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
 * 태그 만들기, 중복 이름, 추천, 삭제와 다른 사용자 차단.
 */
class TagScenarioTest extends BlackboxTest {

    @Test
    void 태그를_만들면_목록에_보이고_같은_이름은_같은_태그로_돌아온다() {
        TestUser user = TestUser.guest();

        long first = Fixtures.createTag(user, "미적분");
        long again = Fixtures.createTag(user, "미적분");
        long caseInsensitive = Fixtures.createTag(user, "Calculus");
        long caseAgain = Fixtures.createTag(user, "calculus");

        assertThat(again).isEqualTo(first);
        assertThat(caseAgain).isEqualTo(caseInsensitive);
        assertThat(names(user.api().get("/api/tags").ok())).containsExactlyInAnyOrder("미적분", "Calculus");
    }

    @Test
    void 지운_태그는_목록과_문제에서_빠지고_다시_만들_수_있다() {
        TestUser user = TestUser.guest();
        long tag = Fixtures.createTag(user, "지울 태그");
        long problem = Fixtures.registerProblem(user, "태그 붙은 문제", Fixtures.rootFolderId(user), List.of(), List.of(tag));

        user.api().delete("/api/tags/" + tag).ok();

        assertThat(names(user.api().get("/api/tags").ok())).doesNotContain("지울 태그");
        assertThat(user.api().get("/api/problems/" + problem).ok().path("tags")).isEmpty();
        Fixtures.createTag(user, "지울 태그");
        assertThat(names(user.api().get("/api/tags").ok())).contains("지울 태그");
    }

    @Test
    void 여러_태그를_한_번에_지운다() {
        TestUser user = TestUser.guest();
        long a = Fixtures.createTag(user, "가");
        long b = Fixtures.createTag(user, "나");

        user.api().delete("/api/tags", Map.of("deleteTagIdList", List.of(a, b))).ok();

        assertThat(user.api().get("/api/tags").ok()).isEmpty();
    }

    @Test
    void 추천은_최근에_쓴_내_태그를_돌려준다() {
        TestUser user = TestUser.guest();
        long tag = Fixtures.createTag(user, "최근");
        Fixtures.registerProblem(user, "추천용", Fixtures.rootFolderId(user), List.of(), List.of(tag));

        JsonNode recommended = user.api().post("/api/tags/recommend", Map.of("imageUrls", List.of())).ok();

        assertThat(names(recommended)).contains("최근");
    }

    @Test
    void 빈_이름이나_너무_긴_이름은_거절한다() {
        TestUser user = TestUser.guest();

        user.api().post("/api/tags", Map.of("name", "   ")).expectError(400, ErrorCodes.TAG_NAME_EMPTY);
        user.api().post("/api/tags", Map.of("name", "가".repeat(31))).expectError(400, ErrorCodes.TAG_NAME_TOO_LONG);
    }

    @Test
    void 남의_태그는_지울_수_없고_목록에도_안_보인다() {
        TestUser owner = TestUser.guest();
        TestUser other = TestUser.guest();
        long tag = Fixtures.createTag(owner, "주인 태그");

        other.api().delete("/api/tags/" + tag).expectDenied();
        other.api().delete("/api/tags", Map.of("deleteTagIdList", List.of(tag))).expectClientError();

        assertThat(names(other.api().get("/api/tags").ok())).doesNotContain("주인 태그");
        assertThat(names(owner.api().get("/api/tags").ok())).contains("주인 태그");
    }

    @Test
    void 태그_API_는_토큰이_없으면_401() {
        Api.anonymous().get("/api/tags").expectStatus(401);
        Api.anonymous().post("/api/tags", Map.of("name", "x")).expectStatus(401);
    }

    private static List<String> names(JsonNode tags) {
        List<String> names = new ArrayList<>();
        tags.forEach(t -> names.add(t.path("name").asText()));
        return names;
    }
}
