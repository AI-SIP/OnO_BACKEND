package com.aisip.OnO.blackbox.scenario;

import com.aisip.OnO.blackbox.support.TestUser;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 시나리오가 앞단계로 자주 쓰는 데이터 만들기. 전부 앱 API 로 만든다.
 */
final class Fixtures {

    /** 실제 앱 버전과 같은 대역. 미션과 XP 적립이 이 버전부터 돈다. */
    static final String CURRENT_APP_VERSION = "4.2.0";

    private Fixtures() {
    }

    static long rootFolderId(TestUser user) {
        return user.api().get("/api/folders/root").ok().path("folderId").asLong();
    }

    static long createFolder(TestUser user, String name, long parentFolderId) {
        return user.api().post("/api/folders", Map.of("folderName", name, "parentFolderId", parentFolderId))
                .success().asLong();
    }

    static long registerProblem(TestUser user, String memo) {
        return registerProblem(user, memo, rootFolderId(user), List.of(), List.of());
    }

    static long registerProblem(TestUser user, String memo, long folderId, List<String> problemImageUrls,
                                List<Long> tagIds) {
        return user.api(CURRENT_APP_VERSION).post("/api/problems/v2", problemBody(memo, folderId, problemImageUrls, tagIds))
                .success().asLong();
    }

    static Map<String, Object> problemBody(String memo, long folderId, List<String> problemImageUrls, List<Long> tagIds) {
        Map<String, Object> body = new HashMap<>();
        body.put("memo", memo);
        body.put("reference", "블랙박스 문제집 p.12");
        body.put("folderId", folderId);
        body.put("solvedAt", now().toString());
        body.put("problemImageUrls", problemImageUrls);
        body.put("answerImageUrls", List.of());
        body.put("tagIds", tagIds);
        return body;
    }

    static long createTag(TestUser user, String name) {
        return user.api().post("/api/tags", Map.of("name", name)).success().path("tagId").asLong();
    }

    static long createPracticeNote(TestUser user, String title, List<Long> problemIds) {
        Map<String, Object> body = new HashMap<>();
        body.put("practiceTitle", title);
        body.put("problemIdList", problemIds);
        return user.api().post("/api/practiceNotes", body).success().asLong();
    }

    /** 앱은 한국 시간 기준 LocalDateTime 을 초 단위로 보낸다. */
    static LocalDateTime now() {
        return LocalDateTime.now(ZoneId.of("Asia/Seoul")).truncatedTo(ChronoUnit.SECONDS);
    }
}
