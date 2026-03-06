package com.aisip.OnO.backend.learningreport.controller;

import com.aisip.OnO.backend.common.response.CommonResponse;
import com.aisip.OnO.backend.learningreport.dto.LearningReportResponseDto;
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

    @GetMapping("")
    public CommonResponse<LearningReportResponseDto> getLearningReport(
            @RequestParam(value = "baseDate", required = false)
            @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate baseDate
    ) {
        Long userId = (Long) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        LearningReportResponseDto report = learningReportService.getLearningReport(userId, baseDate);
        return CommonResponse.success(report);
    }
}
