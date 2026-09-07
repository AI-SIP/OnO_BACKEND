package com.aisip.OnO.backend.studyroom.quartz;

import com.aisip.OnO.backend.studyroom.entity.StudyRoom;
import com.aisip.OnO.backend.studyroom.service.StudyRoomWeeklyReportService;
import com.aisip.OnO.backend.studyroom.support.StudyRoomTestSupport;
import com.aisip.OnO.backend.user.entity.User;
import com.aisip.OnO.backend.util.fcm.dto.NotificationRequestDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.quartz.JobDataMap;
import org.quartz.JobExecutionContext;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * 스터디룸 Quartz 잡 검증.
 *
 * <p>잡은 스케줄러가 자동 실행하는 코드라 평소 테스트에 잡히지 않는다. 실행 자체는 스케줄러 없이
 * 직접 호출해 확인한다. FCM 은 실사용자 푸시 발송 경로이므로 베이스에서 잡아 둔 목으로만 검증한다.
 *
 * <p>{@link JobExecutionContext} 는 인터페이스라 지역 목으로 만든다. {@code @MockBean} 이 아니므로
 * 스프링 컨텍스트가 갈라지지 않는다.
 */
@DisplayName("스터디룸 Quartz 잡")
class StudyRoomQuartzJobTest extends StudyRoomTestSupport {

    @Autowired
    private ChallengeNotificationJob challengeNotificationJob;

    @Autowired
    private StudyRoomWeeklyReportJob weeklyReportJob;

    @Autowired
    private ChallengeNotificationScheduler notificationScheduler;

    @Autowired
    private StudyRoomWeeklyReportService reportService;

    @Nested
    @DisplayName("챌린지 알림 잡")
    class ChallengeNotification {

        @Test
        @DisplayName("중간 알림은 방의 모든 멤버에게 발송된다")
        void halfwayNotificationGoesToEveryMember() {
            RoomFixture fixture = createRoomWithMemberAndOutsider();

            challengeNotificationJob.execute(context(fixture.roomId(), "복습 챌린지", "HALFWAY"));

            ArgumentCaptor<NotificationRequestDto> dto = ArgumentCaptor.forClass(NotificationRequestDto.class);
            verify(fcmService).sendNotificationToAllUserDevice(eq(fixture.host().getId()), dto.capture());
            verify(fcmService).sendNotificationToAllUserDevice(eq(fixture.member().getId()), any());
            verify(fcmService, times(2)).sendNotificationToAllUserDevice(any(), any());

            assertThat(dto.getValue().title()).as("알림 제목").isEqualTo("챌린지 중간 알림");
            assertThat(dto.getValue().body()).as("알림 본문").contains("복습 챌린지").contains("절반");
            assertThat(dto.getValue().data())
                    .as("알림 데이터")
                    .containsEntry("type", "CHALLENGE_NOTIFICATION")
                    .containsEntry("roomId", String.valueOf(fixture.roomId()));
        }

        @Test
        @DisplayName("마감 하루 전 알림은 다른 문구로 발송된다")
        void oneDayLeftNotificationUsesDifferentCopy() {
            RoomFixture fixture = createRoomWithMemberAndOutsider();

            challengeNotificationJob.execute(context(fixture.roomId(), "복습 챌린지", "ONE_DAY_LEFT"));

            ArgumentCaptor<NotificationRequestDto> dto = ArgumentCaptor.forClass(NotificationRequestDto.class);
            verify(fcmService).sendNotificationToAllUserDevice(eq(fixture.host().getId()), dto.capture());

            assertThat(dto.getValue().title()).as("알림 제목").isEqualTo("챌린지 마감 D-1");
            assertThat(dto.getValue().body()).as("알림 본문").contains("내일 마감");
        }

        @Test
        @DisplayName("비멤버에게는 발송되지 않는다")
        void nonMemberIsNotNotified() {
            RoomFixture fixture = createRoomWithMemberAndOutsider();

            challengeNotificationJob.execute(context(fixture.roomId(), "복습 챌린지", "HALFWAY"));

            verify(fcmService, times(0))
                    .sendNotificationToAllUserDevice(eq(fixture.outsider().getId()), any());
        }

        @Test
        @DisplayName("멤버가 없는 방이면 아무것도 발송하지 않는다")
        void emptyRoomSendsNothing() {
            StudyRoom room = roomRepository.saveAndFlush(StudyRoom.create("빈 방", 1L));

            challengeNotificationJob.execute(context(room.getId(), "챌린지", "HALFWAY"));

            verifyNoInteractions(fcmService);
        }

