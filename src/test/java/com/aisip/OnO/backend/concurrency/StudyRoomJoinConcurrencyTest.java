package com.aisip.OnO.backend.concurrency;

import com.aisip.OnO.backend.studyroom.dto.StudyRoomDtos.StudyRoomJoinRequest;
import com.aisip.OnO.backend.studyroom.entity.StudyRoom;
import com.aisip.OnO.backend.studyroom.exception.StudyRoomErrorCase;
import com.aisip.OnO.backend.studyroom.service.StudyRoomInviteService;
import com.aisip.OnO.backend.studyroom.service.StudyRoomService;
import com.aisip.OnO.backend.studyroom.support.StudyRoomTestSupport;
import com.aisip.OnO.backend.user.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 스터디룸 참여의 정원 경합.
 *
 * <p>{@code StudyRoomInviteService.join} 은 "인원을 세고 - 모자라면 넣는다"는 전형적인
 * check-then-act 다. 방어는 {@code StudyRoomService.lockRoom} 의 {@code SELECT ... FOR UPDATE}
 * 하나뿐이라, 그 락이 실제로 카운트-삽입 구간 전체를 감싸는지가 정원 초과 여부를 가른다.
 *
 * <p>MySQL 은 REPEATABLE READ 라서 "락을 잡았다"는 사실만으로는 부족하다. 락 이전에
 * 일반 SELECT 가 먼저 나가 read view 가 만들어져 있으면, 락을 얻은 뒤 세는 COUNT 가
 * 여전히 예전 스냅샷을 보고 이미 커밋된 다른 참여자를 놓칠 수 있다.
 * 여기서 확인하는 것이 정확히 그 지점이다.
 */
@DisplayName("동시성 - 스터디룸 참여 정원")
class StudyRoomJoinConcurrencyTest extends StudyRoomTestSupport {

    private static final int THREAD_COUNT = 8;

    @Autowired
    private StudyRoomInviteService inviteService;

    @Nested
    @DisplayName("방 정원(20명)의 마지막 한 자리")
    class LastSeatInRoom {

        @Test
        @DisplayName("남은 한 자리에 8명이 동시에 들어와도 정확히 1명만 참여하고 정원은 20명을 넘지 않는다")
        void onlyOneJoinsTheLastSeat() {
            User host = fixtures.createUser("host");
            StudyRoom room = createRoom(host);
            fillRoomUpTo(room, StudyRoomService.MAX_ROOM_MEMBER_COUNT - 1);
            String code = "100001";
            saveValidInviteCode(room, code);
            List<User> candidates = createUsers(THREAD_COUNT);

            ConcurrentRunner.Outcome outcome = ConcurrentRunner.runConcurrently(
                    THREAD_COUNT,
                    index -> inviteService.join(new StudyRoomJoinRequest(code), candidates.get(index).getId()));

            assertThat(outcome.serverErrors())
                    .as("정원 초과 거절은 409 여야 한다. 다른 예외가 올라오면 사용자에게 500 이 나간다")
                    .isEmpty();
            assertThat(outcome.successCount())
                    .as("남은 자리가 하나면 성공도 하나여야 한다")
                    .isEqualTo(1);
            assertThat(outcome.rejectedErrorCases())
                    .as("나머지는 모두 정원 초과로 거절")
                    .containsOnly(StudyRoomErrorCase.STUDY_ROOM_FULL)
                    .hasSize(THREAD_COUNT - 1);
            assertThat(memberRepository.countByRoomId(room.getId()))
                    .as("최종 인원은 정원과 정확히 같아야 한다")
                    .isEqualTo(StudyRoomService.MAX_ROOM_MEMBER_COUNT);
        }

