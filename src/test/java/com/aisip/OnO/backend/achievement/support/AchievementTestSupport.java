package com.aisip.OnO.backend.achievement.support;

import com.aisip.OnO.backend.achievement.repository.UserAchievementRepository;
import com.aisip.OnO.backend.achievement.service.AchievementService;
import com.aisip.OnO.backend.mission.dto.MissionRegisterDto;
import com.aisip.OnO.backend.mission.entity.MissionLog;
import com.aisip.OnO.backend.mission.entity.MissionType;
import com.aisip.OnO.backend.mission.repository.MissionLogRepository;
import com.aisip.OnO.backend.problem.dto.ProblemRegisterDto;
import com.aisip.OnO.backend.problem.entity.Problem;
import com.aisip.OnO.backend.problem.repository.ProblemRepository;
import com.aisip.OnO.backend.problemsolve.entity.AnswerStatus;
import com.aisip.OnO.backend.problemsolve.entity.ProblemSolve;
import com.aisip.OnO.backend.problemsolve.repository.ProblemSolveRepository;
import com.aisip.OnO.backend.studyroom.entity.StudyRoom;
import com.aisip.OnO.backend.studyroom.entity.StudyRoomFeed;
import com.aisip.OnO.backend.studyroom.entity.StudyRoomFeedEventType;
import com.aisip.OnO.backend.studyroom.entity.StudyRoomFeedReaction;
import com.aisip.OnO.backend.studyroom.entity.StudyRoomMember;
import com.aisip.OnO.backend.studyroom.entity.StudyRoomMemberRole;
import com.aisip.OnO.backend.studyroom.entity.StudyRoomSharedProblem;
import com.aisip.OnO.backend.studyroom.entity.StudyRoomSharedProblemComment;
import com.aisip.OnO.backend.studyroom.entity.StudyRoomSharedProblemCommentReaction;
import com.aisip.OnO.backend.studyroom.entity.StudyRoomSharedProblemReaction;
import com.aisip.OnO.backend.studyroom.repository.StudyRoomFeedReactionRepository;
import com.aisip.OnO.backend.studyroom.repository.StudyRoomFeedRepository;
import com.aisip.OnO.backend.studyroom.repository.StudyRoomRepository;
import com.aisip.OnO.backend.studyroom.repository.StudyRoomSharedProblemCommentReactionRepository;
import com.aisip.OnO.backend.studyroom.repository.StudyRoomSharedProblemCommentRepository;
import com.aisip.OnO.backend.studyroom.repository.StudyRoomSharedProblemReactionRepository;
import com.aisip.OnO.backend.studyroom.repository.StudyRoomSharedProblemRepository;
import com.aisip.OnO.backend.support.IntegrationTestSupport;
import com.aisip.OnO.backend.support.QueryCounter;
import com.aisip.OnO.backend.user.entity.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;

/**
 * 훈장 도메인 테스트의 공통 베이스.
 *
 * <p>{@code @MockBean} 을 새로 선언하지 않아 스프링 컨텍스트는 다른 도메인 테스트와 그대로 공유된다.
 *
 * <p>훈장은 열두 조건이 여섯 테이블에 흩어져 있어, 조건 하나를 확인하려면 그 테이블에 데이터를
 * 직접 심어야 한다. 서비스 경로로 만들면 미션 적립이 함께 끌려 들어와 무엇을 재는 테스트인지 흐려진다.
 */
public abstract class AchievementTestSupport extends IntegrationTestSupport {

    private static final AtomicLong SEQUENCE = new AtomicLong();

    /** 복습 시각의 기본값. 새벽반(05~08)과 올빼미(00~03) 어느 쪽에도 안 걸리는 시각이다. */
    protected static final LocalDateTime NOON = LocalDateTime.of(2026, 3, 2, 12, 0);

    @Autowired
    protected AchievementService achievementService;

    @Autowired
    protected UserAchievementRepository userAchievementRepository;

    @Autowired
    protected ProblemRepository problemRepository;

    @Autowired
    protected ProblemSolveRepository problemSolveRepository;

    @Autowired
    protected MissionLogRepository missionLogRepository;

    @Autowired
    protected StudyRoomRepository studyRoomRepository;

    @Autowired
    protected StudyRoomFeedRepository feedRepository;

    @Autowired
    protected StudyRoomFeedReactionRepository feedReactionRepository;

    @Autowired
    protected StudyRoomSharedProblemRepository sharedProblemRepository;

    @Autowired
    protected StudyRoomSharedProblemReactionRepository sharedProblemReactionRepository;

    @Autowired
    protected StudyRoomSharedProblemCommentRepository commentRepository;

    @Autowired
    protected StudyRoomSharedProblemCommentReactionRepository commentReactionRepository;

    @Autowired
    protected QueryCounter queryCounter;

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    /**
     * MockMvc 요청에 인증 주체를 싣는다.
     *
     * <p>{@code authenticateAs(userId)} 만으로도 MockMvc 가 인증을 읽지만, 한 테스트에서
     * 사용자를 바꿔 가며 요청할 때는 요청 단위로 붙이는 편이 어느 사용자의 요청인지 분명하다.
     */
    protected RequestPostProcessor asUser(Long userId) {
        return authentication(new UsernamePasswordAuthenticationToken(
                userId, null, List.of(new SimpleGrantedAuthority("ROLE_MEMBER"))));
    }

    // ─────────────────────────── 오답노트 · 폴더 ───────────────────────────

