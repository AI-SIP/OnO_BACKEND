package com.aisip.OnO.backend.studyroom.support;

import com.aisip.OnO.backend.problem.dto.ProblemRegisterDto;
import com.aisip.OnO.backend.problem.entity.Problem;
import com.aisip.OnO.backend.problem.repository.ProblemRepository;
import com.aisip.OnO.backend.problemsolve.entity.AnswerStatus;
import com.aisip.OnO.backend.problemsolve.entity.ProblemSolve;
import com.aisip.OnO.backend.problemsolve.repository.ProblemSolveRepository;
import com.aisip.OnO.backend.studyroom.entity.*;
import com.aisip.OnO.backend.studyroom.repository.*;
import com.aisip.OnO.backend.support.IntegrationTestSupport;
import com.aisip.OnO.backend.user.entity.User;
import com.aisip.OnO.backend.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * studyroom 도메인 테스트의 공통 베이스.
 *
 * <p>{@link IntegrationTestSupport} 의 애노테이션 조합을 그대로 물려받아 스프링 컨텍스트를
 * 다른 도메인 테스트와 공유한다. {@code @MockBean} 은 컨텍스트 캐시 키를 갈라놓으므로
 * 여기에도 추가하지 않는다. FCM·S3·OpenAI·Discord 는 이미 베이스에서 목으로 막혀 있다.
 *
 * <p>스터디룸은 이 프로젝트에서 소유권 경계가 가장 복잡한 도메인이다. 방장·멤버·비멤버·
 * 탈퇴한 멤버가 각각 무엇을 할 수 있는지를 반복해서 검증해야 하므로, 그 조합을 만드는
 * 픽스처를 여기에 모아 둔다.
 *
 * <p>ID 는 절대 하드코딩하지 않는다. {@code DatabaseCleaner} 는 DELETE 로 테이블을 비우므로
 * auto_increment 가 되돌아가지 않고, "999L 은 없는 방" 같은 가정은 스위트가 커지면 깨진다.
 * 존재하지 않는 ID 가 필요하면 {@link #nonExistentId} 계열 헬퍼를 쓴다.
 */
public abstract class StudyRoomTestSupport extends IntegrationTestSupport {

    /** 허용 목록에 있는 커스텀 이모지 키. 리액션 테스트의 기본값. */
    protected static final String EMOJI = "fired_up_sparkle_eyes";
    protected static final String OTHER_EMOJI = "thumbs_up_happy";

    private static final AtomicLong SEQUENCE = new AtomicLong();

    @Autowired
    protected UserRepository userRepository;

    @Autowired
    protected StudyRoomRepository roomRepository;

    @Autowired
    protected StudyRoomMemberRepository memberRepository;

    @Autowired
    protected StudyRoomInviteCodeRepository inviteCodeRepository;

    @Autowired
    protected StudyRoomFeedRepository feedRepository;

    @Autowired
    protected StudyRoomFeedReactionRepository feedReactionRepository;

    @Autowired
    protected StudyRoomChallengeRepository challengeRepository;

    @Autowired
    protected StudyRoomSharedProblemRepository sharedProblemRepository;

    @Autowired
    protected StudyRoomSharedProblemReactionRepository sharedProblemReactionRepository;

    @Autowired
    protected StudyRoomSharedProblemCommentRepository commentRepository;

    @Autowired
    protected StudyRoomSharedProblemCommentReactionRepository commentReactionRepository;

    @Autowired
    protected StudyRoomWeeklyReportRepository weeklyReportRepository;

    @Autowired
    protected StudyRoomWeeklyReportReadRepository weeklyReportReadRepository;

    @Autowired
    protected ProblemRepository problemRepository;

    @Autowired
    protected ProblemSolveRepository problemSolveRepository;

    @Autowired
    protected TransactionTemplate transactionTemplate;

    @PersistenceContext
    protected EntityManager entityManager;

    // ─────────────────────────── 스터디룸 / 멤버 ───────────────────────────

    /**
     * 방장 한 명짜리 스터디룸.
     *
     * <p>{@code StudyRoom.addMember} 는 양방향 연관관계를 세우고 cascade 로 멤버까지 저장한다.
     * 프로덕션의 {@code StudyRoomService.createRoom} 과 같은 경로를 쓴다.
     */
    protected StudyRoom createRoom(User host) {
        return createRoom(host, "스터디룸" + SEQUENCE.incrementAndGet());
    }

    protected StudyRoom createRoom(User host, String name) {
        StudyRoom room = StudyRoom.create(name, host.getId());
        room.addMember(StudyRoomMember.create(host, StudyRoomMemberRole.HOST));
        return roomRepository.save(room);
    }

    /** 방에 일반 멤버를 추가한다. */
    protected StudyRoomMember addMember(StudyRoom room, User user) {
        return addMember(room, user, StudyRoomMemberRole.MEMBER);
    }

    protected StudyRoomMember addMember(StudyRoom room, User user, StudyRoomMemberRole role) {
        StudyRoomMember member = StudyRoomMember.create(user, role);
        member.updateRoom(room);
        return memberRepository.saveAndFlush(member);
    }

    /** 방장 + 일반 멤버 + 비멤버(외부인)로 이루어진 권한 검증용 픽스처. */
    protected RoomFixture createRoomWithMemberAndOutsider() {
        User host = fixtures.createUser("host");
        User member = fixtures.createUser("member");
        User outsider = fixtures.createUser("outsider");
        StudyRoom room = createRoom(host, "권한 검증방");
        addMember(room, member);
        return new RoomFixture(room, host, member, outsider);
    }

    /**
     * 방장/멤버/비멤버를 한 묶음으로 넘기는 픽스처.
     *
     * @param outsider 어떤 방에도 속하지 않은 사용자. 비멤버 격리 검증에 쓴다.
     */
    public record RoomFixture(StudyRoom room, User host, User member, User outsider) {
        public Long roomId() {
            return room.getId();
        }
    }

    // ─────────────────────────── 초대 코드 ───────────────────────────

    protected StudyRoomInviteCode saveInviteCode(StudyRoom room, String code, LocalDateTime expiredAt) {
        return inviteCodeRepository.saveAndFlush(StudyRoomInviteCode.create(room, code, expiredAt));
    }

    /** 24시간 뒤 만료되는 유효한 초대 코드. */
    protected StudyRoomInviteCode saveValidInviteCode(StudyRoom room, String code) {
        return saveInviteCode(room, code, LocalDateTime.now().plusHours(24));
    }

    // ─────────────────────────── 피드 / 리액션 ───────────────────────────

    protected StudyRoomFeed saveFeed(StudyRoom room, User user) {
        return saveFeed(room, user, StudyRoomFeedEventType.PROBLEM_REGISTERED, "{\"count\":1}");
    }

    protected StudyRoomFeed saveFeed(StudyRoom room, User user, StudyRoomFeedEventType eventType, String metadataJson) {
        return feedRepository.saveAndFlush(StudyRoomFeed.create(room, user, eventType, metadataJson));
    }

    protected StudyRoomFeedReaction saveFeedReaction(StudyRoomFeed feed, User user, String emoji) {
        return feedReactionRepository.saveAndFlush(StudyRoomFeedReaction.create(feed, user, emoji));
    }

    // ─────────────────────────── 챌린지 ───────────────────────────

    /** 지금 시작해 7일 뒤 끝나는 개인 문제 등록 챌린지. */
    protected StudyRoomChallenge saveChallenge(StudyRoom room, int targetValue) {
        return saveChallenge(room, "챌린지", StudyRoomChallengeType.INDIVIDUAL,
                StudyRoomChallengeMetric.PROBLEM_COUNT, null, null, targetValue,
                LocalDateTime.now().minusHours(1), LocalDateTime.now().plusDays(7));
    }

    protected StudyRoomChallenge saveChallenge(StudyRoom room, String title, StudyRoomChallengeType type,
                                               StudyRoomChallengeMetric metric, StudyRoomChallengePeriod period,
                                               Integer periodDays, int targetValue,
                                               LocalDateTime startAt, LocalDateTime endAt) {
        return challengeRepository.saveAndFlush(StudyRoomChallenge.create(
                room, title, type, metric, period, periodDays, targetValue, startAt, endAt));
    }

    // ─────────────────────────── 공유 문제 / 댓글 ───────────────────────────

    protected StudyRoomSharedProblem saveSharedProblem(StudyRoom room, User sharer, Problem problem, String comment) {
        return sharedProblemRepository.saveAndFlush(
                StudyRoomSharedProblem.create(room, sharer, problem, comment));
    }

    protected StudyRoomSharedProblemComment saveComment(StudyRoomSharedProblem sharedProblem, User author, String content) {
        return commentRepository.saveAndFlush(
                StudyRoomSharedProblemComment.create(sharedProblem, author, content));
    }

    // ─────────────────────────── 주간 리포트 ───────────────────────────

    protected StudyRoomWeeklyReport saveWeeklyReport(StudyRoom room, LocalDate weekStart) {
        return weeklyReportRepository.saveAndFlush(StudyRoomWeeklyReport.create(
                room, weekStart, weekStart.plusDays(6),
                "탑멤버", null, 10,
                "스트릭왕", null, 5,
                20, 1, "이번 주도 모두 고생했어요!"));
    }

    // ─────────────────────────── 학습 활동 ───────────────────────────

    protected Problem saveProblem(Long userId) {
        long seq = SEQUENCE.incrementAndGet();
        return problemRepository.saveAndFlush(Problem.from(
                new ProblemRegisterDto(null, "메모" + seq, "출처" + seq, null, LocalDateTime.now()),
                userId));
    }

    /**
     * 문제를 새로 만들고 그 문제에 대한 복습 기록 {@code count}건을 남긴다.
     *
     * <p>문제 등록도 학습 활동으로 집계되므로, 복습만 있는 상황을 만들려면
     * {@link #savePracticesOnly} 를 쓴다.
     */
    protected void savePractices(Long userId, LocalDateTime practicedAt, int count) {
        savePracticesOnly(userId, saveProblem(userId), practicedAt, count);
    }

    /** 이미 있는 문제에 복습 기록만 붙인다. 문제 등록일이 학습일 집계에 섞이지 않게 할 때 쓴다. */
    protected void savePracticesOnly(Long userId, Problem problem, LocalDateTime practicedAt, int count) {
        for (int i = 0; i < count; i++) {
            problemSolveRepository.save(ProblemSolve.create(
                    problem, userId, practicedAt.plusSeconds(i), AnswerStatus.CORRECT, null, null, 60, null));
        }
        problemSolveRepository.flush();
    }

    /**
     * 지정한 시각에 등록된 것으로 보이는 문제.
     *
     * <p>{@code created_at} 은 감사 필드라 저장 시점 값이 들어가므로, 과거 활동을 만들려면
     * 저장 후 네이티브 UPDATE 로 되돌려야 한다.
     */
    protected Problem saveProblemCreatedAt(Long userId, LocalDateTime createdAt) {
        Problem problem = saveProblem(userId);
        inTransaction(() -> entityManager.createNativeQuery(
                        "update problem set created_at = :createdAt where id = :id")
                .setParameter("createdAt", createdAt)
                .setParameter("id", problem.getId())
                .executeUpdate());
        return problem;
    }

    // ─────────────────────────── 존재하지 않는 ID ───────────────────────────

    protected Long nonExistentRoomId() {
        return nextId(roomRepository.findAll().stream().map(StudyRoom::getId).toList());
    }

    protected Long nonExistentFeedId() {
        return nextId(feedRepository.findAll().stream().map(StudyRoomFeed::getId).toList());
    }

    protected Long nonExistentSharedProblemId() {
        return nextId(sharedProblemRepository.findAll().stream().map(StudyRoomSharedProblem::getId).toList());
    }

    protected Long nonExistentCommentId() {
        return nextId(commentRepository.findAll().stream().map(StudyRoomSharedProblemComment::getId).toList());
    }

    protected Long nonExistentChallengeId() {
        return nextId(challengeRepository.findAll().stream().map(StudyRoomChallenge::getId).toList());
    }

    protected Long nonExistentReportId() {
        return nextId(weeklyReportRepository.findAll().stream().map(StudyRoomWeeklyReport::getId).toList());
    }

    private Long nextId(List<Long> existingIds) {
        return existingIds.stream().mapToLong(Long::longValue).max().orElse(0L) + 1_000L;
    }

    // ─────────────────────────── 그 밖 ───────────────────────────

    /** 테스트 본문은 트랜잭션 밖이라 지연 로딩이 열리지 않는다. 필요한 경우만 이 안에서 확인한다. */
    protected void inTransaction(Runnable action) {
        transactionTemplate.executeWithoutResult(status -> action.run());
    }

    protected String repeat(char character, int length) {
        return String.valueOf(character).repeat(length);
    }

    /** 정상적인 PNG 시그니처를 가진 최소 바이트열. 썸네일 업로드 검증을 통과한다. */
    protected byte[] pngBytes() {
        return new byte[]{
                (byte) 0x89, 0x50, 0x4E, 0x47,
                0x0D, 0x0A, 0x1A, 0x0A,
                0x00, 0x00, 0x00, 0x0D
        };
    }
}
