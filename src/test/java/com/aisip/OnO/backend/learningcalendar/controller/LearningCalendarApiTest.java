package com.aisip.OnO.backend.learningcalendar.controller;

import com.aisip.OnO.backend.common.emoji.CustomEmojiErrorCase;
import com.aisip.OnO.backend.learningcalendar.exception.LearningCalendarErrorCase;
import com.aisip.OnO.backend.learningcalendar.support.LearningCalendarTestSupport;
import com.aisip.OnO.backend.problem.entity.Problem;
import com.aisip.OnO.backend.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;

import java.time.LocalDate;
import java.time.ZoneId;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 학습 달력 API 를 서비스·리포지토리까지 실제로 태워 검증한다.
 *
 * <p>서비스를 목으로 갈아끼우면 컨트롤러가 SecurityContext 에서 꺼낸 userId 를
 * 실제 조회에 쓰는지 확인할 수 없어, 사용자 격리가 깨져도 테스트가 통과한다.
 */
@DisplayName("학습 달력 API")
class LearningCalendarApiTest extends LearningCalendarTestSupport {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private User owner;
    private User other;

    @BeforeEach
    void setUpUsers() {
        owner = fixtures.createUser("calendar-owner");
        other = fixtures.createOtherUser();
    }

    @Nested
    @DisplayName("달력 조회")
    class GetCalendar {

