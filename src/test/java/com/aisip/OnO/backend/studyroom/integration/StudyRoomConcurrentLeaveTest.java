package com.aisip.OnO.backend.studyroom.integration;

import com.aisip.OnO.backend.studyroom.entity.StudyRoom;
import com.aisip.OnO.backend.studyroom.entity.StudyRoomMember;
import com.aisip.OnO.backend.studyroom.service.StudyRoomService;
import com.aisip.OnO.backend.studyroom.support.StudyRoomTestSupport;
import com.aisip.OnO.backend.user.entity.User;
import com.aisip.OnO.backend.user.service.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;

/**
 * 같은 멤버 행을 두 요청이 지우려 할 때를 본다.
 *
 * <p>멤버십 삭제가 벌크 delete 였을 때는 이미 지워진 행을 다시 지워도 0건으로 조용히 끝났다.
 * 엔티티 delete 로 바꾼 뒤에는 0행 삭제가 낙관적 락 예외로 올라가 500 이 됐다(#368).
 * 나가기 연타, 강퇴와 본인 나가기, 강퇴와 회원 탈퇴가 겹치는 경우가 여기에 해당한다.
 *
 * <p>테스트 트랜잭션 없이 실제 커밋으로 돌린다. 앞선 요청이 커밋한 뒤에 뒤 요청이 이미 읽어 둔
 * 멤버를 지우는 순서를 만들어야 재현되기 때문이다.
 */
@DisplayName("스터디룸 멤버 행 동시 삭제")
class StudyRoomConcurrentLeaveTest extends StudyRoomTestSupport {

    @Autowired
    private StudyRoomService studyRoomService;

    @Autowired
    private UserService userService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("같은 멤버의 나가기 요청이 동시에 두 번 와도 둘 다 200 이고 방은 남는다")
    void concurrentLeaveRequestsBothSucceed() throws Exception {
        for (int round = 0; round < 10; round++) {
            User host = fixtures.createUser("host" + round);
            User member = fixtures.createUser("member" + round);
            StudyRoom room = createRoom(host);
            addMember(room, member);
            var auth = new UsernamePasswordAuthenticationToken(
                    member.getId(), null, List.of(new SimpleGrantedAuthority("ROLE_MEMBER")));

            CyclicBarrier barrier = new CyclicBarrier(2);
            ExecutorService executor = Executors.newFixedThreadPool(2);
            List<Future<Integer>> results = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                results.add(executor.submit(() -> {
                    barrier.await();
                    return mockMvc.perform(delete("/api/study-room/{roomId}/leave", room.getId())
                                    .with(authentication(auth)))
                            .andReturn().getResponse().getStatus();
                }));
            }
            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> result : results) {
                statuses.add(result.get());
            }
            executor.shutdown();

            assertThat(statuses).as("round %d 응답", round).containsOnly(200);
            assertThat(memberRepository.existsByRoomIdAndUserId(room.getId(), member.getId()))
                    .as("round %d 나간 멤버의 멤버십", round).isFalse();
            assertThat(memberRepository.countByRoomId(room.getId())).as("round %d 남은 멤버", round).isEqualTo(1);
        }
    }

    @Test
    @DisplayName("읽어 둔 멤버를 다른 요청이 먼저 지우고 커밋해도 나가기가 성공한다")
    void leaveSucceedsWhenRowWasDeletedAfterRead() {
        User host = fixtures.createUser("host");
        User member = fixtures.createUser("member");
        StudyRoom room = createRoom(host);
        addMember(room, member);

        transactionTemplate.executeWithoutResult(status -> {
            // 이 트랜잭션이 멤버를 먼저 읽어 둔다. 스냅숏과 영속성 컨텍스트에 멤버가 남는다.
            StudyRoomMember loaded = memberRepository.findByRoomIdAndUserId(room.getId(), member.getId()).orElseThrow();
            assertThat(loaded).isNotNull();

            kickInAnotherTransaction(room.getId(), member.getId());

            studyRoomService.leaveRoom(room.getId(), member.getId());
        });

        assertThat(memberRepository.existsByRoomIdAndUserId(room.getId(), member.getId())).as("멤버십").isFalse();
        assertThat(memberRepository.countByRoomId(room.getId())).as("남은 멤버").isEqualTo(1);
    }

    @Test
    @DisplayName("읽어 둔 멤버십이 강퇴로 먼저 지워져도 회원 탈퇴가 롤백되지 않는다")
    void withdrawalSucceedsWhenMembershipWasKickedAfterRead() {
        User host = fixtures.createUser("host");
        User member = fixtures.createUser("member");
        StudyRoom kickedRoom = createRoom(host, "강퇴방");
        addMember(kickedRoom, member);
        User otherHost = fixtures.createUser("otherHost");
        StudyRoom remainingRoom = createRoom(otherHost, "남은방");
        addMember(remainingRoom, member);

        transactionTemplate.executeWithoutResult(status -> {
            StudyRoomMember loaded = memberRepository.findByRoomIdAndUserId(kickedRoom.getId(), member.getId()).orElseThrow();
            assertThat(loaded).isNotNull();

            kickInAnotherTransaction(kickedRoom.getId(), member.getId());

            userService.deleteUserById(member.getId());
        });

        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from user where id = ? and deleted_at is not null", Long.class, member.getId()))
                .as("탈퇴한 사용자").isEqualTo(1);
        assertThat(memberRepository.existsByRoomIdAndUserId(kickedRoom.getId(), member.getId()))
                .as("강퇴된 방의 멤버십").isFalse();
        assertThat(memberRepository.existsByRoomIdAndUserId(remainingRoom.getId(), member.getId()))
                .as("다른 방의 멤버십도 정리").isFalse();
        assertThat(memberRepository.countByRoomId(kickedRoom.getId())).as("강퇴방 남은 멤버").isEqualTo(1);
        assertThat(memberRepository.countByRoomId(remainingRoom.getId())).as("남은방 남은 멤버").isEqualTo(1);
    }

    /** 다른 스레드의 별도 트랜잭션에서 방장 강퇴와 같은 벌크 delete 를 커밋한다. */
    private void kickInAnotherTransaction(Long roomId, Long userId) {
        CompletableFuture.runAsync(() -> transactionTemplate.executeWithoutResult(
                status -> memberRepository.deleteByRoomIdAndUserId(roomId, userId))).join();
    }
}
