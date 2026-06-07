package com.aisip.OnO.backend.learningcalendar.controller;

import com.aisip.OnO.backend.auth.WithMockCustomUser;
import com.aisip.OnO.backend.learningcalendar.dto.LearningCalendarResponseDto;
import com.aisip.OnO.backend.learningcalendar.service.LearningCalendarService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(SpringExtension.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class LearningCalendarControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private LearningCalendarService learningCalendarService;

    @Test
    @DisplayName("학습 달력 조회")
    @WithMockCustomUser(userId = 7L)
    void getLearningCalendar() throws Exception {
        LearningCalendarResponseDto response = LearningCalendarResponseDto.builder()
                .year(2026)
                .month(5)
                .currentStreak(3)
                .bestStreak(10)
                .thisMonthStudyDays(1)
                .records(List.of(LearningCalendarResponseDto.DailyStudyRecord.builder()
                        .date(LocalDate.of(2026, 5, 1))
                        .hasStudied(true)
                        .reviewCount(2)
                        .noteWriteCount(1)
                        .studyMinutes(12)
                        .reviewedItems(List.of("이차방정식 오답노트"))
                        .moodEmojiKey("happy_tears")
                        .build()))
                .build();
        given(learningCalendarService.getLearningCalendar(7L, 2026, 5)).willReturn(response);

        mockMvc.perform(get("/api/learning-calendar")
                        .param("year", "2026")
                        .param("month", "5")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.year").value(2026))
                .andExpect(jsonPath("$.data.month").value(5))
                .andExpect(jsonPath("$.data.currentStreak").value(3))
                .andExpect(jsonPath("$.data.bestStreak").value(10))
                .andExpect(jsonPath("$.data.thisMonthStudyDays").value(1))
                .andExpect(jsonPath("$.data.records[0].date").value("2026-05-01"))
                .andExpect(jsonPath("$.data.records[0].hasStudied").value(true))
                .andExpect(jsonPath("$.data.records[0].reviewCount").value(2))
                .andExpect(jsonPath("$.data.records[0].noteWriteCount").value(1))
                .andExpect(jsonPath("$.data.records[0].studyMinutes").value(12))
                .andExpect(jsonPath("$.data.records[0].reviewedItems[0]").value("이차방정식 오답노트"))
                .andExpect(jsonPath("$.data.records[0].moodEmojiKey").value("happy_tears"));

        verify(learningCalendarService).getLearningCalendar(7L, 2026, 5);
    }

    @Test
    @DisplayName("학습 달력 감정 이모지 저장")
    @WithMockCustomUser(userId = 7L)
    void updateMood() throws Exception {
        given(learningCalendarService.updateMood(7L, new com.aisip.OnO.backend.learningcalendar.dto.LearningCalendarMoodRequestDto(
                LocalDate.of(2026, 6, 7),
                "happy_tears"
        ))).willReturn(new com.aisip.OnO.backend.learningcalendar.dto.LearningCalendarMoodResponseDto(
                LocalDate.of(2026, 6, 7),
                "happy_tears"
        ));

        mockMvc.perform(patch("/api/learning-calendar/mood")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "date": "2026-06-07",
                                  "emojiKey": "happy_tears"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.date").value("2026-06-07"))
                .andExpect(jsonPath("$.data.emojiKey").value("happy_tears"));
    }

    @Test
    @DisplayName("학습 달력 조회 - 잘못된 월은 400을 반환한다")
    @WithMockCustomUser(userId = 7L)
    void getLearningCalendar_invalidMonth() throws Exception {
        mockMvc.perform(get("/api/learning-calendar")
                        .param("year", "2026")
                        .param("month", "13")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }
}
