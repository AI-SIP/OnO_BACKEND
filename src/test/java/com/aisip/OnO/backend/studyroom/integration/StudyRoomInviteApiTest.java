package com.aisip.OnO.backend.studyroom.integration;

import com.aisip.OnO.backend.studyroom.dto.StudyRoomDtos.StudyRoomJoinRequest;
import com.aisip.OnO.backend.studyroom.entity.StudyRoom;
import com.aisip.OnO.backend.studyroom.entity.StudyRoomInviteCode;
import com.aisip.OnO.backend.studyroom.entity.StudyRoomMemberRole;
import com.aisip.OnO.backend.studyroom.service.StudyRoomService;
import com.aisip.OnO.backend.studyroom.support.StudyRoomTestSupport;
import com.aisip.OnO.backend.user.entity.User;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("스터디룸 초대 코드 API")
class StudyRoomInviteApiTest extends StudyRoomTestSupport {

    @Nested
    @DisplayName("코드 발급")
    class IssueCode {

        @Test
        @DisplayName("6자리 숫자 코드와 24시간 뒤 만료 시각을 돌려준다")
        void issuesSixDigitCode() throws Exception {
            RoomFixture fixture = createRoomWithMemberAndOutsider();
            authenticateAs(fixture.host().getId());

            MvcResult result = mockMvc.perform(post("/api/study-room/{roomId}/invite", fixture.roomId()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.code").isString())
                    .andExpect(jsonPath("$.data.expiredAt").isNotEmpty())
                    .andReturn();

            String code = JsonPath.read(result.getResponse().getContentAsString(), "$.data.code");
            assertThat(code).as("발급된 초대 코드").matches("\\d{6}");

            StudyRoomInviteCode saved = inviteCodeRepository.findByCode(code).orElseThrow();
            assertThat(saved.getExpiredAt())
                    .as("만료 시각 — 발급 시점 기준 24시간 뒤")
                    .isAfter(LocalDateTime.now().plusHours(23))
                    .isBefore(LocalDateTime.now().plusHours(25));
        }

        @Test
        @DisplayName("만료되지 않은 코드가 있으면 새로 만들지 않고 그대로 재사용한다")
        void reusesUnexpiredCode() throws Exception {
            RoomFixture fixture = createRoomWithMemberAndOutsider();
            authenticateAs(fixture.host().getId());

            String first = issueCode(fixture.roomId());
            String second = issueCode(fixture.roomId());

            assertThat(second).as("두 번째 발급 결과").isEqualTo(first);
            assertThat(inviteCodeRepository.findAll()).as("저장된 초대 코드 수").hasSize(1);
        }

        @Test
        @DisplayName("기존 코드가 만료됐으면 새 코드를 발급한다")
        void issuesNewCodeWhenPreviousExpired() throws Exception {
            RoomFixture fixture = createRoomWithMemberAndOutsider();
            saveInviteCode(fixture.room(), "111111", LocalDateTime.now().minusMinutes(1));
            authenticateAs(fixture.host().getId());

            String issued = issueCode(fixture.roomId());

            assertThat(issued).as("만료 코드 이후 재발급된 코드").isNotEqualTo("111111");
        }

        @Test
        @DisplayName("방장이 아닌 일반 멤버도 코드를 발급할 수 있다")
        void memberCanIssueCode() throws Exception {
            RoomFixture fixture = createRoomWithMemberAndOutsider();
            authenticateAs(fixture.member().getId());

            mockMvc.perform(post("/api/study-room/{roomId}/invite", fixture.roomId()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.code").isString());
        }

        @Test
        @DisplayName("비멤버는 코드를 발급할 수 없다")
        void nonMemberCannotIssueCode() throws Exception {
            RoomFixture fixture = createRoomWithMemberAndOutsider();
            authenticateAs(fixture.outsider().getId());

            mockMvc.perform(post("/api/study-room/{roomId}/invite", fixture.roomId()))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.errorCode").value(10002));

            assertThat(inviteCodeRepository.findAll()).as("거절된 뒤 저장된 코드").isEmpty();
        }

        @Test
        @DisplayName("존재하지 않는 방의 코드는 발급할 수 없다")
        void unknownRoomCannotIssueCode() throws Exception {
            User user = fixtures.createUser("user");
            authenticateAs(user.getId());

            mockMvc.perform(post("/api/study-room/{roomId}/invite", nonExistentRoomId()))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.errorCode").value(10001));
        }

        @Test
        @DisplayName("인증 없이 발급하면 401 이다")
        void unauthenticatedIsRejected() throws Exception {
            RoomFixture fixture = createRoomWithMemberAndOutsider();
            clearAuthentication();

            mockMvc.perform(post("/api/study-room/{roomId}/invite", fixture.roomId()))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("코드로 참여")
    class JoinWithCode {

        @Test
        @DisplayName("유효한 코드로 참여하면 일반 멤버가 된다")
        void validCodeJoinsAsMember() throws Exception {
            RoomFixture fixture = createRoomWithMemberAndOutsider();
            saveValidInviteCode(fixture.room(), "123456");
            authenticateAs(fixture.outsider().getId());

            mockMvc.perform(post("/api/study-room/join")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(new StudyRoomJoinRequest("123456"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.roomId").value(fixture.roomId()))
                    .andExpect(jsonPath("$.data.memberCount").value(3));

            assertThat(memberRepository.findByRoomIdAndUserId(fixture.roomId(), fixture.outsider().getId()))
                    .as("참여자의 멤버십")
                    .isPresent()
                    .get()
                    .extracting(member -> member.getRole())
                    .isEqualTo(StudyRoomMemberRole.MEMBER);
        }

        @Test
        @DisplayName("같은 코드는 여러 사용자가 재사용할 수 있다")
        void codeCanBeUsedByMultipleUsers() throws Exception {
            RoomFixture fixture = createRoomWithMemberAndOutsider();
            saveValidInviteCode(fixture.room(), "123456");
            User second = fixtures.createUser("second");

            authenticateAs(fixture.outsider().getId());
            join("123456").andExpect(status().isOk());

            authenticateAs(second.getId());
            join("123456").andExpect(status().isOk());

            assertThat(memberRepository.countByRoomId(fixture.roomId())).as("최종 멤버 수").isEqualTo(4);
        }

        @ParameterizedTest(name = "코드 = \"{0}\"")
        @ValueSource(strings = {"12345", "1234567", "abcdef", "ABCDEF", "12 456", "12-456", "１２３４５６"})
        @DisplayName("6자리 숫자가 아닌 코드는 형식 단계에서 거절된다")
        void malformedCodeIsRejected(String code) throws Exception {
            RoomFixture fixture = createRoomWithMemberAndOutsider();
            saveValidInviteCode(fixture.room(), "123456");
            authenticateAs(fixture.outsider().getId());

            join(code)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value(10006));
        }

        @Test
        @DisplayName("코드가 null 이면 400 이다")
        void nullCodeIsRejected() throws Exception {
            User user = fixtures.createUser("user");
            authenticateAs(user.getId());

            mockMvc.perform(post("/api/study-room/join")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value(10006));
        }

        @Test
        @DisplayName("형식은 맞지만 존재하지 않는 코드는 400 이다")
        void unknownCodeIsRejected() throws Exception {
            RoomFixture fixture = createRoomWithMemberAndOutsider();
            saveValidInviteCode(fixture.room(), "123456");
            authenticateAs(fixture.outsider().getId());

            join("999999")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value(10006));
        }

        @Test
        @DisplayName("만료된 코드는 만료 전용 에러로 거절된다")
        void expiredCodeIsRejected() throws Exception {
            RoomFixture fixture = createRoomWithMemberAndOutsider();
            saveInviteCode(fixture.room(), "123456", LocalDateTime.now().minusSeconds(1));
            authenticateAs(fixture.outsider().getId());

            join("123456")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value(10007));

            assertThat(memberRepository.existsByRoomIdAndUserId(fixture.roomId(), fixture.outsider().getId()))
                    .as("만료 코드로는 멤버가 되지 않는다")
                    .isFalse();
        }

        @Test
        @DisplayName("이미 참여 중인 사용자가 다시 참여하면 409 다")
        void alreadyMemberIsRejected() throws Exception {
            RoomFixture fixture = createRoomWithMemberAndOutsider();
            saveValidInviteCode(fixture.room(), "123456");
            authenticateAs(fixture.member().getId());

            join("123456")
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.errorCode").value(10008));

            assertThat(memberRepository.countByRoomId(fixture.roomId())).as("중복 참여 후 멤버 수").isEqualTo(2);
        }

        @Test
        @DisplayName("방장이 자기 방 코드로 다시 참여해도 409 다")
        void hostRejoiningOwnRoomIsRejected() throws Exception {
            RoomFixture fixture = createRoomWithMemberAndOutsider();
            saveValidInviteCode(fixture.room(), "123456");
            authenticateAs(fixture.host().getId());

            join("123456")
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.errorCode").value(10008));
        }

        @Test
        @DisplayName("탈퇴했던 사용자는 같은 코드로 다시 참여할 수 있다")
        void leftMemberCanRejoin() throws Exception {
            RoomFixture fixture = createRoomWithMemberAndOutsider();
            saveValidInviteCode(fixture.room(), "123456");
            authenticateAs(fixture.member().getId());
            mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                            .delete("/api/study-room/{roomId}/leave", fixture.roomId()))
                    .andExpect(status().isOk());

            join("123456").andExpect(status().isOk());

            assertThat(memberRepository.existsByRoomIdAndUserId(fixture.roomId(), fixture.member().getId()))
                    .as("재참여한 멤버십")
                    .isTrue();
        }

        @Test
        @DisplayName("정원(20명)이 찬 방에는 참여할 수 없다")
        void fullRoomIsRejected() throws Exception {
            User host = fixtures.createUser("host");
            StudyRoom room = createRoom(host, "정원방");
            for (int i = 1; i < StudyRoomService.MAX_ROOM_MEMBER_COUNT; i++) {
                addMember(room, fixtures.createUser("filler" + i));
            }
            saveValidInviteCode(room, "123456");
            User latecomer = fixtures.createUser("latecomer");
            authenticateAs(latecomer.getId());

            join("123456")
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.errorCode").value(10004));

            assertThat(memberRepository.countByRoomId(room.getId()))
                    .as("정원 초과 시도 후 멤버 수")
                    .isEqualTo(StudyRoomService.MAX_ROOM_MEMBER_COUNT);
        }

        @Test
        @DisplayName("정원 직전(19명)이면 마지막 한 자리로 참여할 수 있다")
        void lastSeatIsAvailable() throws Exception {
            User host = fixtures.createUser("host");
            StudyRoom room = createRoom(host, "한자리방");
            for (int i = 1; i < StudyRoomService.MAX_ROOM_MEMBER_COUNT - 1; i++) {
                addMember(room, fixtures.createUser("filler" + i));
            }
            saveValidInviteCode(room, "123456");
            User latecomer = fixtures.createUser("latecomer");
            authenticateAs(latecomer.getId());

            join("123456").andExpect(status().isOk());

            assertThat(memberRepository.countByRoomId(room.getId()))
                    .as("마지막 자리 참여 후 멤버 수")
                    .isEqualTo(StudyRoomService.MAX_ROOM_MEMBER_COUNT);
        }

        @Test
        @DisplayName("이미 10개 방에 속한 사용자는 더 참여할 수 없다")
        void userRoomLimitIsEnforced() throws Exception {
            User joiner = fixtures.createUser("joiner");
            for (int i = 0; i < StudyRoomService.MAX_USER_ROOM_COUNT; i++) {
                createRoom(joiner, "내방" + i);
            }
            User host = fixtures.createUser("host");
            StudyRoom target = createRoom(host, "11번째방");
            saveValidInviteCode(target, "123456");
            authenticateAs(joiner.getId());

            join("123456")
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.errorCode").value(10005));
        }

        @Test
        @DisplayName("참여 후 곧바로 방 상세를 조회할 수 있다")
        void joinedUserCanReadRoom() throws Exception {
            RoomFixture fixture = createRoomWithMemberAndOutsider();
            saveValidInviteCode(fixture.room(), "123456");
            authenticateAs(fixture.outsider().getId());
            join("123456").andExpect(status().isOk());

            mockMvc.perform(get("/api/study-room/{roomId}", fixture.roomId()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.memberCount").value(3));
        }

        @Test
        @DisplayName("인증 없이 참여하면 401 이다")
        void unauthenticatedIsRejected() throws Exception {
            RoomFixture fixture = createRoomWithMemberAndOutsider();
            saveValidInviteCode(fixture.room(), "123456");
            clearAuthentication();

            join("123456").andExpect(status().isUnauthorized());
        }
    }

    private String issueCode(Long roomId) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/study-room/{roomId}/invite", roomId))
                .andExpect(status().isOk())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.data.code");
    }

    private org.springframework.test.web.servlet.ResultActions join(String code) throws Exception {
        return mockMvc.perform(post("/api/study-room/join")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new StudyRoomJoinRequest(code))));
    }
}
