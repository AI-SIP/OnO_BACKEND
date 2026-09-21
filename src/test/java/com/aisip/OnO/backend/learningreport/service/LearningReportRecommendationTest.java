package com.aisip.OnO.backend.learningreport.service;

import com.aisip.OnO.backend.learningreport.dto.LearningRecommendations;
import com.aisip.OnO.backend.learningreport.dto.LearningReportResponseDto;
import com.aisip.OnO.backend.learningreport.support.LearningReportTestSupport;
import com.aisip.OnO.backend.problem.entity.Problem;
import com.aisip.OnO.backend.problemsolve.entity.AnswerStatus;
import com.aisip.OnO.backend.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.times;

/**
 * 추천 문구는 룰 기반 결과를 만든 뒤 AI 응답이 있으면 덮어쓰는 구조다.
 * AI 는 부가 정보이므로 어떤 응답이 오든(혹은 오지 않든) 리포트 자체는 정상이어야 한다.
 *
 * <p>{@code openAIClient} 는 {@code IntegrationTestSupport} 에서 이미 목으로 잡혀 있다.
 * 여기서 다시 {@code @MockBean} 을 선언하면 스프링 컨텍스트가 새로 뜨므로 그 목을 그대로 쓴다.
 */
@DisplayName("LearningReportService - 추천")
class LearningReportRecommendationTest extends LearningReportTestSupport {

    private static final LocalDate BASE_DATE = LocalDate.of(2026, 2, 21);

    private Long userId;

    @BeforeEach
    void setUpUser() {
        userId = fixtures.createUser("report-ai").getId();
    }

    /** 주간 구간(2/16~2/18)에 사흘 연속, 전부 정답, 문제당 10분. */
    private void givenStrongWeek() {
        Problem problem = saveNoteWrittenAt(userId, LocalDateTime.of(2026, 2, 16, 8, 0));
        saveSolve(userId, problem, LocalDateTime.of(2026, 2, 16, 9, 0), AnswerStatus.CORRECT, 600);
        saveSolve(userId, problem, LocalDateTime.of(2026, 2, 17, 9, 0), AnswerStatus.CORRECT, 600);
        saveSolve(userId, problem, LocalDateTime.of(2026, 2, 18, 9, 0), AnswerStatus.CORRECT, 600);
    }

    @Nested
    @DisplayName("룰 기반 추천")
    class RuleBased {

        @Test
        @DisplayName("AI 응답이 없으면 룰 기반 문구를 그대로 내려준다")
        void fallsBackToRuleBasedWhenAiReturnsNothing() {
            given(openAIClient.recommendLearningReport(anyMap())).willReturn(Optional.empty());

            LearningRecommendations recommendations =
                    learningReportService.getLearningReport(userId, BASE_DATE).recommendations();

            assertThat(recommendations.strengths())
                    .containsExactly("기록이 누적되고 있어 개인화 분석의 정확도가 점점 좋아지고 있습니다.");
            assertThat(recommendations.gaps()).containsExactly(
                    "최근 주간 정답률이 낮아 취약 유형에 대한 집중 복습이 필요합니다.",
                    "문제당 학습 시간이 짧아 오답 원인 점검이 충분하지 않을 수 있습니다.");
            assertThat(recommendations.actions()).containsExactly(
                    "최근 오답 유형 유형 문제를 다음 주에 3문제 이상 재풀이하세요.",
                    "오답 문제를 푼 뒤 5분 동안 틀린 이유와 개선점을 1문장씩 기록하세요.",
                    "연속 학습일 목표를 최소 3일로 설정하세요.");
            assertThat(recommendations.nextWeekGoal())
                    .isEqualTo("다음 주에는 복습 5회, 평균 정답률 60%를 목표로 하세요.");
            assertThat(recommendations.confidence())
                    .as("누적 복습이 20회 미만이면 신뢰도는 70")
                    .isEqualTo(70.0);
        }

        @Test
        @DisplayName("연속 학습·정답률 상승·복습량 증가가 모두 강점으로 잡히고 3개로 제한된다")
        void collectsStrengthsUpToThree() {
            givenStrongWeek();
            given(openAIClient.recommendLearningReport(anyMap())).willReturn(Optional.empty());

            LearningRecommendations recommendations =
                    learningReportService.getLearningReport(userId, BASE_DATE).recommendations();

            assertThat(recommendations.strengths()).containsExactly(
                    "최근 주차에 연속 학습 흐름이 안정적으로 유지되고 있습니다.",
                    "이전 주 대비 정답률이 상승했습니다.",
                    "이전 달 대비 복습량이 증가해 학습 루틴이 강화되고 있습니다.");
            assertThat(recommendations.gaps())
                    .as("정답률 100%, 문제당 10분이면 약점 문구가 붙지 않는다")
                    .containsExactly("큰 약점은 없지만 학습량 변동을 줄이면 성과를 더 안정화할 수 있습니다.");
            assertThat(recommendations.nextWeekGoal())
                    .isEqualTo("다음 주에는 복습 5회, 평균 정답률 100%를 목표로 하세요.");
        }

