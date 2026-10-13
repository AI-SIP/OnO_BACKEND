package com.aisip.OnO.backend.mcp.tool;

import com.aisip.OnO.backend.admin.repository.AdminStatsQueryRepository;
import com.aisip.OnO.backend.admin.service.AdminStatsService;
import com.aisip.OnO.backend.admin.service.AdminStatsService.Daily;
import com.aisip.OnO.backend.admin.service.AdminStatsService.Home;
import com.aisip.OnO.backend.admin.service.AdminStatsService.Period;
import com.aisip.OnO.backend.mcp.dto.McpStatsDtos.ServiceStats;
import com.aisip.OnO.backend.mcp.dto.McpStatsDtos.TodayOverview;
import com.aisip.OnO.backend.mcp.dto.McpStatsDtos.UserCount;
import lombok.RequiredArgsConstructor;
import org.springaicommunity.mcp.annotation.McpTool;
import org.springaicommunity.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 관리자 홈과 분석 화면(/admin/main, /admin/analysis)이 보여 주는 지표를 MCP 로 연다. */
@Component
@RequiredArgsConstructor
public class AdminStatsMcpTools {

    /** 관리자 분석 화면과 같은 상한. 한 번에 1년 넘게 집계하면 쿼리가 무거워진다. */
    static final int MAX_RANGE_DAYS = 366;
    static final int DEFAULT_RANGE_DAYS = 7;
    /** 일별 행은 기간이 길면 응답을 수만 자로 키운다. 한 달 이하일 때만 싣는다. */
    static final int MAX_DAILY_ROWS_DAYS = 31;
    private static final int TOP_LIMIT = 10;

    private final AdminStatsService statsService;
    private final AdminStatsQueryRepository statsQueryRepository;
    private final McpToolAudit audit;

    @McpTool(name = "get_service_stats",
            description = "기간 운영 지표를 조회합니다. 사용자(DAU, 가입, 리텐션), 학습(문제 등록, 풀이, 정답률), "
                    + "AI 분석 상태, 미션과 업적, 스터디룸 지표와 일별 행, 직전 같은 길이 기간과의 비교를 돌려줍니다. "
                    + "기간을 비우면 오늘까지 최근 7일이고, 최대 366일까지 조회합니다. 일별 행은 31일 이하일 때만 담깁니다. "
                    + "숫자는 게스트와 관리자 계정을 뺀 실사용자 기준입니다.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true, openWorldHint = false))
    public ServiceStats getServiceStats(
            @McpToolParam(description = "시작일 yyyy-MM-dd (KST). 비우면 종료일 기준 6일 전", required = false) String startDate,
            @McpToolParam(description = "종료일 yyyy-MM-dd (KST). 비우면 오늘", required = false) String endDate) {
        Map<String, Object> arguments = new LinkedHashMap<>();
        arguments.put("startDate", startDate);
        arguments.put("endDate", endDate);
        return audit.record("get_service_stats", arguments, () -> buildServiceStats(startDate, endDate));
    }

    @McpTool(name = "get_today_overview",
            description = "오늘(KST)의 활성 사용자, 가입, 문제 등록, 풀이 수를 어제와 비교하고, "
                    + "최근 14일 일별 활성 사용자와 AI 분석 상태별 누적 건수를 돌려줍니다.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true, openWorldHint = false))
    public TodayOverview getTodayOverview() {
        return audit.record("get_today_overview", Map.of(), () -> {
            LocalDate today = McpToolArguments.today();
            Home home = statsService.home(today);
            return new TodayOverview(today, home.activeUsers(), home.signups(), home.problems(), home.solves(),
                    home.recentDau(), home.analysisStatuses());
        });
    }

    private ServiceStats buildServiceStats(String startDate, String endDate) {
        LocalDate today = McpToolArguments.today();
        LocalDate end = orElse(McpToolArguments.parseDate(endDate, "endDate"), today);
        LocalDate start = orElse(McpToolArguments.parseDate(startDate, "startDate"), end.minusDays(DEFAULT_RANGE_DAYS - 1));
        if (start.isAfter(end)) {
            LocalDate swap = start;
            start = end;
            end = swap;
        }
        if (ChronoUnit.DAYS.between(start, end) + 1 > MAX_RANGE_DAYS) {
            start = end.minusDays(MAX_RANGE_DAYS - 1);
        }

        Period period = statsService.period(start, end);
        Daily daily = statsService.daily(start, end);
        Period previous = period.previous();
        return new ServiceStats(
                start, end, period.days(), previous.start(), previous.end(),
                statsService.userStats(period, daily, today),
                statsService.learningStats(period, daily),
                statsService.analysisStats(period),
                statsService.growthStats(period, daily),
                statsService.roomStats(period),
                period.days() <= MAX_DAILY_ROWS_DAYS ? daily.rowsNewestFirst() : List.of(),
                statsQueryRepository.topProblemWriters(start, end, TOP_LIMIT).stream()
                        .map(row -> new UserCount(row.userId(), row.count())).toList(),
                statsQueryRepository.topSolvers(start, end, TOP_LIMIT).stream()
                        .map(row -> new UserCount(row.userId(), row.count())).toList());
    }

    private static LocalDate orElse(LocalDate value, LocalDate fallback) {
        return value == null ? fallback : value;
    }
}
