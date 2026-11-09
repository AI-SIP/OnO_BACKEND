package com.aisip.OnO.blackbox.scenario;

import com.aisip.OnO.blackbox.support.Api;
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
 * 스터디룸. 방장(host), 멤버(member), 방 밖 사용자(outsider) 세 명이 실제로 주고받는 흐름이다.
 * 방 안 데이터가 방 밖으로 새지 않는지가 핵심이라, 모든 하위 기능에 outsider 요청을 같이 넣는다.
 */
class StudyRoomScenarioTest extends BlackboxTest {

    private static final String ROOMS = "/api/study-room";
    private static final String EMOJI = "cool_sunglasses";

    @Test
    void 방을_만들고_초대_코드로_가입하면_양쪽_목록과_상세에_보인다() {
        TestUser host = TestUser.guest();
        TestUser member = TestUser.guest();

        long roomId = createRoom(host, "수능 스터디");
        join(member, invite(host, roomId));

        JsonNode detail = member.api().get(ROOMS + "/" + roomId).ok();
        assertThat(detail.path("hostUserId").asLong()).isEqualTo(host.userId());
        assertThat(detail.path("memberCount").asInt()).isEqualTo(2);
        assertThat(userIds(detail.path("members"))).containsExactlyInAnyOrder(host.userId(), member.userId());
        assertThat(roomIds(host.api().get(ROOMS).ok())).contains(roomId);
        assertThat(roomIds(member.api().get(ROOMS).ok())).contains(roomId);
        // 같은 경로의 복수형 별칭도 같은 응답이다(StudyRoomController 의 /api/study-rooms).
        assertThat(roomIds(member.api().get("/api/study-rooms").ok())).contains(roomId);
    }

    @Test
    void 방장은_방_이름과_썸네일을_바꾸고_멤버는_못_바꾼다() {
        TestUser host = TestUser.guest();
        TestUser member = TestUser.guest();
        long roomId = createRoom(host, "이름 전");
        join(member, invite(host, roomId));

        host.api().patch(ROOMS + "/" + roomId, Map.of("name", "이름 후")).ok();
        JsonNode thumbnail = host.api().multipart("PATCH", ROOMS + "/" + roomId + "/thumbnail", Map.of(),
                "thumbnail", "room.png", "image/png", Images.PNG).ok();
        String presigned = Images.uploadViaPresignedUrl(host);
        host.api().patch(ROOMS + "/" + roomId + "/thumbnail-url", Map.of("thumbnailUrl", presigned)).ok();

        assertThat(thumbnail.path("thumbnailUrl").asText()).isNotBlank();
        JsonNode detail = member.api().get(ROOMS + "/" + roomId).ok();
        assertThat(detail.path("name").asText()).isEqualTo("이름 후");
        assertThat(detail.path("thumbnailUrl").asText()).isEqualTo(presigned);

        member.api().patch(ROOMS + "/" + roomId, Map.of("name", "멤버가 바꿈"))
                .expectError(403, ErrorCodes.STUDY_ROOM_HOST_ONLY);
        String membersFile = Images.uploadViaPresignedUrl(member);
        member.api().patch(ROOMS + "/" + roomId + "/thumbnail-url", Map.of("thumbnailUrl", membersFile))
                .expectError(403, ErrorCodes.STUDY_ROOM_HOST_ONLY);
    }

    @Test
    void 앱처럼_멀티파트로_썸네일과_함께_방을_만들고_이름을_바꾼다() {
        TestUser host = TestUser.guest();

        JsonNode created = host.api().multipart("POST", ROOMS, Map.of("name", "사진 있는 방"),
                "thumbnailImage", "room.png", "image/png", Images.PNG).success();
        long roomId = created.path("roomId").asLong();
        assertThat(created.path("thumbnailUrl").asText()).isNotBlank();

        host.api().multipart("PATCH", ROOMS + "/" + roomId, Map.of("name", "이름 바꾼 방"), null, null, null, null).ok();

        assertThat(host.api().get(ROOMS + "/" + roomId).ok().path("name").asText()).isEqualTo("이름 바꾼 방");
    }

