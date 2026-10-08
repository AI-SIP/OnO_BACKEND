package com.aisip.OnO.backend.learningreport.controller;

import com.aisip.OnO.backend.common.response.CommonResponse;
import com.aisip.OnO.backend.learningreport.dto.LearningOverviewPeriod;
import com.aisip.OnO.backend.learningreport.dto.LearningOverviewResponseDto;
import com.aisip.OnO.backend.learningreport.dto.LearningReportResponseDto;
import com.aisip.OnO.backend.learningreport.dto.LearningReportSummaryResponseDto;
import com.aisip.OnO.backend.learningreport.service.LearningOverviewService;
import com.aisip.OnO.backend.learningreport.service.LearningReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/learning-reports")
public class LearningReportController {

    private final LearningReportService learningReportService;
    private final LearningOverviewService learningOverviewService;


    @GetMapping("/summary")
    public CommonResponse<LearningReportSummaryResponseDto> getLearningReportSummary() {
        Long userId = (Long) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        LearningReportSummaryResponseDto summary = learningReportService.getLearningReportSummary(userId);
        return CommonResponse.success(summary);
    }

    @GetMapping("")
    public CommonResponse<LearningReportResponseDto> getLearningReport(
            @RequestParam(value = "baseDate", required = false)
            @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate baseDate
    ) {
        Long userId = (Long) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        LearningReportResponseDto report = learningReportService.getLearningReport(userId, baseDate);
        return CommonResponse.success(report);
    }

    /**
     * 새 학습 보고서 화면용 개요. 기존 두 엔드포인트는 구버전 앱이 계속 부르므로 그대로 둔다.
     * 잘못된 {@code period} 는 타입 변환에 실패해 400 으로 나간다. 오늘보다 뒤의 {@code baseDate} 는 오늘로 맞춘다.
     */
    @GetMapping("/overview")
    public CommonResponse<LearningOverviewResponseDto> getLearningOverview(
            @RequestParam(value = "period", defaultValue = "WEEK") LearningOverviewPeriod period,
            @RequestParam(value = "baseDate", required = false)
            @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate baseDate
    ) {
        Long userId = (Long) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return CommonResponse.success(learningOverviewService.getOverview(userId, period, baseDate));
    }
}
