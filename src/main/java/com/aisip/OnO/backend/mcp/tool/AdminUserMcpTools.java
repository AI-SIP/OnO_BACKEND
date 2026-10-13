package com.aisip.OnO.backend.mcp.tool;

import com.aisip.OnO.backend.admin.dto.AdminUserRows;
import com.aisip.OnO.backend.admin.repository.AdminUserQueryRepository;
import com.aisip.OnO.backend.common.exception.ApplicationException;
import com.aisip.OnO.backend.mcp.dto.McpAdminDtos.Levels;
import com.aisip.OnO.backend.mcp.dto.McpAdminDtos.MissionLogRow;
import com.aisip.OnO.backend.mcp.dto.McpAdminDtos.SolveRow;
import com.aisip.OnO.backend.mcp.dto.McpAdminDtos.StudyRoomMembership;
import com.aisip.OnO.backend.mcp.dto.McpAdminDtos.UserActivity;
import com.aisip.OnO.backend.mcp.dto.McpAdminDtos.UserRow;
import com.aisip.OnO.backend.mcp.dto.McpAdminDtos.UserSearchResult;
import com.aisip.OnO.backend.user.exception.UserErrorCase;
import lombok.RequiredArgsConstructor;
import org.springaicommunity.mcp.annotation.McpTool;
import org.springaicommunity.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

/** 관리자 유저 화면(/admin/users, /admin/user/{id})을 MCP 로 연다. 이름과 이메일은 돌려주지 않는다. */
@Component
@RequiredArgsConstructor
public class AdminUserMcpTools {

    static final int RECENT_LIMIT = 20;
    static final int LOGIN_DAYS_LIMIT = 30;
    static final String DEFAULT_SORT = "createdAt";

    private final AdminUserQueryRepository userQueryRepository;
    private final McpToolAudit audit;

    @McpTool(name = "search_users",
            description = "사용자를 검색합니다. q 는 이름, 이메일 일부나 userId 와 맞춰 보지만 응답에는 userId 와 활동 수치만 담깁니다. "
                    + "전체 사용자 수, 오늘 가입, 최근 7일 활성 사용자 요약도 함께 돌려줍니다. "
                    + "요약은 게스트와 관리자 계정을 빼고 세지만 목록에는 모든 계정이 나옵니다.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true, openWorldHint = false))
    public UserSearchResult searchUsers(
            @McpToolParam(description = "검색어. 이름, 이메일 일부 또는 userId", required = false) String q,
            @McpToolParam(description = "플랫폼 필터 (예: IOS, ANDROID)", required = false) String platform,
            @McpToolParam(description = "정렬 기준: createdAt, lastActiveAt, totalStudyLevel, problemCount, solveCount, practiceNoteCount. 기본 createdAt", required = false) String sortBy,
            @McpToolParam(description = "true 면 오름차순. 기본 내림차순", required = false) Boolean ascending,
            @McpToolParam(description = "가져올 개수. 기본 20, 최대 50", required = false) Integer limit,
            @McpToolParam(description = "0부터 시작하는 페이지", required = false) Integer page) {
        Map<String, Object> arguments = new LinkedHashMap<>();
        arguments.put("q", q);
        arguments.put("platform", platform);
        arguments.put("sortBy", sortBy);
        arguments.put("ascending", ascending);
        arguments.put("limit", limit);
        arguments.put("page", page);
        return audit.record("search_users", arguments, () -> {
            String keyword = McpToolArguments.blankToNull(q);
            String selectedPlatform = McpToolArguments.blankToNull(platform);
            int size = McpToolArguments.limit(limit);
            long offset = (long) McpToolArguments.page(page) * size;
            // 이름은 정렬 기준에서 뺀다. 응답에 이름이 없는데 이름순으로 주면 순서가 뜻을 잃는다.
            // 비어 있으면 기본값을 직접 넘긴다. resolveSortColumn 이 Map.of 를 쓰고 있어 null 로 찾으면 NPE 가 난다.
            String requested = McpToolArguments.blankToNull(sortBy);
            String sort = requested == null || "name".equals(requested) ? DEFAULT_SORT : requested;

            long total = userQueryRepository.countUsers(keyword, selectedPlatform);
            var users = userQueryRepository.findUsers(keyword, selectedPlatform, sort,
                            Boolean.TRUE.equals(ascending) ? "asc" : "desc", offset, size).stream()
                    .map(AdminUserMcpTools::toUserRow)
                    .toList();
            LocalDate today = McpToolArguments.today();
            AdminUserRows.Summary summary = userQueryRepository.summarize(
                    today.atStartOfDay(), today.minusDays(6).atStartOfDay());
            return new UserSearchResult(summary, McpPage.of(total, users));
        });
    }

    @McpTool(name = "get_user_activity",
            description = "사용자 한 명의 활동을 봅니다. 알림 설정과 마지막 알림 시각, 레벨, 데이터 수, 최근 30일 로그인 날짜, "
                    + "최근 풀이 20건(정답 여부와 시간), 최근 미션 기록 20건, 속한 스터디룸을 돌려줍니다. "
                    + "문의 대응에서 '알림이 안 온다', '기록이 사라졌다' 같은 상황을 확인할 때 씁니다.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true, openWorldHint = false))
    public UserActivity getUserActivity(@McpToolParam(description = "조회할 사용자 id") Long userId) {
        return audit.record("get_user_activity", Map.of("userId", String.valueOf(userId)), () -> {
            if (userId == null) {
                throw new IllegalArgumentException("userId 가 필요합니다.");
            }
            AdminUserRows.Profile profile = userQueryRepository.findProfile(userId)
                    .orElseThrow(() -> new ApplicationException(UserErrorCase.USER_NOT_FOUND));
            return new UserActivity(
                    profile.userId(),
                    profile.platform(),
                    profile.notificationEnabled(),
                    profile.lastActiveAt(),
                    profile.lastNotifiedAt(),
                    profile.createdAt(),
                    new Levels(profile.attendance(), profile.noteWrite(), profile.problemPractice(),
                            profile.notePractice(), profile.totalStudy()),
                    userQueryRepository.countOwnedData(userId),
                    userQueryRepository.findRecentLoginDates(userId, LOGIN_DAYS_LIMIT),
                    userQueryRepository.findSolves(userId).stream().limit(RECENT_LIMIT)
                            .map(s -> new SolveRow(s.solveId(), s.problemId(), s.answerStatus(), s.timeSpentSeconds(),
                                    s.moodEmojiKey(), s.practicedAt()))
                            .toList(),
                    userQueryRepository.findMissionLogs(userId).stream().limit(RECENT_LIMIT)
                            .map(m -> new MissionLogRow(m.missionType(), m.point(), m.createdAt()))
                            .toList(),
                    userQueryRepository.findStudyRooms(userId).stream()
                            .map(r -> new StudyRoomMembership(r.roomId(), r.role(), r.memberCount(), r.weeklyGoal(), r.joinedAt()))
                            .toList());
        });
    }

    private static UserRow toUserRow(AdminUserRows.ListRow row) {
        return new UserRow(row.userId(), row.platform(), row.totalStudyLevel(), row.totalStudyPoint(),
                row.problemCount(), row.solveCount(), row.practiceNoteCount(), row.lastActiveAt(), row.createdAt());
    }
}