    @Test
    void 남이_올린_파일을_방_썸네일로_쓸_수_없다() {
        TestUser host = TestUser.guest();
        TestUser stranger = TestUser.guest();
        long roomId = createRoom(host, "썸네일");
        String strangersFile = Images.uploadViaPresignedUrl(stranger);

        host.api().patch(ROOMS + "/" + roomId + "/thumbnail-url", Map.of("thumbnailUrl", strangersFile))
                .expectError(403, ErrorCodes.IMAGE_USER_UNMATCHED);
    }

    @Test
    void 멤버가_오답노트를_쓰면_방_피드에_올라오고_반응을_누르고_취소할_수_있다() {
        TestUser host = TestUser.guest();
        TestUser member = TestUser.guest();
        long roomId = createRoom(host, "피드");
        join(member, invite(host, roomId));

        Fixtures.registerProblem(member, "피드에 올라갈 문제");

        // 피드는 커밋 뒤 비동기로 만들어진다(StudyRoomActivityListener).
        JsonNode feed = eventually(Duration.ofSeconds(15), () -> {
            JsonNode items = host.api().get(ROOMS + "/" + roomId + "/feed").ok().path("content");
            assertThat(items).as("피드: %s", items).isNotEmpty();
            return items.get(0);
        });
        assertThat(feed.path("userId").asLong()).isEqualTo(member.userId());
        // 피드 eventType 은 enum 이름의 소문자로 내려간다. 앱이 이 문자열로 분기한다.
        assertThat(feed.path("eventType").asText()).isEqualTo("problem_registered");
        long feedId = feed.path("feedId").asLong();

        JsonNode reacted = host.api().post(ROOMS + "/" + roomId + "/feed/" + feedId + "/reactions", Map.of("emoji", EMOJI)).success();
        assertThat(reacted.toString()).contains(EMOJI);
        host.api().post(ROOMS + "/" + roomId + "/feed/" + feedId + "/reactions", Map.of("emoji", EMOJI)).success();

        host.api().post(ROOMS + "/" + roomId + "/feed/" + feedId + "/reactions", Map.of("emoji", "not-an-emoji"))
                .expectError(400, ErrorCodes.INVALID_EMOJI_KEY);
    }

    @Test
    void 공유한_문제에_댓글과_반응을_달고_작성자만_고칠_수_있다() {
        TestUser host = TestUser.guest();
        TestUser member = TestUser.guest();
        long roomId = createRoom(host, "공유");
        join(member, invite(host, roomId));
        String image = Images.uploadViaPresignedUrl(host);
        long problemId = Fixtures.registerProblem(host, "같이 볼 문제", Fixtures.rootFolderId(host), List.of(image), List.of());
        String base = ROOMS + "/" + roomId + "/shared-problems";

        JsonNode shared = host.api().post(base, Map.of("problemId", problemId, "comment", "이거 어떻게 풀어요?")).success();
        long sharedId = shared.path("sharedProblemId").asLong();
        host.api().post(base, Map.of("problemId", problemId, "comment", "또 공유")).expectError(409, ErrorCodes.ALREADY_SHARED_PROBLEM);

        JsonNode list = member.api().get(base).ok().path("content");
        assertThat(list.get(0).path("sharedProblemId").asLong()).isEqualTo(sharedId);
        assertThat(list.get(0).path("problemImageUrls").get(0).asText()).isEqualTo(image);

        long commentId = member.api().post(base + "/" + sharedId + "/comments", Map.of("content", "근의 공식 써 보세요"))
                .success().path("commentId").asLong();
        member.api().patch(base + "/" + sharedId + "/comments/" + commentId, Map.of("content", "판별식부터 보세요")).ok();
        host.api().patch(base + "/" + sharedId + "/comments/" + commentId, Map.of("content", "방장이 고침"))
                .expectError(403, ErrorCodes.SHARED_PROBLEM_COMMENT_FORBIDDEN);
        host.api().post(base + "/" + sharedId + "/comments/" + commentId + "/reactions", Map.of("emoji", EMOJI)).success();
        member.api().post(base + "/" + sharedId + "/reactions", Map.of("emoji", EMOJI)).success();

        JsonNode comments = host.api().get(base + "/" + sharedId + "/comments").ok().path("content");
        assertThat(comments.get(0).path("content").asText()).isEqualTo("판별식부터 보세요");
        assertThat(comments.get(0).path("isEdited").asBoolean()).isTrue();
        assertThat(comments.get(0).path("isMine").asBoolean()).isFalse();
        assertThat(member.api().get(base).ok().path("content").get(0).path("commentCount").asLong()).isEqualTo(1);

        member.api().delete(base + "/" + sharedId + "/comments/" + commentId).ok();
        assertThat(host.api().get(base + "/" + sharedId + "/comments").ok().path("content")).isEmpty();

        host.api().delete(base + "/" + sharedId).ok();
        assertThat(member.api().get(base).ok().path("content")).isEmpty();
    }

