package com.aisip.OnO.backend.studyroom.service;

import com.aisip.OnO.backend.common.exception.ApplicationException;
import com.aisip.OnO.backend.studyroom.dto.StudyRoomDtos.InviteCodeResponse;
import com.aisip.OnO.backend.studyroom.dto.StudyRoomDtos.StudyRoomCreateRequest;
import com.aisip.OnO.backend.studyroom.dto.StudyRoomDtos.StudyRoomDetailResponse;
import com.aisip.OnO.backend.studyroom.dto.StudyRoomDtos.StudyRoomGoalUpdateRequest;
import com.aisip.OnO.backend.studyroom.dto.StudyRoomDtos.StudyRoomJoinRequest;
import com.aisip.OnO.backend.studyroom.entity.StudyRoomFeed;
import com.aisip.OnO.backend.studyroom.entity.StudyRoomFeedEventType;
import com.aisip.OnO.backend.studyroom.entity.StudyRoomFeedReaction;
import com.aisip.OnO.backend.studyroom.entity.StudyRoomMemberRole;
import com.aisip.OnO.backend.studyroom.exception.StudyRoomErrorCase;
import com.aisip.OnO.backend.studyroom.repository.StudyRoomFeedReactionRepository;
import com.aisip.OnO.backend.studyroom.repository.StudyRoomFeedRepository;
import com.aisip.OnO.backend.studyroom.repository.StudyRoomRepository;
import com.aisip.OnO.backend.studyroom.repository.StudyRoomMemberRepository;
import com.aisip.OnO.backend.user.entity.User;
import com.aisip.OnO.backend.user.repository.UserRepository;
import com.aisip.OnO.backend.util.RandomUserGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class StudyRoomServiceIntegrationTest {

    @Autowired
    private StudyRoomService studyRoomService;

    @Autowired
    private StudyRoomInviteService inviteService;

    @Autowired
    private StudyRoomMemberRepository memberRepository;

    @Autowired
    private StudyRoomRepository roomRepository;

    @Autowired
    private StudyRoomFeedRepository feedRepository;

    @Autowired
    private StudyRoomFeedReactionRepository feedReactionRepository;

    @Autowired
    private UserRepository userRepository;

    @Test
    void createRoomCreatesHostMember() {
        User host = userRepository.save(RandomUserGenerator.createRandomUser("GOOGLE", "방장", "host"));

        StudyRoomDetailResponse response = studyRoomService.createRoom(new StudyRoomCreateRequest("수능 준비방"), host.getId());

        assertThat(response.name()).isEqualTo("수능 준비방");
        assertThat(response.hostUserId()).isEqualTo(host.getId());
        assertThat(response.memberCount()).isEqualTo(1);
        assertThat(memberRepository.existsByRoomIdAndUserId(response.roomId(), host.getId())).isTrue();
    }

    @Test
    void joinWithInviteCodeAddsMember() {
        User host = userRepository.save(RandomUserGenerator.createRandomUser("GOOGLE", "방장", "host"));
        User member = userRepository.save(RandomUserGenerator.createRandomUser("GOOGLE", "멤버", "member"));
        StudyRoomDetailResponse room = studyRoomService.createRoom(new StudyRoomCreateRequest("수능 준비방"), host.getId());
        InviteCodeResponse invite = inviteService.issueInviteCode(room.roomId(), host.getId());

        StudyRoomDetailResponse joined = inviteService.join(new StudyRoomJoinRequest(invite.code()), member.getId());

        assertThat(joined.memberCount()).isEqualTo(2);
        assertThat(memberRepository.existsByRoomIdAndUserId(room.roomId(), member.getId())).isTrue();
    }

    @Test
    void memberCanRejoinAfterLeavingRoom() {
        User host = userRepository.save(RandomUserGenerator.createRandomUser("GOOGLE", "방장", "host"));
        User member = userRepository.save(RandomUserGenerator.createRandomUser("GOOGLE", "멤버", "member"));
        StudyRoomDetailResponse room = studyRoomService.createRoom(new StudyRoomCreateRequest("수능 준비방"), host.getId());
        InviteCodeResponse invite = inviteService.issueInviteCode(room.roomId(), host.getId());
        inviteService.join(new StudyRoomJoinRequest(invite.code()), member.getId());

        studyRoomService.leaveRoom(room.roomId(), member.getId());
        StudyRoomDetailResponse rejoined = inviteService.join(new StudyRoomJoinRequest(invite.code()), member.getId());

        assertThat(rejoined.memberCount()).isEqualTo(2);
        assertThat(memberRepository.existsByRoomIdAndUserId(room.roomId(), member.getId())).isTrue();
    }

    @Test
    void feedReactionCanBeRecreatedAfterDelete() {
        User host = userRepository.save(RandomUserGenerator.createRandomUser("GOOGLE", "방장", "host"));
        StudyRoomDetailResponse room = studyRoomService.createRoom(new StudyRoomCreateRequest("수능 준비방"), host.getId());
        StudyRoomFeed feed = feedRepository.save(StudyRoomFeed.create(
                roomRepository.findById(room.roomId()).orElseThrow(),
                host,
                StudyRoomFeedEventType.SESSION_STARTED,
                "{}"
        ));
        StudyRoomFeedReaction reaction = feedReactionRepository.save(StudyRoomFeedReaction.create(feed, host, "🔥"));

        feedReactionRepository.delete(reaction);
        feedReactionRepository.flush();
        StudyRoomFeedReaction recreated = feedReactionRepository.saveAndFlush(StudyRoomFeedReaction.create(feed, host, "🔥"));

        assertThat(recreated.getId()).isNotNull();
    }

    @Test
    void nonMemberCannotReadRoom() {
        User host = userRepository.save(RandomUserGenerator.createRandomUser("GOOGLE", "방장", "host"));
        User outsider = userRepository.save(RandomUserGenerator.createRandomUser("GOOGLE", "외부", "outsider"));
        StudyRoomDetailResponse room = studyRoomService.createRoom(new StudyRoomCreateRequest("수능 준비방"), host.getId());

        assertThatThrownBy(() -> studyRoomService.getRoom(room.roomId(), outsider.getId()))
                .isInstanceOf(ApplicationException.class)
                .extracting("errorCase")
                .isEqualTo(StudyRoomErrorCase.STUDY_ROOM_FORBIDDEN);
    }

    @Test
    void hostLeaveTransfersHostToNextMember() {
        User host = userRepository.save(RandomUserGenerator.createRandomUser("GOOGLE", "방장", "host"));
        User member = userRepository.save(RandomUserGenerator.createRandomUser("GOOGLE", "멤버", "member"));
        StudyRoomDetailResponse room = studyRoomService.createRoom(new StudyRoomCreateRequest("수능 준비방"), host.getId());
        InviteCodeResponse invite = inviteService.issueInviteCode(room.roomId(), host.getId());
        inviteService.join(new StudyRoomJoinRequest(invite.code()), member.getId());

        studyRoomService.leaveRoom(room.roomId(), host.getId());

        assertThat(memberRepository.existsByRoomIdAndUserId(room.roomId(), host.getId())).isFalse();
        assertThat(memberRepository.findByRoomIdAndUserId(room.roomId(), member.getId()).orElseThrow().getRole())
                .isEqualTo(StudyRoomMemberRole.HOST);
        assertThat(roomRepository.findById(room.roomId()).orElseThrow().getHostUserId()).isEqualTo(member.getId());
    }

    @Test
    void hostLeaveDeletesRoomWhenNoOtherMemberExists() {
        User host = userRepository.save(RandomUserGenerator.createRandomUser("GOOGLE", "방장", "host"));
        StudyRoomDetailResponse room = studyRoomService.createRoom(new StudyRoomCreateRequest("수능 준비방"), host.getId());

        studyRoomService.leaveRoom(room.roomId(), host.getId());

        assertThat(roomRepository.findById(room.roomId())).isEmpty();
    }

    @Test
    void updateGoalCanSetAndClearWeeklyGoal() {
        User host = userRepository.save(RandomUserGenerator.createRandomUser("GOOGLE", "방장", "host"));
        StudyRoomDetailResponse room = studyRoomService.createRoom(new StudyRoomCreateRequest("수능 준비방"), host.getId());

        assertThat(studyRoomService.updateGoal(room.roomId(), host.getId(), new StudyRoomGoalUpdateRequest(20)).weeklyGoal())
                .isEqualTo(20);
        assertThat(studyRoomService.updateGoal(room.roomId(), host.getId(), new StudyRoomGoalUpdateRequest(0)).weeklyGoal())
                .isNull();
    }
}
