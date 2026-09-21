package com.aisip.OnO.backend.studyroom.integration;

import com.aisip.OnO.backend.studyroom.dto.StudyRoomDtos.StudyRoomDetailResponse;
import com.aisip.OnO.backend.studyroom.dto.StudyRoomDtos.StudyRoomMemberResponse;
import com.aisip.OnO.backend.studyroom.entity.StudyRoom;
import com.aisip.OnO.backend.studyroom.entity.StudyRoomMember;
import com.aisip.OnO.backend.studyroom.entity.StudyRoomMemberRole;
import com.aisip.OnO.backend.studyroom.service.StudyRoomService;
import com.aisip.OnO.backend.studyroom.support.StudyRoomTestSupport;
import com.aisip.OnO.backend.user.entity.User;
import com.aisip.OnO.backend.user.service.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 회원 탈퇴가 스터디룸 멤버십을 어떻게 정리하는지 본다.
 *
 * <p>{@code User} 는 소프트 삭제라 탈퇴해도 {@code study_room_member} 행과
 * {@code study_room.host_user_id} 가 그대로 남는다. 방장이 탈퇴하면 방장 권한이 허공에 뜨고
 * 남은 멤버는 방 수정·삭제·챌린지 생성을 할 수 없게 된다. 탈퇴 경로가 방장이 직접 나갈 때와
 * 같은 규칙(가장 오래된 멤버에게 위임, 남은 멤버가 없으면 방 삭제)을 타는지 확인한다.
 */
@DisplayName("회원 탈퇴 시 스터디룸 정리")
class StudyRoomUserWithdrawalTest extends StudyRoomTestSupport {

    @Autowired
    private UserService userService;

    @Autowired
    private StudyRoomService studyRoomService;

    @Nested
    @DisplayName("방장이 탈퇴하면")
    class HostWithdraws {

        @Test
        @DisplayName("가장 먼저 들어온 멤버가 방장이 되고 host_user_id 도 그 멤버로 바뀐다")
        void delegatesToOldestMember() {
            User host = fixtures.createUser("host");
            User older = fixtures.createUser("older");
            User newer = fixtures.createUser("newer");
            StudyRoom room = createRoom(host, "위임방");
            StudyRoomMember newerMember = addMember(room, newer);
            StudyRoomMember olderMember = addMember(room, older);
            // 저장 순서와 반대로 가입 시각을 둬서, 위임 기준이 id 가 아니라 가입 시각인지 확인한다.
            setJoinedAt(newerMember, LocalDateTime.now().minusDays(1));
            setJoinedAt(olderMember, LocalDateTime.now().minusDays(3));

            userService.deleteUserById(host.getId());

            assertThat(roomRepository.findById(room.getId()).orElseThrow().getHostUserId())
                    .as("앱은 hostUserId 로 방장을 판단한다")
                    .isEqualTo(older.getId());
            assertThat(roleOf(room, older)).as("가장 오래된 멤버의 역할").isEqualTo(StudyRoomMemberRole.HOST);
            assertThat(roleOf(room, newer)).as("나중에 들어온 멤버의 역할").isEqualTo(StudyRoomMemberRole.MEMBER);
            assertThat(memberRepository.existsByRoomIdAndUserId(room.getId(), host.getId()))
                    .as("탈퇴한 방장의 멤버십").isFalse();
            assertThat(memberRepository.countByRoomIdAndRole(room.getId(), StudyRoomMemberRole.HOST))
                    .as("방장은 한 명").isEqualTo(1);
        }

        @Test
        @DisplayName("위임받은 멤버는 곧바로 방장 권한으로 방을 수정할 수 있다")
        void delegatedHostCanManageRoom() throws Exception {
            RoomFixture fixture = createRoomWithMemberAndOutsider();

            userService.deleteUserById(fixture.host().getId());

            authenticateAs(fixture.member().getId());
            mockMvc.perform(patch("/api/study-room/{roomId}", fixture.roomId())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\":\"새 방장이 바꾼 이름\"}"))
                    .andExpect(status().isOk());

            assertThat(roomRepository.findById(fixture.roomId()).orElseThrow().getName())
                    .isEqualTo("새 방장이 바꾼 이름");
        }

