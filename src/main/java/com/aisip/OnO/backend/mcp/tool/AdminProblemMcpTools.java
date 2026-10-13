package com.aisip.OnO.backend.mcp.tool;

import com.aisip.OnO.backend.admin.dto.AdminLearningDto;
import com.aisip.OnO.backend.admin.repository.AdminLearningQueryRepository;
import com.aisip.OnO.backend.mcp.dto.McpAdminDtos.ProblemList;
import com.aisip.OnO.backend.mcp.dto.McpAdminDtos.ProblemRow;
import com.aisip.OnO.backend.problem.entity.AnalysisStatus;
import lombok.RequiredArgsConstructor;
import org.springaicommunity.mcp.annotation.McpTool;
import org.springaicommunity.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** 관리자 문제 화면(/admin/problems)을 MCP 로 연다. 메모, 출처, 이미지는 돌려주지 않는다. */
@Component
@RequiredArgsConstructor
public class AdminProblemMcpTools {

    private final AdminLearningQueryRepository learningQueryRepository;
    private final McpToolAudit audit;

    @McpTool(name = "list_problems",
            description = "등록된 문제를 최근 순으로 봅니다. AI 분석 상태, 등록일(KST), 사용자로 거를 수 있고, "
                    + "전체와 오늘 등록, 분석 실패, 요청 한도 초과, 분석 중 건수 요약을 함께 돌려줍니다. "
                    + "요약은 게스트와 관리자 계정을 빼고 세지만 목록에는 모든 계정의 문제가 나옵니다. "
                    + "분석 상태: " + AdminProblemMcpTools.STATUS_LIST + ".",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true, openWorldHint = false))
    public ProblemList listProblems(
            @McpToolParam(description = "AI 분석 상태 (예: FAILED, RATE_LIMIT_EXCEEDED)", required = false) String status,
            @McpToolParam(description = "등록일 yyyy-MM-dd (KST)", required = false) String date,
            @McpToolParam(description = "사용자 id", required = false) Long userId,
            @McpToolParam(description = "가져올 개수. 기본 20, 최대 50", required = false) Integer limit,
            @McpToolParam(description = "0부터 시작하는 페이지", required = false) Integer page) {
        Map<String, Object> arguments = new LinkedHashMap<>();
        arguments.put("status", status);
        arguments.put("date", date);
        arguments.put("userId", userId);
        arguments.put("limit", limit);
        arguments.put("page", page);
        return audit.record("list_problems", arguments, () -> {
            AdminLearningQueryRepository.Filter filter = new AdminLearningQueryRepository.Filter(
                    McpToolArguments.parseDate(date, "date"), userId, normalizeStatus(status));
            int size = McpToolArguments.limit(limit);
            int offset = McpToolArguments.page(page) * size;
            var problems = learningQueryRepository.findProblems(filter, offset, size).stream()
                    .map(AdminProblemMcpTools::toRow)
                    .toList();
            return new ProblemList(
                    learningQueryRepository.summarizeProblems(McpToolArguments.today()),
                    McpPage.of(learningQueryRepository.countProblems(filter), problems));
        });
    }

    /**
     * 도구 설명은 컴파일 시점 상수여야 해서 enum 을 그대로 쓸 수 없다. AnalysisStatus 와 어긋나지 않는지
     * AdminProblemMcpToolsTest 가 확인한다.
     */
    static final String STATUS_LIST = "PROCESSING, COMPLETED, FAILED, NOT_STARTED, NO_IMAGE, RATE_LIMIT_EXCEEDED";

    /** 모르는 상태를 조용히 무시하면 AI 는 전체 목록을 그 상태의 목록으로 착각한다. 그래서 거절한다. */
    static String normalizeStatus(String status) {
        String value = McpToolArguments.blankToNull(status);
        if (value == null) {
            return null;
        }
        String upper = value.toUpperCase(Locale.ROOT);
        return Arrays.stream(AnalysisStatus.values())
                .map(Enum::name)
                .filter(upper::equals)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "알 수 없는 분석 상태입니다: " + status + ". 가능한 값: " + STATUS_LIST));
    }

    private static ProblemRow toRow(AdminLearningDto.ProblemRow row) {
        return new ProblemRow(row.problemId(), row.userId(), row.folderId(), row.analysisStatus(), row.subject(),
                row.problemType(), row.solveCount(), row.tagCount(), row.createdAt());
    }
}