    @Test
    void 남의_문제를_방에_공유할_수_없다() {
        TestUser host = TestUser.guest();
        TestUser member = TestUser.guest();
        long roomId = createRoom(host, "공유 차단");
        join(member, invite(host, roomId));
        long hostsProblem = Fixtures.registerProblem(host, "방장 문제");

        member.api().post(ROOMS + "/" + roomId + "/shared-problems", Map.of("problemId", hostsProblem, "comment", "남의 것"))
                .expectClientError();
    }

    @Test
    void 챌린지를_만들고_멤버는_자기가_만든_것만_지운다() {
        TestUser host = TestUser.guest();
        TestUser member = TestUser.guest();
        long roomId = createRoom(host, "챌린지");
        join(member, invite(host, roomId));
        String base = ROOMS + "/" + roomId + "/challenges";

        long hostsChallenge = host.api().post(base, challenge("매일 1문제")).success().path("challengeId").asLong();
        long membersChallenge = member.api().post(base, challenge("매일 복습")).success().path("challengeId").asLong();

        List<Long> ids = new ArrayList<>();
        member.api().get(base).ok().forEach(c -> ids.add(c.path("challengeId").asLong()));
        assertThat(ids).contains(hostsChallenge, membersChallenge);

        member.api().delete(base + "/" + hostsChallenge).expectError(403, ErrorCodes.STUDY_ROOM_HOST_ONLY);
        member.api().delete(base + "/" + membersChallenge).ok();
        host.api().delete(base + "/" + hostsChallenge).ok();
        host.api().delete(base + "/" + hostsChallenge).expectError(404, ErrorCodes.CHALLENGE_NOT_FOUND);
    }

    @Test
    void 주간_목표를_정하고_주간_리포트는_아직_없다() {
        TestUser host = TestUser.guest();
        long roomId = createRoom(host, "목표");

        JsonNode goal = host.api().put(ROOMS + "/" + roomId + "/members/me/goal", Map.of("weeklyGoal", 10)).ok();
        assertThat(goal.path("weeklyGoal").asInt()).isEqualTo(10);

        // 주간 리포트는 월요일 Quartz 작업이 만든다. 시간을 앞당길 수 없어 빈 목록과 없는 리포트 읽음 처리까지만 본다.
        assertThat(host.api().get(ROOMS + "/" + roomId + "/weekly-reports").ok()).isEmpty();
        host.api().patch(ROOMS + "/" + roomId + "/weekly-reports/999999999/read").expectStatus(404);
    }