        @Test
        @DisplayName("내 학습 기록으로 계산한 달력을 내려준다")
        void returnsCalendarOfAuthenticatedUser() throws Exception {
            LocalDate today = LocalDate.now(KST);
            Problem note = saveNoteWrittenAt(owner.getId(), today.atTime(8, 0), "메모", "이차방정식 오답노트");
            saveSolveAt(owner.getId(), note, today.atTime(9, 0), 600);
            saveSolveAt(owner.getId(), note, today.atTime(10, 0), 120);

            int todayIndex = today.getDayOfMonth() - 1;

            authenticateAs(owner.getId());

            mockMvc.perform(get("/api/learning-calendar")
                            .param("year", String.valueOf(today.getYear()))
                            .param("month", String.valueOf(today.getMonthValue())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.year").value(today.getYear()))
                    .andExpect(jsonPath("$.data.month").value(today.getMonthValue()))
                    .andExpect(jsonPath("$.data.currentStreak").value(1))
                    .andExpect(jsonPath("$.data.bestStreak").value(1))
                    .andExpect(jsonPath("$.data.thisMonthStudyDays").value(1))
                    .andExpect(jsonPath("$.data.records.length()").value(today.lengthOfMonth()))
                    // 레코드는 1일부터 말일까지 순서대로 담기므로 오늘의 인덱스는 (일 - 1)이다.
                    .andExpect(jsonPath("$.data.records[" + todayIndex + "].date").value(today.toString()))
                    .andExpect(jsonPath("$.data.records[" + todayIndex + "].hasStudied").value(true))
                    .andExpect(jsonPath("$.data.records[" + todayIndex + "].reviewCount").value(2))
                    .andExpect(jsonPath("$.data.records[" + todayIndex + "].noteWriteCount").value(1))
                    .andExpect(jsonPath("$.data.records[" + todayIndex + "].studyMinutes").value(12))
                    .andExpect(jsonPath("$.data.records[" + todayIndex + "].reviewedItems[0]")
                            .value("이차방정식 오답노트"));

            evictStreakCache(owner.getId());
        }

        @Test
        @DisplayName("다른 사용자의 학습 기록은 내 달력에 포함되지 않는다")
        void doesNotLeakOtherUsersRecords() throws Exception {
            LocalDate today = LocalDate.now(KST);
            Problem otherNote = saveNoteWrittenAt(other.getId(), today.atTime(8, 0));
            saveSolveAt(other.getId(), otherNote, today.atTime(9, 0), 600);

            authenticateAs(owner.getId());

            mockMvc.perform(get("/api/learning-calendar")
                            .param("year", String.valueOf(today.getYear()))
                            .param("month", String.valueOf(today.getMonthValue())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.thisMonthStudyDays").value(0))
                    .andExpect(jsonPath("$.data.currentStreak").value(0));

            evictStreakCache(owner.getId());
        }

        @ParameterizedTest(name = "month={0}")
        @ValueSource(strings = {"0", "13", "-1"})
        @DisplayName("범위를 벗어난 월은 400을 반환한다")
        void rejectsOutOfRangeMonth(String month) throws Exception {
            authenticateAs(owner.getId());

            mockMvc.perform(get("/api/learning-calendar")
                            .param("year", "2026")
                            .param("month", month))
                    .andExpect(status().isBadRequest());
        }

        @ParameterizedTest(name = "year={0}")
        @ValueSource(strings = {"0", "-1"})
        @DisplayName("범위를 벗어난 연도는 400을 반환한다")
        void rejectsOutOfRangeYear(String year) throws Exception {
            authenticateAs(owner.getId());

            mockMvc.perform(get("/api/learning-calendar")
                            .param("year", year)
                            .param("month", "5"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("월 파라미터가 숫자가 아니면 400을 반환한다")
        void rejectsNonNumericMonth() throws Exception {
            authenticateAs(owner.getId());

            mockMvc.perform(get("/api/learning-calendar")
                            .param("year", "2026")
                            .param("month", "may"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("필수 파라미터가 없으면 400을 반환한다")
        void rejectsMissingParameter() throws Exception {
            authenticateAs(owner.getId());

            mockMvc.perform(get("/api/learning-calendar").param("year", "2026"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("인증 없이 조회하면 401을 반환한다")
        void rejectsUnauthenticated() throws Exception {
            clearAuthentication();

            mockMvc.perform(get("/api/learning-calendar")
                            .param("year", "2026")
                            .param("month", "5"))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("감정 이모지 저장")
    class UpdateMood {

        @Test
        @DisplayName("학습 기록이 있는 날짜에 이모지를 저장한다")
        void savesMood() throws Exception {
            LocalDate date = LocalDate.of(2026, 6, 7);
            saveNoteWrittenAt(owner.getId(), date.atTime(8, 0));

            authenticateAs(owner.getId());

            mockMvc.perform(patch("/api/learning-calendar/mood")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"date": "2026-06-07", "emojiKey": "happy_tears"}
                                    """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.date").value("2026-06-07"))
                    .andExpect(jsonPath("$.data.emojiKey").value("happy_tears"));
        }

        @Test
        @DisplayName("학습 기록이 없는 날짜는 404와 12001을 반환한다")
        void rejectsDateWithoutRecord() throws Exception {
            authenticateAs(owner.getId());

            mockMvc.perform(patch("/api/learning-calendar/mood")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"date": "2026-06-07", "emojiKey": "happy_tears"}
                                    """))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.errorCode")
                            .value(LearningCalendarErrorCase.CALENDAR_RECORD_NOT_FOUND.getErrorCode()));
        }

        @Test
        @DisplayName("날짜가 없으면 400과 12002를 반환한다")
        void rejectsMissingDate() throws Exception {
            authenticateAs(owner.getId());

            mockMvc.perform(patch("/api/learning-calendar/mood")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"emojiKey": "happy_tears"}
                                    """))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode")
                            .value(LearningCalendarErrorCase.INVALID_DATE_FORMAT.getErrorCode()));
        }

        @Test
        @DisplayName("허용 목록에 없는 이모지 키는 400과 11001을 반환한다")
        void rejectsUnknownEmoji() throws Exception {
            LocalDate date = LocalDate.of(2026, 6, 7);
            saveNoteWrittenAt(owner.getId(), date.atTime(8, 0));

            authenticateAs(owner.getId());

            mockMvc.perform(patch("/api/learning-calendar/mood")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"date": "2026-06-07", "emojiKey": "not_an_emoji"}
                                    """))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode")
                            .value(CustomEmojiErrorCase.INVALID_EMOJI_KEY.getErrorCode()));
        }

        @Test
        @DisplayName("다른 사용자만 학습한 날짜에는 저장할 수 없다")
        void cannotSaveMoodOnAnotherUsersDate() throws Exception {
            LocalDate date = LocalDate.of(2026, 6, 7);
            saveNoteWrittenAt(other.getId(), date.atTime(8, 0));

            authenticateAs(owner.getId());

            mockMvc.perform(patch("/api/learning-calendar/mood")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"date": "2026-06-07", "emojiKey": "happy_tears"}
                                    """))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.errorCode")
                            .value(LearningCalendarErrorCase.CALENDAR_RECORD_NOT_FOUND.getErrorCode()));
        }

        @Test
        @DisplayName("인증 없이 저장하면 401을 반환한다")
        void rejectsUnauthenticated() throws Exception {
            clearAuthentication();

            mockMvc.perform(patch("/api/learning-calendar/mood")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"date": "2026-06-07", "emojiKey": "happy_tears"}
                                    """))
                    .andExpect(status().isUnauthorized());
        }
    }
}
