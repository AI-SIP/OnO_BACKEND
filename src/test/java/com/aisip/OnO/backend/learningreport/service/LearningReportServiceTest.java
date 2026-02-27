package com.aisip.OnO.backend.learningreport.service;

import com.aisip.OnO.backend.learningreport.dto.LearningComparison;
import com.aisip.OnO.backend.learningreport.dto.LearningPeriodReport;
import com.aisip.OnO.backend.learningreport.dto.LearningReportResponseDto;
import com.aisip.OnO.backend.problem.dto.ProblemRegisterDto;
import com.aisip.OnO.backend.problem.entity.Problem;
import com.aisip.OnO.backend.problem.entity.ProblemAnalysis;
import com.aisip.OnO.backend.problem.repository.ProblemAnalysisRepository;
import com.aisip.OnO.backend.problem.repository.ProblemRepository;
import com.aisip.OnO.backend.problemsolve.entity.AnswerStatus;
import com.aisip.OnO.backend.problemsolve.entity.ProblemSolve;
import com.aisip.OnO.backend.problemsolve.repository.ProblemSolveRepository;
import com.aisip.OnO.backend.user.dto.UserRegisterDto;
import com.aisip.OnO.backend.user.entity.User;
import com.aisip.OnO.backend.user.repository.UserRepository;
import org.assertj.core.data.Offset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "learning-report.ai.enabled=false")
@ActiveProfiles("local")
@Transactional
class LearningReportServiceTest {

    @Autowired
    private LearningReportService learningReportService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProblemRepository problemRepository;

    @Autowired
    private ProblemAnalysisRepository problemAnalysisRepository;

    @Autowired
    private ProblemSolveRepository problemSolveRepository;

    @Test
    @DisplayName("학습 리포트 조회 - 주간/월간/누적 집계가 올바르게 계산된다")
    void getLearningReport_success() {
        User targetUser = createUser("target-user");
        User otherUser = createUser("other-user");
        Long userId = targetUser.getId();

        Problem algebraProblem = createProblemWithAnalysis(userId, "대수");
        Problem geometryProblem = createProblemWithAnalysis(userId, "기하");
        Problem trigProblem = createProblemWithAnalysis(userId, "삼각함수");

        createSolve(algebraProblem, userId, LocalDateTime.of(2026, 2, 16, 9, 0), AnswerStatus.CORRECT, 600);
        createSolve(algebraProblem, userId, LocalDateTime.of(2026, 2, 17, 9, 0), AnswerStatus.WRONG, 300);
        createSolve(geometryProblem, userId, LocalDateTime.of(2026, 2, 18, 9, 0), AnswerStatus.PARTIAL, 120);
        createSolve(geometryProblem, userId, LocalDateTime.of(2026, 2, 20, 9, 0), AnswerStatus.WRONG, 180);
        createSolve(algebraProblem, userId, LocalDateTime.of(2026, 2, 10, 9, 0), AnswerStatus.WRONG, 60);
        createSolve(trigProblem, userId, LocalDateTime.of(2026, 2, 1, 9, 0), AnswerStatus.CORRECT, 120);
        createSolve(trigProblem, userId, LocalDateTime.of(2026, 1, 10, 9, 0), AnswerStatus.CORRECT, 120);
        createSolve(algebraProblem, userId, LocalDateTime.of(2026, 1, 11, 9, 0), AnswerStatus.WRONG, 120);

        Problem otherUserProblem = createProblemWithAnalysis(otherUser.getId(), "확률");
        createSolve(otherUserProblem, otherUser.getId(), LocalDateTime.of(2026, 2, 17, 9, 0), AnswerStatus.WRONG, 999);

        LearningReportResponseDto report = learningReportService.getLearningReport(userId, LocalDate.of(2026, 2, 21));

        LearningPeriodReport weekly = report.weekly();
        assertThat(weekly.reviewCount()).isEqualTo(4L);
        assertThat(weekly.averageAccuracy()).isEqualTo(37.5);
        assertThat(weekly.consecutiveLearningDays()).isEqualTo(3);
        assertThat(weekly.averageStudyTimeMinutes()).isEqualTo(5.0);

        Map<String, Long> weeklyTrend = weekly.trend().stream()
                .collect(Collectors.toMap(t -> t.label(), t -> t.reviewCount()));
        assertThat(weeklyTrend)
                .containsEntry("2026-02-16", 1L)
                .containsEntry("2026-02-17", 1L)
                .containsEntry("2026-02-18", 1L)
                .containsEntry("2026-02-19", 0L)
                .containsEntry("2026-02-20", 1L)
                .containsEntry("2026-02-21", 0L)
                .containsEntry("2026-02-22", 0L);

        Map<String, Long> weeklyWeakAreaMap = weekly.weakAreas().stream()
                .collect(Collectors.toMap(w -> w.topic(), w -> w.wrongCount()));
        assertThat(weeklyWeakAreaMap).containsEntry("대수", 1L).containsEntry("기하", 1L);

        LearningPeriodReport monthly = report.monthly();
        assertThat(monthly.reviewCount()).isEqualTo(6L);
        assertThat(monthly.averageAccuracy()).isCloseTo(41.666666, within(0.001));
        assertThat(monthly.consecutiveLearningDays()).isEqualTo(3);
        assertThat(monthly.averageStudyTimeMinutes()).isCloseTo(3.833333, within(0.0001));
        Map<String, Long> monthlyTrend = monthly.trend().stream()
                .collect(Collectors.toMap(t -> t.label(), t -> t.reviewCount()));
        assertThat(monthlyTrend)
                .containsEntry("1주차", 1L)
                .containsEntry("2주차", 1L)
                .containsEntry("3주차", 4L)
                .containsEntry("4주차", 0L);

        LearningPeriodReport total = report.total();
        assertThat(total.reviewCount()).isEqualTo(8L);
        assertThat(total.averageAccuracy()).isCloseTo(43.75, within(0.0001));

        Map<String, Long> totalTrend = total.trend().stream()
                .collect(Collectors.toMap(t -> t.label(), t -> t.reviewCount()));
        assertThat(totalTrend).containsEntry("2026-01", 2L).containsEntry("2026-02", 6L);

        Map<String, Long> totalWeakAreaMap = total.weakAreas().stream()
                .collect(Collectors.toMap(w -> w.topic(), w -> w.wrongCount()));
        assertThat(totalWeakAreaMap).containsEntry("대수", 3L).containsEntry("기하", 1L);

        LearningComparison weeklyComparison = report.weeklyComparison();
        assertThat(weeklyComparison.reviewCountChangeRate()).isEqualTo(300.0);
        assertThat(weeklyComparison.averageAccuracyChangeRate()).isEqualTo(100.0);
        assertThat(weeklyComparison.consecutiveLearningDaysChangeRate()).isEqualTo(200.0);
        assertThat(weeklyComparison.averageStudyTimeChangeRate()).isEqualTo(400.0);
        assertThat(report.recommendations()).isNotNull();
        assertThat(report.recommendations().actions()).hasSize(3);
    }

