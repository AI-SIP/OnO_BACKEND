package com.aisip.OnO.backend.mcp.dto;

import com.aisip.OnO.backend.admin.dto.AdminStatsDto.AnalysisStats;
import com.aisip.OnO.backend.admin.dto.AdminStatsDto.DailyRow;
import com.aisip.OnO.backend.admin.dto.AdminStatsDto.GrowthStats;
import com.aisip.OnO.backend.admin.dto.AdminStatsDto.LabelCount;
import com.aisip.OnO.backend.admin.dto.AdminStatsDto.LearningStats;
import com.aisip.OnO.backend.admin.dto.AdminStatsDto.Metric;
import com.aisip.OnO.backend.admin.dto.AdminStatsDto.RoomStats;
import com.aisip.OnO.backend.admin.dto.AdminStatsDto.UserStats;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 운영 지표 도구의 응답.
 *
 * <p>{@code UserStats}, {@code LearningStats} 같은 집계 레코드는 숫자와 라벨만 담고 있어 그대로 싣는다.
 * 사람이 섞인 목록(상위 작성자, 최근 가입자)은 userId 와 숫자만 남긴 레코드로 바꿔 싣는다.
 */
public final class McpStatsDtos {

    private McpStatsDtos() {
    }

    public record ServiceStats(
            LocalDate startDate,
            LocalDate endDate,
            long days,
            LocalDate previousStartDate,
            LocalDate previousEndDate,
            UserStats users,
            LearningStats learning,
            AnalysisStats analysis,
            GrowthStats growth,
            RoomStats rooms,
            List<DailyRow> dailyNewestFirst,
            List<UserCount> topProblemWriters,
            List<UserCount> topSolvers
    ) {
    }

    public record TodayOverview(
            LocalDate date,
            Metric activeUsers,
            Metric signups,
            Metric problems,
            Metric solves,
            Map<LocalDate, Long> recentDailyActiveUsers,
            List<LabelCount> analysisStatuses
    ) {
    }

    public record UserCount(Long userId, long count) {
    }
}
