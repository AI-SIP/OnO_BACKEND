package com.aisip.OnO.backend.mcp;

import com.aisip.OnO.backend.feedback.entity.UserFeedback;
import com.aisip.OnO.backend.folder.entity.Folder;
import com.aisip.OnO.backend.mission.entity.MissionType;
import com.aisip.OnO.backend.problem.entity.AnalysisStatus;
import com.aisip.OnO.backend.problem.entity.Problem;
import com.aisip.OnO.backend.problem.entity.ProblemAnalysis;
import com.aisip.OnO.backend.problem.repository.ProblemAnalysisRepository;
import com.aisip.OnO.backend.studyroom.entity.StudyRoom;
import com.aisip.OnO.backend.studyroom.entity.StudyRoomInviteCode;
import com.aisip.OnO.backend.studyroom.repository.StudyRoomInviteCodeRepository;
import com.aisip.OnO.backend.feedback.repository.UserFeedbackRepository;
import com.aisip.OnO.backend.user.entity.User;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 도구 8개를 MCP 로 실제 호출해서 결과와 개인정보 차단을 확인한다.
 *
 * <p>픽스처에 알아보기 쉬운 개인정보 문자열을 심어 두고, 모든 도구 응답에 그 문자열이 하나도 없는지 본다.
 * MCP 응답은 외부 AI 대화에 남기 때문에 관리자 화면보다 기준이 엄격하다.
 */
@DisplayName("MCP 관리자 도구")
@ExtendWith(OutputCaptureExtension.class)
class McpAdminToolsTest extends McpTestSupport {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    /** 응답에 나오면 안 되는 값. 사용자 이름과 이메일은 setUp 에서 더한다. */
    private static final List<String> SECRET_MARKERS = List.of(
            "메모누출확인", "출처누출확인", "방이름누출확인", "초대코드누출", "10.9.8.7", "profile-leak.png");

    @Autowired
    private ProblemAnalysisRepository problemAnalysisRepository;

    @Autowired
    private StudyRoomInviteCodeRepository inviteCodeRepository;

    @Autowired
    private UserFeedbackRepository userFeedbackRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private User owner;
    private User member;
    private Problem failedProblem;
    private Problem plainProblem;
    private StudyRoom room;

    @BeforeEach
    void setUp() {
        owner = fixtures.createUser("leakowner");
        member = fixtures.createUser("leakmember");
        jdbcTemplate.update("UPDATE user SET profile_image_url = ? WHERE id = ?", "https://s3/profile-leak.png", owner.getId());

        Folder root = fixtures.createRootFolder(owner.getId());
        failedProblem = saveProblem(owner.getId(), root, "메모누출확인");
        jdbcTemplate.update("UPDATE problem SET reference = ? WHERE id = ?", "출처누출확인", failedProblem.getId());
        ProblemAnalysis analysis = ProblemAnalysis.createProcessing(failedProblem);
        analysis.updateWithFailure("분석 실패");
        problemAnalysisRepository.save(analysis);
        plainProblem = saveProblem(owner.getId(), root, "평범한 메모누출확인");

        saveMissionLog(owner, MissionType.USER_LOGIN, owner.getId());

        room = saveStudyRoom("방이름누출확인", owner);
        jdbcTemplate.update("INSERT INTO study_room_member (room_id, user_id, role, created_at, updated_at) VALUES (?, ?, 'MEMBER', NOW(), NOW())",
                room.getId(), member.getId());
        inviteCodeRepository.save(StudyRoomInviteCode.create(room, "초대코드누출", LocalDateTime.now(KST).plusDays(1)));

        userFeedbackRepository.save(UserFeedback.builder()
                .npsScore(9)
                .usagePurpose("시험 대비")
                .painPoints("사진 올리기가 느려요")
                .ipAddress("10.9.8.7")
                .submittedAt(LocalDateTime.now(KST))
                .build());
    }

