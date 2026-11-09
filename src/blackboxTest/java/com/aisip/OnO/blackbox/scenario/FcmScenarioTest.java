package com.aisip.OnO.blackbox.scenario;

import com.aisip.OnO.blackbox.support.Api;
import com.aisip.OnO.blackbox.support.BlackboxTest;
import com.aisip.OnO.blackbox.support.TestUser;
import org.junit.jupiter.api.Test;

import java.util.Map;

/**
 * 푸시 토큰 등록. 실제 발송은 확인하지 않는다. 앱 컨테이너는 외부로 나갈 수 없고 Firebase 키도 더미라 발송이 일어날 수 없다.
 */
class FcmScenarioTest extends BlackboxTest {

    @Test
    void 기기_토큰을_등록하고_같은_토큰을_다시_보내도_성공한다() {
        TestUser user = TestUser.guest();

        user.api().post("/api/fcm/token", Map.of("token", "device-token-" + user.userId())).ok();
        user.api().post("/api/fcm/token", Map.of("token", "device-token-" + user.userId())).ok();
    }

    @Test
    void 빈_토큰은_400() {
        TestUser user = TestUser.guest();

        user.api().post("/api/fcm/token", Map.of("token", " ")).expectStatus(400);
        user.api().post("/api/fcm/token", Map.of()).expectStatus(400);
    }

    @Test
    void 푸시_API_는_토큰이_없으면_401() {
        Api.anonymous().post("/api/fcm/token", Map.of("token", "x")).expectStatus(401);
        // 테스트 발송 API 는 호출한 사용자 본인 기기로만 보낸다(FcmController.sendNoti). 인증 없이 못 부르는지만 본다.
        Api.anonymous().post("/api/fcm/send").expectStatus(401);
    }
}