        @Test
        @DisplayName("빈자리가 넉넉하면 동시 참여자 전원이 들어온다")
        void everyoneJoinsWhenSeatsRemain() {
            User host = fixtures.createUser("host");
            StudyRoom room = createRoom(host);
            String code = "100002";
            saveValidInviteCode(room, code);
            List<User> candidates = createUsers(THREAD_COUNT);

            ConcurrentRunner.Outcome outcome = ConcurrentRunner.runConcurrently(
                    THREAD_COUNT,
                    index -> inviteService.join(new StudyRoomJoinRequest(code), candidates.get(index).getId()));

            assertThat(outcome.failures()).as("거절될 이유가 없다").isEmpty();
            assertThat(memberRepository.countByRoomId(room.getId()))
                    .as("방장 + 동시 참여자 전원")
                    .isEqualTo(THREAD_COUNT + 1L);
        }
    }

    @Nested
    @DisplayName("같은 사용자의 중복 참여")
    class DuplicateJoinBySameUser {

        @Test
        @DisplayName("한 사용자가 같은 코드를 8번 동시에 눌러도 멤버십은 하나만 생긴다")
        void createsSingleMembershipUnderConcurrentRequests() {
            User host = fixtures.createUser("host");
            StudyRoom room = createRoom(host);
            String code = "100003";
            saveValidInviteCode(room, code);
            User joiner = fixtures.createUser("joiner");

            ConcurrentRunner.Outcome outcome = ConcurrentRunner.runConcurrently(
                    THREAD_COUNT,
                    () -> inviteService.join(new StudyRoomJoinRequest(code), joiner.getId()));

            assertThat(outcome.serverErrors())
                    .as("유니크 제약 위반이 그대로 올라오면 500 이다")
                    .isEmpty();
            assertThat(outcome.successCount())
                    .as("중복 참여는 정확히 한 번만 성공해야 한다")
                    .isEqualTo(1);
            assertThat(outcome.rejectedErrorCases())
                    .as("나머지는 이미 참여 중으로 거절")
                    .containsOnly(StudyRoomErrorCase.ALREADY_MEMBER);
            assertThat(memberRepository.countByRoomId(room.getId()))
                    .as("방장 + 참여자 1명")
                    .isEqualTo(2L);
        }
    }

    @Nested
    @DisplayName("사용자당 참여 가능한 방 수(10개)")
    class PerUserRoomLimit {

        @Test
        @DisplayName("서로 다른 방에 동시에 들어가도 참여 방 수는 상한을 넘지 않는다")
        void doesNotExceedPerUserRoomLimit() {
            User joiner = fixtures.createUser("joiner");
            int alreadyJoined = StudyRoomService.MAX_USER_ROOM_COUNT - 1;
            for (int i = 0; i < alreadyJoined; i++) {
                addMember(createRoom(fixtures.createUser("host")), joiner);
            }
            List<String> codes = new ArrayList<>();
            for (int i = 0; i < THREAD_COUNT; i++) {
                StudyRoom room = createRoom(fixtures.createUser("host"));
                String code = "20000%d".formatted(i);
                saveValidInviteCode(room, code);
                codes.add(code);
            }

            ConcurrentRunner.Outcome outcome = ConcurrentRunner.runConcurrently(
                    THREAD_COUNT,
                    index -> inviteService.join(new StudyRoomJoinRequest(codes.get(index)), joiner.getId()));

            assertThat(outcome.serverErrors())
                    .as("상한 초과 거절은 409 여야 한다")
                    .isEmpty();
            assertThat(memberRepository.countByUserId(joiner.getId()))
                    .as("이미 %d개에 속해 있으므로 %d개를 넘길 수 없다",
                            alreadyJoined, StudyRoomService.MAX_USER_ROOM_COUNT)
                    .isEqualTo(StudyRoomService.MAX_USER_ROOM_COUNT);
            assertThat(outcome.successCount()).as("남은 한 자리만큼만 성공").isEqualTo(1);
            assertThat(outcome.rejectedErrorCases())
                    .containsOnly(StudyRoomErrorCase.STUDY_ROOM_LIMIT_EXCEEDED);
        }
    }

    private void fillRoomUpTo(StudyRoom room, int memberCount) {
        long current = memberRepository.countByRoomId(room.getId());
        for (long i = current; i < memberCount; i++) {
            addMember(room, fixtures.createUser("filler"));
        }
    }

    private List<User> createUsers(int count) {
        List<User> users = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            users.add(fixtures.createUser("candidate"));
        }
        return users;
    }
}
