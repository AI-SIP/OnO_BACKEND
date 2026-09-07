package com.aisip.OnO.backend.problem.quartz;

import com.aisip.OnO.backend.problem.dto.ProblemRegisterDto;
import com.aisip.OnO.backend.problem.entity.Problem;
import com.aisip.OnO.backend.problem.repository.ProblemRepository;
import com.aisip.OnO.backend.support.IntegrationTestSupport;
import com.aisip.OnO.backend.user.entity.User;
import com.aisip.OnO.backend.user.repository.UserRepository;
import com.aisip.OnO.backend.util.fcm.dto.NotificationRequestDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * 복습 알림 배치의 발송 대상 선정을 검증한다.
 *
 * <p>FCM 은 실사용자 푸시 발송 경로다. {@code fcmService} 는 {@link IntegrationTestSupport} 에서
 * 목으로 잡혀 있으므로 실제 발송은 일어나지 않고, 여기서는 "누구에게 무엇을 보내려 했는지"만 본다.
 *
 * <p>배치는 {@code LocalDate.now(Asia/Seoul)} 을 직접 읽으므로 기준일을 주입할 수 없다.
 * 픽스처를 실행 시점의 서울 날짜 기준 상대일로 만들어 실행 날짜와 무관하게 통과하도록 한다.
 */
@DisplayName("ReviewDueNotificationJob")
class ReviewDueNotificationJobTest extends IntegrationTestSupport {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    @Autowired
    private AutowireCapableBeanFactory beanFactory;

    @Autowired
    private ProblemRepository problemRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private LocalDate today;

    @BeforeEach
    void setUpToday() {
        today = LocalDate.now(KST);
    }

    // ─────────────────────────── 실행 ───────────────────────────

    private void runJob() {
        ReviewDueNotificationJob job = beanFactory.createBean(ReviewDueNotificationJob.class);
        job.executeInternal(null);
    }

    // ─────────────────────────── 픽스처 ───────────────────────────

    /** 오늘 접속했고 알림을 켠 사용자. 복습 알림(흐름 1)의 기본 대상. */
    private User activeUser(String name) {
        return userWithActivity(name, today.atTime(9, 0), null, true);
    }

    private User userWithActivity(
            String name, LocalDateTime lastActiveAt, LocalDate lastNotifiedAt, boolean notificationEnabled
    ) {
        User user = fixtures.createUser(name);
        jdbcTemplate.update(
                "UPDATE user SET last_active_at = ?, last_notified_at = ?, notification_enabled = ? WHERE id = ?",
                lastActiveAt, lastNotifiedAt, notificationEnabled, user.getId());
        return user;
    }

    private Problem dueProblem(Long userId, LocalDate nextReviewAt) {
        Problem problem = problemRepository.save(Problem.from(
                new ProblemRegisterDto(null, "메모", "출처", null, LocalDateTime.now()), userId));
        problem.updateReviewSchedule(nextReviewAt, 1, 0);
        return problemRepository.saveAndFlush(problem);
    }

    // ─────────────────────────── 검증 도우미 ───────────────────────────

    /** 사용자별로 어떤 종류의 알림을 보내려 했는지 모은다. */
    private Map<Long, String> sentNotificationTypes() {
        ArgumentCaptor<Long> userIdCaptor = ArgumentCaptor.forClass(Long.class);
        ArgumentCaptor<NotificationRequestDto> requestCaptor =
                ArgumentCaptor.forClass(NotificationRequestDto.class);
        verify(fcmService, atLeast(0))
                .sendNotificationToAllUserDevice(userIdCaptor.capture(), requestCaptor.capture());

        Map<Long, String> types = new LinkedHashMap<>();
        List<Long> userIds = userIdCaptor.getAllValues();
        List<NotificationRequestDto> requests = requestCaptor.getAllValues();
        for (int i = 0; i < userIds.size(); i++) {
            types.put(userIds.get(i), requests.get(i).data().get("type"));
        }
        return types;
    }

    private NotificationRequestDto capturedNotificationFor(Long userId) {
        ArgumentCaptor<NotificationRequestDto> requestCaptor =
                ArgumentCaptor.forClass(NotificationRequestDto.class);
        verify(fcmService).sendNotificationToAllUserDevice(eq(userId), requestCaptor.capture());
        return requestCaptor.getValue();
    }

    private LocalDate lastNotifiedAtOf(Long userId) {
        return userRepository.findById(userId).orElseThrow().getLastNotifiedAt();
    }

    @Nested
    @DisplayName("복습 알림")
    class ReviewDueNotification {

