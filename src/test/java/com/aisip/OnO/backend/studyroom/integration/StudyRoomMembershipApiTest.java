package com.aisip.OnO.backend.studyroom.integration;

import com.aisip.OnO.backend.studyroom.dto.StudyRoomDtos.StudyRoomGoalUpdateRequest;
import com.aisip.OnO.backend.studyroom.entity.StudyRoom;
import com.aisip.OnO.backend.studyroom.entity.StudyRoomMemberRole;
import com.aisip.OnO.backend.studyroom.support.StudyRoomTestSupport;
import com.aisip.OnO.backend.user.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("스터디룸 멤버십 API — 탈퇴·방장 위임·강퇴·목표")
class StudyRoomMembershipApiTest extends StudyRoomTestSupport {

    @Nested
    @DisplayName("탈퇴")
    class LeaveRoom {

        @Test
        @DisplayName("일반 멤버가 나가면 자기 멤버십만 사라지고 방은 남는다")
        void memberLeaveRemovesOnlyOwnMembership() throws Exception {
            RoomFixture fixture = createRoomWithMemberAndOutsider();
            authenticateAs(fixture.member().getId());

            mockMvc.perform(delete("/api/study-room/{roomId}/leave", fixture.roomId()))
                    .andExpect(status().isOk());

            assertThat(memberRepository.existsByRoomIdAndUserId(fixture.roomId(), fixture.member().getId()))
                    .as("탈퇴한 멤버의 멤버십").isFalse();
            assertThat(memberRepository.existsByRoomIdAndUserId(fixture.roomId(), fixture.host().getId()))
                    .as("남은 방장의 멤버십").isTrue();
            assertThat(roomRepository.findById(fixture.roomId())).as("남아 있는 방").isPresent();
        }

        @Test
        @DisplayName("방장이 나가면 남은 멤버 중 한 명이 방장으로 승격된다")
        void hostLeaveDelegatesToRemainingMember() throws Exception {
            RoomFixture fixture = createRoomWithMemberAndOutsider();
            authenticateAs(fixture.host().getId());

            mockMvc.perform(delete("/api/study-room/{roomId}/leave", fixture.roomId()))
                    .andExpect(status().isOk());

            StudyRoom room = roomRepository.findById(fixture.roomId()).orElseThrow();
            assertThat(room.getHostUserId()).as("위임된 방장 ID").isEqualTo(fixture.member().getId());
            assertThat(memberRepository.findByRoomIdAndUserId(fixture.roomId(), fixture.member().getId()))
                    .as("승격된 멤버의 역할")
                    .isPresent()
                    .get()
                    .extracting(member -> member.getRole())
                    .isEqualTo(StudyRoomMemberRole.HOST);
            assertThat(memberRepository.existsByRoomIdAndUserId(fixture.roomId(), fixture.host().getId()))
                    .as("나간 방장의 멤버십").isFalse();
        }

        @Test
        @DisplayName("위임받은 새 방장은 곧바로 방장 권한을 행사할 수 있다")
        void delegatedHostGainsHostPrivileges() throws Exception {
            RoomFixture fixture = createRoomWithMemberAndOutsider();
            authenticateAs(fixture.host().getId());
            mockMvc.perform(delete("/api/study-room/{roomId}/leave", fixture.roomId()))
                    .andExpect(status().isOk());

            authenticateAs(fixture.member().getId());
            mockMvc.perform(delete("/api/study-room/{roomId}", fixture.roomId()))
                    .andExpect(status().isOk());

            assertThat(roomRepository.findById(fixture.roomId())).as("새 방장이 삭제한 방").isEmpty();
        }

        @Test
        @DisplayName("마지막 남은 방장이 나가면 방 자체가 삭제된다")
        void lastMemberLeaveDeletesRoom() throws Exception {
            User host = fixtures.createUser("host");
            StudyRoom room = createRoom(host, "혼자방");
            authenticateAs(host.getId());

            mockMvc.perform(delete("/api/study-room/{roomId}/leave", room.getId()))
                    .andExpect(status().isOk());

            assertThat(roomRepository.findById(room.getId())).as("삭제된 방").isEmpty();
            assertThat(memberRepository.countByRoomId(room.getId())).as("남은 멤버십").isZero();
        }

        @Test
        @DisplayName("비멤버는 탈퇴할 수 없다")
        void nonMemberCannotLeave() throws Exception {
            RoomFixture fixture = createRoomWithMemberAndOutsider();
            authenticateAs(fixture.outsider().getId());

            mockMvc.perform(delete("/api/study-room/{roomId}/leave", fixture.roomId()))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.errorCode").value(10002));

            assertThat(memberRepository.countByRoomId(fixture.roomId())).as("멤버 수 변화 없음").isEqualTo(2);
        }

