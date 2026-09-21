package com.aisip.OnO.backend.concurrency;

import com.aisip.OnO.backend.studyroom.entity.StudyRoomWeeklyReport;
import com.aisip.OnO.backend.studyroom.service.StudyRoomWeeklyReportService;
import com.aisip.OnO.backend.studyroom.support.StudyRoomTestSupport;
import com.aisip.OnO.backend.user.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 주간 리포트 읽음 처리의 동시 요청.
 *
 * <p>{@code markRead} 는 읽음 기록을 찾아보고 없으면 넣는 check-then-act 이고,
 * study_room_weekly_report_read 에는 {@code (report_id, user_id)} 유니크 제약이 있다.
 * 리포트를 여는 순간 요청이 두 번 나가면 두 트랜잭션이 모두 "안 읽음"을 읽고 INSERT 해
 * 유니크 제약 위반이 500 으로 나갔다.
 */
@DisplayName("동시성 - 주간 리포트 읽음 처리")
class StudyRoomWeeklyReportReadConcurrencyTest extends StudyRoomTestSupport {

    private static final int THREAD_COUNT = 8;

    @Autowired
    private StudyRoomWeeklyReportService weeklyReportService;

    @Nested
    @DisplayName("읽음 기록 중복")
    class DuplicateReadRecord {

        @Test
        @DisplayName("같은 리포트를 8번 동시에 읽음 처리해도 500 없이 기록은 한 건만 남는다")
        void marksReadOnceUnderConcurrentRequests() {
            RoomFixture fixture = createRoomWithMemberAndOutsider();
            StudyRoomWeeklyReport report = saveWeeklyReport(fixture.room(), lastWeekMonday());

            ConcurrentRunner.Outcome outcome = ConcurrentRunner.runConcurrently(THREAD_COUNT,
                    () -> weeklyReportService.markRead(fixture.roomId(), report.getId(), fixture.member().getId()));

            assertThat(outcome.serverErrors())
                    .as("유니크 제약 위반이 그대로 올라오면 사용자에게 500 이 나간다")
                    .isEmpty();
            assertThat(weeklyReportReadRepository.findAllByReportIdsAndUserId(
                    List.of(report.getId()), fixture.member().getId()))
                    .as("읽음 기록은 사용자당 한 건")
                    .hasSize(1);
        }

        @Test
        @DisplayName("여러 멤버가 동시에 읽어도 각자의 기록이 하나씩 남는다")
        void keepsOneRecordPerMember() {
            RoomFixture fixture = createRoomWithMemberAndOutsider();
            StudyRoomWeeklyReport report = saveWeeklyReport(fixture.room(), lastWeekMonday());
            List<Long> readerIds = new ArrayList<>();
            for (int i = 0; i < THREAD_COUNT; i++) {
                User reader = fixtures.createUser("reader");
                addMember(fixture.room(), reader);
                readerIds.add(reader.getId());
            }

            ConcurrentRunner.Outcome outcome = ConcurrentRunner.runConcurrently(THREAD_COUNT,
                    index -> weeklyReportService.markRead(fixture.roomId(), report.getId(), readerIds.get(index)));

            assertThat(outcome.failures()).as("사용자가 다르면 충돌할 이유가 없다").isEmpty();
            assertThat(weeklyReportReadRepository.count())
                    .as("여덟 명의 읽음 기록이 하나씩")
                    .isEqualTo(THREAD_COUNT);
        }
    }

    private LocalDate lastWeekMonday() {
        return LocalDate.now().with(TemporalAdjusters.previous(DayOfWeek.MONDAY));
    }
}