        @Test
        @DisplayName("복습 예정일이 오늘이거나 지난 문제가 있으면 알림 대상이다")
        void notifiesWhenReviewIsDueTodayOrEarlier() {
            User dueToday = activeUser("due-today");
            User overdue = activeUser("overdue");
            dueProblem(dueToday.getId(), today);
            dueProblem(overdue.getId(), today.minusDays(3));

            runJob();

            assertThat(sentNotificationTypes())
                    .containsEntry(dueToday.getId(), "review_due")
                    .containsEntry(overdue.getId(), "review_due");
        }

        @Test
        @DisplayName("복습 예정일이 아직 오지 않은 문제만 있으면 보내지 않는다")
        void skipsFutureReviewSchedule() {
            User user = activeUser("future-due");
            dueProblem(user.getId(), today.plusDays(1));

            runJob();

            then(fcmService).should(never()).sendNotificationToAllUserDevice(eq(user.getId()), any());
            assertThat(lastNotifiedAtOf(user.getId())).isNull();
        }

        @Test
        @DisplayName("복습 예정일이 없는 문제는 대상이 되지 않는다")
        void skipsProblemWithoutSchedule() {
            User user = activeUser("no-schedule");
            problemRepository.save(Problem.from(
                    new ProblemRegisterDto(null, "메모", "출처", null, LocalDateTime.now()), user.getId()));

            runJob();

            then(fcmService).should(never()).sendNotificationToAllUserDevice(anyLong(), any());
        }

        @Test
        @DisplayName("알림을 끈 사용자에게는 보내지 않는다")
        void skipsUserWithNotificationDisabled() {
            User enabled = activeUser("notify-on");
            User disabled = userWithActivity("notify-off", today.atTime(9, 0), null, false);
            dueProblem(enabled.getId(), today);
            dueProblem(disabled.getId(), today);

            runJob();

            then(fcmService).should().sendNotificationToAllUserDevice(eq(enabled.getId()), any());
            then(fcmService).should(never()).sendNotificationToAllUserDevice(eq(disabled.getId()), any());
            assertThat(lastNotifiedAtOf(disabled.getId()))
                    .as("보내지 않았으므로 발송 이력도 남기지 않는다")
                    .isNull();
        }

        @Test
        @DisplayName("오늘 이미 알림을 받은 사용자에게는 다시 보내지 않는다")
        void skipsUserAlreadyNotifiedToday() {
            User user = userWithActivity("already-notified", today.atTime(9, 0), today, true);
            dueProblem(user.getId(), today);

            runJob();

            then(fcmService).should(never()).sendNotificationToAllUserDevice(eq(user.getId()), any());
        }

        @Test
        @DisplayName("어제 알림을 받았다면 오늘 다시 보낸다")
        void notifiesUserNotifiedYesterday() {
            User user = userWithActivity("notified-yesterday", today.atTime(9, 0), today.minusDays(1), true);
            dueProblem(user.getId(), today);

            runJob();

            assertThat(sentNotificationTypes()).containsEntry(user.getId(), "review_due");
            assertThat(lastNotifiedAtOf(user.getId())).isEqualTo(today);
        }

        @Test
        @DisplayName("한 번도 접속한 적이 없는 사용자는 대상이 아니다")
        void skipsUserWithoutLastActiveAt() {
            User user = userWithActivity("never-active", null, null, true);
            dueProblem(user.getId(), today);

            runJob();

            then(fcmService).should(never()).sendNotificationToAllUserDevice(anyLong(), any());
        }

        @Test
        @DisplayName("미접속 7일 경계에서 그날 자정 접속자는 복습 알림 대상이다")
        void sevenDayBoundaryStaysInReviewFlow() {
            User onBoundary = userWithActivity(
                    "boundary-active", today.minusDays(7).atStartOfDay(), null, true);
            dueProblem(onBoundary.getId(), today);

            runJob();

            assertThat(sentNotificationTypes())
                    .as("7일 전 자정 접속은 아직 활성 사용자로 본다")
                    .containsEntry(onBoundary.getId(), "review_due");
        }

        @Test
        @DisplayName("7일 경계를 1초 넘긴 사용자는 복습 알림이 아니라 재참여 알림을 받는다")
        void justPastSevenDayBoundaryFallsToReengagement() {
            User justPast = userWithActivity(
                    "boundary-inactive", today.minusDays(7).atStartOfDay().minusSeconds(1), null, true);
            dueProblem(justPast.getId(), today);

            runJob();

            assertThat(sentNotificationTypes()).containsEntry(justPast.getId(), "reengagement");
        }

