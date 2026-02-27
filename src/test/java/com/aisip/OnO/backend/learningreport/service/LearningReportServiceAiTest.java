package com.aisip.OnO.backend.learningreport.service;

import com.aisip.OnO.backend.learningreport.dto.LearningRecommendations;
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
import com.aisip.OnO.backend.util.ai.OpenAIClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("local")
@Transactional
class LearningReportServiceAiTest {

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

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private OpenAIClient openAIClient;

    @Test
    @DisplayName("학습 리포트 조회 - AI 추천 결과가 recommendations에 반영된다")
    void getLearningReport_appliesAiRecommendations() {
        User user = createUser("ai-user");
        Problem problem = createProblemWithAnalysis(user.getId(), "대수");
        createSolve(problem, user.getId(), LocalDateTime.of(2026, 2, 21, 10, 0), AnswerStatus.WRONG, 300);

        LearningRecommendations aiRecommendations = LearningRecommendations.builder()
                .strengths(java.util.List.of("복습 루틴을 유지하고 있습니다."))
                .gaps(java.util.List.of("대수 유형 오답이 반복됩니다."))
                .actions(java.util.List.of("대수 유형 3문제를 다시 풀어보세요."))
                .nextWeekGoal("다음 주 복습 5회를 목표로 하세요.")
                .confidence(88.0)
                .build();

        when(openAIClient.recommendLearningReport(anyMap())).thenReturn(Optional.of(aiRecommendations));

        LearningReportResponseDto report = learningReportService.getLearningReport(user.getId(), LocalDate.of(2026, 2, 21));
        System.out.println("[AI Recommendation Result]");
        try {
            System.out.println(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(report.recommendations()));
        } catch (Exception e) {
            System.out.println(report.recommendations());
        }

        assertThat(report.recommendations()).isNotNull();
        assertThat(report.recommendations().strengths()).containsExactly("복습 루틴을 유지하고 있습니다.");
        assertThat(report.recommendations().gaps()).containsExactly("대수 유형 오답이 반복됩니다.");
        assertThat(report.recommendations().actions()).containsExactly("대수 유형 3문제를 다시 풀어보세요.");
        assertThat(report.recommendations().nextWeekGoal()).isEqualTo("다음 주 복습 5회를 목표로 하세요.");
        assertThat(report.recommendations().confidence()).isEqualTo(88.0);

        verify(openAIClient).recommendLearningReport(anyMap());
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
}