    protected Problem saveProblem(Long userId) {
        return problemRepository.save(
                Problem.from(new ProblemRegisterDto(null, "메모", null, null, null), userId));
    }

    protected void saveProblems(Long userId, int count) {
        for (int i = 0; i < count; i++) {
            saveProblem(userId);
        }
    }

    protected void saveFolders(Long userId, int count) {
        for (int i = 0; i < count; i++) {
            fixtures.createFolder(userId, "폴더" + SEQUENCE.incrementAndGet(), null);
        }
    }

    // ─────────────────────────── 복습 기록 ───────────────────────────

    protected ProblemSolve saveSolve(Problem problem, Long userId, LocalDateTime practicedAt, AnswerStatus status) {
        return saveSolve(problem, userId, practicedAt, status, "회고");
    }

    protected ProblemSolve saveSolve(Problem problem, Long userId, LocalDateTime practicedAt,
                                     AnswerStatus status, String reflection) {
        return problemSolveRepository.save(
                ProblemSolve.create(problem, userId, practicedAt, status, reflection, null, 120, null));
    }

    /**
     * 같은 문제를 시각만 한 시간씩 밀어 여러 번 푼 기록.
     *
     * <p>무결점과 불사조는 복습 시각 순서를 보기 때문에 모든 기록을 같은 시각에 넣으면
     * 무엇을 재는 테스트인지 알 수 없게 된다. 항상 순서가 드러나게 만든다.
     */
    protected void saveSolveSequence(Problem problem, Long userId, LocalDateTime start, AnswerStatus... statuses) {
        for (int i = 0; i < statuses.length; i++) {
            saveSolve(problem, userId, start.plusHours(i), statuses[i]);
        }
    }

    // ─────────────────────────── 로그인 기록 ───────────────────────────

    /**
     * {@code date} 부터 하루씩 이어지는 로그인 기록.
     *
     * <p>{@code created_at} 은 JPA Auditing 이 채우므로 애플리케이션에서는 과거 시각을 만들 수 없다.
     * 연속 출석은 과거 날짜 없이는 검증할 수 없어 직접 밀어 넣는다. {@code MissionTestSupport} 와 같은 방법이다.
     */
    protected void saveLoginDays(User user, LocalDate startDate, int days) {
        for (int i = 0; i < days; i++) {
            saveLoginAt(user, startDate.plusDays(i).atTime(9, 0));
        }
    }

    protected void saveLoginAt(User user, LocalDateTime createdAt) {
        MissionLog log = missionLogRepository.save(MissionLog.from(
                MissionRegisterDto.builder()
                        .userId(user.getId())
                        .missionType(MissionType.USER_LOGIN)
                        .referenceId(SEQUENCE.incrementAndGet())
                        .build(),
                user));
        jdbcTemplate.update("UPDATE mission_log SET created_at = ? WHERE id = ?", createdAt, log.getId());
    }

    // ─────────────────────────── 스터디룸 · 리액션 ───────────────────────────

    protected StudyRoom joinNewRoom(User user) {
        StudyRoom room = StudyRoom.create("스터디룸" + SEQUENCE.incrementAndGet(), user.getId());
        room.addMember(StudyRoomMember.create(user, StudyRoomMemberRole.HOST));
        return studyRoomRepository.save(room);
    }

    /**
     * 피드 하나에 리액션 {@code count} 개.
     *
     * <p>이모지를 매번 다르게 쓴다. {@code (feed_id, user_id, emoji)} 유니크 키 때문에 같은 이모지로는
     * 한 번만 누를 수 있다. 응원 백 번을 만들려고 피드를 백 개 만드는 것보다 이쪽이 싸다.
     */
    protected void saveFeedReactions(User user, StudyRoom room, int count) {
        StudyRoomFeed feed = feedRepository.save(
                StudyRoomFeed.create(room, user, StudyRoomFeedEventType.PRACTICE_COMPLETED, null));
        for (int i = 0; i < count; i++) {
            feedReactionRepository.save(StudyRoomFeedReaction.create(feed, user, emoji()));
        }
    }

    protected void saveSharedProblemReactions(User user, StudyRoom room, int count) {
        StudyRoomSharedProblem shared = sharedProblemRepository.save(
                StudyRoomSharedProblem.create(room, user, saveProblem(user.getId()), "같이 봐요"));
        for (int i = 0; i < count; i++) {
            sharedProblemReactionRepository.save(
                    StudyRoomSharedProblemReaction.create(shared, user, emoji()));
        }
    }

    protected void saveCommentReactions(User user, StudyRoom room, int count) {
        StudyRoomSharedProblem shared = sharedProblemRepository.save(
                StudyRoomSharedProblem.create(room, user, saveProblem(user.getId()), "같이 봐요"));
        StudyRoomSharedProblemComment comment = commentRepository.save(
                StudyRoomSharedProblemComment.create(shared, user, "저도 틀렸어요"));
        for (int i = 0; i < count; i++) {
            commentReactionRepository.save(
                    StudyRoomSharedProblemCommentReaction.create(comment, user, emoji()));
        }
    }

    private String emoji() {
        return "cheer_" + SEQUENCE.incrementAndGet();
    }

    // ─────────────────────────── 확인 ───────────────────────────

    /** 훈장 행 개수. 두 번 불러도 안 늘어나는지 확인할 때 쓴다. */
    protected long achievementRowCount(Long userId) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM user_achievement WHERE user_id = ?", Long.class, userId);
        return count == null ? 0 : count;
    }
}