        @Test
        @DisplayName("알림 본문에 복습할 문제 개수를 담는다")
        void includesDueCountInMessage() {
            User user = activeUser("due-count");
            dueProblem(user.getId(), today);
            dueProblem(user.getId(), today.minusDays(1));
            dueProblem(user.getId(), today.minusDays(2));
            dueProblem(user.getId(), today.plusDays(5));

            runJob();

            NotificationRequestDto request = capturedNotificationFor(user.getId());
            assertThat(request.title()).isEqualTo("오늘의 복습 알림");
            assertThat(request.body()).as("예정일이 지난 3건만 센다").isEqualTo("오늘 복습할 문제가 3개 있어요!");
            assertThat(request.data()).containsEntry("type", "review_due");
        }

        @Test
        @DisplayName("다른 사용자의 복습 예정 문제는 내 알림 개수에 포함되지 않는다")
        void dueCountIsScopedPerUser() {
            User mine = activeUser("due-mine");
            User theirs = activeUser("due-theirs");
            dueProblem(mine.getId(), today);
            dueProblem(theirs.getId(), today);
            dueProblem(theirs.getId(), today.minusDays(1));

            runJob();

            assertThat(capturedNotificationFor(mine.getId()).body())
                    .isEqualTo("오늘 복습할 문제가 1개 있어요!");
            assertThat(capturedNotificationFor(theirs.getId()).body())
                    .isEqualTo("오늘 복습할 문제가 2개 있어요!");
        }

        @Test
        @DisplayName("발송 대상이 없으면 아무 것도 하지 않고 끝난다")
        void doesNothingWhenNoTargets() {
            activeUser("idle");

            runJob();

            then(fcmService).shouldHaveNoInteractions();
        }
    }

    @Nested
    @DisplayName("재참여 알림")
    class Reengagement {

        @Test
        @DisplayName("7일 이상 30일 이하 미접속 사용자에게 복습 예정 문제가 없어도 보낸다")
        void notifiesInactiveUserWithoutDueProblems() {
            User user = userWithActivity("inactive-10d", today.minusDays(10).atTime(9, 0), null, true);

            runJob();

            assertThat(sentNotificationTypes()).containsEntry(user.getId(), "reengagement");
            NotificationRequestDto request = capturedNotificationFor(user.getId());
            assertThat(request.title()).isEqualTo("오랜만이에요!");
            assertThat(lastNotifiedAtOf(user.getId())).isEqualTo(today);
        }

        @Test
        @DisplayName("최근 5일 안에 알림을 받았으면 다시 보내지 않는다")
        void respectsFiveDayInterval() {
            User recentlyNotified = userWithActivity(
                    "inactive-notified-3d", today.minusDays(10).atTime(9, 0), today.minusDays(3), true);
            User longAgoNotified = userWithActivity(
                    "inactive-notified-6d", today.minusDays(10).atTime(9, 0), today.minusDays(6), true);

            runJob();

            then(fcmService).should(never())
                    .sendNotificationToAllUserDevice(eq(recentlyNotified.getId()), any());
            assertThat(sentNotificationTypes()).containsEntry(longAgoNotified.getId(), "reengagement");
        }

        @Test
        @DisplayName("알림을 끈 사용자에게는 보내지 않는다")
        void skipsUserWithNotificationDisabled() {
            User user = userWithActivity("inactive-notify-off", today.minusDays(10).atTime(9, 0), null, false);

            runJob();

            then(fcmService).shouldHaveNoInteractions();
            assertThat(lastNotifiedAtOf(user.getId())).isNull();
        }

        @Test
        @DisplayName("30일을 넘긴 미접속 사용자는 재참여 흐름이 아니라 장기 미접속 흐름으로 간다")
        void longInactiveUserIsHandledSeparately() {
            User longInactive = userWithActivity("inactive-40d", today.minusDays(40).atTime(9, 0), null, true);

            runJob();

            assertThat(sentNotificationTypes()).containsEntry(longInactive.getId(), "reengagement_monthly");
        }
    }

    @Nested
    @DisplayName("장기 미접속 알림")
    class LongInactiveReengagement {

        @Test
        @DisplayName("30일 초과 미접속 사용자에게 월 1회 보낸다")
        void notifiesLongInactiveUser() {
            User user = userWithActivity("long-inactive", today.minusDays(45).atTime(9, 0), null, true);

            runJob();

            NotificationRequestDto request = capturedNotificationFor(user.getId());
            assertThat(request.title()).isEqualTo("오답노트가 기다리고 있어요");
            assertThat(request.data()).containsEntry("type", "reengagement_monthly");
            assertThat(lastNotifiedAtOf(user.getId())).isEqualTo(today);
        }