    private List<String> markers() {
        List<String> all = new java.util.ArrayList<>(SECRET_MARKERS);
        for (User user : List.of(owner, member)) {
            all.add(user.getName());
            all.add(user.getEmail());
        }
        return all;
    }

    private void assertNoSecrets(String tool, String body) {
        for (String marker : markers()) {
            assertThat(body).as("%s 응답에 개인정보 [%s] 가 있다", tool, marker).doesNotContain(marker);
        }
    }

    @Test
    @DisplayName("어떤 도구 응답에도 이름, 이메일, 메모, 출처, 방 이름, 초대 코드, IP, 이미지 URL 이 없다")
    void noToolLeaksPersonalData() throws Exception {
        Map<String, Map<String, Object>> calls = new LinkedHashMap<>();
        calls.put("get_service_stats", Map.of());
        calls.put("get_today_overview", Map.of());
        calls.put("search_users", Map.of());
        calls.put("get_user_activity", Map.of("userId", owner.getId()));
        calls.put("list_problems", Map.of());
        calls.put("list_feedbacks", Map.of());
        calls.put("list_study_rooms", Map.of());
        calls.put("get_study_room", Map.of("roomId", room.getId()));

        for (var call : calls.entrySet()) {
            String body = callToolRaw(call.getKey(), call.getValue());
            assertThat(body).as("%s 응답이 비었다", call.getKey()).isNotBlank();
            assertNoSecrets(call.getKey(), body);
        }
    }

    @Test
    @DisplayName("이메일로 검색은 되지만 응답에는 userId 만 나온다")
    void searchByEmailReturnsIdOnly() throws Exception {
        JsonNode result = callTool("search_users", Map.of("q", owner.getEmail()));

        JsonNode users = result.path("users");
        assertThat(users.path("total").asLong()).isEqualTo(1);
        assertThat(users.path("items").get(0).path("userId").asLong()).isEqualTo(owner.getId());
        assertThat(users.path("items").get(0).path("problemCount").asLong()).isEqualTo(2);
        assertThat(result.path("summary").path("totalUsers").asLong()).isGreaterThanOrEqualTo(2);
    }

    @Test
    @DisplayName("정렬 기준을 비우거나 이름으로 줘도 가입일 순으로 돌려준다")
    void sortFallsBackToCreatedAt() throws Exception {
        // 같은 시각에 만들어지면 동률을 id 내림차순으로 깨서 오름차순 검증이 흔들린다. 가입 시각을 벌려 둔다.
        forceCreatedAt("user", owner.getId(), LocalDateTime.now(KST).minusDays(2));
        forceCreatedAt("user", member.getId(), LocalDateTime.now(KST).minusDays(1));
        for (Map<String, Object> arguments : List.<Map<String, Object>>of(Map.of(), Map.of("sortBy", ""), Map.of("sortBy", "name"))) {
            JsonNode items = callTool("search_users", arguments).path("users").path("items");
            assertThat(items.get(0).path("userId").asLong()).as("인자 %s", arguments).isEqualTo(member.getId());
        }
        JsonNode ascending = callTool("search_users", Map.of("ascending", true)).path("users").path("items");
        assertThat(ascending.get(0).path("userId").asLong()).isEqualTo(owner.getId());
    }

    @Test
    @DisplayName("사용자 활동에는 데이터 수와 최근 미션 기록, 스터디룸 역할이 담긴다")
    void userActivity() throws Exception {
        JsonNode result = callTool("get_user_activity", Map.of("userId", owner.getId()));

        assertThat(result.path("userId").asLong()).isEqualTo(owner.getId());
        assertThat(result.path("counts").path("problemCount").asLong()).isEqualTo(2);
        assertThat(result.path("recentMissionLogs").get(0).path("missionType").asText())
                .as("관리자 저장소가 미션 종류를 화면용 라벨로 준다").isEqualTo("출석");
        assertThat(result.path("studyRooms").get(0).path("roomId").asLong()).isEqualTo(room.getId());
        assertThat(result.path("studyRooms").get(0).path("role").asText()).isEqualTo("HOST");
    }