        @Test
        @DisplayName("취약 유형이 있으면 첫 번째 행동 문구에 그 유형을 넣는다")
        void firstActionMentionsTopWeakArea() {
            Problem algebra = saveAnalyzedNote(userId, "대수", LocalDateTime.of(2026, 2, 16, 8, 0));
            saveSolve(userId, algebra, LocalDateTime.of(2026, 2, 16, 9, 0), AnswerStatus.WRONG, 60);
            saveSolve(userId, algebra, LocalDateTime.of(2026, 2, 17, 9, 0), AnswerStatus.WRONG, 60);
            given(openAIClient.recommendLearningReport(anyMap())).willReturn(Optional.empty());

            LearningRecommendations recommendations =
                    learningReportService.getLearningReport(userId, BASE_DATE).recommendations();

            assertThat(recommendations.actions().get(0))
                    .isEqualTo("대수 유형 문제를 다음 주에 3문제 이상 재풀이하세요.");
            assertThat(recommendations.gaps())
                    .contains("오답이 반복된 유형이 있어 개념 복습 우선순위 조정이 필요합니다.");
        }

        @Test
        @DisplayName("누적 복습이 20회 이상이면 신뢰도가 85로 올라간다")
        void confidenceRisesWithEnoughHistory() {
            Problem problem = saveNoteWrittenAt(userId, LocalDateTime.of(2025, 12, 1, 8, 0));
            for (int i = 0; i < 20; i++) {
                saveSolve(userId, problem, LocalDateTime.of(2025, 12, 1, 9, 0).plusMinutes(i),
                        AnswerStatus.CORRECT, 60);
            }
            given(openAIClient.recommendLearningReport(anyMap())).willReturn(Optional.empty());

            LearningReportResponseDto report = learningReportService.getLearningReport(userId, BASE_DATE);

            assertThat(report.total().reviewCount()).isEqualTo(20L);
            assertThat(report.recommendations().confidence()).isEqualTo(85.0);
        }
    }

    @Nested
    @DisplayName("AI 추천 반영")
    class AiRecommendations {

        @Test
        @DisplayName("AI 응답이 오면 추천 문구를 그대로 반영한다")
        void appliesAiRecommendations() {
            givenStrongWeek();
            given(openAIClient.recommendLearningReport(anyMap())).willReturn(Optional.of(
                    LearningRecommendations.builder()
                            .strengths(List.of("복습 루틴을 유지하고 있습니다."))
                            .gaps(List.of("대수 유형 오답이 반복됩니다."))
                            .actions(List.of("대수 유형 3문제를 다시 풀어보세요."))
                            .nextWeekGoal("다음 주 복습 5회를 목표로 하세요.")
                            .confidence(88.0)
                            .build()));

            LearningRecommendations recommendations =
                    learningReportService.getLearningReport(userId, BASE_DATE).recommendations();

            assertThat(recommendations.strengths()).containsExactly("복습 루틴을 유지하고 있습니다.");
            assertThat(recommendations.gaps()).containsExactly("대수 유형 오답이 반복됩니다.");
            assertThat(recommendations.actions()).containsExactly("대수 유형 3문제를 다시 풀어보세요.");
            assertThat(recommendations.nextWeekGoal()).isEqualTo("다음 주 복습 5회를 목표로 하세요.");
            assertThat(recommendations.confidence()).isEqualTo(88.0);
        }

        @Test
        @DisplayName("AI가 비워 둔 항목은 룰 기반 값으로 메우고 항목 수는 3개로 자른다")
        void mergesPartialAiResponse() {
            given(openAIClient.recommendLearningReport(anyMap())).willReturn(Optional.of(
                    LearningRecommendations.builder()
                            .strengths(null)
                            .gaps(List.of())
                            .actions(List.of("액션1", "액션2", "액션3", "액션4", "액션5"))
                            .nextWeekGoal("   ")
                            .confidence(null)
                            .build()));

            LearningRecommendations recommendations =
                    learningReportService.getLearningReport(userId, BASE_DATE).recommendations();

            assertThat(recommendations.strengths())
                    .as("null 이면 룰 기반 강점을 쓴다")
                    .containsExactly("기록이 누적되고 있어 개인화 분석의 정확도가 점점 좋아지고 있습니다.");
            assertThat(recommendations.gaps())
                    .as("빈 목록이면 룰 기반 약점을 쓴다")
                    .hasSize(2);
            assertThat(recommendations.actions())
                    .as("AI 가 4개 이상 줘도 3개까지만 쓴다")
                    .containsExactly("액션1", "액션2", "액션3");
            assertThat(recommendations.nextWeekGoal())
                    .as("공백 문자열이면 룰 기반 목표를 쓴다")
                    .isEqualTo("다음 주에는 복습 5회, 평균 정답률 60%를 목표로 하세요.");
            assertThat(recommendations.confidence()).isEqualTo(70.0);
        }