    @Test
    void 방장이_내보낸_멤버와_스스로_나간_멤버는_방을_볼_수_없다() {
        TestUser host = TestUser.guest();
        TestUser kicked = TestUser.guest();
        TestUser leaver = TestUser.guest();
        long roomId = createRoom(host, "내보내기");
        String code = invite(host, roomId);
        join(kicked, code);
        join(leaver, code);

        kicked.api().delete(ROOMS + "/" + roomId + "/members/" + leaver.userId())
                .expectError(403, ErrorCodes.STUDY_ROOM_HOST_ONLY);
        host.api().delete(ROOMS + "/" + roomId + "/members/" + kicked.userId()).ok();
        leaver.api().delete(ROOMS + "/" + roomId + "/leave").ok();

        kicked.api().get(ROOMS + "/" + roomId).expectError(403, ErrorCodes.STUDY_ROOM_FORBIDDEN);
        leaver.api().get(ROOMS + "/" + roomId).expectError(403, ErrorCodes.STUDY_ROOM_FORBIDDEN);
        assertThat(host.api().get(ROOMS + "/" + roomId).ok().path("memberCount").asInt()).isEqualTo(1);
    }

    @Test
    void 방장이_방을_지우면_멤버도_더_볼_수_없고_멤버는_방을_지울_수_없다() {
        TestUser host = TestUser.guest();
        TestUser member = TestUser.guest();
        long roomId = createRoom(host, "지울 방");
        join(member, invite(host, roomId));

        member.api().delete(ROOMS + "/" + roomId).expectError(403, ErrorCodes.STUDY_ROOM_HOST_ONLY);
        host.api().delete(ROOMS + "/" + roomId).ok();

        member.api().get(ROOMS + "/" + roomId).expectDenied();
        assertThat(roomIds(member.api().get(ROOMS).ok())).doesNotContain(roomId);
    }

    @Test
    void 초대_코드가_틀리거나_이미_멤버면_가입되지_않는다() {
        TestUser host = TestUser.guest();
        TestUser member = TestUser.guest();
        long roomId = createRoom(host, "가입 검증");
        String code = invite(host, roomId);
        join(member, code);

        member.api().post(ROOMS + "/join", Map.of("code", code)).expectError(409, ErrorCodes.ALREADY_MEMBER);
        TestUser.guest().api().post(ROOMS + "/join", Map.of("code", "WRONG-CODE"))
                .expectError(400, ErrorCodes.INVITE_CODE_INVALID);
        // 초대 코드는 멤버 누구나 받을 수 있고, 살아 있는 코드가 있으면 같은 코드를 돌려준다(StudyRoomInviteService).
        assertThat(member.api().post(ROOMS + "/" + roomId + "/invite").success().path("code").asText()).isEqualTo(code);
    }

