package com.aisip.OnO.backend.studyroom.service;

import com.aisip.OnO.backend.studyroom.dto.StudyRoomDtos.ChallengeResponse;
import com.aisip.OnO.backend.studyroom.dto.StudyRoomStats;
import com.aisip.OnO.backend.studyroom.entity.*;
import com.aisip.OnO.backend.studyroom.quartz.ChallengeNotificationScheduler;
import com.aisip.OnO.backend.studyroom.repository.StudyRoomChallengeRepository;
import com.aisip.OnO.backend.studyroom.repository.StudyRoomMemberRepository;
import com.aisip.OnO.backend.user.entity.User;
import com.aisip.OnO.backend.util.RandomUserGenerator;
import com.aisip.OnO.backend.util.fcm.service.FcmService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.util.ReflectionTestUtils.setField;

@ExtendWith(MockitoExtension.class)
class StudyRoomChallengeServiceTest {

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
        challengeService = new StudyRoomChallengeService(accessService, challengeRepository, memberRepository, statsService, notificationScheduler, fcmService);
    }

    @Test
    void weeklyPeriodChallengeUsesCurrentPeriodRangeAndDoesNotCompleteEarly() {
        LocalDateTime startAt = LocalDateTime.now().minusDays(10).truncatedTo(ChronoUnit.SECONDS);
        LocalDateTime endAt = startAt.plusDays(21);
        User user = user(1L, "멤버");
        StudyRoomMember member = StudyRoomMember.create(user, StudyRoomMemberRole.HOST);
        StudyRoomChallenge challenge = challenge(
                StudyRoomChallengeType.INDIVIDUAL,
                StudyRoomChallengeMetric.PRACTICE_COUNT,
                StudyRoomChallengePeriod.WEEKLY,
                3,
                startAt,
                endAt
        );

        given(memberRepository.findAllWithUserByRoomId(10L)).willReturn(List.of(member));
        given(challengeRepository.findAllByRoomIdOrderByEndAtAsc(10L))
                .willReturn(List.of(challenge));
        given(statsService.currentStreaks(anyList(), any(), any())).willReturn(Map.of(user.getId(), 0));
        given(statsService.getStats(anyList(), any(LocalDateTime.class), any(LocalDateTime.class), anyMap()))
                .willReturn(Map.of(user.getId(), new StudyRoomStats(0, 3, 0)));

        List<ChallengeResponse> responses = challengeService.getChallenges(10L, user.getId());

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).status()).isEqualTo("in_progress");
        assertThat(responses.get(0).memberProgress()).singleElement().satisfies(progress -> {
            assertThat(progress.current()).isEqualTo(3);
            assertThat(progress.cleared()).isTrue();
        });
        ArgumentCaptor<LocalDateTime> rangeStart = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<LocalDateTime> rangeEnd = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(statsService).getStats(anyList(), rangeStart.capture(), rangeEnd.capture(), anyMap());
        assertThat(rangeStart.getValue()).isEqualTo(startAt.plusWeeks(1));
        assertThat(rangeEnd.getValue()).isEqualTo(startAt.plusWeeks(2));
    }

    @Test
    void nonPeriodGroupChallengeCompletesWhenGroupCurrentReachesTarget() {
        LocalDateTime startAt = LocalDateTime.now().minusDays(1).truncatedTo(ChronoUnit.SECONDS);
        LocalDateTime endAt = LocalDateTime.now().plusDays(1).truncatedTo(ChronoUnit.SECONDS);
        User first = user(1L, "첫번째");
        User second = user(2L, "두번째");
        StudyRoomChallenge challenge = challenge(
                StudyRoomChallengeType.GROUP,
                StudyRoomChallengeMetric.PRACTICE_COUNT,
                null,
                5,
                startAt,
                endAt
        );

        given(memberRepository.findAllWithUserByRoomId(10L))
                .willReturn(List.of(StudyRoomMember.create(first, StudyRoomMemberRole.HOST),
                        StudyRoomMember.create(second, StudyRoomMemberRole.MEMBER)));
        given(challengeRepository.findAllByRoomIdOrderByEndAtAsc(10L))
                .willReturn(List.of(challenge));
        given(statsService.currentStreaks(anyList(), any(), any())).willReturn(Map.of(first.getId(), 0, second.getId(), 0));
        given(statsService.getStats(anyList(), any(LocalDateTime.class), any(LocalDateTime.class), anyMap()))
                .willReturn(Map.of(
                        first.getId(), new StudyRoomStats(0, 2, 0),
                        second.getId(), new StudyRoomStats(0, 3, 0)
                ));

        ChallengeResponse response = challengeService.getChallenges(10L, first.getId()).get(0);

        assertThat(response.status()).isEqualTo("completed");
        assertThat(response.groupCurrent()).isEqualTo(5);
    }

    @Test
    void dailyPeriodChallengeAggregatesCurrentDayWindow() {
        // DAILY 주기: startAt = 3일 전 자정, endAt = 4일 후 자정
        LocalDateTime startAt = LocalDateTime.now().truncatedTo(ChronoUnit.DAYS).minusDays(3);
        LocalDateTime endAt = startAt.plusDays(7);
        User user = user(1L, "멤버");
        StudyRoomMember member = StudyRoomMember.create(user, StudyRoomMemberRole.HOST);
        StudyRoomChallenge challenge = challenge(
                StudyRoomChallengeType.INDIVIDUAL,
                StudyRoomChallengeMetric.PRACTICE_COUNT,
                StudyRoomChallengePeriod.DAILY,
                2,
                startAt,
                endAt
        );

        given(memberRepository.findAllWithUserByRoomId(10L)).willReturn(List.of(member));
        given(challengeRepository.findAllByRoomIdOrderByEndAtAsc(10L))
                .willReturn(List.of(challenge));
        given(statsService.currentStreaks(anyList(), any(), any())).willReturn(Map.of(user.getId(), 0));
        // practice_count=1, target=2 → cleared=false
        given(statsService.getStats(anyList(), any(LocalDateTime.class), any(LocalDateTime.class), anyMap()))
                .willReturn(Map.of(user.getId(), new StudyRoomStats(0, 1, 0)));

        List<ChallengeResponse> responses = challengeService.getChallenges(10L, user.getId());

        assertThat(responses).hasSize(1);
        ChallengeResponse response = responses.get(0);
        assertThat(response.status()).isEqualTo("in_progress");
        assertThat(response.memberProgress()).singleElement().satisfies(p -> {
            assertThat(p.current()).isEqualTo(1);
            assertThat(p.cleared()).isFalse();
        });

        // 오늘(day 3) 구간: [startAt+3days, startAt+4days]
        ArgumentCaptor<LocalDateTime> rangeStart = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<LocalDateTime> rangeEnd = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(statsService).getStats(anyList(), rangeStart.capture(), rangeEnd.capture(), anyMap());
        assertThat(rangeStart.getValue()).isEqualTo(startAt.plusDays(3));
        assertThat(rangeEnd.getValue()).isEqualTo(startAt.plusDays(4));
    }

    @Test
    void individualChallengeStaysInProgressWhenNotAllMembersCleared() {
        LocalDateTime startAt = LocalDateTime.now().minusDays(1).truncatedTo(ChronoUnit.SECONDS);
        LocalDateTime endAt = LocalDateTime.now().plusDays(1).truncatedTo(ChronoUnit.SECONDS);
        User member1 = user(1L, "첫번째");
        User member2 = user(2L, "두번째");
        StudyRoomChallenge challenge = challenge(
                StudyRoomChallengeType.INDIVIDUAL,
                StudyRoomChallengeMetric.PRACTICE_COUNT,
                null,
                3,
                startAt,
                endAt
        );

        given(memberRepository.findAllWithUserByRoomId(10L))
                .willReturn(List.of(
                        StudyRoomMember.create(member1, StudyRoomMemberRole.HOST),
                        StudyRoomMember.create(member2, StudyRoomMemberRole.MEMBER)
                ));
        given(challengeRepository.findAllByRoomIdOrderByEndAtAsc(10L))
                .willReturn(List.of(challenge));
        given(statsService.currentStreaks(anyList(), any(), any()))
                .willReturn(Map.of(member1.getId(), 0, member2.getId(), 0));
        // member1: practice_count=2 (미달), member2: practice_count=5 (달성)
        given(statsService.getStats(anyList(), any(LocalDateTime.class), any(LocalDateTime.class), anyMap()))
                .willReturn(Map.of(
                        member1.getId(), new StudyRoomStats(0, 2, 0),
                        member2.getId(), new StudyRoomStats(0, 5, 0)
                ));

        ChallengeResponse response = challengeService.getChallenges(10L, member1.getId()).get(0);

        // 일부 멤버만 달성했으므로 in_progress 유지
        assertThat(response.status()).isEqualTo("in_progress");
        assertThat(response.memberProgress()).anySatisfy(p -> {
            assertThat(p.userId()).isEqualTo(member1.getId());
            assertThat(p.cleared()).isFalse();
        });
        assertThat(response.memberProgress()).anySatisfy(p -> {
            assertThat(p.userId()).isEqualTo(member2.getId());
            assertThat(p.cleared()).isTrue();
        });
    }

    @Test
    void expiredEndAtChallengeReturnsExpiredStatus() {
        LocalDateTime startAt = LocalDateTime.now().minusDays(10).truncatedTo(ChronoUnit.SECONDS);
        LocalDateTime endAt = LocalDateTime.now().minusDays(2).truncatedTo(ChronoUnit.SECONDS);
        User user = user(1L, "멤버");
        StudyRoomChallenge challenge = challenge(
                StudyRoomChallengeType.INDIVIDUAL,
                StudyRoomChallengeMetric.PRACTICE_COUNT,
                null,
                10,
                startAt,
                endAt
        );

        given(memberRepository.findAllWithUserByRoomId(10L))
                .willReturn(List.of(StudyRoomMember.create(user, StudyRoomMemberRole.HOST)));
        given(challengeRepository.findAllByRoomIdOrderByEndAtAsc(10L))
                .willReturn(List.of(challenge));
        given(statsService.currentStreaks(anyList(), any(), any())).willReturn(Map.of(user.getId(), 0));
        // practice_count=1, target=10 → 미달성이고 endAt도 과거
        given(statsService.getStats(anyList(), any(LocalDateTime.class), any(LocalDateTime.class), anyMap()))
                .willReturn(Map.of(user.getId(), new StudyRoomStats(0, 1, 0)));

        ChallengeResponse response = challengeService.getChallenges(10L, user.getId()).get(0);

        assertThat(response.status()).isEqualTo("expired");
    }

    @Test
    void attendanceMetricUsesAttendanceDayCountFromStatsService() {
        LocalDateTime startAt = LocalDateTime.now().minusDays(1).truncatedTo(ChronoUnit.SECONDS);
        LocalDateTime endAt = LocalDateTime.now().plusDays(1).truncatedTo(ChronoUnit.SECONDS);
        User user = user(1L, "멤버");
        StudyRoomChallenge challenge = challenge(
                StudyRoomChallengeType.INDIVIDUAL,
                StudyRoomChallengeMetric.ATTENDANCE,
                null,
                5,
                startAt,
                endAt
        );

        given(memberRepository.findAllWithUserByRoomId(10L))
                .willReturn(List.of(StudyRoomMember.create(user, StudyRoomMemberRole.HOST)));
        given(challengeRepository.findAllByRoomIdOrderByEndAtAsc(10L))
                .willReturn(List.of(challenge));
        given(statsService.currentStreaks(anyList(), any(), any())).willReturn(Map.of(user.getId(), 0));
        given(statsService.getStats(anyList(), any(LocalDateTime.class), any(LocalDateTime.class), anyMap()))
                .willReturn(Map.of(user.getId(), new StudyRoomStats(0, 0, 0)));
        // attendanceDayCounts → 6 (target=5이므로 cleared=true)
        given(statsService.attendanceDayCounts(anyList(), any(LocalDateTime.class), any(LocalDateTime.class)))
                .willReturn(Map.of(user.getId(), 6));

        List<ChallengeResponse> responses = challengeService.getChallenges(10L, user.getId());

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).memberProgress()).singleElement().satisfies(p -> {
            assertThat(p.current()).isEqualTo(6);
            assertThat(p.cleared()).isTrue();
        });
        verify(statsService).attendanceDayCounts(anyList(), any(LocalDateTime.class), any(LocalDateTime.class));
    }

    @Test
    void monthlyPeriodChallengeAggregatesCurrentMonthWindow() {
        // 2달 전 첫째날 자정부터 시작하는 MONTHLY 주기 챌린지
        LocalDateTime startAt = LocalDate.now().minusMonths(2).withDayOfMonth(1).atStartOfDay();
        LocalDateTime endAt = startAt.plusMonths(6);
        User user = user(1L, "멤버");
        StudyRoomMember member = StudyRoomMember.create(user, StudyRoomMemberRole.HOST);
        StudyRoomChallenge challenge = challenge(
                StudyRoomChallengeType.INDIVIDUAL,
                StudyRoomChallengeMetric.PRACTICE_COUNT,
                StudyRoomChallengePeriod.MONTHLY,
                3,
                startAt,
                endAt
        );

        given(memberRepository.findAllWithUserByRoomId(10L)).willReturn(List.of(member));
        given(challengeRepository.findAllByRoomIdOrderByEndAtAsc(10L))
                .willReturn(List.of(challenge));
        given(statsService.currentStreaks(anyList(), any(), any())).willReturn(Map.of(user.getId(), 0));
        // practice_count=1, target=3 → 미달성 → status = in_progress
        given(statsService.getStats(anyList(), any(LocalDateTime.class), any(LocalDateTime.class), anyMap()))
                .willReturn(Map.of(user.getId(), new StudyRoomStats(0, 1, 0)));

        List<ChallengeResponse> responses = challengeService.getChallenges(10L, user.getId());

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).status()).isEqualTo("in_progress");
        assertThat(responses.get(0).memberProgress()).singleElement().satisfies(p -> {
            assertThat(p.current()).isEqualTo(1);
            assertThat(p.cleared()).isFalse();
        });

        // 현재 구간 = [startAt.plusMonths(2), startAt.plusMonths(3)] 임을 검증
        ArgumentCaptor<LocalDateTime> rangeStart = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<LocalDateTime> rangeEnd = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(statsService).getStats(anyList(), rangeStart.capture(), rangeEnd.capture(), anyMap());
        assertThat(rangeStart.getValue()).isEqualTo(startAt.plusMonths(2));
        assertThat(rangeEnd.getValue()).isEqualTo(startAt.plusMonths(3));
    }

    @Test
    void streakMetricUsesAttendanceDayCount() {
        LocalDateTime startAt = LocalDateTime.now().minusDays(14).truncatedTo(ChronoUnit.SECONDS);
        LocalDateTime endAt = startAt.plusDays(28);
        User user = user(7L, "멤버");
        StudyRoomMember member = StudyRoomMember.create(user, StudyRoomMemberRole.HOST);
        StudyRoomChallenge challenge = challenge(
                StudyRoomChallengeType.INDIVIDUAL,
                StudyRoomChallengeMetric.STREAK,
                null,
                5,
                startAt,
                endAt
        );

        given(memberRepository.findAllWithUserByRoomId(10L)).willReturn(List.of(member));
        given(challengeRepository.findAllByRoomIdOrderByEndAtAsc(10L))
                .willReturn(List.of(challenge));
        given(statsService.currentStreaks(anyList(), any(), any())).willReturn(Map.of(user.getId(), 7));
        given(statsService.getStats(anyList(), any(LocalDateTime.class), any(LocalDateTime.class), anyMap()))
                .willReturn(Map.of(user.getId(), new StudyRoomStats(0, 0, 7)));
        // attendanceDayCounts = 7, target = 5 → cleared=true
        given(statsService.attendanceDayCounts(anyList(), any(LocalDateTime.class), any(LocalDateTime.class)))
                .willReturn(Map.of(user.getId(), 7));

        List<ChallengeResponse> responses = challengeService.getChallenges(10L, user.getId());

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).memberProgress()).singleElement().satisfies(p -> {
            assertThat(p.current()).isEqualTo(7);
            assertThat(p.cleared()).isTrue();
        });
        verify(statsService).attendanceDayCounts(anyList(), any(LocalDateTime.class), any(LocalDateTime.class));
    }

    private StudyRoomChallenge challenge(StudyRoomChallengeType type, StudyRoomChallengeMetric metric,
                                         StudyRoomChallengePeriod period, int targetValue,
                                         LocalDateTime startAt, LocalDateTime endAt) {
        StudyRoom room = StudyRoom.create("테스트방", 1L);
        setField(room, "id", 10L);
        StudyRoomChallenge challenge = StudyRoomChallenge.create(room, "테스트 챌린지", type, metric, period,
                null, targetValue, startAt, endAt);
        setField(challenge, "id", 100L);
        return challenge;
    }

    private User user(Long id, String name) {
        User user = RandomUserGenerator.createRandomUser("GOOGLE", name, "study-room-service-test");
        setField(user, "id", id);
        return user;
    }
}
