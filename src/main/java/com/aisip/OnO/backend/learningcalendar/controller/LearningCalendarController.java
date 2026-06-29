package com.aisip.OnO.backend.learningcalendar.controller;

import com.aisip.OnO.backend.common.response.CommonResponse;
import com.aisip.OnO.backend.learningcalendar.dto.LearningCalendarMoodRequestDto;
import com.aisip.OnO.backend.learningcalendar.dto.LearningCalendarMoodResponseDto;
import com.aisip.OnO.backend.learningcalendar.dto.LearningCalendarResponseDto;
import com.aisip.OnO.backend.learningcalendar.service.LearningCalendarService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/learning-calendar")
public class LearningCalendarController {

    private final LearningCalendarService learningCalendarService;

    @GetMapping("")
    public CommonResponse<LearningCalendarResponseDto> getLearningCalendar(
            @RequestParam("year") int year,
            @RequestParam("month") int month
    ) {
        validateYearMonth(year, month);
        Long userId = (Long) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return CommonResponse.success(learningCalendarService.getLearningCalendar(userId, year, month));
    }

    @PatchMapping("/mood")
    public CommonResponse<LearningCalendarMoodResponseDto> updateMood(@RequestBody LearningCalendarMoodRequestDto request) {
        Long userId = (Long) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return CommonResponse.success(learningCalendarService.updateMood(userId, request));
    }

    private void validateYearMonth(int year, int month) {
        if (year < 1 || month < 1 || month > 12) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "잘못된 요청입니다.");
        }
    }
}