        @Test
        @DisplayName("한 사용자에게 발송이 실패해도 나머지 멤버에게는 계속 시도한다")
        void failureForOneMemberDoesNotStopTheRest() {
            RoomFixture fixture = createRoomWithMemberAndOutsider();
            willThrow(new RuntimeException("FCM 장애"))
                    .given(fcmService).sendNotificationToAllUserDevice(eq(fixture.host().getId()), any());

            assertThatCode(() -> challengeNotificationJob.execute(
                    context(fixture.roomId(), "챌린지", "HALFWAY")))
                    .as("한 명이 실패해도 잡은 예외를 던지지 않는다")
                    .doesNotThrowAnyException();

            verify(fcmService).sendNotificationToAllUserDevice(eq(fixture.member().getId()), any());
        }

        private JobExecutionContext context(Long roomId, String challengeTitle, String notificationType) {
            JobDataMap dataMap = new JobDataMap();
            dataMap.put("roomId", String.valueOf(roomId));
            dataMap.put("challengeTitle", challengeTitle);
            dataMap.put("notificationType", notificationType);
            JobExecutionContext context = mock(JobExecutionContext.class);
            given(context.getMergedJobDataMap()).willReturn(dataMap);
            return context;
        }
    }

    @Nested
    @DisplayName("알림 예약")
    class NotificationScheduling {

        @Test
        @DisplayName("아직 오지 않은 시점의 알림만 등록되고 취소도 예외 없이 동작한다")
        void scheduleAndCancel() {
            User host = fixtures.createUser("host");
            StudyRoom room = createRoom(host, "예약 방");
            var challenge = saveChallenge(room, 5);

            assertThatCode(() -> notificationScheduler.scheduleNotifications(challenge))
                    .as("알림 등록").doesNotThrowAnyException();
            assertThatCode(() -> notificationScheduler.cancelNotifications(challenge.getId()))
                    .as("알림 취소").doesNotThrowAnyException();
        }

        @Test
        @DisplayName("이미 지난 챌린지는 알림을 등록하지 않아도 예외가 나지 않는다")
        void pastChallengeSchedulesNothing() {
            User host = fixtures.createUser("host");
            StudyRoom room = createRoom(host, "지난 방");
            var challenge = saveChallenge(room, "지난 챌린지",
                    com.aisip.OnO.backend.studyroom.entity.StudyRoomChallengeType.INDIVIDUAL,
                    com.aisip.OnO.backend.studyroom.entity.StudyRoomChallengeMetric.PROBLEM_COUNT,
                    null, null, 1,
                    java.time.LocalDateTime.now().minusDays(10),
                    java.time.LocalDateTime.now().minusDays(1));

            assertThatCode(() -> notificationScheduler.scheduleNotifications(challenge))
                    .as("지난 챌린지 알림 등록").doesNotThrowAnyException();
        }

        @Test
        @DisplayName("등록하지 않은 챌린지를 취소해도 예외가 나지 않는다")
        void cancellingUnknownChallengeIsSafe() {
            assertThatCode(() -> notificationScheduler.cancelNotifications(nonExistentChallengeId()))
                    .as("없는 챌린지 알림 취소").doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("주간 리포트 잡")
    class WeeklyReportJob {

        @Test
        @DisplayName("잡을 실행하면 방마다 리포트가 만들어진다")
        void executeCreatesReports() {
            User host = fixtures.createUser("host");
            StudyRoom room = createRoom(host, "리포트 방");

            weeklyReportJob.execute(mock(JobExecutionContext.class));

            LocalDate expectedWeekStart = LocalDate.now(ZoneId.of("Asia/Seoul"))
                    .with(TemporalAdjusters.previous(DayOfWeek.MONDAY));
            assertThat(weeklyReportRepository.findAll())
                    .as("잡이 만든 리포트")
                    .singleElement()
                    .satisfies(report -> {
                        assertThat(report.getRoom().getId()).as("대상 방").isEqualTo(room.getId());
                        assertThat(report.getWeekStart()).as("주 시작일").isEqualTo(expectedWeekStart);
                    });
        }

        @Test
        @DisplayName("두 번 실행해도 리포트가 중복 생성되지 않는다")
        void executeIsIdempotent() {
            User host = fixtures.createUser("host");
            createRoom(host, "리포트 방");

            weeklyReportJob.execute(mock(JobExecutionContext.class));
            weeklyReportJob.execute(mock(JobExecutionContext.class));

            assertThat(weeklyReportRepository.findAll()).as("생성된 리포트").hasSize(1);
        }

        @Test
        @DisplayName("잡과 서비스 직접 호출은 같은 결과를 만든다")
        void jobDelegatesToService() {
            User host = fixtures.createUser("host");
            createRoom(host, "리포트 방");

            weeklyReportJob.execute(mock(JobExecutionContext.class));
            long afterJob = weeklyReportRepository.count();
            reportService.createPreviousWeekReports();

            assertThat(weeklyReportRepository.count())
                    .as("서비스 직접 호출 후 개수")
                    .isEqualTo(afterJob);
        }
    }
}