    @Test
    void 방_밖_사용자는_방의_어떤_것도_보거나_쓸_수_없다() {
        TestUser host = TestUser.guest();
        TestUser outsider = TestUser.guest();
        long roomId = createRoom(host, "비공개");
        long problemId = Fixtures.registerProblem(host, "방 안 문제");
        long sharedId = host.api().post(ROOMS + "/" + roomId + "/shared-problems", Map.of("problemId", problemId, "comment", "공유"))
                .success().path("sharedProblemId").asLong();
        long challengeId = host.api().post(ROOMS + "/" + roomId + "/challenges", challenge("방 안 챌린지"))
                .success().path("challengeId").asLong();
        long outsidersProblem = Fixtures.registerProblem(outsider, "밖 문제");
        String base = ROOMS + "/" + roomId;

        outsider.api().get(base).expectError(403, ErrorCodes.STUDY_ROOM_FORBIDDEN);
        outsider.api().get(base + "/feed").expectError(403, ErrorCodes.STUDY_ROOM_FORBIDDEN);
        outsider.api().get(base + "/challenges").expectError(403, ErrorCodes.STUDY_ROOM_FORBIDDEN);
        outsider.api().post(base + "/challenges", challenge("침입")).expectError(403, ErrorCodes.STUDY_ROOM_FORBIDDEN);
        outsider.api().delete(base + "/challenges/" + challengeId).expectError(403, ErrorCodes.STUDY_ROOM_FORBIDDEN);
        outsider.api().get(base + "/weekly-reports").expectError(403, ErrorCodes.STUDY_ROOM_FORBIDDEN);
        outsider.api().get(base + "/shared-problems").expectError(403, ErrorCodes.STUDY_ROOM_FORBIDDEN);
        outsider.api().post(base + "/shared-problems", Map.of("problemId", outsidersProblem, "comment", "침입"))
                .expectError(403, ErrorCodes.STUDY_ROOM_FORBIDDEN);
        outsider.api().get(base + "/shared-problems/" + sharedId + "/comments").expectError(403, ErrorCodes.STUDY_ROOM_FORBIDDEN);
        outsider.api().post(base + "/shared-problems/" + sharedId + "/comments", Map.of("content", "침입"))
                .expectError(403, ErrorCodes.STUDY_ROOM_FORBIDDEN);
        outsider.api().post(base + "/shared-problems/" + sharedId + "/reactions", Map.of("emoji", EMOJI))
                .expectError(403, ErrorCodes.STUDY_ROOM_FORBIDDEN);
        outsider.api().delete(base + "/shared-problems/" + sharedId).expectError(403, ErrorCodes.STUDY_ROOM_FORBIDDEN);
        outsider.api().put(base + "/members/me/goal", Map.of("weeklyGoal", 3)).expectError(403, ErrorCodes.STUDY_ROOM_FORBIDDEN);
        outsider.api().delete(base + "/leave").expectClientError();
        outsider.api().patch(base, Map.of("name", "탈취")).expectClientError();
        outsider.api().delete(base).expectClientError();
        outsider.api().post(base + "/invite").expectClientError();

        // 방 안 문제는 공유됐어도 방 밖 사용자가 문제 API 로 직접 볼 수 없다.
        outsider.api().get("/api/problems/" + problemId).expectDenied();
        assertThat(host.api().get(base).ok().path("memberCount").asInt()).isEqualTo(1);
    }

    @Test
    void 없는_방은_404_이고_잘못된_요청은_400() {
        TestUser user = TestUser.guest();
        long roomId = createRoom(user, "검증");

        user.api().get(ROOMS + "/999999999").expectStatus(404);
        user.api().post(ROOMS, Map.of("name", "")).expectClientError();
        user.api().post(ROOMS + "/" + roomId + "/challenges", Map.of("title", "값 없음")).expectClientError();
        Map<String, Object> badType = challenge("잘못된 종류");
        badType.put("type", "SOLO");
        user.api().post(ROOMS + "/" + roomId + "/challenges", badType).expectClientError();
    }

    @Test
    void 스터디룸_API_는_토큰이_없으면_401() {
        Api.anonymous().get(ROOMS).expectStatus(401);
        Api.anonymous().post(ROOMS + "/join", Map.of("code", "x")).expectStatus(401);
    }

    private static long createRoom(TestUser host, String name) {
        return host.api().post(ROOMS, Map.of("name", name)).success().path("roomId").asLong();
    }

    private static String invite(TestUser host, long roomId) {
        return host.api().post(ROOMS + "/" + roomId + "/invite").success().path("code").asText();
    }

    private static void join(TestUser user, String code) {
        user.api().post(ROOMS + "/join", Map.of("code", code)).success();
    }

    private static Map<String, Object> challenge(String title) {
        Map<String, Object> body = new HashMap<>();
        body.put("title", title);
        body.put("type", "INDIVIDUAL");
        body.put("metric", "PROBLEM_COUNT");
        body.put("period", "DAILY");
        body.put("targetValue", 1);
        body.put("startAt", Fixtures.now().toString());
        body.put("endAt", Fixtures.now().plusDays(7).toString());
        return body;
    }

    private static List<Long> userIds(JsonNode members) {
        List<Long> ids = new ArrayList<>();
        members.forEach(m -> ids.add(m.path("userId").asLong()));
        return ids;
    }

    private static List<Long> roomIds(JsonNode rooms) {
        List<Long> ids = new ArrayList<>();
        rooms.forEach(r -> ids.add(r.path("roomId").asLong()));
        return ids;
    }
}