    @Test
    @DisplayName("없는 사용자는 도구 오류로 알린다")
    void unknownUser() throws Exception {
        assertThat(callToolError("get_user_activity", Map.of("userId", 999_999_999L))).contains("사용자를 찾을 수 없습니다");
    }

    @Nested
    @DisplayName("문제 목록")
    class Problems {

        @Test
        @DisplayName("분석 상태로 거르면 그 상태의 문제만, 요약은 전체 기준으로 준다")
        void filtersByStatus() throws Exception {
            JsonNode result = callTool("list_problems", Map.of("status", "failed"));

            JsonNode problems = result.path("problems");
            assertThat(problems.path("total").asLong()).isEqualTo(1);
            assertThat(problems.path("items").get(0).path("problemId").asLong()).isEqualTo(failedProblem.getId());
            assertThat(problems.path("items").get(0).path("analysisStatus").asText()).isEqualTo("FAILED");
            assertThat(result.path("summary").path("failed").asLong()).isEqualTo(1);
        }

        @Test
        @DisplayName("모르는 상태는 조용히 무시하지 않고 가능한 값을 알려 준다")
        void rejectsUnknownStatus() throws Exception {
            assertThat(callToolError("list_problems", Map.of("status", "BROKEN")))
                    .contains("알 수 없는 분석 상태").contains("RATE_LIMIT_EXCEEDED");
        }

        @Test
        @DisplayName("limit 은 50 을 넘지 않고, 전체 건수는 따로 준다")
        void capsLimit() throws Exception {
            JsonNode problems = callTool("list_problems", Map.of("limit", 1000)).path("problems");

            assertThat(problems.path("total").asLong()).isEqualTo(2);
            assertThat(problems.path("returned").asInt()).isEqualTo(2);
            assertThat(callTool("list_problems", Map.of("limit", 1)).path("problems").path("returned").asInt()).isEqualTo(1);
        }

        @Test
        @DisplayName("날짜 형식이 틀리면 이유를 담아 오류로 돌려준다")
        void rejectsBadDate() throws Exception {
            assertThat(callToolError("list_problems", Map.of("date", "10/08"))).contains("yyyy-MM-dd");
        }
    }

    @Test
    @DisplayName("분석 상태 설명은 AnalysisStatus 와 어긋나지 않는다")
    void statusDescriptionMatchesEnum() throws Exception {
        String expected = Arrays.stream(AnalysisStatus.values()).map(Enum::name).collect(Collectors.joining(", "));
        JsonNode tools = rpcResult("tools/list", null).path("tools");
        String description = "";
        for (JsonNode tool : tools) {
            if (tool.path("name").asText().equals("list_problems")) {
                description = tool.path("description").asText();
            }
        }
        assertThat(description).contains(expected);
    }

    @Nested
    @DisplayName("운영 지표")
    class Stats {

        @Test
        @DisplayName("기간을 비우면 오늘까지 7일, 거꾸로 주면 바로잡고, 366일을 넘으면 자른다")
        void normalizesRange() throws Exception {
            LocalDate today = LocalDate.now(KST);

            JsonNode defaults = callTool("get_service_stats", Map.of());
            assertThat(defaults.path("endDate").asText()).isEqualTo(today.toString());
            assertThat(defaults.path("days").asLong()).isEqualTo(7);

            JsonNode reversed = callTool("get_service_stats", Map.of("startDate", "2026-03-10", "endDate", "2026-03-01"));
            assertThat(reversed.path("startDate").asText()).isEqualTo("2026-03-01");
            assertThat(reversed.path("endDate").asText()).isEqualTo("2026-03-10");
            assertThat(reversed.path("previousEndDate").asText()).isEqualTo("2026-02-28");

            JsonNode capped = callTool("get_service_stats", Map.of("startDate", "2020-01-01", "endDate", "2026-03-01"));
            assertThat(capped.path("days").asLong()).isEqualTo(366);
            assertThat(capped.path("dailyNewestFirst")).as("한 달이 넘으면 일별 행을 싣지 않는다").isEmpty();
            assertThat(defaults.path("dailyNewestFirst")).hasSize(7);

            JsonNode month = callTool("get_service_stats", Map.of("startDate", "2026-03-01", "endDate", "2026-03-31"));
            assertThat(month.path("dailyNewestFirst")).hasSize(31);
        }