        @Test
        @DisplayName("이미 탈퇴한 멤버가 다시 탈퇴하려 하면 403 이다")
        void leftMemberCannotLeaveAgain() throws Exception {
            RoomFixture fixture = createRoomWithMemberAndOutsider();
            authenticateAs(fixture.member().getId());
            mockMvc.perform(delete("/api/study-room/{roomId}/leave", fixture.roomId()))
                    .andExpect(status().isOk());

            mockMvc.perform(delete("/api/study-room/{roomId}/leave", fixture.roomId()))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.errorCode").value(10002));
        }

        @Test
        @DisplayName("존재하지 않는 방에서는 탈퇴할 수 없다")
        void unknownRoomIsNotFound() throws Exception {
            User user = fixtures.createUser("user");
            authenticateAs(user.getId());

            mockMvc.perform(delete("/api/study-room/{roomId}/leave", nonExistentRoomId()))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.errorCode").value(10001));
        }
    }

    @Nested
    @DisplayName("강퇴")
    class KickMember {

        @Test
        @DisplayName("방장은 일반 멤버를 강퇴할 수 있다")
        void hostCanKickMember() throws Exception {
            RoomFixture fixture = createRoomWithMemberAndOutsider();
            authenticateAs(fixture.host().getId());

            mockMvc.perform(delete("/api/study-room/{roomId}/members/{memberId}",
                            fixture.roomId(), fixture.member().getId()))
                    .andExpect(status().isOk());

            assertThat(memberRepository.existsByRoomIdAndUserId(fixture.roomId(), fixture.member().getId()))
                    .as("강퇴된 멤버의 멤버십").isFalse();
        }

        @Test
        @DisplayName("방장이 자기 자신을 강퇴하려 하면 403 이다")
        void hostCannotKickSelf() throws Exception {
            RoomFixture fixture = createRoomWithMemberAndOutsider();
            authenticateAs(fixture.host().getId());

            mockMvc.perform(delete("/api/study-room/{roomId}/members/{memberId}",
                            fixture.roomId(), fixture.host().getId()))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.errorCode").value(10003));

            assertThat(memberRepository.existsByRoomIdAndUserId(fixture.roomId(), fixture.host().getId()))
                    .as("방장 자신의 멤버십은 유지된다").isTrue();
        }

        @Test
        @DisplayName("일반 멤버는 다른 멤버를 강퇴할 수 없다")
        void memberCannotKick() throws Exception {
            RoomFixture fixture = createRoomWithMemberAndOutsider();
            User another = fixtures.createUser("another");
            addMember(fixture.room(), another);
            authenticateAs(fixture.member().getId());

            mockMvc.perform(delete("/api/study-room/{roomId}/members/{memberId}",
                            fixture.roomId(), another.getId()))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.errorCode").value(10003));

            assertThat(memberRepository.existsByRoomIdAndUserId(fixture.roomId(), another.getId()))
                    .as("강퇴되지 않은 멤버십").isTrue();
        }

        @Test
        @DisplayName("비멤버는 강퇴할 수 없다")
        void nonMemberCannotKick() throws Exception {
            RoomFixture fixture = createRoomWithMemberAndOutsider();
            authenticateAs(fixture.outsider().getId());

            mockMvc.perform(delete("/api/study-room/{roomId}/members/{memberId}",
                            fixture.roomId(), fixture.member().getId()))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.errorCode").value(10002));
        }

        @Test
        @DisplayName("방에 속하지 않은 사용자를 강퇴하려 하면 403 이다")
        void kickingOutsiderIsForbidden() throws Exception {
            RoomFixture fixture = createRoomWithMemberAndOutsider();
            authenticateAs(fixture.host().getId());

            mockMvc.perform(delete("/api/study-room/{roomId}/members/{memberId}",
                            fixture.roomId(), fixture.outsider().getId()))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.errorCode").value(10002));
        }