        @Test
        @DisplayName("혼자 있던 방은 삭제된다")
        void deletesRoomWhenAlone() {
            User host = fixtures.createUser("host");
            StudyRoom room = createRoom(host, "혼자방");

            userService.deleteUserById(host.getId());

            assertThat(roomRepository.findById(room.getId())).as("삭제된 방").isEmpty();
            assertThat(memberRepository.countByRoomId(room.getId())).as("남은 멤버십").isZero();
        }
    }

    @Nested
    @DisplayName("일반 멤버가 탈퇴하면")
    class MemberWithdraws {

        @Test
        @DisplayName("자기 멤버십만 사라지고 방과 방장은 그대로다")
        void removesOnlyOwnMembership() {
            RoomFixture fixture = createRoomWithMemberAndOutsider();

            userService.deleteUserById(fixture.member().getId());

            StudyRoom room = roomRepository.findById(fixture.roomId()).orElseThrow();
            assertThat(room.getHostUserId()).as("방장 유지").isEqualTo(fixture.host().getId());
            assertThat(roleOf(room, fixture.host())).isEqualTo(StudyRoomMemberRole.HOST);
            assertThat(memberRepository.countByRoomId(fixture.roomId()))
                    .as("정원 계산에서 탈퇴자가 빠진다")
                    .isEqualTo(1);
        }
    }

    @Test
    @DisplayName("여러 방에 속해 있으면 방마다 역할에 맞게 정리한다")
    void cleansUpEveryRoom() {
        User leaver = fixtures.createUser("leaver");
        User other = fixtures.createUser("other");
        StudyRoom hostedAlone = createRoom(leaver, "혼자 연 방");
        StudyRoom hostedShared = createRoom(leaver, "같이 연 방");
        addMember(hostedShared, other);
        StudyRoom joined = createRoom(other, "들어간 방");
        addMember(joined, leaver);

        userService.deleteUserById(leaver.getId());

        assertThat(roomRepository.findById(hostedAlone.getId())).as("혼자 연 방").isEmpty();
        assertThat(roomRepository.findById(hostedShared.getId()).orElseThrow().getHostUserId())
                .as("같이 연 방의 새 방장").isEqualTo(other.getId());
        assertThat(roomRepository.findById(joined.getId())).as("들어간 방").isPresent();
        assertThat(memberRepository.countByUserId(leaver.getId())).as("남은 멤버십").isZero();
    }

    @Test
    @DisplayName("스터디룸에 속하지 않은 사용자의 탈퇴는 다른 방에 영향이 없다")
    void doesNotTouchOtherRooms() {
        RoomFixture fixture = createRoomWithMemberAndOutsider();

        userService.deleteUserById(fixture.outsider().getId());

        assertThat(memberRepository.countByRoomId(fixture.roomId())).isEqualTo(2);
        assertThat(roomRepository.findById(fixture.roomId()).orElseThrow().getHostUserId())
                .isEqualTo(fixture.host().getId());
    }

    /**
     * 이 수정 전에 이미 탈퇴한 사용자(멤버 행이 남은 채 소프트 삭제된 사용자)가 조회에서
     * 어떻게 보이는지 기록해 둔다. 운영 데이터 정리 방안을 정하는 근거다.
     */
    @Nested
    @DisplayName("정리 없이 소프트 삭제만 된 기존 탈퇴자")
    class LegacyWithdrawnMember {

        @Test
        @DisplayName("user 를 join fetch 하는 멤버 목록에서는 빠지지만 정원 계산에는 남는다")
        void hiddenFromMemberListButCounted() {
            RoomFixture fixture = createRoomWithMemberAndOutsider();
            softDeleteOnly(fixture.member());

            StudyRoomDetailResponse detail = studyRoomService.getRoom(fixture.roomId(), fixture.host().getId());

            assertThat(detail.members()).extracting(StudyRoomMemberResponse::userId)
                    .as("상세 멤버 목록")
                    .containsExactly(fixture.host().getId());
            assertThat(detail.memberCount()).as("상세 memberCount").isEqualTo(1);
            assertThat(memberRepository.countByRoomId(fixture.roomId()))
                    .as("초대 코드 가입 정원 검사는 멤버 행을 그대로 센다")
                    .isEqualTo(2);
        }

        @Test
        @DisplayName("방장이 나갈 때 남아 있는 탈퇴자에게는 방장을 넘기지 않는다")
        void leaveRoomSkipsWithdrawnCandidate() {
            User host = fixtures.createUser("host");
            User ghost = fixtures.createUser("ghost");
            User alive = fixtures.createUser("alive");
            StudyRoom room = createRoom(host, "유령방");
            StudyRoomMember ghostMember = addMember(room, ghost);
            StudyRoomMember aliveMember = addMember(room, alive);
            setJoinedAt(ghostMember, LocalDateTime.now().minusDays(3));
            setJoinedAt(aliveMember, LocalDateTime.now().minusDays(1));
            softDeleteOnly(ghost);

            studyRoomService.leaveRoom(room.getId(), host.getId());

            assertThat(roomRepository.findById(room.getId()).orElseThrow().getHostUserId())
                    .as("가장 오래된 멤버 행은 탈퇴자지만 살아 있는 멤버가 방장이 된다")
                    .isEqualTo(alive.getId());
        }
    }

    private StudyRoomMemberRole roleOf(StudyRoom room, User user) {
        return memberRepository.findByRoomIdAndUserId(room.getId(), user.getId())
                .map(StudyRoomMember::getRole)
                .orElseThrow();
    }

    private void setJoinedAt(StudyRoomMember member, LocalDateTime joinedAt) {
        inTransaction(() -> entityManager.createNativeQuery(
                        "update study_room_member set created_at = :joinedAt where id = :id")
                .setParameter("joinedAt", joinedAt)
                .setParameter("id", member.getId())
                .executeUpdate());
    }

    /** 이번 수정 전의 탈퇴처럼 멤버십은 그대로 두고 사용자만 소프트 삭제한다. */
    private void softDeleteOnly(User user) {
        inTransaction(() -> entityManager.createNativeQuery(
                        "update user set deleted_at = now() where id = :id")
                .setParameter("id", user.getId())
                .executeUpdate());
    }
}
