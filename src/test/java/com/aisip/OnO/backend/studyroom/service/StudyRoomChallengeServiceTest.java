package com.aisip.OnO.backend.studyroom.service;

import com.aisip.OnO.backend.studyroom.dto.StudyRoomDtos.ChallengeResponse;
import com.aisip.OnO.backend.studyroom.dto.StudyRoomStats;
import com.aisip.OnO.backend.studyroom.entity.*;
import com.aisip.OnO.backend.studyroom.repository.StudyRoomChallengeRepository;
import com.aisip.OnO.backend.studyroom.repository.StudyRoomMemberRepository;
import com.aisip.OnO.backend.user.entity.User;
import com.aisip.OnO.backend.util.RandomUserGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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

    private StudyRoomChallengeService challengeService;

    @BeforeEach
    void setUp() {
        challengeService = new StudyRoomChallengeService(accessService, challengeRepository, memberRepository, statsService);
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
        given(challengeRepository.findActiveByRoomId(eq(10L), eq(StudyRoomChallengeStatus.IN_PROGRESS), any(LocalDateTime.class)))
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
        given(challengeRepository.findActiveByRoomId(eq(10L), eq(StudyRoomChallengeStatus.IN_PROGRESS), any(LocalDateTime.class)))
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

    private StudyRoomChallenge challenge(StudyRoomChallengeType type, StudyRoomChallengeMetric metric,
                                         StudyRoomChallengePeriod period, int targetValue,
                                         LocalDateTime startAt, LocalDateTime endAt) {
        StudyRoom room = StudyRoom.create("테스트방", 1L);
        setField(room, "id", 10L);
        StudyRoomChallenge challenge = StudyRoomChallenge.create(room, "테스트 챌린지", type, metric, period,
                targetValue, startAt, endAt);
        setField(challenge, "id", 100L);
        return challenge;
    }

    private User user(Long id, String name) {
        User user = RandomUserGenerator.createRandomUser("GOOGLE", name, "study-room-service-test");
        setField(user, "id", id);
        return user;
    }
}
