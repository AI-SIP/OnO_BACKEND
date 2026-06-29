package com.aisip.OnO.backend.studyroom.service;

import com.aisip.OnO.backend.common.emoji.CustomEmojiValidator;
import com.aisip.OnO.backend.studyroom.entity.*;
import com.aisip.OnO.backend.studyroom.repository.StudyRoomFeedReactionRepository;
import com.aisip.OnO.backend.studyroom.repository.StudyRoomFeedRepository;
import com.aisip.OnO.backend.studyroom.repository.StudyRoomMemberRepository;
import com.aisip.OnO.backend.user.entity.User;
import com.aisip.OnO.backend.user.repository.UserRepository;
import com.aisip.OnO.backend.util.RandomUserGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.util.ReflectionTestUtils.setField;

@ExtendWith(MockitoExtension.class)
class StudyRoomFeedServiceUnitTest {

    @Mock
    private StudyRoomAccessService accessService;

    @Mock
    private StudyRoomMemberRepository memberRepository;

    @Mock
    private StudyRoomFeedRepository feedRepository;

    @Mock
    private StudyRoomFeedReactionRepository reactionRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private StudyRoomReactionService reactionService;

    @Mock
    private CustomEmojiValidator customEmojiValidator;

    private StudyRoomFeedService feedService;

    private User user;
    private StudyRoomMember membership;
    private StudyRoom room;

    @BeforeEach
    void setUp() {
        user = RandomUserGenerator.createRandomUser("GOOGLE", "테스트", "feed-unit-test");
        setField(user, "id", 1L);

        room = StudyRoom.create("테스트방", 1L);
        setField(room, "id", 10L);

        membership = StudyRoomMember.create(user, StudyRoomMemberRole.MEMBER);
        membership.updateRoom(room);

        feedService = new StudyRoomFeedService(
                accessService, memberRepository, feedRepository,
                reactionRepository, userRepository, new ObjectMapper(),
                reactionService, customEmojiValidator
        );

        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(memberRepository.findAllWithRoomByUserId(1L)).willReturn(List.of(membership));
    }

    @Test
    void problemRegisteredAccumulatesCountOnSameDayFeed() {
        // 오늘 이미 count=2인 피드가 존재
        StudyRoomFeed existingFeed = StudyRoomFeed.create(
                room, user, StudyRoomFeedEventType.PROBLEM_REGISTERED, "{\"count\":2}");

        given(feedRepository.findTopByRoomIdAndUserIdAndEventTypeAndCreatedAtBetweenOrderByCreatedAtDesc(
                any(), any(), any(StudyRoomFeedEventType.class), any(LocalDateTime.class), any(LocalDateTime.class)))
                .willReturn(Optional.of(existingFeed));

        // count=3 추가 → 기존 count(2) + 신규 count(3) = 5
        feedService.createFeedsForUserRooms(1L, StudyRoomFeedEventType.PROBLEM_REGISTERED, Map.of("count", 3));

        // 새 피드를 저장해서는 안 됨
        verify(feedRepository, never()).save(any());
        // 기존 피드의 count가 5로 업데이트되어야 함
        assertThat(existingFeed.getMetadataJson()).contains("\"count\":5");
    }

    @Test
    void problemRegisteredCreatesNewFeedWhenNoSameDayFeedExists() {
        // 오늘 동일 유형의 피드가 없음
        given(feedRepository.findTopByRoomIdAndUserIdAndEventTypeAndCreatedAtBetweenOrderByCreatedAtDesc(
                any(), any(), any(StudyRoomFeedEventType.class), any(LocalDateTime.class), any(LocalDateTime.class)))
                .willReturn(Optional.empty());
        given(feedRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

        feedService.createFeedsForUserRooms(1L, StudyRoomFeedEventType.PROBLEM_REGISTERED, Map.of("count", 1));

        // 새 피드가 저장되어야 함
        verify(feedRepository).save(any(StudyRoomFeed.class));
    }

    @Test
    void nonProblemRegisteredEventAlwaysCreatesNewFeed() {
        given(feedRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

        // SESSION_STARTED는 당일 중복 체크 없이 항상 새 피드 생성
        feedService.createFeedsForUserRooms(1L, StudyRoomFeedEventType.SESSION_STARTED, Map.of());

        // 새 피드가 저장되어야 함
        verify(feedRepository).save(any(StudyRoomFeed.class));
        // 당일 기존 피드 조회 쿼리는 호출되면 안 됨
        verify(feedRepository, never())
                .findTopByRoomIdAndUserIdAndEventTypeAndCreatedAtBetweenOrderByCreatedAtDesc(
                        any(), any(), any(), any(), any());
    }
}
