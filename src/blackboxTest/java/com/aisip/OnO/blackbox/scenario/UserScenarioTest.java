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
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 내 정보 조회와 수정, 알림 설정, 프로필 이미지, 탈퇴.
 */
class UserScenarioTest extends BlackboxTest {

    @Test
    void 내_정보를_조회하고_이름을_바꾸면_다시_조회할_때_반영된다() {
        TestUser user = TestUser.guest();

        user.api().patch("/api/users", Map.of("name", "바뀐이름")).ok();

        JsonNode me = user.api().get("/api/users").ok();
        assertThat(me.path("userId").asLong()).isEqualTo(user.userId());
        assertThat(me.path("name").asText()).isEqualTo("바뀐이름");
        assertThat(me.path("totalStudyLevel").asLong()).isPositive();
    }

    @Test
    void 알림을_끄면_조회_응답에_꺼진_상태로_내려온다() {
        TestUser user = TestUser.guest();

        user.api().patch("/api/users/notification-settings", Map.of("notificationEnabled", false)).ok();

        assertThat(user.api().get("/api/users").ok().path("notificationEnabled").asBoolean()).isFalse();
    }

    @Test
    void 프로필_이미지를_올리면_S3_에_저장되고_지우면_S3_에서도_지워진다() {
        TestUser user = TestUser.guest();

        JsonNode updated = user.api().multipart("PATCH", "/api/users/me/profile-image", Map.of(),
                "profileImage", "me.png", "image/png", Images.PNG).ok();
        String url = updated.path("profileImageUrl").asText();
        String key = Images.objectKey(url);
        assertThat(BlackboxStack.get().objectExists(key)).isTrue();

        JsonNode cleared = user.api().delete("/api/users/me/profile-image").ok();

        assertThat(cleared.path("profileImageUrl").isNull() || cleared.path("profileImageUrl").isMissingNode()).isTrue();
        // S3 삭제는 커밋 뒤 RabbitMQ 를 거쳐 비동기로 처리된다.
        eventually(Duration.ofSeconds(15), () -> assertThat(BlackboxStack.get().objectExists(key)).isFalse());
    }

    @Test
    void 이미지가_아닌_파일은_프로필로_받지_않는다() {
        TestUser user = TestUser.guest();

        user.api().multipart("PATCH", "/api/users/me/profile-image", Map.of(),
                        "profileImage", "me.png", "image/png", "not an image".getBytes())
                .expectError(400, ErrorCodes.INVALID_IMAGE_FILE);
    }

    @Test
    void presigned_URL_로_올린_내_파일은_프로필로_쓸_수_있다() {
        TestUser user = TestUser.guest();
        String fileUrl = Images.uploadViaPresignedUrl(user);

        JsonNode updated = user.api().patch("/api/users/me/profile-image-url", Map.of("profileImageUrl", fileUrl)).ok();

        assertThat(updated.path("profileImageUrl").asText()).isEqualTo(fileUrl);
    }

    @Test
    void 남이_올린_파일_URL_을_내_프로필로_쓸_수_없다() {
        TestUser owner = TestUser.guest();
        TestUser other = TestUser.guest();
        String ownersFile = Images.uploadViaPresignedUrl(owner);

        other.api().patch("/api/users/me/profile-image-url", Map.of("profileImageUrl", ownersFile))
                .expectError(403, ErrorCodes.IMAGE_USER_UNMATCHED);
    }

    @Test
    void 탈퇴하면_남은_토큰으로_아무것도_할_수_없고_갱신도_안_된다() {
        TestUser user = TestUser.guest();
        long problemId = Fixtures.registerProblem(user, "탈퇴 전 문제");

        user.api().delete("/api/users").expectStatus(200);

        user.api().get("/api/problems/" + problemId).expectError(401, ErrorCodes.INVALID_ACCESS_TOKEN);
        user.api().post("/api/fcm/token", Map.of("token", "device-after-withdrawal"))
                .expectError(401, ErrorCodes.INVALID_ACCESS_TOKEN);
        Api.anonymous().post("/api/auth/refresh", Map.of("refreshToken", user.refreshToken()))
                .expectError(401, ErrorCodes.REFRESH_TOKEN_NOT_FOUND);
    }

    @Test
    void 사용자_API_는_토큰이_없으면_401() {
        Api.anonymous().get("/api/users").expectStatus(401);
        Api.anonymous().patch("/api/users", Map.of("name", "x")).expectStatus(401);
        Api.anonymous().delete("/api/users").expectStatus(401);
    }
}
