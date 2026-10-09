package com.aisip.OnO.backend.learningreport.controller;

import com.aisip.OnO.backend.folder.entity.Folder;
import com.aisip.OnO.backend.learningreport.support.LearningReportTestSupport;
import com.aisip.OnO.backend.problem.entity.Problem;
import com.aisip.OnO.backend.problemsolve.entity.AnswerStatus;
import com.aisip.OnO.backend.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("학습 보고서 개요 API")
class LearningOverviewApiTest extends LearningReportTestSupport {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final String URL = "/api/learning-reports/overview";

    private User owner;
    private User other;

    @BeforeEach
    void setUpUsers() {
        owner = fixtures.createUser("overview-owner");
        other = fixtures.createOtherUser();
    }

    private static LocalDateTime at(int month, int day) {
        return LocalDateTime.of(2026, month, day, 10, 0);
    }

    private static LocalDate thisMonday() {
        return LocalDate.now(KST).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    /**
     * 지난 주(2026-09-14 ~ 09-20)를 본다. 이미 끝난 주라 비교는 그 앞 주(09-07 ~ 09-13) 전체다.
     * 문제 하나의 기록이 09-08 정답, 09-15 정답, 09-16 오답, 09-17 부분 정답이라 지금은 헷갈리는 문제다.
     */
    @Test
    @DisplayName("계약대로 모든 필드를 내려준다")
    void returnsContractShape() throws Exception {
        Folder root = fixtures.createFolder(owner.getId(), "책장", null);
        Problem problem = saveNoteInFolder(owner.getId(), root, at(9, 1));
        saveSolve(owner.getId(), problem, at(9, 8), AnswerStatus.CORRECT, null);
        saveSolve(owner.getId(), problem, at(9, 15), AnswerStatus.CORRECT, null);
        saveSolve(owner.getId(), problem, at(9, 16), AnswerStatus.WRONG, null);
        saveSolve(owner.getId(), problem, at(9, 17), AnswerStatus.PARTIAL, null);

        authenticateAs(owner.getId());

        mockMvc.perform(get(URL).param("period", "WEEK").param("baseDate", "2026-09-16"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.period").value("WEEK"))
                .andExpect(jsonPath("$.data.startDate").value("2026-09-14"))
                .andExpect(jsonPath("$.data.endDate").value("2026-09-20"))
                .andExpect(jsonPath("$.data.hasPrevious").value(true))
                .andExpect(jsonPath("$.data.hasNext").value(true))
                .andExpect(jsonPath("$.data.summary.reviewCount").value(3))
                .andExpect(jsonPath("$.data.summary.accuracy").value(50.0))
                .andExpect(jsonPath("$.data.summary.studyDays").value(3))
                .andExpect(jsonPath("$.data.summary.currentStreak").value(0))
                .andExpect(jsonPath("$.data.previous.reviewCount").value(1))
                .andExpect(jsonPath("$.data.previous.accuracy").value(100.0))
                .andExpect(jsonPath("$.data.previous.studyDays").value(1))
                .andExpect(jsonPath("$.data.noteStatus.totalCount").value(1))
                .andExpect(jsonPath("$.data.noteStatus.knownCount").value(0))
                .andExpect(jsonPath("$.data.noteStatus.unsureCount").value(1))
                .andExpect(jsonPath("$.data.noteStatus.unsolvedCount").value(0))
                .andExpect(jsonPath("$.data.noteStatus.newlyKnownCount").value(0))
                .andExpect(jsonPath("$.data.noteStatus.knownThreshold").value(3))
                .andExpect(jsonPath("$.data.weakFolders.length()").value(1))
                .andExpect(jsonPath("$.data.weakFolders[0].folderId").value(root.getId()))
                .andExpect(jsonPath("$.data.weakFolders[0].name").value("책장"))
                .andExpect(jsonPath("$.data.weakFolders[0].solveCount").value(3))
                .andExpect(jsonPath("$.data.weakFolders[0].wrongCount").value(1))
                .andExpect(jsonPath("$.data.weakFolders[0].accuracy").value(50.0))
                .andExpect(jsonPath("$.data.trend.length()").value(7))
                .andExpect(jsonPath("$.data.trend[0].startDate").value("2026-09-14"))
                .andExpect(jsonPath("$.data.trend[0].endDate").value("2026-09-14"))
                .andExpect(jsonPath("$.data.trend[0].reviewCount").value(0))
                .andExpect(jsonPath("$.data.trend[1].reviewCount").value(1))
                .andExpect(jsonPath("$.data.trend[2].reviewCount").value(1))
                .andExpect(jsonPath("$.data.trend[3].reviewCount").value(1))
                .andExpect(jsonPath("$.data.trend[6].startDate").value("2026-09-20"));
    }

    @Test
    @DisplayName("전체는 previous 를 null 로, 정답률을 셀 기록이 없으면 accuracy 를 null 로 내려준다")
    void totalHasNullPrevious() throws Exception {
        authenticateAs(owner.getId());

        mockMvc.perform(get(URL).param("period", "TOTAL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.period").value("TOTAL"))
                .andExpect(jsonPath("$.data.startDate").value(nullValue()))
                .andExpect(jsonPath("$.data.endDate").value(LocalDate.now(KST).toString()))
                .andExpect(jsonPath("$.data.previous").value(nullValue()))
                .andExpect(jsonPath("$.data.summary.accuracy").value(nullValue()))
                .andExpect(jsonPath("$.data.hasPrevious").value(false))
                .andExpect(jsonPath("$.data.hasNext").value(false))
                .andExpect(jsonPath("$.data.trend.length()").value(6));
    }

    @Test
    @DisplayName("period 를 생략하면 오늘(KST)이 든 주를 준다")
    void defaultsToCurrentWeek() throws Exception {
        authenticateAs(owner.getId());

        mockMvc.perform(get(URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.period").value("WEEK"))
                .andExpect(jsonPath("$.data.startDate").value(thisMonday().toString()))
                .andExpect(jsonPath("$.data.endDate").value(thisMonday().plusDays(6).toString()))
                .andExpect(jsonPath("$.data.hasNext").value(false));
    }

    @Test
    @DisplayName("잘못된 period 는 400 이다")
    void rejectsUnknownPeriod() throws Exception {
        authenticateAs(owner.getId());

        mockMvc.perform(get(URL).param("period", "YEAR"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("오늘보다 뒤의 baseDate 는 오늘로 맞춘다")
    void clampsFutureBaseDate() throws Exception {
        authenticateAs(owner.getId());

        mockMvc.perform(get(URL).param("period", "WEEK")
                        .param("baseDate", LocalDate.now(KST).plusDays(30).toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.startDate").value(thisMonday().toString()))
                .andExpect(jsonPath("$.data.hasNext").value(false));
    }

    @Test
    @DisplayName("다른 사용자의 문제, 풀이, 폴더는 섞이지 않는다")
    void doesNotMixOtherUsersData() throws Exception {
        LocalDateTime todayMorning = LocalDate.now(KST).atTime(9, 0);

        Problem mine = saveNoteWrittenAt(owner.getId(), todayMorning.minusDays(30));
        saveSolve(owner.getId(), mine, todayMorning, AnswerStatus.CORRECT, null);

        Folder othersFolder = fixtures.createFolder(other.getId(), "남의 폴더", null);
        Problem theirs = saveNoteInFolder(other.getId(), othersFolder, todayMorning.minusDays(30));
        saveNoteWrittenAt(other.getId(), todayMorning.minusDays(30));
        saveSolve(other.getId(), theirs, todayMorning, AnswerStatus.WRONG, null);
        saveSolve(other.getId(), theirs, todayMorning.minusDays(1), AnswerStatus.WRONG, null);

        authenticateAs(owner.getId());

        mockMvc.perform(get(URL).param("period", "WEEK"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.summary.reviewCount").value(1))
                .andExpect(jsonPath("$.data.summary.accuracy").value(100.0))
                .andExpect(jsonPath("$.data.summary.currentStreak").value(1))
                .andExpect(jsonPath("$.data.noteStatus.totalCount").value(1))
                .andExpect(jsonPath("$.data.noteStatus.unsureCount").value(1))
                .andExpect(jsonPath("$.data.weakFolders.length()").value(0));
    }
}