        @Test
        @DisplayName("최근 30일 안에 알림을 받았으면 다시 보내지 않는다")
        void respectsThirtyDayInterval() {
            User recentlyNotified = userWithActivity(
                    "long-inactive-notified-10d", today.minusDays(45).atTime(9, 0), today.minusDays(10), true);
            User longAgoNotified = userWithActivity(
                    "long-inactive-notified-40d", today.minusDays(45).atTime(9, 0), today.minusDays(40), true);

            runJob();

            then(fcmService).should(never())
                    .sendNotificationToAllUserDevice(eq(recentlyNotified.getId()), any());
            assertThat(sentNotificationTypes())
                    .containsEntry(longAgoNotified.getId(), "reengagement_monthly");
        }

        @Test
        @DisplayName("알림을 끈 사용자에게는 보내지 않는다")
        void skipsUserWithNotificationDisabled() {
            userWithActivity("long-inactive-off", today.minusDays(45).atTime(9, 0), null, false);

            runJob();

            then(fcmService).shouldHaveNoInteractions();
        }
    }

    @Nested
    @DisplayName("발송 실패 내성")
    class FailureTolerance {

        @Test
        @DisplayName("한 사용자 발송이 실패해도 나머지 사용자에게는 계속 보낸다")
        void continuesAfterSingleFailure() {
            User failing = activeUser("fcm-failing");
            User succeeding = activeUser("fcm-ok");
            dueProblem(failing.getId(), today);
            dueProblem(succeeding.getId(), today);

            willThrow(new IllegalStateException("fcm queue down"))
                    .given(fcmService).sendNotificationToAllUserDevice(eq(failing.getId()), any());

            runJob();

            then(fcmService).should().sendNotificationToAllUserDevice(eq(succeeding.getId()), any());
            assertThat(lastNotifiedAtOf(succeeding.getId()))
                    .as("성공한 사용자에게는 발송 이력이 남아야 한다")
                    .isEqualTo(today);
            assertThat(lastNotifiedAtOf(failing.getId()))
                    .as("실패한 사용자에게는 발송 이력을 남기지 않아야 다음 실행에서 재시도된다")
                    .isNull();
        }

        @Test
        @DisplayName("재참여 알림 발송이 실패해도 장기 미접속 흐름은 계속 실행된다")
        void oneFlowFailureDoesNotStopOthers() {
            User reengagement = userWithActivity("flow-reengagement", today.minusDays(10).atTime(9, 0), null, true);
            User longInactive = userWithActivity("flow-long-inactive", today.minusDays(40).atTime(9, 0), null, true);

            willThrow(new IllegalStateException("fcm queue down"))
                    .given(fcmService).sendNotificationToAllUserDevice(eq(reengagement.getId()), any());

            runJob();

            assertThat(sentNotificationTypes())
                    .containsEntry(longInactive.getId(), "reengagement_monthly");
            assertThat(lastNotifiedAtOf(longInactive.getId())).isEqualTo(today);
        }
    }

    @Nested
    @DisplayName("여러 흐름이 겹칠 때")
    class OverlappingFlows {

        @Test
        @DisplayName("한 번 실행에서 사용자당 알림은 한 통만 보낸다")
        void sendsAtMostOneNotificationPerUser() {
            User active = activeUser("multi-active");
            User inactive = userWithActivity("multi-inactive", today.minusDays(10).atTime(9, 0), null, true);
            User longInactive = userWithActivity("multi-long", today.minusDays(40).atTime(9, 0), null, true);
            dueProblem(active.getId(), today);
            dueProblem(inactive.getId(), today);
            dueProblem(longInactive.getId(), today);

            runJob();

            Map<Long, Integer> callCounts = new HashMap<>();
            ArgumentCaptor<Long> userIdCaptor = ArgumentCaptor.forClass(Long.class);
            verify(fcmService, atLeast(0)).sendNotificationToAllUserDevice(userIdCaptor.capture(), any());
            userIdCaptor.getAllValues()
                    .forEach(id -> callCounts.merge(id, 1, Integer::sum));

            assertThat(callCounts).containsOnly(
                    entry(active.getId(), 1),
                    entry(inactive.getId(), 1),
                    entry(longInactive.getId(), 1));
        }
    }
}
