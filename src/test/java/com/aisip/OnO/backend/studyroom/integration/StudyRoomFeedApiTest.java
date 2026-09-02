package com.aisip.OnO.backend.studyroom.integration;

import com.aisip.OnO.backend.studyroom.dto.StudyRoomDtos.ReactionToggleRequest;
import com.aisip.OnO.backend.studyroom.entity.StudyRoom;
import com.aisip.OnO.backend.studyroom.entity.StudyRoomFeed;
import com.aisip.OnO.backend.studyroom.entity.StudyRoomFeedEventType;
import com.aisip.OnO.backend.studyroom.service.StudyRoomFeedService;
import com.aisip.OnO.backend.studyroom.support.StudyRoomTestSupport;
import com.aisip.OnO.backend.user.entity.User;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import java.util.ArrayList;
import java.util.Map;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("스터디룸 피드 API")
class StudyRoomFeedApiTest extends StudyRoomTestSupport {

    @Autowired
    private StudyRoomFeedService feedService;

    @Nested
    @DisplayName("조회")
    class GetFeed {

        @Test
        @DisplayName("멤버는 방의 피드를 최신순으로 볼 수 있다")
        void memberSeesFeedInLatestOrder() throws Exception {
            RoomFixture fixture = createRoomWithMemberAndOutsider();
            StudyRoomFeed first = saveFeed(fixture.room(), fixture.host());
            StudyRoomFeed second = saveFeed(fixture.room(), fixture.member());
            authenticateAs(fixture.member().getId());

            mockMvc.perform(get("/api/study-room/{roomId}/feed", fixture.roomId()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.content.length()").value(2))
                    .andExpect(jsonPath("$.data.content[0].feedId").value(second.getId()))
                    .andExpect(jsonPath("$.data.content[1].feedId").value(first.getId()))
                    .andExpect(jsonPath("$.data.hasNext").value(false));
        }

        @Test
        @DisplayName("피드가 없으면 빈 목록과 hasNext=false 를 준다")
        void emptyFeed() throws Exception {
            RoomFixture fixture = createRoomWithMemberAndOutsider();
            authenticateAs(fixture.member().getId());

            mockMvc.perform(get("/api/study-room/{roomId}/feed", fixture.roomId()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.content").isEmpty())
                    .andExpect(jsonPath("$.data.hasNext").value(false))
                    .andExpect(jsonPath("$.data.nextCursor").doesNotExist());
        }

        @Test
        @DisplayName("커서로 다음 페이지를 이어서 받을 수 있다")
        void cursorPagination() throws Exception {
            RoomFixture fixture = createRoomWithMemberAndOutsider();
            List<Long> feedIds = new ArrayList<>();
            for (int i = 0; i < 5; i++) {
                feedIds.add(saveFeed(fixture.room(), fixture.host()).getId());
            }
            authenticateAs(fixture.host().getId());

            MvcResult firstPage = mockMvc.perform(get("/api/study-room/{roomId}/feed", fixture.roomId())
                            .param("size", "2"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.content.length()").value(2))
                    .andExpect(jsonPath("$.data.hasNext").value(true))
                    .andReturn();
            Number nextCursor = JsonPath.read(firstPage.getResponse().getContentAsString(), "$.data.nextCursor");

            mockMvc.perform(get("/api/study-room/{roomId}/feed", fixture.roomId())
                            .param("size", "2")
                            .param("cursor", String.valueOf(nextCursor.longValue())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.content.length()").value(2))
                    .andExpect(jsonPath("$.data.content[0].feedId").value(feedIds.get(2)))
                    .andExpect(jsonPath("$.data.hasNext").value(true));
        }

        @Test
        @DisplayName("마지막 페이지에서는 hasNext=false 이고 커서가 비어 있다")
        void lastPageHasNoCursor() throws Exception {
            RoomFixture fixture = createRoomWithMemberAndOutsider();
            for (int i = 0; i < 3; i++) {
                saveFeed(fixture.room(), fixture.host());
            }
            authenticateAs(fixture.host().getId());

            mockMvc.perform(get("/api/study-room/{roomId}/feed", fixture.roomId())
                            .param("size", "10"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.content.length()").value(3))
                    .andExpect(jsonPath("$.data.hasNext").value(false))
                    .andExpect(jsonPath("$.data.nextCursor").doesNotExist());
        }

        @Test
        @DisplayName("size 는 50 으로 잘린다")
        void sizeIsCappedAtFifty() throws Exception {
            RoomFixture fixture = createRoomWithMemberAndOutsider();
            saveFeed(fixture.room(), fixture.host());
            authenticateAs(fixture.host().getId());

            mockMvc.perform(get("/api/study-room/{roomId}/feed", fixture.roomId())
                            .param("size", "500"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.size").value(50));
        }

        @Test
        @DisplayName("다른 방의 피드는 섞이지 않는다")
        void feedIsScopedToRoom() throws Exception {
            RoomFixture fixture = createRoomWithMemberAndOutsider();
            StudyRoom otherRoom = createRoom(fixture.outsider(), "남의 방");
            saveFeed(otherRoom, fixture.outsider());
            saveFeed(fixture.room(), fixture.host());
            authenticateAs(fixture.host().getId());

            mockMvc.perform(get("/api/study-room/{roomId}/feed", fixture.roomId()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.content.length()").value(1))
                    .andExpect(jsonPath("$.data.content[0].userId").value(fixture.host().getId()));
        }

        @ParameterizedTest(name = "{0}")
        @EnumSource(StudyRoomFeedEventType.class)
        @DisplayName("모든 이벤트 종류가 소문자 문자열로 내려간다")
        void everyEventTypeIsSerializedLowercase(StudyRoomFeedEventType eventType) throws Exception {
            RoomFixture fixture = createRoomWithMemberAndOutsider();
            saveFeed(fixture.room(), fixture.host(), eventType, "{}");
            authenticateAs(fixture.host().getId());

            mockMvc.perform(get("/api/study-room/{roomId}/feed", fixture.roomId()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.content[0].eventType")
                            .value(eventType.name().toLowerCase()));
        }

        @ParameterizedTest(name = "깨진 metadata = \"{0}\"")
        @ValueSource(strings = {"", "   ", "not-json", "{", "{\"count\":", "[1,2,3]", "{\"a\": }"})
        @DisplayName("metadata_json 이 깨져 있어도 500 이 아니라 빈 metadata 로 응답한다")
        void brokenMetadataDoesNotBreakResponse(String broken) throws Exception {
            RoomFixture fixture = createRoomWithMemberAndOutsider();
            saveFeed(fixture.room(), fixture.host(), StudyRoomFeedEventType.PROBLEM_REGISTERED, broken);
            authenticateAs(fixture.host().getId());

            mockMvc.perform(get("/api/study-room/{roomId}/feed", fixture.roomId()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.content.length()").value(1))
                    .andExpect(jsonPath("$.data.content[0].metadata").isMap())
                    .andExpect(jsonPath("$.data.content[0].metadata").isEmpty());
        }

        @Test
        @DisplayName("metadata_json 이 JSON 리터럴 null 이면 metadata 가 비어 온다")
        void jsonNullLiteralMetadataIsEmpty() throws Exception {
            RoomFixture fixture = createRoomWithMemberAndOutsider();
            saveFeed(fixture.room(), fixture.host(), StudyRoomFeedEventType.PROBLEM_REGISTERED, "null");
            authenticateAs(fixture.host().getId());

            mockMvc.perform(get("/api/study-room/{roomId}/feed", fixture.roomId()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.content.length()").value(1))
                    .andExpect(jsonPath("$.data.content[0].metadata").doesNotExist());
        }

        @Test
        @DisplayName("metadata_json 이 null 이어도 빈 metadata 로 응답한다")
        void nullMetadataDoesNotBreakResponse() throws Exception {
            RoomFixture fixture = createRoomWithMemberAndOutsider();
            saveFeed(fixture.room(), fixture.host(), StudyRoomFeedEventType.PROBLEM_REGISTERED, null);
            authenticateAs(fixture.host().getId());

            mockMvc.perform(get("/api/study-room/{roomId}/feed", fixture.roomId()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.content[0].metadata").isEmpty());
        }

        @Test
        @DisplayName("방을 떠난 사용자가 남긴 피드는 계속 보인다")
        void feedOfLeftMemberRemainsVisible() throws Exception {
            RoomFixture fixture = createRoomWithMemberAndOutsider();
            StudyRoomFeed feed = saveFeed(fixture.room(), fixture.member());
            inTransaction(() -> memberRepository.deleteByRoomIdAndUserId(fixture.roomId(), fixture.member().getId()));
            authenticateAs(fixture.host().getId());

            mockMvc.perform(get("/api/study-room/{roomId}/feed", fixture.roomId()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.content.length()").value(1))
                    .andExpect(jsonPath("$.data.content[0].feedId").value(feed.getId()))
                    .andExpect(jsonPath("$.data.content[0].userId").value(fixture.member().getId()));
        }

        @Test
        @DisplayName("방을 떠난 사용자는 그 방의 피드를 더 이상 볼 수 없다")
        void leftMemberLosesFeedAccess() throws Exception {
            RoomFixture fixture = createRoomWithMemberAndOutsider();
            saveFeed(fixture.room(), fixture.member());
            inTransaction(() -> memberRepository.deleteByRoomIdAndUserId(fixture.roomId(), fixture.member().getId()));
            authenticateAs(fixture.member().getId());

            mockMvc.perform(get("/api/study-room/{roomId}/feed", fixture.roomId()))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.errorCode").value(10002));
        }

        @Test
        @DisplayName("비멤버는 피드를 볼 수 없다")
        void nonMemberCannotRead() throws Exception {
            RoomFixture fixture = createRoomWithMemberAndOutsider();
            saveFeed(fixture.room(), fixture.host());
            authenticateAs(fixture.outsider().getId());

            mockMvc.perform(get("/api/study-room/{roomId}/feed", fixture.roomId()))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.errorCode").value(10002));
        }

        @Test
        @DisplayName("인증 없이 조회하면 401 이다")
        void unauthenticatedIsRejected() throws Exception {
            RoomFixture fixture = createRoomWithMemberAndOutsider();
            clearAuthentication();

            mockMvc.perform(get("/api/study-room/{roomId}/feed", fixture.roomId()))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("리액션 토글")
    class ToggleReaction {

        @Test
        @DisplayName("처음 누르면 추가되고 다시 누르면 취소된다")
        void toggleAddsThenRemoves() throws Exception {
            RoomFixture fixture = createRoomWithMemberAndOutsider();
            StudyRoomFeed feed = saveFeed(fixture.room(), fixture.host());
            authenticateAs(fixture.host().getId());

            toggle(fixture.roomId(), feed.getId(), EMOJI)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.feedId").value(feed.getId()))
                    .andExpect(jsonPath("$.data.reactions[0].emoji").value(EMOJI))
                    .andExpect(jsonPath("$.data.reactions[0].count").value(1))
                    .andExpect(jsonPath("$.data.reactions[0].reactedByMe").value(true));

            toggle(fixture.roomId(), feed.getId(), EMOJI)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.reactions").isEmpty());

            assertThat(feedReactionRepository.findAllByFeedId(feed.getId()))
                    .as("취소 후 남은 리액션").isEmpty();
        }

        @Test
        @DisplayName("취소한 뒤 같은 이모지를 다시 달 수 있다")
        void reactionCanBeRecreatedAfterCancel() throws Exception {
            RoomFixture fixture = createRoomWithMemberAndOutsider();
            StudyRoomFeed feed = saveFeed(fixture.room(), fixture.host());
            authenticateAs(fixture.host().getId());

            toggle(fixture.roomId(), feed.getId(), EMOJI).andExpect(status().isOk());
            toggle(fixture.roomId(), feed.getId(), EMOJI).andExpect(status().isOk());
            toggle(fixture.roomId(), feed.getId(), EMOJI)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.reactions[0].count").value(1));
        }

        @Test
        @DisplayName("다른 이모지를 누르면 기존 리액션과 함께 쌓인다")
        void differentEmojiIsAddedAlongside() throws Exception {
            RoomFixture fixture = createRoomWithMemberAndOutsider();
            StudyRoomFeed feed = saveFeed(fixture.room(), fixture.host());
            authenticateAs(fixture.host().getId());

            toggle(fixture.roomId(), feed.getId(), EMOJI).andExpect(status().isOk());
            toggle(fixture.roomId(), feed.getId(), OTHER_EMOJI)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.reactions.length()").value(2));

            assertThat(feedReactionRepository.findAllByFeedId(feed.getId()))
                    .as("한 사용자가 서로 다른 이모지 두 개를 남긴 상태").hasSize(2);
        }

        @Test
        @DisplayName("여러 멤버가 같은 이모지를 누르면 count 가 합산되고 내 여부는 각자 다르다")
        void sameEmojiFromMultipleMembersIsAggregated() throws Exception {
            RoomFixture fixture = createRoomWithMemberAndOutsider();
            StudyRoomFeed feed = saveFeed(fixture.room(), fixture.host());

            authenticateAs(fixture.host().getId());
            toggle(fixture.roomId(), feed.getId(), EMOJI).andExpect(status().isOk());

            authenticateAs(fixture.member().getId());
            toggle(fixture.roomId(), feed.getId(), EMOJI)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.reactions[0].count").value(2))
                    .andExpect(jsonPath("$.data.reactions[0].reactedByMe").value(true));
        }

        @Test
        @DisplayName("토글은 남의 리액션을 지우지 않는다")
        void toggleNeverDeletesOthersReaction() throws Exception {
            RoomFixture fixture = createRoomWithMemberAndOutsider();
            StudyRoomFeed feed = saveFeed(fixture.room(), fixture.host());
            saveFeedReaction(feed, fixture.host(), EMOJI);
            authenticateAs(fixture.member().getId());

            toggle(fixture.roomId(), feed.getId(), EMOJI)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.reactions[0].count").value(2));
            toggle(fixture.roomId(), feed.getId(), EMOJI)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.reactions[0].count").value(1));

            assertThat(feedReactionRepository.findAllByFeedId(feed.getId()))
                    .as("방장의 리액션은 남아 있다")
                    .singleElement()
                    .satisfies(reaction -> assertThat(reaction.getUser().getId()).isEqualTo(fixture.host().getId()));
        }

        @ParameterizedTest(name = "이모지 = \"{0}\"")
        @ValueSource(strings = {"not_allowed", "🔥", "", "FIRED_UP_SPARKLE_EYES", "fired_up_sparkle_eyes "})
        @DisplayName("허용 목록에 없는 이모지는 400 으로 거절된다")
        void disallowedEmojiIsRejected(String emoji) throws Exception {
            RoomFixture fixture = createRoomWithMemberAndOutsider();
            StudyRoomFeed feed = saveFeed(fixture.room(), fixture.host());
            authenticateAs(fixture.host().getId());

            toggle(fixture.roomId(), feed.getId(), emoji)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value(11001));

            assertThat(feedReactionRepository.findAllByFeedId(feed.getId()))
                    .as("거절된 뒤 저장된 리액션").isEmpty();
        }

        @Test
        @DisplayName("이모지가 null 이면 400 이다")
        void nullEmojiIsRejected() throws Exception {
            RoomFixture fixture = createRoomWithMemberAndOutsider();
            StudyRoomFeed feed = saveFeed(fixture.room(), fixture.host());
            authenticateAs(fixture.host().getId());

            mockMvc.perform(post("/api/study-room/{roomId}/feed/{feedId}/reactions",
                            fixture.roomId(), feed.getId())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value(11001));
        }

        @Test
        @DisplayName("존재하지 않는 피드에는 리액션할 수 없다")
        void unknownFeedIsNotFound() throws Exception {
            RoomFixture fixture = createRoomWithMemberAndOutsider();
            authenticateAs(fixture.host().getId());

            toggle(fixture.roomId(), nonExistentFeedId(), EMOJI)
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.errorCode").value(10001));
        }

        @Test
        @DisplayName("다른 방의 피드 ID 로는 리액션할 수 없다")
        void feedFromAnotherRoomIsNotFound() throws Exception {
            RoomFixture fixture = createRoomWithMemberAndOutsider();
            StudyRoom otherRoom = createRoom(fixture.outsider(), "남의 방");
            StudyRoomFeed otherFeed = saveFeed(otherRoom, fixture.outsider());
            authenticateAs(fixture.host().getId());

            toggle(fixture.roomId(), otherFeed.getId(), EMOJI)
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.errorCode").value(10001));

            assertThat(feedReactionRepository.findAllByFeedId(otherFeed.getId()))
                    .as("남의 방 피드에는 리액션이 달리지 않는다").isEmpty();
        }

        @Test
        @DisplayName("비멤버는 리액션할 수 없다")
        void nonMemberCannotReact() throws Exception {
            RoomFixture fixture = createRoomWithMemberAndOutsider();
            StudyRoomFeed feed = saveFeed(fixture.room(), fixture.host());
            authenticateAs(fixture.outsider().getId());

            toggle(fixture.roomId(), feed.getId(), EMOJI)
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.errorCode").value(10002));

            assertThat(feedReactionRepository.findAllByFeedId(feed.getId()))
                    .as("비멤버 리액션은 저장되지 않는다").isEmpty();
        }

        @Test
        @DisplayName("인증 없이 리액션하면 401 이다")
        void unauthenticatedIsRejected() throws Exception {
            RoomFixture fixture = createRoomWithMemberAndOutsider();
            StudyRoomFeed feed = saveFeed(fixture.room(), fixture.host());
            clearAuthentication();

            toggle(fixture.roomId(), feed.getId(), EMOJI)
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("학습 활동으로 만들어지는 피드")
    class FeedCreatedFromActivity {

        @Test
        @DisplayName("같은 날 문제 등록 피드는 하나로 합쳐지고 count 가 누적된다")
        void problemRegisteredAccumulatesWithinSameDay() {
            RoomFixture fixture = createRoomWithMemberAndOutsider();
            feedService.createFeedsForUserRooms(fixture.host().getId(),
                    StudyRoomFeedEventType.PROBLEM_REGISTERED, Map.of("count", 1));
            feedService.createFeedsForUserRooms(fixture.host().getId(),
                    StudyRoomFeedEventType.PROBLEM_REGISTERED, Map.of("count", 2));

            List<StudyRoomFeed> feeds = feedRepository.findAll();
            assertThat(feeds).as("같은 날 문제 등록 피드").hasSize(1);
            assertThat(feeds.get(0).getMetadataJson()).as("누적된 count").contains("\"count\":3");
        }

        @Test
        @DisplayName("문제 등록 외의 이벤트는 매번 새 피드가 된다")
        void otherEventsAlwaysCreateNewFeed() {
            RoomFixture fixture = createRoomWithMemberAndOutsider();
            feedService.createFeedsForUserRooms(fixture.host().getId(),
                    StudyRoomFeedEventType.PRACTICE_COMPLETED, Map.of("count", 1));
            feedService.createFeedsForUserRooms(fixture.host().getId(),
                    StudyRoomFeedEventType.PRACTICE_COMPLETED, Map.of("count", 1));

            assertThat(feedRepository.findAll()).as("복습 완료 피드").hasSize(2);
        }

        @Test
        @DisplayName("여러 방에 속한 사용자의 활동은 모든 방에 피드로 남는다")
        void activityFansOutToEveryRoom() {
            User user = fixtures.createUser("user");
            StudyRoom first = createRoom(user, "첫방");
            StudyRoom second = createRoom(user, "둘째방");

            feedService.createFeedsForUserRooms(user.getId(),
                    StudyRoomFeedEventType.LEVEL_UP, Map.of("level", 3));

            assertThat(feedRepository.findAll())
                    .as("두 방 모두에 생긴 피드")
                    .hasSize(2)
                    .extracting(feed -> feed.getRoom().getId())
                    .containsExactlyInAnyOrder(first.getId(), second.getId());
        }

        @Test
        @DisplayName("어느 방에도 속하지 않은 사용자의 활동은 피드를 만들지 않는다")
        void activityOfRoomlessUserCreatesNothing() {
            User user = fixtures.createUser("lonely");

            feedService.createFeedsForUserRooms(user.getId(),
                    StudyRoomFeedEventType.STREAK_MILESTONE, Map.of("days", 7));

            assertThat(feedRepository.findAll()).as("생성된 피드").isEmpty();
        }

        @Test
        @DisplayName("metadata 가 null 이면 빈 JSON 객체로 저장된다")
        void nullMetadataIsStoredAsEmptyObject() {
            RoomFixture fixture = createRoomWithMemberAndOutsider();

            feedService.createFeedsForUserRooms(fixture.host().getId(),
                    StudyRoomFeedEventType.CHALLENGE_CLEARED, null);

            assertThat(feedRepository.findAll())
                    .as("저장된 피드")
                    .singleElement()
                    .satisfies(feed -> assertThat(feed.getMetadataJson()).isEqualTo("{}"));
        }
    }

    private ResultActions toggle(Long roomId, Long feedId, String emoji) throws Exception {
        return mockMvc.perform(post("/api/study-room/{roomId}/feed/{feedId}/reactions", roomId, feedId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new ReactionToggleRequest(emoji))));
    }
}