        @Test
        @DisplayName("AI 호출이 예외로 실패해도 집계 결과는 그대로 내려간다")
        void reportSurvivesAiFailure() {
            givenStrongWeek();
            willThrow(new IllegalStateException("openai unavailable"))
                    .given(openAIClient).recommendLearningReport(anyMap());

            LearningReportResponseDto report = learningReportService.getLearningReport(userId, BASE_DATE);

            assertThat(report.weekly().reviewCount()).as("집계는 영향받지 않는다").isEqualTo(3L);
            assertThat(report.weekly().averageAccuracy()).isEqualTo(100.0);
            assertThat(report.recommendations())
                    .as("추천은 룰 기반으로 대체된다")
                    .isNotNull();
            assertThat(report.recommendations().actions()).hasSize(3);
            assertThat(report.recommendations().confidence()).isEqualTo(70.0);
        }

        @Test
        @DisplayName("AI에 넘기는 요약에는 사용자 식별자와 룰 기반 추천이 함께 담긴다")
        void sendsSummaryPayloadToAi() {
            givenStrongWeek();
            given(openAIClient.recommendLearningReport(anyMap())).willReturn(Optional.empty());

            learningReportService.getLearningReport(userId, BASE_DATE);

            @SuppressWarnings("unchecked")
            ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
            then(openAIClient).should().recommendLearningReport(captor.capture());

            Map<String, Object> payload = captor.getValue();
            assertThat(payload).containsKeys(
                    "userId", "weekly", "monthly", "total",
                    "weeklyComparison", "monthlyComparison", "ruleBasedRecommendations");
            assertThat(payload.get("userId")).isEqualTo(userId);
            assertThat(payload.get("ruleBasedRecommendations")).isInstanceOf(LearningRecommendations.class);
        }
    }

    @Nested
    @DisplayName("리포트 캐시")
    class ReportCache {

        @Test
        @DisplayName("같은 기준일을 다시 조회하면 캐시를 쓰고 AI를 다시 부르지 않는다")
        void reusesCachedReport() {
            givenStrongWeek();
            given(openAIClient.recommendLearningReport(anyMap())).willReturn(Optional.empty());

            LearningReportResponseDto first = learningReportService.getLearningReport(userId, BASE_DATE);

            Problem extra = saveNoteWrittenAt(userId, LocalDateTime.of(2026, 2, 19, 8, 0));
            saveSolve(userId, extra, LocalDateTime.of(2026, 2, 19, 9, 0), AnswerStatus.WRONG, 60);

            LearningReportResponseDto second = learningReportService.getLearningReport(userId, BASE_DATE);

            assertThat(second.weekly().reviewCount())
                    .as("캐시 적중이므로 새로 추가한 복습은 아직 반영되지 않는다")
                    .isEqualTo(first.weekly().reviewCount());
            then(openAIClient).should(times(1)).recommendLearningReport(anyMap());
        }

        @Test
        @DisplayName("기준일이 다르면 캐시를 공유하지 않는다")
        void differentBaseDateUsesDifferentCacheEntry() {
            givenStrongWeek();
            given(openAIClient.recommendLearningReport(anyMap())).willReturn(Optional.empty());

            LearningReportResponseDto first = learningReportService.getLearningReport(userId, BASE_DATE);
            LearningReportResponseDto second =
                    learningReportService.getLearningReport(userId, BASE_DATE.minusDays(3));

            assertThat(first.weekly().endDate()).isEqualTo(BASE_DATE);
            assertThat(second.weekly().endDate()).isEqualTo(BASE_DATE.minusDays(3));
            then(openAIClient).should(times(2)).recommendLearningReport(anyMap());
        }

        @Test
        @DisplayName("다른 사용자의 캐시를 가져다 쓰지 않는다")
        void cacheIsScopedPerUser() {
            givenStrongWeek();
            given(openAIClient.recommendLearningReport(anyMap())).willReturn(Optional.empty());

            User other = fixtures.createOtherUser();
            learningReportService.getLearningReport(userId, BASE_DATE);
            LearningReportResponseDto otherReport =
                    learningReportService.getLearningReport(other.getId(), BASE_DATE);

            assertThat(otherReport.weekly().reviewCount()).isZero();
            assertThat(otherReport.total().reviewCount()).isZero();
        }
    }

}