        @Test
        @DisplayName("오늘 등록한 문제와 분석 상태가 집계된다")
        void countsToday() throws Exception {
            JsonNode today = callTool("get_today_overview", Map.of());

            assertThat(today.path("problems").path("value").asDouble()).isEqualTo(2.0);
            assertThat(today.path("analysisStatuses").toString()).contains("FAILED");

            JsonNode stats = callTool("get_service_stats", Map.of());
            assertThat(stats.path("topProblemWriters").get(0).path("userId").asLong()).isEqualTo(owner.getId());
            assertThat(stats.path("topProblemWriters").get(0).path("count").asLong()).isEqualTo(2);
        }
    }

    @Test
    @DisplayName("피드백은 내용과 평균 NPS 를 주고 IP 는 뺀다")
    void feedbacks() throws Exception {
        JsonNode result = callTool("list_feedbacks", Map.of());

        assertThat(result.path("totalCount").asLong()).isEqualTo(1);
        assertThat(result.path("averageNps").asDouble()).isEqualTo(9.0);
        JsonNode feedback = result.path("feedbacks").path("items").get(0);
        assertThat(feedback.path("painPoints").asText()).isEqualTo("사진 올리기가 느려요");
        assertThat(feedback.has("ipAddress")).isFalse();
    }

    @Nested
    @DisplayName("스터디룸")
    class StudyRooms {

        @Test
        @DisplayName("목록은 방장 userId 와 인원을 준다")
        void list() throws Exception {
            JsonNode rooms = callTool("list_study_rooms", Map.of()).path("rooms");

            assertThat(rooms.path("total").asLong()).isEqualTo(1);
            assertThat(rooms.path("items").get(0).path("hostUserId").asLong()).isEqualTo(owner.getId());
            assertThat(rooms.path("items").get(0).path("memberCount").asLong()).isEqualTo(2);
        }

        @Test
        @DisplayName("상세는 멤버를 userId 와 방장 여부로만 준다")
        void detail() throws Exception {
            JsonNode detail = callTool("get_study_room", Map.of("roomId", room.getId()));

            assertThat(detail.path("members")).hasSize(2);
            assertThat(detail.path("members").toString())
                    .contains("\"userId\":" + owner.getId())
                    .contains("\"userId\":" + member.getId());
            assertThat(detail.has("inviteCodes")).isFalse();
        }

        @Test
        @DisplayName("없는 방은 도구 오류로 알린다")
        void unknownRoom() throws Exception {
            assertThat(callToolError("get_study_room", Map.of("roomId", 999_999_999L))).contains("스터디룸을 찾을 수 없습니다");
        }
    }

    @Test
    @DisplayName("도구 호출은 이름과 입력값, 건수를 감사 로그로 남기고 응답 본문은 남기지 않는다")
    void auditLog(CapturedOutput output) throws Exception {
        callTool("list_problems", Map.of("status", "FAILED"));
        callToolError("get_user_activity", Map.of("userId", 999_999_999L));

        assertThat(output.getOut())
                .contains("MCP tool called - tool: list_problems, arguments: {status=FAILED")
                .contains("outcome: success, size: 1")
                .contains("MCP tool called - tool: get_user_activity, arguments: {userId=999999999}, outcome: error");
        assertThat(output.getOut()).doesNotContain("메모누출확인");
    }
}
