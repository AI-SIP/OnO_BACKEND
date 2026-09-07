package com.aisip.OnO.backend.studyroom.service;

import com.aisip.OnO.backend.studyroom.dto.StudyRoomDtos.ChallengeResponse;
import com.aisip.OnO.backend.studyroom.dto.StudyRoomStats;
import com.aisip.OnO.backend.studyroom.entity.*;
import com.aisip.OnO.backend.studyroom.quartz.ChallengeNotificationScheduler;
import com.aisip.OnO.backend.studyroom.repository.StudyRoomChallengeRepository;
import com.aisip.OnO.backend.studyroom.repository.StudyRoomMemberRepository;
import com.aisip.OnO.backend.support.TestUsers;
import com.aisip.OnO.backend.user.entity.User;
import com.aisip.OnO.backend.util.fcm.service.FcmService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.util.ReflectionTestUtils.setField;

/**
 * 챌린지 집계 구간 계산과 상태 판정에 대한 단위 테스트.
 *
 * <p>주기(period/periodDays)가 있는 챌린지는 "지금 속한 구간"만 집계해야 한다.
 * 이 계산이 틀리면 전체 기간 합계로 진행도가 부풀려져 챌린지가 조기 완료된다.
 * DB 없이 검증할 수 있는 순수 계산이라 통계 조회는 목으로 대체한다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("StudyRoomChallengeService")
class StudyRoomChallengeServiceTest {

    private static final Long ROOM_ID = 10L;
    private static final AtomicLong USER_SEQUENCE = new AtomicLong();

    @Mock
    private StudyRoomAccessService accessService;

    @Mock
    private StudyRoomChallengeRepository challengeRepository;

    @Mock
    private StudyRoomMemberRepository memberRepository;

    @Mock
    private StudyRoomStatsService statsService;

    @Mock
    private ChallengeNotificationScheduler notificationScheduler;

    @Mock
    private FcmService fcmService;

    private StudyRoomChallengeService challengeService;

    @BeforeEach
    void setUp() {
        challengeService = new StudyRoomChallengeService(accessService, challengeRepository,
                memberRepository, statsService, notificationScheduler, fcmService);
    }

    @Nested
    @DisplayName("집계 구간")
    class AggregationRange {

        @Test
        @DisplayName("주기가 없으면 챌린지 전체 기간을 집계한다")
        void withoutPeriodWholeRangeIsUsed() {
            LocalDateTime startAt = truncated(LocalDateTime.now().minusDays(10));
            LocalDateTime endAt = startAt.plusDays(30);
            StudyRoomMember member = member("멤버");
            StudyRoomChallenge challenge = challenge(StudyRoomChallengeType.INDIVIDUAL,
                    StudyRoomChallengeMetric.PROBLEM_COUNT, null, null, 100, startAt, endAt);
            stubRoom(member, challenge);
            stubStats(member, new StudyRoomStats(1, 0, 0));

            challengeService.getChallenges(ROOM_ID, member.getUser().getId());

            assertRange(startAt, endAt);
        }

        @Test
        @DisplayName("주간 주기는 지금 속한 한 주만 집계한다")
        void weeklyPeriodUsesCurrentWeekWindow() {
            LocalDateTime startAt = truncated(LocalDateTime.now().minusDays(10));
            LocalDateTime endAt = startAt.plusDays(21);
            StudyRoomMember member = member("멤버");
            StudyRoomChallenge challenge = challenge(StudyRoomChallengeType.INDIVIDUAL,
                    StudyRoomChallengeMetric.PRACTICE_COUNT, StudyRoomChallengePeriod.WEEKLY, null, 3, startAt, endAt);
            stubRoom(member, challenge);
            stubStats(member, new StudyRoomStats(0, 3, 0));

            List<ChallengeResponse> responses = challengeService.getChallenges(ROOM_ID, member.getUser().getId());

            assertRange(startAt.plusWeeks(1), startAt.plusWeeks(2));
            assertThat(responses).singleElement().satisfies(response -> {
                assertThat(response.status()).as("주기 챌린지는 조기 완료되지 않는다").isEqualTo("in_progress");
                assertThat(response.memberProgress()).singleElement().satisfies(progress -> {
                    assertThat(progress.current()).as("이번 주 진행도").isEqualTo(3);
                    assertThat(progress.cleared()).as("이번 주 달성 여부").isTrue();
                });
            });
        }

        @Test
        @DisplayName("일간 주기는 오늘 하루만 집계한다")
        void dailyPeriodUsesTodayWindow() {
            LocalDateTime startAt = truncated(LocalDateTime.now().minusDays(3));
            LocalDateTime endAt = startAt.plusDays(10);
            StudyRoomMember member = member("멤버");
            StudyRoomChallenge challenge = challenge(StudyRoomChallengeType.INDIVIDUAL,
                    StudyRoomChallengeMetric.PROBLEM_COUNT, StudyRoomChallengePeriod.DAILY, null, 1, startAt, endAt);
            stubRoom(member, challenge);
            stubStats(member, new StudyRoomStats(1, 0, 0));

            challengeService.getChallenges(ROOM_ID, member.getUser().getId());

            assertRange(startAt.plusDays(3), startAt.plusDays(4));
        }

        @Test
        @DisplayName("월간 주기는 지금 속한 한 달만 집계한다")
        void monthlyPeriodUsesCurrentMonthWindow() {
            LocalDateTime startAt = truncated(LocalDateTime.now().minusMonths(2).minusDays(1));
            LocalDateTime endAt = startAt.plusMonths(6);
            StudyRoomMember member = member("멤버");
            StudyRoomChallenge challenge = challenge(StudyRoomChallengeType.INDIVIDUAL,
                    StudyRoomChallengeMetric.PROBLEM_COUNT, StudyRoomChallengePeriod.MONTHLY, null, 1, startAt, endAt);
            stubRoom(member, challenge);
            stubStats(member, new StudyRoomStats(1, 0, 0));

            challengeService.getChallenges(ROOM_ID, member.getUser().getId());

            assertRange(startAt.plusMonths(2), startAt.plusMonths(3));
        }

        @Test
        @DisplayName("periodDays 를 쓰면 그 일수 단위로 구간이 잘린다")
        void customPeriodDaysWindow() {
            LocalDateTime startAt = truncated(LocalDateTime.now().minusDays(7));
            LocalDateTime endAt = startAt.plusDays(30);
            StudyRoomMember member = member("멤버");
            StudyRoomChallenge challenge = challenge(StudyRoomChallengeType.INDIVIDUAL,
                    StudyRoomChallengeMetric.PROBLEM_COUNT, null, 3, 1, startAt, endAt);
            stubRoom(member, challenge);
            stubStats(member, new StudyRoomStats(1, 0, 0));

            challengeService.getChallenges(ROOM_ID, member.getUser().getId());

            assertRange(startAt.plusDays(6), startAt.plusDays(9));
        }

        @Test
        @DisplayName("마지막 구간이 종료 시각을 넘으면 종료 시각에서 잘린다")
        void lastWindowIsClampedToEndAt() {
            LocalDateTime startAt = truncated(LocalDateTime.now().minusDays(9));
            LocalDateTime endAt = startAt.plusDays(10);
            StudyRoomMember member = member("멤버");
            StudyRoomChallenge challenge = challenge(StudyRoomChallengeType.INDIVIDUAL,
                    StudyRoomChallengeMetric.PROBLEM_COUNT, StudyRoomChallengePeriod.WEEKLY, null, 1, startAt, endAt);
            stubRoom(member, challenge);
            stubStats(member, new StudyRoomStats(1, 0, 0));

            challengeService.getChallenges(ROOM_ID, member.getUser().getId());

            assertRange(startAt.plusWeeks(1), endAt);
        }
    }

    @Nested
    @DisplayName("상태 판정")
    class StatusResolution {

        @Test
        @DisplayName("주기 없는 개인 챌린지는 모든 멤버가 목표를 채워야 완료된다")
        void individualCompletesOnlyWhenEveryoneClears() {
            LocalDateTime startAt = truncated(LocalDateTime.now().minusDays(1));
            LocalDateTime endAt = truncated(LocalDateTime.now().plusDays(1));
            StudyRoomMember first = member("첫번째");
            StudyRoomMember second = member("두번째");
            StudyRoomChallenge challenge = challenge(StudyRoomChallengeType.INDIVIDUAL,
                    StudyRoomChallengeMetric.PROBLEM_COUNT, null, null, 2, startAt, endAt);
            given(memberRepository.findAllWithUserByRoomId(ROOM_ID)).willReturn(List.of(first, second));
            given(challengeRepository.findAllByRoomIdOrderByEndAtAsc(ROOM_ID)).willReturn(List.of(challenge));
            given(statsService.currentStreaks(anyList(), any(), any())).willReturn(Map.of());
            given(statsService.getStats(anyList(), any(LocalDateTime.class), any(LocalDateTime.class), anyMap()))
                    .willReturn(Map.of(
                            first.getUser().getId(), new StudyRoomStats(2, 0, 0),
                            second.getUser().getId(), new StudyRoomStats(1, 0, 0)));

            List<ChallengeResponse> responses = challengeService.getChallenges(ROOM_ID, first.getUser().getId());

            assertThat(responses).singleElement()
                    .extracting(ChallengeResponse::status)
                    .as("한 명이 미달이면 진행 중")
                    .isEqualTo("in_progress");
        }

        @Test
        @DisplayName("주기 없는 단체 챌린지는 합계가 목표에 닿으면 완료되고 원자적 전이를 시도한다")
        void groupCompletesOnSumAndTriesAtomicTransition() {
            LocalDateTime startAt = truncated(LocalDateTime.now().minusDays(1));
            LocalDateTime endAt = truncated(LocalDateTime.now().plusDays(1));
            StudyRoomMember first = member("첫번째");
            StudyRoomMember second = member("두번째");
            StudyRoomChallenge challenge = challenge(StudyRoomChallengeType.GROUP,
                    StudyRoomChallengeMetric.PROBLEM_COUNT, null, null, 5, startAt, endAt);
            given(memberRepository.findAllWithUserByRoomId(ROOM_ID)).willReturn(List.of(first, second));
            given(challengeRepository.findAllByRoomIdOrderByEndAtAsc(ROOM_ID)).willReturn(List.of(challenge));
            given(statsService.currentStreaks(anyList(), any(), any())).willReturn(Map.of());
            given(statsService.getStats(anyList(), any(LocalDateTime.class), any(LocalDateTime.class), anyMap()))
                    .willReturn(Map.of(
                            first.getUser().getId(), new StudyRoomStats(3, 0, 0),
                            second.getUser().getId(), new StudyRoomStats(2, 0, 0)));
            given(challengeRepository.tryTransitionFromInProgress(eq(challenge.getId()),
                    eq(StudyRoomChallengeStatus.COMPLETED), any())).willReturn(1);

            // 완료 전이는 커밋 이후 FCM 발송을 예약한다. 트랜잭션 동기화가 열려 있지 않으면
            // registerSynchronization 이 IllegalStateException 을 던지므로 여기서 직접 연다.
            TransactionSynchronizationManager.initSynchronization();
            List<ChallengeResponse> responses;
            try {
                responses = challengeService.getChallenges(ROOM_ID, first.getUser().getId());
                assertThat(TransactionSynchronizationManager.getSynchronizations())
                        .as("커밋 이후로 미뤄진 알림 발송")
                        .hasSize(1);
            } finally {
                TransactionSynchronizationManager.clearSynchronization();
            }

            assertThat(responses).singleElement().satisfies(response -> {
                assertThat(response.status()).as("합계 달성 시 상태").isEqualTo("completed");
                assertThat(response.groupCurrent()).as("단체 진행도 합계").isEqualTo(5);
                assertThat(response.memberProgress()).as("단체 챌린지의 멤버별 진행도").isEmpty();
            });
            verify(challengeRepository).tryTransitionFromInProgress(eq(challenge.getId()),
                    eq(StudyRoomChallengeStatus.COMPLETED), any());
            verifyNoInteractions(fcmService);
        }

        @Test
        @DisplayName("원자적 전이에서 밀린 스레드는 알림을 예약하지 않는다")
        void losingThreadDoesNotScheduleNotification() {
            LocalDateTime startAt = truncated(LocalDateTime.now().minusDays(1));
            LocalDateTime endAt = truncated(LocalDateTime.now().plusDays(1));
            StudyRoomMember member = member("멤버");
            StudyRoomChallenge challenge = challenge(StudyRoomChallengeType.GROUP,
                    StudyRoomChallengeMetric.PROBLEM_COUNT, null, null, 1, startAt, endAt);
            stubRoom(member, challenge);
            stubStats(member, new StudyRoomStats(5, 0, 0));
            given(challengeRepository.tryTransitionFromInProgress(eq(challenge.getId()),
                    eq(StudyRoomChallengeStatus.COMPLETED), any())).willReturn(0);

            TransactionSynchronizationManager.initSynchronization();
            try {
                challengeService.getChallenges(ROOM_ID, member.getUser().getId());
                assertThat(TransactionSynchronizationManager.getSynchronizations())
                        .as("전이에 실패한 스레드는 알림을 예약하지 않는다")
                        .isEmpty();
            } finally {
                TransactionSynchronizationManager.clearSynchronization();
            }
            verifyNoInteractions(fcmService);
        }

        @Test
        @DisplayName("종료 시각이 지나면 expired 가 된다")
        void endedChallengeIsExpired() {
            LocalDateTime startAt = truncated(LocalDateTime.now().minusDays(10));
            LocalDateTime endAt = truncated(LocalDateTime.now().minusDays(1));
            StudyRoomMember member = member("멤버");
            StudyRoomChallenge challenge = challenge(StudyRoomChallengeType.INDIVIDUAL,
                    StudyRoomChallengeMetric.PROBLEM_COUNT, null, null, 100, startAt, endAt);
            stubRoom(member, challenge);
            stubStats(member, new StudyRoomStats(0, 0, 0));

            List<ChallengeResponse> responses = challengeService.getChallenges(ROOM_ID, member.getUser().getId());

            assertThat(responses).singleElement().extracting(ChallengeResponse::status)
                    .as("종료된 챌린지 상태").isEqualTo("expired");
            assertThat(challenge.getStatus()).as("엔티티에 반영된 상태")
                    .isEqualTo(StudyRoomChallengeStatus.EXPIRED);
        }

        @ParameterizedTest(name = "이미 {0} 인 챌린지")
        @EnumSource(value = StudyRoomChallengeStatus.class,
                names = {"COMPLETED", "FAILED", "EXPIRED"})
        @DisplayName("진행 중이 아닌 챌린지의 상태는 다시 계산하지 않는다")
        void terminalStatusIsNotRecomputed(StudyRoomChallengeStatus terminal) {
            LocalDateTime startAt = truncated(LocalDateTime.now().minusDays(1));
            LocalDateTime endAt = truncated(LocalDateTime.now().plusDays(1));
            StudyRoomMember member = member("멤버");
            StudyRoomChallenge challenge = challenge(StudyRoomChallengeType.INDIVIDUAL,
                    StudyRoomChallengeMetric.PROBLEM_COUNT, null, null, 1, startAt, endAt);
            challenge.updateStatus(terminal);
            stubRoom(member, challenge);
            stubStats(member, new StudyRoomStats(10, 0, 0));

            List<ChallengeResponse> responses = challengeService.getChallenges(ROOM_ID, member.getUser().getId());

            assertThat(responses).singleElement().extracting(ChallengeResponse::status)
                    .as("종결 상태는 그대로 유지된다")
                    .isEqualTo(terminal.name().toLowerCase());
        }

        @Test
        @DisplayName("멤버가 아무도 없으면 개인 챌린지는 완료되지 않는다")
        void emptyRoomNeverCompletesIndividualChallenge() {
            LocalDateTime startAt = truncated(LocalDateTime.now().minusDays(1));
            LocalDateTime endAt = truncated(LocalDateTime.now().plusDays(1));
            StudyRoomChallenge challenge = challenge(StudyRoomChallengeType.INDIVIDUAL,
                    StudyRoomChallengeMetric.PROBLEM_COUNT, null, null, 1, startAt, endAt);
            given(memberRepository.findAllWithUserByRoomId(ROOM_ID)).willReturn(List.of());
            given(challengeRepository.findAllByRoomIdOrderByEndAtAsc(ROOM_ID)).willReturn(List.of(challenge));
            given(statsService.currentStreaks(anyList(), any(), any())).willReturn(Map.of());
            given(statsService.getStats(anyList(), any(LocalDateTime.class), any(LocalDateTime.class), anyMap()))
                    .willReturn(Map.of());

            List<ChallengeResponse> responses = challengeService.getChallenges(ROOM_ID, 1L);

            assertThat(responses).singleElement().extracting(ChallengeResponse::status)
                    .as("멤버 없는 방의 챌린지 상태").isEqualTo("in_progress");
        }
    }

    @Nested
    @DisplayName("지표별 진행도")
    class MetricValues {

        @Test
        @DisplayName("problem_count 는 문제 등록 수를 쓴다")
        void problemCountUsesProblemStats() {
            assertProgress(StudyRoomChallengeMetric.PROBLEM_COUNT, new StudyRoomStats(7, 3, 1), 0, 7);
        }

        @Test
        @DisplayName("practice_count 는 복습 수를 쓴다")
        void practiceCountUsesPracticeStats() {
            assertProgress(StudyRoomChallengeMetric.PRACTICE_COUNT, new StudyRoomStats(7, 3, 1), 0, 3);
        }

        @Test
        @DisplayName("attendance 는 출석일 수를 쓴다")
        void attendanceUsesAttendanceDayCount() {
            assertProgress(StudyRoomChallengeMetric.ATTENDANCE, new StudyRoomStats(7, 3, 1), 4, 4);
        }

        @Test
        @DisplayName("과거 데이터 호환용 streak 지표도 출석일 수를 쓴다")
        void deprecatedStreakMetricUsesAttendanceDayCount() {
            assertProgress(StudyRoomChallengeMetric.STREAK, new StudyRoomStats(7, 3, 1), 5, 5);
        }

        @Test
        @DisplayName("통계가 없는 멤버의 진행도는 0 이다")
        void missingStatsMeansZeroProgress() {
            LocalDateTime startAt = truncated(LocalDateTime.now().minusDays(1));
            LocalDateTime endAt = truncated(LocalDateTime.now().plusDays(1));
            StudyRoomMember member = member("멤버");
            StudyRoomChallenge challenge = challenge(StudyRoomChallengeType.INDIVIDUAL,
                    StudyRoomChallengeMetric.PROBLEM_COUNT, null, null, 1, startAt, endAt);
            given(memberRepository.findAllWithUserByRoomId(ROOM_ID)).willReturn(List.of(member));
            given(challengeRepository.findAllByRoomIdOrderByEndAtAsc(ROOM_ID)).willReturn(List.of(challenge));
            given(statsService.currentStreaks(anyList(), any(), any())).willReturn(Map.of());
            given(statsService.getStats(anyList(), any(LocalDateTime.class), any(LocalDateTime.class), anyMap()))
                    .willReturn(Map.of());

            List<ChallengeResponse> responses = challengeService.getChallenges(ROOM_ID, member.getUser().getId());

            assertThat(responses).singleElement()
                    .satisfies(response -> assertThat(response.memberProgress()).singleElement()
                            .satisfies(progress -> {
                                assertThat(progress.current()).as("통계 없는 멤버의 진행도").isZero();
                                assertThat(progress.cleared()).as("달성 여부").isFalse();
                            }));
        }

        private void assertProgress(StudyRoomChallengeMetric metric, StudyRoomStats stats,
                                    int attendanceDays, int expectedCurrent) {
            LocalDateTime startAt = truncated(LocalDateTime.now().minusDays(1));
            LocalDateTime endAt = truncated(LocalDateTime.now().plusDays(1));
            StudyRoomMember member = member("멤버");
            StudyRoomChallenge challenge = challenge(StudyRoomChallengeType.INDIVIDUAL,
                    metric, null, null, 1_000, startAt, endAt);
            given(memberRepository.findAllWithUserByRoomId(ROOM_ID)).willReturn(List.of(member));
            given(challengeRepository.findAllByRoomIdOrderByEndAtAsc(ROOM_ID)).willReturn(List.of(challenge));
            given(statsService.currentStreaks(anyList(), any(), any())).willReturn(Map.of());
            given(statsService.getStats(anyList(), any(LocalDateTime.class), any(LocalDateTime.class), anyMap()))
                    .willReturn(Map.of(member.getUser().getId(), stats));
            boolean attendanceMetric = metric == StudyRoomChallengeMetric.ATTENDANCE
                    || metric == StudyRoomChallengeMetric.STREAK;
            if (attendanceMetric) {
                given(statsService.attendanceDayCounts(anyList(), any(), any()))
                        .willReturn(Map.of(member.getUser().getId(), attendanceDays));
            }

            List<ChallengeResponse> responses = challengeService.getChallenges(ROOM_ID, member.getUser().getId());

            assertThat(responses).singleElement()
                    .satisfies(response -> assertThat(response.memberProgress()).singleElement()
                            .extracting(progress -> progress.current())
                            .as("%s 지표의 진행도", metric)
                            .isEqualTo(expectedCurrent));
        }
    }

    @Nested
    @DisplayName("정렬")
    class Ordering {

        @Test
        @DisplayName("진행 중 챌린지가 먼저 오고 그 안에서는 마감이 늦은 순이다")
        void inProgressFirstThenLatestEndAt() {
            StudyRoomMember member = member("멤버");
            StudyRoomChallenge expired = challenge(StudyRoomChallengeType.INDIVIDUAL,
                    StudyRoomChallengeMetric.PROBLEM_COUNT, null, null, 100,
                    truncated(LocalDateTime.now().minusDays(10)), truncated(LocalDateTime.now().minusDays(1)));
            StudyRoomChallenge soon = challenge(StudyRoomChallengeType.INDIVIDUAL,
                    StudyRoomChallengeMetric.PROBLEM_COUNT, null, null, 100,
                    truncated(LocalDateTime.now().minusDays(1)), truncated(LocalDateTime.now().plusDays(1)));
            StudyRoomChallenge later = challenge(StudyRoomChallengeType.INDIVIDUAL,
                    StudyRoomChallengeMetric.PROBLEM_COUNT, null, null, 100,
                    truncated(LocalDateTime.now().minusDays(1)), truncated(LocalDateTime.now().plusDays(10)));
            given(memberRepository.findAllWithUserByRoomId(ROOM_ID)).willReturn(List.of(member));
            given(challengeRepository.findAllByRoomIdOrderByEndAtAsc(ROOM_ID))
                    .willReturn(List.of(expired, soon, later));
            given(statsService.currentStreaks(anyList(), any(), any())).willReturn(Map.of());
            given(statsService.getStats(anyList(), any(LocalDateTime.class), any(LocalDateTime.class), anyMap()))
                    .willReturn(Map.of(member.getUser().getId(), new StudyRoomStats(0, 0, 0)));

            List<ChallengeResponse> responses = challengeService.getChallenges(ROOM_ID, member.getUser().getId());

            assertThat(responses)
                    .extracting(ChallengeResponse::challengeId)
                    .as("정렬 결과")
                    .containsExactly(later.getId(), soon.getId(), expired.getId());
        }
    }

    // ─────────────────────────── 헬퍼 ───────────────────────────

    private void stubRoom(StudyRoomMember member, StudyRoomChallenge challenge) {
        given(memberRepository.findAllWithUserByRoomId(ROOM_ID)).willReturn(List.of(member));
        given(challengeRepository.findAllByRoomIdOrderByEndAtAsc(ROOM_ID)).willReturn(List.of(challenge));
    }

    private void stubStats(StudyRoomMember member, StudyRoomStats stats) {
        given(statsService.currentStreaks(anyList(), any(), any())).willReturn(Map.of(member.getUser().getId(), 0));
        given(statsService.getStats(anyList(), any(LocalDateTime.class), any(LocalDateTime.class), anyMap()))
                .willReturn(Map.of(member.getUser().getId(), stats));
    }

    private void assertRange(LocalDateTime expectedStart, LocalDateTime expectedEnd) {
        ArgumentCaptor<LocalDateTime> start = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<LocalDateTime> end = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(statsService).getStats(anyList(), start.capture(), end.capture(), anyMap());
        assertThat(start.getValue()).as("집계 시작").isEqualTo(expectedStart);
        assertThat(end.getValue()).as("집계 종료").isEqualTo(expectedEnd);
    }

    private StudyRoomMember member(String name) {
        long id = USER_SEQUENCE.incrementAndGet();
        User user = TestUsers.create("GOOGLE", name, "challenge-unit");
        setField(user, "id", id);
        return StudyRoomMember.create(user, StudyRoomMemberRole.MEMBER);
    }

    private StudyRoomChallenge challenge(StudyRoomChallengeType type, StudyRoomChallengeMetric metric,
                                         StudyRoomChallengePeriod period, Integer periodDays, int targetValue,
                                         LocalDateTime startAt, LocalDateTime endAt) {
        StudyRoom room = StudyRoom.create("스터디룸", 1L);
        setField(room, "id", ROOM_ID);
        StudyRoomChallenge challenge = StudyRoomChallenge.create(room, "챌린지", type, metric,
                period, periodDays, targetValue, startAt, endAt);
        setField(challenge, "id", USER_SEQUENCE.incrementAndGet());
        return challenge;
    }

    private LocalDateTime truncated(LocalDateTime value) {
        return value.truncatedTo(ChronoUnit.SECONDS);
    }
}
