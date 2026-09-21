package com.aisip.OnO.backend.studyroom.integration;

import com.aisip.OnO.backend.studyroom.entity.StudyRoom;
import com.aisip.OnO.backend.studyroom.entity.StudyRoomWeeklyReport;
import com.aisip.OnO.backend.studyroom.support.StudyRoomTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("스터디룸 주간 리포트 API")
class StudyRoomWeeklyReportApiTest extends StudyRoomTestSupport {

    @Nested
    @DisplayName("조회")
    class GetReports {

        @Test
        @DisplayName("리포트가 없으면 빈 목록을 준다")
        void emptyWhenNoReport() throws Exception {
            RoomFixture fixture = createRoomWithMemberAndOutsider();
            authenticateAs(fixture.member().getId());

            mockMvc.perform(get("/api/study-room/{roomId}/weekly-reports", fixture.roomId()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data").isEmpty());
        }

        @Test
        @DisplayName("최신 주가 먼저 오고 읽지 않은 리포트는 isRead=false 다")
        void latestFirstAndUnread() throws Exception {
            RoomFixture fixture = createRoomWithMemberAndOutsider();
            StudyRoomWeeklyReport older = saveWeeklyReport(fixture.room(), LocalDate.of(2026, 1, 5));
            StudyRoomWeeklyReport newer = saveWeeklyReport(fixture.room(), LocalDate.of(2026, 1, 12));
            authenticateAs(fixture.member().getId());

            mockMvc.perform(get("/api/study-room/{roomId}/weekly-reports", fixture.roomId()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.length()").value(2))
                    .andExpect(jsonPath("$.data[0].reportId").value(newer.getId()))
                    .andExpect(jsonPath("$.data[1].reportId").value(older.getId()))
                    .andExpect(jsonPath("$.data[0].isRead").value(false));
        }

        @Test
        @DisplayName("리포트의 집계 값과 응원 메시지가 그대로 내려온다")
        void reportFieldsAreSerialized() throws Exception {
            RoomFixture fixture = createRoomWithMemberAndOutsider();
            saveWeeklyReport(fixture.room(), LocalDate.of(2026, 1, 5));
            authenticateAs(fixture.member().getId());

            mockMvc.perform(get("/api/study-room/{roomId}/weekly-reports", fixture.roomId()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data[0].weekStart").value("2026-01-05"))
                    .andExpect(jsonPath("$.data[0].weekEnd").value("2026-01-11"))
                    .andExpect(jsonPath("$.data[0].topMemberName").value("탑멤버"))
                    .andExpect(jsonPath("$.data[0].topMemberProblemCount").value(10))
                    .andExpect(jsonPath("$.data[0].longestStreakDays").value(5))
                    .andExpect(jsonPath("$.data[0].totalProblems").value(20))
                    .andExpect(jsonPath("$.data[0].challengesCompleted").value(1))
                    .andExpect(jsonPath("$.data[0].cheerMessage").value("이번 주도 모두 고생했어요!"));
        }

        @Test
        @DisplayName("프로필 이미지가 없는 리포트도 정상 응답한다")
        void nullProfileImageUrlIsFine() throws Exception {
            RoomFixture fixture = createRoomWithMemberAndOutsider();
            saveWeeklyReport(fixture.room(), LocalDate.of(2026, 1, 5));
            authenticateAs(fixture.member().getId());

            mockMvc.perform(get("/api/study-room/{roomId}/weekly-reports", fixture.roomId()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data[0].topMemberProfileImageUrl").doesNotExist())
                    .andExpect(jsonPath("$.data[0].longestStreakProfileImageUrl").doesNotExist());
        }

        @ParameterizedTest(name = "limit={0} → {1}건")
        @CsvSource({"1, 1", "3, 3", "0, 1", "-5, 1", "100, 5"})
        @DisplayName("limit 은 1 이상 12 이하로 보정된다")
        void limitIsClamped(int limit, int expectedSize) throws Exception {
            RoomFixture fixture = createRoomWithMemberAndOutsider();
            for (int i = 0; i < 5; i++) {
                saveWeeklyReport(fixture.room(), LocalDate.of(2026, 1, 5).minusWeeks(i));
            }
            authenticateAs(fixture.member().getId());

            mockMvc.perform(get("/api/study-room/{roomId}/weekly-reports", fixture.roomId())
                            .param("limit", String.valueOf(limit)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.length()").value(expectedSize));
        }

        @Test
        @DisplayName("다른 방의 리포트는 섞이지 않는다")
        void reportsAreScopedToRoom() throws Exception {
            RoomFixture fixture = createRoomWithMemberAndOutsider();
            StudyRoom otherRoom = createRoom(fixture.outsider(), "남의 방");
            saveWeeklyReport(otherRoom, LocalDate.of(2026, 1, 5));
            authenticateAs(fixture.member().getId());

            mockMvc.perform(get("/api/study-room/{roomId}/weekly-reports", fixture.roomId()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data").isEmpty());
        }

        @Test
        @DisplayName("비멤버는 주간 리포트를 볼 수 없다")
        void nonMemberCannotRead() throws Exception {
            RoomFixture fixture = createRoomWithMemberAndOutsider();
            saveWeeklyReport(fixture.room(), LocalDate.of(2026, 1, 5));
            authenticateAs(fixture.outsider().getId());

            mockMvc.perform(get("/api/study-room/{roomId}/weekly-reports", fixture.roomId()))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.errorCode").value(10002));
        }

        @Test
        @DisplayName("인증 없이 조회하면 401 이다")
        void unauthenticatedIsRejected() throws Exception {
            RoomFixture fixture = createRoomWithMemberAndOutsider();
            clearAuthentication();

            mockMvc.perform(get("/api/study-room/{roomId}/weekly-reports", fixture.roomId()))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("읽음 처리")
    class MarkRead {

        @Test
        @DisplayName("읽음 처리하면 이후 조회에서 isRead=true 다")
        void markingReadIsReflected() throws Exception {
            RoomFixture fixture = createRoomWithMemberAndOutsider();
            StudyRoomWeeklyReport report = saveWeeklyReport(fixture.room(), LocalDate.of(2026, 1, 5));
            authenticateAs(fixture.member().getId());

            mockMvc.perform(patch("/api/study-room/{roomId}/weekly-reports/{reportId}/read",
                            fixture.roomId(), report.getId()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.reportId").value(report.getId()))
                    .andExpect(jsonPath("$.data.isRead").value(true));

            mockMvc.perform(get("/api/study-room/{roomId}/weekly-reports", fixture.roomId()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data[0].isRead").value(true));
        }

        @Test
        @DisplayName("읽음 처리는 사용자별로 따로 관리된다")
        void readStateIsPerUser() throws Exception {
            RoomFixture fixture = createRoomWithMemberAndOutsider();
            StudyRoomWeeklyReport report = saveWeeklyReport(fixture.room(), LocalDate.of(2026, 1, 5));
            authenticateAs(fixture.member().getId());
            mockMvc.perform(patch("/api/study-room/{roomId}/weekly-reports/{reportId}/read",
                            fixture.roomId(), report.getId()))
                    .andExpect(status().isOk());

            authenticateAs(fixture.host().getId());
            mockMvc.perform(get("/api/study-room/{roomId}/weekly-reports", fixture.roomId()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data[0].isRead").value(false));
        }

        @Test
        @DisplayName("두 번 읽음 처리해도 기록은 하나만 남는다")
        void markingReadTwiceIsIdempotent() throws Exception {
            RoomFixture fixture = createRoomWithMemberAndOutsider();
            StudyRoomWeeklyReport report = saveWeeklyReport(fixture.room(), LocalDate.of(2026, 1, 5));
            authenticateAs(fixture.member().getId());

            mockMvc.perform(patch("/api/study-room/{roomId}/weekly-reports/{reportId}/read",
                    fixture.roomId(), report.getId())).andExpect(status().isOk());
            mockMvc.perform(patch("/api/study-room/{roomId}/weekly-reports/{reportId}/read",
                    fixture.roomId(), report.getId())).andExpect(status().isOk());

            assertThat(weeklyReportReadRepository.findAll()).as("저장된 읽음 기록").hasSize(1);
        }

        @Test
        @DisplayName("존재하지 않는 리포트는 404 다")
        void unknownReportIsNotFound() throws Exception {
            RoomFixture fixture = createRoomWithMemberAndOutsider();
            authenticateAs(fixture.member().getId());

            mockMvc.perform(patch("/api/study-room/{roomId}/weekly-reports/{reportId}/read",
                            fixture.roomId(), nonExistentReportId()))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.errorCode").value(10014));
        }

        @Test
        @DisplayName("다른 방의 리포트 ID 로는 읽음 처리할 수 없다")
        void reportFromAnotherRoomIsNotFound() throws Exception {
            RoomFixture fixture = createRoomWithMemberAndOutsider();
            StudyRoom otherRoom = createRoom(fixture.outsider(), "남의 방");
            StudyRoomWeeklyReport otherReport = saveWeeklyReport(otherRoom, LocalDate.of(2026, 1, 5));
            authenticateAs(fixture.member().getId());

            mockMvc.perform(patch("/api/study-room/{roomId}/weekly-reports/{reportId}/read",
                            fixture.roomId(), otherReport.getId()))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.errorCode").value(10014));

            assertThat(weeklyReportReadRepository.findAll()).as("저장된 읽음 기록").isEmpty();
        }

        @Test
        @DisplayName("비멤버는 읽음 처리할 수 없다")
        void nonMemberCannotMarkRead() throws Exception {
            RoomFixture fixture = createRoomWithMemberAndOutsider();
            StudyRoomWeeklyReport report = saveWeeklyReport(fixture.room(), LocalDate.of(2026, 1, 5));
            authenticateAs(fixture.outsider().getId());

            mockMvc.perform(patch("/api/study-room/{roomId}/weekly-reports/{reportId}/read",
                            fixture.roomId(), report.getId()))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.errorCode").value(10002));

            assertThat(weeklyReportReadRepository.findAll()).as("저장된 읽음 기록").isEmpty();
        }

        @Test
        @DisplayName("인증 없이 읽음 처리하면 401 이다")
        void unauthenticatedIsRejected() throws Exception {
            RoomFixture fixture = createRoomWithMemberAndOutsider();
            StudyRoomWeeklyReport report = saveWeeklyReport(fixture.room(), LocalDate.of(2026, 1, 5));
            clearAuthentication();

            mockMvc.perform(patch("/api/study-room/{roomId}/weekly-reports/{reportId}/read",
                            fixture.roomId(), report.getId()))
                    .andExpect(status().isUnauthorized());
        }
    }
}
