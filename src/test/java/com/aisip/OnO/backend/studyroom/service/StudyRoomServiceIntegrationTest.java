package com.aisip.OnO.backend.studyroom.service;

import com.aisip.OnO.backend.common.exception.ApplicationException;
import com.aisip.OnO.backend.config.rabbitmq.producer.S3DeleteProducer;
import com.aisip.OnO.backend.problem.dto.ProblemRegisterDto;
import com.aisip.OnO.backend.problem.entity.Problem;
import com.aisip.OnO.backend.problem.repository.ProblemRepository;
import com.aisip.OnO.backend.problemsolve.entity.AnswerStatus;
import com.aisip.OnO.backend.problemsolve.entity.ProblemSolve;
import com.aisip.OnO.backend.problemsolve.repository.ProblemSolveRepository;
import com.aisip.OnO.backend.studyroom.dto.StudyRoomDtos.InviteCodeResponse;
import com.aisip.OnO.backend.studyroom.dto.StudyRoomDtos.StudyRoomListResponse;
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
import com.aisip.OnO.backend.util.fileupload.exception.FileUploadErrorCase;
import com.aisip.OnO.backend.util.fileupload.service.FileUploadService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mock.web.MockMultipartFile;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

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

    @Autowired
    private ProblemRepository problemRepository;

    @Autowired
    private ProblemSolveRepository problemSolveRepository;

    @MockBean
    private FileUploadService fileUploadService;

    @MockBean
    private S3DeleteProducer s3DeleteProducer;

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
        StudyRoomFeedReaction reaction = feedReactionRepository.save(StudyRoomFeedReaction.create(feed, host, "fired_up_sparkle_eyes"));

        feedReactionRepository.delete(reaction);
        feedReactionRepository.flush();
        StudyRoomFeedReaction recreated = feedReactionRepository.saveAndFlush(StudyRoomFeedReaction.create(feed, host, "fired_up_sparkle_eyes"));

        assertThat(recreated.getId()).isNotNull();
        assertThat(recreated.getEmoji()).isEqualTo("fired_up_sparkle_eyes");
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
    void getMyRoomsIncludesTodayPracticeSummary() {
        User host = userRepository.save(RandomUserGenerator.createRandomUser("GOOGLE", "방장", "host"));
        User member = userRepository.save(RandomUserGenerator.createRandomUser("GOOGLE", "멤버", "member"));
        StudyRoomDetailResponse room = studyRoomService.createRoom(new StudyRoomCreateRequest("수능 준비방"), host.getId());
        InviteCodeResponse invite = inviteService.issueInviteCode(room.roomId(), host.getId());
        inviteService.join(new StudyRoomJoinRequest(invite.code()), member.getId());
        createPractice(host.getId(), 2);
        createPractice(member.getId(), 3);

        List<StudyRoomListResponse> rooms = studyRoomService.getMyRooms(host.getId());

        StudyRoomListResponse response = rooms.stream()
                .filter(item -> item.roomId().equals(room.roomId()))
                .findFirst()
                .orElseThrow();
        assertThat(response.memberCount()).isEqualTo(2);
        assertThat(response.todayPracticeMemberCount()).isEqualTo(2);
        assertThat(response.todayPracticeCount()).isEqualTo(5);
    }

    @Test
    void getRoomIncludesMemberTodayPracticeCount() {
        User host = userRepository.save(RandomUserGenerator.createRandomUser("GOOGLE", "방장", "host"));
        User member = userRepository.save(RandomUserGenerator.createRandomUser("GOOGLE", "멤버", "member"));
        StudyRoomDetailResponse room = studyRoomService.createRoom(new StudyRoomCreateRequest("수능 준비방"), host.getId());
        InviteCodeResponse invite = inviteService.issueInviteCode(room.roomId(), host.getId());
        inviteService.join(new StudyRoomJoinRequest(invite.code()), member.getId());
        createPractice(host.getId(), 2);
        createPractice(member.getId(), 0);

        StudyRoomDetailResponse response = studyRoomService.getRoom(room.roomId(), host.getId());

        assertThat(response.members()).anySatisfy(item -> {
            assertThat(item.userId()).isEqualTo(host.getId());
            assertThat(item.todayPracticeCount()).isEqualTo(2);
        });
        assertThat(response.members()).anySatisfy(item -> {
            assertThat(item.userId()).isEqualTo(member.getId());
            assertThat(item.todayPracticeCount()).isZero();
        });
    }

    @Test
    void updateThumbnailUploadsAndStoresThumbnailUrl() {
        User host = userRepository.save(RandomUserGenerator.createRandomUser("GOOGLE", "방장", "host"));
        StudyRoomDetailResponse room = studyRoomService.createRoom(new StudyRoomCreateRequest("수능 준비방"), host.getId());
        MockMultipartFile firstThumbnail = new MockMultipartFile("thumbnail", "first.png", "image/png", pngBytes());
        MockMultipartFile secondThumbnail = new MockMultipartFile("thumbnail", "second.png", "image/png", pngBytes());
        given(fileUploadService.uploadFileToS3(firstThumbnail)).willReturn("https://cdn.example.com/first.png");
        given(fileUploadService.uploadFileToS3(secondThumbnail)).willReturn("https://cdn.example.com/second.png");

        assertThat(studyRoomService.updateThumbnail(room.roomId(), host.getId(), firstThumbnail).thumbnailUrl())
                .isEqualTo("https://cdn.example.com/first.png");
        assertThat(studyRoomService.updateThumbnail(room.roomId(), host.getId(), secondThumbnail).thumbnailUrl())
                .isEqualTo("https://cdn.example.com/second.png");

        assertThat(roomRepository.findById(room.roomId()).orElseThrow().getThumbnailUrl())
                .isEqualTo("https://cdn.example.com/second.png");
        verify(s3DeleteProducer).sendDeleteMessage("https://cdn.example.com/first.png", room.roomId());
    }

    @Test
    void nonHostCannotUpdateThumbnail() {
        User host = userRepository.save(RandomUserGenerator.createRandomUser("GOOGLE", "방장", "host"));
        User member = userRepository.save(RandomUserGenerator.createRandomUser("GOOGLE", "멤버", "member"));
        StudyRoomDetailResponse room = studyRoomService.createRoom(new StudyRoomCreateRequest("수능 준비방"), host.getId());
        InviteCodeResponse invite = inviteService.issueInviteCode(room.roomId(), host.getId());
        inviteService.join(new StudyRoomJoinRequest(invite.code()), member.getId());
        MockMultipartFile thumbnail = new MockMultipartFile("thumbnail", "first.png", "image/png", pngBytes());

        assertThatThrownBy(() -> studyRoomService.updateThumbnail(room.roomId(), member.getId(), thumbnail))
                .isInstanceOf(ApplicationException.class)
                .extracting("errorCase")
                .isEqualTo(StudyRoomErrorCase.STUDY_ROOM_HOST_ONLY);
    }

    @Test
    void updateThumbnailRejectsInvalidMimeAndSignature() {
        User host = userRepository.save(RandomUserGenerator.createRandomUser("GOOGLE", "방장", "host"));
        StudyRoomDetailResponse room = studyRoomService.createRoom(new StudyRoomCreateRequest("수능 준비방"), host.getId());
        MockMultipartFile thumbnail = new MockMultipartFile("thumbnail", "first.png", "image/png", "not-image".getBytes());

        assertThatThrownBy(() -> studyRoomService.updateThumbnail(room.roomId(), host.getId(), thumbnail))
                .isInstanceOf(ApplicationException.class)
                .extracting("errorCase")
                .isEqualTo(FileUploadErrorCase.INVALID_IMAGE_FILE);
        verify(fileUploadService, never()).uploadFileToS3(thumbnail);
    }

    @Test
    void updateThumbnailRejectsOversizedImage() {
        User host = userRepository.save(RandomUserGenerator.createRandomUser("GOOGLE", "방장", "host"));
        StudyRoomDetailResponse room = studyRoomService.createRoom(new StudyRoomCreateRequest("수능 준비방"), host.getId());
        MockMultipartFile thumbnail = new MockMultipartFile("thumbnail", "large.png", "image/png", largePngBytes());

        assertThatThrownBy(() -> studyRoomService.updateThumbnail(room.roomId(), host.getId(), thumbnail))
                .isInstanceOf(ApplicationException.class)
                .extracting("errorCase")
                .isEqualTo(FileUploadErrorCase.FILE_SIZE_EXCEEDED);
        verify(fileUploadService, never()).uploadFileToS3(thumbnail);
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

    private void createPractice(Long userId, int count) {
        Problem problem = problemRepository.save(Problem.from(
                new ProblemRegisterDto(null, "memo", "reference", null, LocalDateTime.now()),
                userId
        ));
        for (int i = 0; i < count; i++) {
            problemSolveRepository.save(ProblemSolve.create(
                    problem,
                    userId,
                    LocalDateTime.now(),
                    AnswerStatus.CORRECT,
                    null,
                    null,
                    60
            ));
        }
    }

    private byte[] pngBytes() {
        return new byte[]{
                (byte) 0x89, 0x50, 0x4E, 0x47,
                0x0D, 0x0A, 0x1A, 0x0A,
                0x00, 0x00, 0x00, 0x0D
        };
    }

    private byte[] largePngBytes() {
        byte[] bytes = new byte[(5 * 1024 * 1024) + 1];
        byte[] header = pngBytes();
        System.arraycopy(header, 0, bytes, 0, header.length);
        return bytes;
    }
}
