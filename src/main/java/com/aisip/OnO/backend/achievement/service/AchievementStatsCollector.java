package com.aisip.OnO.backend.achievement.service;

import com.aisip.OnO.backend.folder.repository.FolderRepository;
import com.aisip.OnO.backend.mission.entity.MissionType;
import com.aisip.OnO.backend.mission.repository.MissionLogRepository;
import com.aisip.OnO.backend.problem.repository.ProblemRepository;
import com.aisip.OnO.backend.problemsolve.repository.ProblemSolveRepository;
import com.aisip.OnO.backend.studyroom.repository.StudyRoomFeedReactionRepository;
import com.aisip.OnO.backend.studyroom.repository.StudyRoomMemberRepository;
import com.aisip.OnO.backend.studyroom.repository.StudyRoomSharedProblemCommentReactionRepository;
import com.aisip.OnO.backend.studyroom.repository.StudyRoomSharedProblemReactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 훈장 판정에 쓸 숫자를 모은다.
 *
 * <p><b>이 클래스가 조회 한 번에 나가는 쿼리 수를 정한다.</b> 지금은 아홉 번이다.
 * 오답노트 1, 폴더 1, 복습 기록 1, 로그인 날짜 1, 스터디룸 1, 리액션 3, 그리고
 * 서비스가 따로 읽는 받은 훈장 목록 1 이다.
 *
 * <p>열두 조건을 각각 세면 열두 번이 넘는다. 그중 여섯(집념·불사조·새벽반·올빼미·무결점·회고왕)이
 * 같은 {@code problem_solve} 표를 보기 때문에, 그 표를 한 번만 읽어 자바에서 여섯 값을 만든다
 * ({@link SolveScan}). 나머지는 인덱스를 타는 단순 count 라 더 접을 것이 없다.
 *
 * <p>리액션 셋을 한 문장으로 합치면 아홉이 일곱이 되지만 그렇게 하지 않았다. 세 도메인에 걸친
 * 네이티브 쿼리를 어느 리포지토리에도 자연스럽게 둘 수 없고, 정작 비용은 문장 수가 아니라
 * {@code study_room_feed_reaction} 과 {@code study_room_shared_problem_reaction} 에
 * {@code user_id} 선두 인덱스가 없다는 쪽에 있다. 합쳐도 그 스캔은 그대로다.
 */
@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AchievementStatsCollector {

    private final ProblemRepository problemRepository;
    private final FolderRepository folderRepository;
    private final ProblemSolveRepository problemSolveRepository;
    private final MissionLogRepository missionLogRepository;
    private final StudyRoomMemberRepository studyRoomMemberRepository;
    private final StudyRoomFeedReactionRepository feedReactionRepository;
    private final StudyRoomSharedProblemReactionRepository sharedProblemReactionRepository;
    private final StudyRoomSharedProblemCommentReactionRepository commentReactionRepository;

    public AchievementStats collect(Long userId) {
        SolveScan solves = SolveScan.of(problemSolveRepository.findAllMarksByUserId(userId));

        return new AchievementStats(
                nullSafe(problemRepository.countByUserId(userId)),
                folderRepository.countByUserId(userId),
                solves.maxSolveCountOnOneProblem(),
                solves.comebackCount(),
                solves.dawnSolveCount(),
                solves.nightSolveCount(),
                solves.reflectionCount(),
                solves.longestCorrectStreak(),
                longestLoginStreak(userId),
                studyRoomMemberRepository.countByUserId(userId),
                reactionCount(userId)
        );
    }

    private long longestLoginStreak(Long userId) {
        List<LocalDate> loginDates = missionLogRepository
                .findDistinctLogDates(userId, MissionType.USER_LOGIN).stream()
                .map(AchievementStatsCollector::toLocalDate)
                .toList();

        return ConsecutiveDays.longestRun(loginDates);
    }

    /**
     * 응원한 횟수. 훈장표대로 리액션 세 자리를 합친다.
     *
     * <p>누른 자리가 어디든 응원한 것은 응원한 것이다. 피드 리액션만 세면 공유 문제에만 응원을 누른
     * 사용자가 아무것도 안 한 것으로 잡힌다.
     */
    private long reactionCount(Long userId) {
        return feedReactionRepository.countByUserId(userId)
                + sharedProblemReactionRepository.countByUserId(userId)
                + commentReactionRepository.countByUserId(userId);
    }

    /**
     * {@code FUNCTION('DATE', ...)} 이 돌려준 값을 날짜로 맞춘다.
     *
     * <p>Hibernate 가 이 함수의 자바 타입을 정하지 않고 JDBC 드라이버가 주는 대로 넘기기 때문에
     * 드라이버 버전에 따라 {@code java.sql.Date} 일 수도 {@code LocalDate} 일 수도 있다.
     * 한쪽만 받도록 적어 두면 드라이버를 올리는 날 훈장 화면이 ClassCastException 으로 닫힌다.
     * {@code MissionLogService} 도 같은 이유로 같은 처리를 한다.
     */
    private static LocalDate toLocalDate(Object value) {
        if (value instanceof LocalDate localDate) {
            return localDate;
        }
        if (value instanceof LocalDateTime localDateTime) {
            return localDateTime.toLocalDate();
        }
        if (value instanceof java.sql.Date date) {
            return date.toLocalDate();
        }
        if (value instanceof Timestamp timestamp) {
            return timestamp.toLocalDateTime().toLocalDate();
        }
        return LocalDate.parse(value.toString());
    }

    /** {@code ProblemRepository.countByUserId} 만 {@code Long} 을 돌려준다. 행이 없으면 0 이다. */
    private static long nullSafe(Long count) {
        return count == null ? 0 : count;
    }
}
