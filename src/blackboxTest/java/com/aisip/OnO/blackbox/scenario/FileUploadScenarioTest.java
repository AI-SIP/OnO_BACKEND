package com.aisip.OnO.blackbox.scenario;

import com.aisip.OnO.blackbox.support.Api;
import com.aisip.OnO.blackbox.support.BlackboxStack;
import com.aisip.OnO.blackbox.support.BlackboxTest;
import com.aisip.OnO.blackbox.support.ErrorCodes;
import com.aisip.OnO.blackbox.support.Images;
import com.aisip.OnO.blackbox.support.TestUser;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 이미지 업로드 API. presigned URL 발급, 서버 경유 업로드, 삭제와 소유자 확인.
 */
class FileUploadScenarioTest extends BlackboxTest {

    @Test
    void presigned_URL_은_요청한_개수만큼_내_경로로_발급된다() {
        TestUser user = TestUser.guest();

        JsonNode issued = user.api().get("/api/fileUpload/presigned-urls", Map.of("count", 3, "contentType", "image/png")).ok();

        assertThat(issued).hasSize(3);
        issued.forEach(item -> {
            assertThat(item.path("presignedUrl").asText()).startsWith("http://" + BlackboxStack.S3_HOST_IN_NETWORK + "/");
            assertThat(Images.objectKey(item.path("fileUrl").asText())).startsWith("image/users/" + user.userId() + "/");
        });
    }

    @Test
    void 서버를_거쳐_올린_이미지는_S3_에_저장된다() {
        TestUser user = TestUser.guest();

        String url = user.api().multipart("POST", "/api/fileUpload/image", Map.of(),
                "image", "photo.png", "image/png", Images.PNG).ok().asText();

        assertThat(BlackboxStack.get().objectExists(Images.objectKey(url))).isTrue();
    }

    @Test
    void 여러_이미지를_한_번에_올린다() {
        TestUser user = TestUser.guest();

        JsonNode urls = user.api().multipart("POST", "/api/fileUpload/images", Map.of(),
                "images", "a.png", "image/png", Images.PNG).ok();

        assertThat(urls).hasSize(1);
        assertThat(BlackboxStack.get().objectExists(Images.objectKey(urls.get(0).asText()))).isTrue();
    }

    @Test
    void 문제에_붙은_내_이미지를_지우면_S3_에서도_지워지고_남은_지울_수_없다() {
        TestUser owner = TestUser.guest();
        TestUser other = TestUser.guest();
        String image = Images.uploadViaPresignedUrl(owner);
        long problemId = Fixtures.registerProblem(owner, "이미지 문제", Fixtures.rootFolderId(owner), List.of(image), List.of());
        String query = "?imageUrl=" + URLEncoder.encode(image, StandardCharsets.UTF_8);

        other.api().delete("/api/fileUpload/image" + query).expectClientError();
        assertThat(BlackboxStack.get().objectExists(Images.objectKey(image))).isTrue();

        owner.api().delete("/api/fileUpload/image" + query).ok();

        assertThat(owner.api().get("/api/problems/" + problemId).ok().path("imageUrlList")).isEmpty();
        eventually(Duration.ofSeconds(15), () ->
                assertThat(BlackboxStack.get().objectExists(Images.objectKey(image))).isFalse());
    }

    @Test
    void 어디에도_붙지_않은_URL_을_지우려_하면_404() {
        TestUser user = TestUser.guest();
        String image = Images.uploadViaPresignedUrl(user);

        user.api().delete("/api/fileUpload/image?imageUrl=" + URLEncoder.encode(image, StandardCharsets.UTF_8))
                .expectError(404, ErrorCodes.FILE_NOT_FOUND);
    }

    @Test
    void 업로드_API_는_토큰이_없으면_401() {
        Api.anonymous().get("/api/fileUpload/presigned-urls").expectStatus(401);
    }
}
