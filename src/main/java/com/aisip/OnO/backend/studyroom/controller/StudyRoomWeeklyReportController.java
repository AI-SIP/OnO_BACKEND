package com.aisip.OnO.backend.studyroom.controller;

import com.aisip.OnO.backend.common.response.CommonResponse;
import com.aisip.OnO.backend.studyroom.dto.StudyRoomDtos.*;
import com.aisip.OnO.backend.studyroom.service.StudyRoomWeeklyReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/study-room/{roomId}/weekly-reports")
public class StudyRoomWeeklyReportController {

    private final StudyRoomWeeklyReportService reportService;

    @GetMapping
    public CommonResponse<List<WeeklyReportResponse>> getReports(@PathVariable("roomId") Long roomId,
                                                                 @RequestParam(value = "limit", defaultValue = "4") int limit) {
        return CommonResponse.success(reportService.getReports(roomId, currentUserId(), limit));
    }

    @PatchMapping("/{reportId}/read")
    public CommonResponse<WeeklyReportReadResponse> markRead(@PathVariable("roomId") Long roomId,
                                                             @PathVariable("reportId") Long reportId) {
        return CommonResponse.success(reportService.markRead(roomId, reportId, currentUserId()));
    }

    private Long currentUserId() {
        return (Long) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }
}