    @Test
    @DisplayName("학습 리포트 조회 - 데이터가 없으면 0 기반 결과를 반환한다")
    void getLearningReport_emptyData() {
        User user = createUser("empty-user");

        LearningReportResponseDto report = learningReportService.getLearningReport(user.getId(), LocalDate.of(2026, 2, 21));

        assertThat(report.weekly().reviewCount()).isEqualTo(0L);
        assertThat(report.monthly().reviewCount()).isEqualTo(0L);
        assertThat(report.total().reviewCount()).isEqualTo(0L);
        assertThat(report.weekly().averageAccuracy()).isEqualTo(0.0);
        assertThat(report.monthly().averageStudyTimeMinutes()).isEqualTo(0.0);
        assertThat(report.total().weakAreas()).isEmpty();
        assertThat(report.weekly().trend()).hasSize(7);
    }

    private User createUser(String identifier) {
        return userRepository.save(User.from(UserRegisterDto.builder()
                .email(identifier + "@test.com")
                .name(identifier)
                .identifier(identifier)
                .platform("GOOGLE")
                .password("password")
                .build()));
    }

    private Problem createProblemWithAnalysis(Long userId, String problemType) {
        Problem problemEntity = problemRepository.save(Problem.from(
                new ProblemRegisterDto(null, "memo-" + problemType, "ref-" + problemType, null, LocalDateTime.now()),
                userId
        ));

        ProblemAnalysis analysis = ProblemAnalysis.createProcessing(problemEntity);
        analysis.updateWithSuccess("수학", problemType, "[]", "solution", "mistake", "tip");
        problemAnalysisRepository.save(analysis);
        return problemEntity;
    }

    private void createSolve(Problem problemEntity, Long userId, LocalDateTime practicedAt, AnswerStatus status, Integer seconds) {
        problemSolveRepository.save(ProblemSolve.create(
                problemEntity,
                userId,
                practicedAt,
                status,
                null,
                null,
                seconds
        ));
    }

    private static Offset<Double> within(double value) {
        return Offset.offset(value);
    }
}
