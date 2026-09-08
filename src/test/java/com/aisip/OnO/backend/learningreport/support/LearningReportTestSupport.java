package com.aisip.OnO.backend.learningreport.support;

import com.aisip.OnO.backend.learningreport.dto.LearningTrendPoint;
import com.aisip.OnO.backend.learningreport.dto.LearningWeakArea;
import com.aisip.OnO.backend.learningreport.service.LearningReportService;
import com.aisip.OnO.backend.mission.dto.MissionRegisterDto;
import com.aisip.OnO.backend.mission.entity.MissionLog;
import com.aisip.OnO.backend.mission.entity.MissionType;
import com.aisip.OnO.backend.mission.repository.MissionLogRepository;
import com.aisip.OnO.backend.problem.dto.ProblemRegisterDto;
import com.aisip.OnO.backend.problem.entity.Problem;
import com.aisip.OnO.backend.problem.entity.ProblemAnalysis;
import com.aisip.OnO.backend.problem.repository.ProblemAnalysisRepository;
import com.aisip.OnO.backend.problem.repository.ProblemRepository;
import com.aisip.OnO.backend.problemsolve.entity.AnswerStatus;
import com.aisip.OnO.backend.problemsolve.entity.ProblemSolve;
import com.aisip.OnO.backend.problemsolve.repository.ProblemSolveRepository;
import com.aisip.OnO.backend.support.IntegrationTestSupport;
import com.aisip.OnO.backend.user.entity.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * learningreport 도메인 테스트의 공통 픽스처.
 *
 * <p>리포트는 네 종류의 원천 데이터를 집계한다.
 * <ul>
 *   <li>복습 수 / 정답률 / 학습 시간 - {@code problem_solve.practiced_at}</li>
 *   <li>오답노트 작성 수 - {@code problem.created_at}</li>
 *   <li>복습노트 사용 수 - {@code mission_log.created_at} 중 {@code NOTE_PRACTICE}</li>
 *   <li>취약 유형 - {@code problem_analysis.problem_type} + 오답 기록</li>
 * </ul>
 * {@code created_at} 은 JPA Auditing 이 채우므로 원하는 시각으로 만들려면 네이티브 UPDATE 가 필요하다.
 */
public abstract class LearningReportTestSupport extends IntegrationTestSupport {

    private static final AtomicLong MISSION_REFERENCE_SEQUENCE = new AtomicLong();

    @Autowired
    protected LearningReportService learningReportService;

    @Autowired
    protected ProblemRepository problemRepository;

    @Autowired
    protected ProblemAnalysisRepository problemAnalysisRepository;

    @Autowired
    protected ProblemSolveRepository problemSolveRepository;

    @Autowired
    protected MissionLogRepository missionLogRepository;

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    protected Problem saveNoteWrittenAt(Long userId, LocalDateTime createdAt) {
        Problem problem = problemRepository.save(Problem.from(
                new ProblemRegisterDto(null, "memo-" + createdAt, "ref-" + createdAt, null, LocalDateTime.now()),
                userId
        ));
        jdbcTemplate.update("UPDATE problem SET created_at = ? WHERE id = ?", createdAt, problem.getId());
        return problem;
    }

    /** 취약 유형 집계에 잡히려면 문제에 분석 결과(problemType)가 붙어 있어야 한다. */
    protected Problem saveAnalyzedNote(Long userId, String problemType, LocalDateTime createdAt) {
        Problem problem = saveNoteWrittenAt(userId, createdAt);
        ProblemAnalysis analysis = ProblemAnalysis.createProcessing(problem);
        analysis.updateWithSuccess("수학", problemType, "[]", "solution", "mistake", "tip");
        problemAnalysisRepository.save(analysis);
        return problem;
    }

    protected ProblemSolve saveSolve(
            Long userId, Problem problem, LocalDateTime practicedAt, AnswerStatus answerStatus, Integer seconds
    ) {
        return problemSolveRepository.save(
                ProblemSolve.create(problem, userId, practicedAt, answerStatus, null, null, seconds, null)
        );
    }

    protected MissionLog saveNotePractice(User user, LocalDateTime createdAt) {
        MissionLog missionLog = missionLogRepository.save(MissionLog.from(
                MissionRegisterDto.builder()
                        .userId(user.getId())
                        .missionType(MissionType.NOTE_PRACTICE)
                        .referenceId(MISSION_REFERENCE_SEQUENCE.incrementAndGet())
                        .build(),
                user
        ));
        jdbcTemplate.update("UPDATE mission_log SET created_at = ? WHERE id = ?", createdAt, missionLog.getId());
        return missionLog;
    }

    /** NOTE_PRACTICE 가 아닌 미션은 복습노트 사용 수에 잡히면 안 된다. */
    protected MissionLog saveMissionLog(User user, MissionType missionType, LocalDateTime createdAt) {
        MissionLog missionLog = missionLogRepository.save(MissionLog.from(
                MissionRegisterDto.builder()
                        .userId(user.getId())
                        .missionType(missionType)
                        .referenceId(MISSION_REFERENCE_SEQUENCE.incrementAndGet())
                        .build(),
                user
        ));
        jdbcTemplate.update("UPDATE mission_log SET created_at = ? WHERE id = ?", createdAt, missionLog.getId());
        return missionLog;
    }

    protected Map<String, Long> trendMap(List<LearningTrendPoint> trend) {
        return trend.stream().collect(Collectors.toMap(
                LearningTrendPoint::label, LearningTrendPoint::reviewCount));
    }

    protected List<String> trendLabels(List<LearningTrendPoint> trend) {
        return trend.stream().map(LearningTrendPoint::label).toList();
    }

    protected Map<String, Long> weakAreaMap(List<LearningWeakArea> weakAreas) {
        return weakAreas.stream().collect(Collectors.toMap(
                LearningWeakArea::topic, LearningWeakArea::wrongCount));
    }
}