        @Test
        @DisplayName("강퇴된 멤버는 더 이상 방을 조회할 수 없다")
        void kickedMemberLosesAccess() throws Exception {
            RoomFixture fixture = createRoomWithMemberAndOutsider();
            authenticateAs(fixture.host().getId());
            mockMvc.perform(delete("/api/study-room/{roomId}/members/{memberId}",
                            fixture.roomId(), fixture.member().getId()))
                    .andExpect(status().isOk());

            authenticateAs(fixture.member().getId());
            mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                            .get("/api/study-room/{roomId}", fixture.roomId()))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.errorCode").value(10002));
        }
    }

    @Nested
    @DisplayName("주간 목표")
    class WeeklyGoal {

        @Test
        @DisplayName("목표를 설정하면 현재 주간 문제 등록 수가 진행도로 함께 온다")
        void settingGoalReturnsProgress() throws Exception {
            RoomFixture fixture = createRoomWithMemberAndOutsider();
            saveProblem(fixture.member().getId());
            saveProblem(fixture.member().getId());
            authenticateAs(fixture.member().getId());

            mockMvc.perform(put("/api/study-room/{roomId}/members/me/goal", fixture.roomId())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(new StudyRoomGoalUpdateRequest(5))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.weeklyGoal").value(5))
                    .andExpect(jsonPath("$.data.goalProgress").value(2));
        }

        @Test
        @DisplayName("0 을 넣으면 목표가 해제되고 진행도도 비워진다")
        void zeroClearsGoal() throws Exception {
            RoomFixture fixture = createRoomWithMemberAndOutsider();
            authenticateAs(fixture.member().getId());
            mockMvc.perform(put("/api/study-room/{roomId}/members/me/goal", fixture.roomId())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(new StudyRoomGoalUpdateRequest(5))))
                    .andExpect(status().isOk());

            mockMvc.perform(put("/api/study-room/{roomId}/members/me/goal", fixture.roomId())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(new StudyRoomGoalUpdateRequest(0))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.weeklyGoal").doesNotExist())
                    .andExpect(jsonPath("$.data.goalProgress").doesNotExist());
        }

        @Test
        @DisplayName("null 을 넣어도 목표가 해제된다")
        void nullClearsGoal() throws Exception {
            RoomFixture fixture = createRoomWithMemberAndOutsider();
            authenticateAs(fixture.member().getId());

            mockMvc.perform(put("/api/study-room/{roomId}/members/me/goal", fixture.roomId())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(new StudyRoomGoalUpdateRequest(null))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.weeklyGoal").doesNotExist());
        }

        @Test
        @DisplayName("음수 목표는 400 으로 거절된다")
        void negativeGoalIsRejected() throws Exception {
            RoomFixture fixture = createRoomWithMemberAndOutsider();
            authenticateAs(fixture.member().getId());

            mockMvc.perform(put("/api/study-room/{roomId}/members/me/goal", fixture.roomId())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(new StudyRoomGoalUpdateRequest(-1))))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value(10016));
        }

        @Test
        @DisplayName("목표는 방마다 따로 관리된다")
        void goalIsScopedPerRoom() throws Exception {
            User user = fixtures.createUser("user");
            StudyRoom first = createRoom(user, "첫방");
            StudyRoom second = createRoom(user, "둘째방");
            authenticateAs(user.getId());

            mockMvc.perform(put("/api/study-room/{roomId}/members/me/goal", first.getId())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(new StudyRoomGoalUpdateRequest(3))))
                    .andExpect(status().isOk());

            assertThat(memberRepository.findByRoomIdAndUserId(first.getId(), user.getId()).orElseThrow().getWeeklyGoal())
                    .as("첫 방의 목표").isEqualTo(3);
            assertThat(memberRepository.findByRoomIdAndUserId(second.getId(), user.getId()).orElseThrow().getWeeklyGoal())
                    .as("둘째 방의 목표 — 영향받지 않는다").isNull();
        }

        @Test
        @DisplayName("비멤버는 목표를 설정할 수 없다")
        void nonMemberCannotSetGoal() throws Exception {
            RoomFixture fixture = createRoomWithMemberAndOutsider();
            authenticateAs(fixture.outsider().getId());

            mockMvc.perform(put("/api/study-room/{roomId}/members/me/goal", fixture.roomId())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(new StudyRoomGoalUpdateRequest(5))))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.errorCode").value(10002));
        }

        @Test
        @DisplayName("설정한 목표는 방 상세의 내 멤버 항목에 반영된다")
        void goalAppearsInRoomDetail() throws Exception {
            RoomFixture fixture = createRoomWithMemberAndOutsider();
            savePractices(fixture.member().getId(), LocalDateTime.now(), 1);
            authenticateAs(fixture.member().getId());
            mockMvc.perform(put("/api/study-room/{roomId}/members/me/goal", fixture.roomId())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(new StudyRoomGoalUpdateRequest(4))))
                    .andExpect(status().isOk());

            mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                            .get("/api/study-room/{roomId}", fixture.roomId()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.members[?(@.userId == " + fixture.member().getId() + ")].weeklyGoal").value(4));
        }
    }
}
