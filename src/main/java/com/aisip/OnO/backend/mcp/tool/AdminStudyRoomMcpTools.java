package com.aisip.OnO.backend.mcp.tool;

import com.aisip.OnO.backend.admin.dto.AdminStudyRoomViews.RoomHeader;
import com.aisip.OnO.backend.admin.dto.AdminStudyRoomViews.RoomRow;
import com.aisip.OnO.backend.admin.repository.AdminStudyRoomQueryRepository;
import com.aisip.OnO.backend.common.exception.ApplicationException;
import com.aisip.OnO.backend.mcp.dto.McpAdminDtos.Challenge;
import com.aisip.OnO.backend.mcp.dto.McpAdminDtos.Feed;
import com.aisip.OnO.backend.mcp.dto.McpAdminDtos.Member;
import com.aisip.OnO.backend.mcp.dto.McpAdminDtos.SharedProblem;
import com.aisip.OnO.backend.mcp.dto.McpAdminDtos.StudyRoomDetail;
import com.aisip.OnO.backend.mcp.dto.McpAdminDtos.StudyRoomList;
import com.aisip.OnO.backend.mcp.dto.McpAdminDtos.StudyRoomRow;
import com.aisip.OnO.backend.mcp.dto.McpAdminDtos.WeeklyReport;
import com.aisip.OnO.backend.studyroom.exception.StudyRoomErrorCase;
import lombok.RequiredArgsConstructor;
import org.springaicommunity.mcp.annotation.McpTool;
import org.springaicommunity.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 관리자 스터디룸 화면(/admin/study-rooms)을 MCP 로 연다.
 * 방 이름, 챌린지 제목, 댓글, 피드 본문, 응원 메시지, 초대 코드는 돌려주지 않는다.
 */
@Component
@RequiredArgsConstructor
public class AdminStudyRoomMcpTools {

    private final AdminStudyRoomQueryRepository studyRoomQueryRepository;
    private final McpToolAudit audit;

    @McpTool(name = "list_study_rooms",
            description = "스터디룸을 최근 활동 순으로 봅니다. 방마다 방장 userId, 인원, 공유 문제와 댓글 수, 챌린지 진행과 완료 수를 주고, "
                    + "전체 방 수, 이번 주 활동한 방, 전체 멤버, 진행 중 챌린지 요약을 함께 돌려줍니다. "
                    + "요약은 게스트와 관리자 계정이 만든 방과 활동을 빼고 세지만 목록에는 모든 방이 나옵니다.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true, openWorldHint = false))
    public StudyRoomList listStudyRooms(
            @McpToolParam(description = "가져올 개수. 기본 20, 최대 50", required = false) Integer limit,
            @McpToolParam(description = "0부터 시작하는 페이지", required = false) Integer page) {
        Map<String, Object> arguments = new LinkedHashMap<>();
        arguments.put("limit", limit);
        arguments.put("page", page);
        return audit.record("list_study_rooms", arguments, () -> {
            int size = McpToolArguments.limit(limit);
            int offset = McpToolArguments.page(page) * size;
            LocalDateTime weekStart = McpToolArguments.today()
                    .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                    .atStartOfDay();
            var rooms = studyRoomQueryRepository.findRooms(offset, size).stream()
                    .map(AdminStudyRoomMcpTools::toRow)
                    .toList();
            return new StudyRoomList(studyRoomQueryRepository.overview(weekStart),
                    McpPage.of(studyRoomQueryRepository.countRooms(), rooms));
        });
    }

    @McpTool(name = "get_study_room",
            description = "스터디룸 하나의 멤버(userId, 방장 여부, 주간 목표), 챌린지 상태, 공유 문제, 최근 피드 이벤트, 주간 리포트 수치를 돌려줍니다.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true, openWorldHint = false))
    public StudyRoomDetail getStudyRoom(@McpToolParam(description = "스터디룸 id") Long roomId) {
        return audit.record("get_study_room", Map.of("roomId", String.valueOf(roomId)), () -> {
            if (roomId == null) {
                throw new IllegalArgumentException("roomId 가 필요합니다.");
            }
            RoomHeader room = studyRoomQueryRepository.findRoom(roomId)
                    .orElseThrow(() -> new ApplicationException(StudyRoomErrorCase.STUDY_ROOM_NOT_FOUND));
            return new StudyRoomDetail(
                    room.id(),
                    room.hostUserId(),
                    room.createdAt(),
                    studyRoomQueryRepository.countReactions(roomId),
                    studyRoomQueryRepository.findMembers(roomId).stream()
                            .map(m -> new Member(m.userId(), m.host(), m.weeklyGoal(), m.joinedAt(), m.sharedProblemCount()))
                            .toList(),
                    studyRoomQueryRepository.findChallenges(roomId).stream()
                            .map(c -> new Challenge(c.id(), c.typeLabel(), c.metricLabel(), c.periodLabel(), c.targetValue(),
                                    c.status(), c.startAt(), c.endAt(), c.completedAt()))
                            .toList(),
                    studyRoomQueryRepository.findSharedProblems(roomId).stream()
                            .map(p -> new SharedProblem(p.problemId(), p.sharedByUserId(), p.reactionCount(), p.commentCount(), p.sharedAt()))
                            .toList(),
                    studyRoomQueryRepository.findRecentFeeds(roomId).stream()
                            .map(f -> new Feed(f.eventLabel(), f.userId(), f.reactionCount(), f.createdAt()))
                            .toList(),
                    studyRoomQueryRepository.findWeeklyReports(roomId).stream()
                            .map(r -> new WeeklyReport(r.weekStart(), r.weekEnd(), r.topMemberProblemCount(),
                                    r.longestStreakDays(), r.totalProblems(), r.challengesCompleted(), r.readCount()))
                            .toList());
        });
    }

    private static StudyRoomRow toRow(RoomRow row) {
        return new StudyRoomRow(row.id(), row.hostUserId(), row.memberCount(), row.sharedProblemCount(),
                row.commentCount(), row.inProgressChallengeCount(), row.completedChallengeCount(),
                row.lastActivityAt(), row.createdAt());
    }
}
