package com.aisip.OnO.backend.config.rabbitmq.consumer;

import com.aisip.OnO.backend.folder.entity.Folder;
import com.aisip.OnO.backend.problem.entity.AnalysisStatus;
import com.aisip.OnO.backend.problem.entity.Problem;
import com.aisip.OnO.backend.problem.entity.ProblemAnalysis;
import com.aisip.OnO.backend.problem.entity.ProblemImageType;
import com.aisip.OnO.backend.user.entity.User;
import com.aisip.OnO.backend.util.ai.NonRetryableAnalysisException;
import com.aisip.OnO.backend.util.ai.ProblemAnalysisResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willReturn;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * AI 문제 분석 컨슈머 테스트.
 *
 * <p>{@code OpenAIClient} 는 베이스에서 목으로 잡혀 있으므로 실제 OpenAI 호출·과금은 일어나지 않는다.
 * 컨슈머 아래의 분석 서비스·실패 기록 서비스는 실물 빈을 그대로 쓰고 DB 상태로 결과를 확인한다.
 *
 * <p>계약이 갈리는 지점이 핵심이다. 재시도해도 소용없는 실패(모델 거절, 문제/분석 레코드 삭제)는
 * 예외를 삼켜 ACK 하고, 일시적 실패(4xx·5xx 등)는 예외를 던져 재시도·DLQ 로 넘긴다.
 */
@DisplayName("ProblemAnalysisConsumer")
class ProblemAnalysisConsumerTest extends RabbitConsumerTestSupport {

    @Autowired
    private ProblemAnalysisConsumer consumer;

    private User owner;
    private Folder rootFolder;

    @BeforeEach
    void setUpOwner() {
        owner = fixtures.createUser();
        rootFolder = fixtures.createRootFolder(owner.getId());
    }

    private static ProblemAnalysisResult successResult() {
        return ProblemAnalysisResult.builder()
                .subject("수학")
                .problemType("계산")
                .keyPoints(List.of("이차방정식"))
                .solution("판별식으로 근의 개수를 구한다")
                .commonMistakes("부호 실수")
                .studyTips("공식을 유도해 보기")
                .build();
    }

    /**
     * 프로덕션에서 실제로 났던 실패를 그대로 재현한다.
     * OpenAI 가 "Unable to download content from the provided URL before the timeout" 400 을 내면
     * {@code OpenAIClient} 가 RuntimeException 으로 감싸 올린다.
     */
    private static RuntimeException imageDownloadTimeout() {
        HttpClientErrorException cause = new HttpClientErrorException(
                HttpStatus.BAD_REQUEST,
                "Bad Request",
                "{\"error\":{\"message\":\"Unable to download content from the provided URL before the timeout\"}}"
                        .getBytes(StandardCharsets.UTF_8),
                StandardCharsets.UTF_8);
        return new RuntimeException("AI 이미지 분석 중 오류가 발생했습니다: " + cause.getMessage(), cause);
    }

    /** 소프트 삭제된 문제의 id. 분석 도중 사용자가 문제를 지운 상황을 만든다. */
    private Long deletedProblemId() {
        Problem problem = saveProblem(owner.getId(), rootFolder);
        Long problemId = problem.getId();
        problemRepository.deleteById(problemId);
        return problemId;
    }

    private Problem analyzableProblem() {
        Problem problem = saveProblem(owner.getId(), rootFolder);
        saveImageData(problem, "https://s3/problem.png", ProblemImageType.PROBLEM_IMAGE);
        saveAnalysisRow(problem);
        return problem;
    }

    private AnalysisStatus statusOf(Problem problem) {
        return problemAnalysisRepository.findByProblemId(problem.getId())
                .map(ProblemAnalysis::getStatus)
                .orElseThrow();
    }

    // ════════════════════════════ 정상 처리 ════════════════════════════

    @Nested
    @DisplayName("정상 처리")
    class Analyze {

        @Test
        @DisplayName("메시지의 문제를 분석해 COMPLETED 로 저장한다")
        void completesAnalysis() {
            Problem problem = analyzableProblem();
            given(openAIClient.analyzeImages(anyList())).willReturn(successResult());

            consumer.handleAnalysisMessage(analysisMessage(problem.getId()));

            assertThat(statusOf(problem)).isEqualTo(AnalysisStatus.COMPLETED);
            assertThat(problemAnalysisRepository.findByProblemId(problem.getId()).orElseThrow().getSubject())
                    .isEqualTo("수학");
        }

        @Test
        @DisplayName("메시지에 담긴 문제의 이미지만 분석에 넘긴다")
        void analyzesOnlyMessagesProblemImages() {
            Problem target = saveProblem(owner.getId(), rootFolder);
            saveImageData(target, "https://s3/target.png", ProblemImageType.PROBLEM_IMAGE);
            saveAnalysisRow(target);

            Problem other = saveProblem(owner.getId(), rootFolder);
            saveImageData(other, "https://s3/other.png", ProblemImageType.PROBLEM_IMAGE);
            saveAnalysisRow(other);

            given(openAIClient.analyzeImages(anyList())).willReturn(successResult());

            consumer.handleAnalysisMessage(analysisMessage(target.getId()));

            @SuppressWarnings("unchecked")
            ArgumentCaptor<List<String>> captor = ArgumentCaptor.forClass(List.class);
            verify(openAIClient).analyzeImages(captor.capture());
            assertThat(captor.getValue()).containsExactly("https://s3/target.png");
        }

        @Test
        @DisplayName("이미 COMPLETED 인 문제는 다시 분석하지 않는다")
        void skipsAlreadyCompleted() {
            Problem problem = analyzableProblem();
            inTransaction(() -> {
                ProblemAnalysis analysis = problemAnalysisRepository.findByProblemId(problem.getId()).orElseThrow();
                analysis.updateWithSuccess("수학", "계산", "[]", "풀이", "실수", "팁");
                problemAnalysisRepository.save(analysis);
            });

            consumer.handleAnalysisMessage(analysisMessage(problem.getId()));

            verify(openAIClient, never()).analyzeImages(anyList());
        }

        @Test
        @DisplayName("정상 처리에서는 Discord 알림을 보내지 않는다")
        void doesNotNotifyDiscordOnSuccess() {
            Problem problem = analyzableProblem();
            given(openAIClient.analyzeImages(anyList())).willReturn(successResult());

            consumer.handleAnalysisMessage(analysisMessage(problem.getId()));

            verifyNoInteractions(discordWebhookNotificationService);
        }
    }

    // ════════════════════════════ 재시도 없는 실패 ════════════════════════════

    @Nested
    @DisplayName("재시도해도 소용없는 실패")
    class NonRetryableFailure {

        @Test
        @DisplayName("모델이 판독을 거절하면 FAILED 로 남기고 예외 없이 종료한다")
        void ackOnNonRetryableRejection() {
            Problem problem = analyzableProblem();
            given(openAIClient.analyzeImages(anyList()))
                    .willThrow(new NonRetryableAnalysisException("분석이 불가능한 이미지입니다."));

            assertThatCode(() -> consumer.handleAnalysisMessage(analysisMessage(problem.getId())))
                    .as("재시도해도 같은 결과라 큐에 되돌리지 않는다")
                    .doesNotThrowAnyException();

            assertThat(statusOf(problem)).isEqualTo(AnalysisStatus.FAILED);
        }

        @Test
        @DisplayName("분석 도중 문제가 삭제됐으면 예외 없이 종료한다")
        void ackWhenProblemDeleted() {
            Long deletedProblemId = deletedProblemId();

            assertThatCode(() -> consumer.handleAnalysisMessage(analysisMessage(deletedProblemId)))
                    .as("사라진 문제를 재분석할 방법은 없다")
                    .doesNotThrowAnyException();

            verify(openAIClient, never()).analyzeImages(anyList());
        }

        @Test
        @DisplayName("분석 레코드가 없으면 예외 없이 종료한다")
        void ackWhenAnalysisRowMissing() {
            Problem problem = saveProblem(owner.getId(), rootFolder);
            saveImageData(problem, "https://s3/problem.png", ProblemImageType.PROBLEM_IMAGE);

            assertThatCode(() -> consumer.handleAnalysisMessage(analysisMessage(problem.getId())))
                    .doesNotThrowAnyException();

            verify(openAIClient, never()).analyzeImages(anyList());
        }
    }

    // ════════════════════════════ 재시도 대상 실패 ════════════════════════════

    @Nested
    @DisplayName("재시도 대상 실패")
    class RetryableFailure {

        @Test
        @DisplayName("이미지 다운로드 타임아웃(OpenAI 400)은 FAILED 로 기록하고 예외를 던진다")
        void rethrowsImageDownloadTimeout() {
            Problem problem = analyzableProblem();
            given(openAIClient.analyzeImages(anyList())).willThrow(imageDownloadTimeout());

            assertThatThrownBy(() -> consumer.handleAnalysisMessage(analysisMessage(problem.getId())))
                    .as("일시적 실패는 재시도·DLQ 로 넘겨야 한다")
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining(String.valueOf(problem.getId()));

            assertThat(statusOf(problem))
                    .as("재시도가 남아 있어도 현재 상태는 FAILED 로 드러난다")
                    .isEqualTo(AnalysisStatus.FAILED);
        }

        @Test
        @DisplayName("재시도 실패가 반복돼도 다음 재시도에서 성공하면 COMPLETED 로 회복된다")
        void recoversOnRetry() {
            Problem problem = analyzableProblem();
            given(openAIClient.analyzeImages(anyList())).willThrow(imageDownloadTimeout());

            assertThatThrownBy(() -> consumer.handleAnalysisMessage(analysisMessage(problem.getId())))
                    .isInstanceOf(RuntimeException.class);

            // 이미 예외를 던지도록 스텁된 목이라 given(...) 형태로 다시 스텁하면 스텁 도중 예외가 난다.
            willReturn(successResult()).given(openAIClient).analyzeImages(anyList());
            consumer.handleAnalysisMessage(analysisMessageWithRetryCount(problem.getId(), 1));

            assertThat(statusOf(problem))
                    .as("FAILED 로 기록된 뒤에도 재시도 메시지는 정상 처리돼야 한다")
                    .isEqualTo(AnalysisStatus.COMPLETED);
        }

        @Test
        @DisplayName("예상하지 못한 예외도 감싸서 던진다")
        void rethrowsUnexpectedError() {
            Problem problem = analyzableProblem();
            given(openAIClient.analyzeImages(anyList())).willThrow(new IllegalStateException("boom"));

            assertThatThrownBy(() -> consumer.handleAnalysisMessage(analysisMessage(problem.getId())))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("GPT 문제 분석 실패");
        }
    }

    // ════════════════════════════ 메시지 결손 ════════════════════════════

    @Nested
    @DisplayName("메시지 필드 결손")
    class MissingFields {

        @Test
        @DisplayName("problemId 가 null 이면 분석을 시도하지 않고 종료한다")
        void skipsNullProblemId() {
            assertThatCode(() -> consumer.handleAnalysisMessage(analysisMessage(null)))
                    .as("조회할 수 없는 메시지는 재시도해도 영원히 실패한다")
                    .doesNotThrowAnyException();

            verify(openAIClient, never()).analyzeImages(anyList());
        }
    }

    // ════════════════════════════ DLQ ════════════════════════════

    @Nested
    @DisplayName("DLQ 핸들러")
    class DeadLetter {

        @Test
        @DisplayName("최종 실패 메시지는 Discord 로 알린다")
        void notifiesDiscord() {
            Problem problem = analyzableProblem();

            consumer.handleAnalysisDLQ(analysisMessageWithRetryCount(problem.getId(), 3));

            ArgumentCaptor<String> details = ArgumentCaptor.forClass(String.class);
            verify(discordWebhookNotificationService).sendErrorNotification(
                    eq("RabbitMQ DLQ - GPT Analysis"), details.capture(), eq("ERROR"), anyString());
            assertThat(details.getValue())
                    .contains(String.valueOf(problem.getId()))
                    .contains("3");
        }

        @Test
        @DisplayName("DLQ 로 밀린 분석은 PROCESSING 에 갇히지 않고 FAILED 로 전이된다")
        void marksAnalysisFailed() {
            Problem problem = analyzableProblem();
            inTransaction(() -> {
                ProblemAnalysis analysis = problemAnalysisRepository.findByProblemId(problem.getId()).orElseThrow();
                analysis.updateToProcessing();
                problemAnalysisRepository.save(analysis);
            });

            consumer.handleAnalysisDLQ(analysisMessage(problem.getId()));

            ProblemAnalysis analysis = problemAnalysisRepository.findByProblemId(problem.getId()).orElseThrow();
            assertThat(analysis.getStatus()).isEqualTo(AnalysisStatus.FAILED);
            assertThat(analysis.getErrorMessage()).isNotBlank();
        }

        @Test
        @DisplayName("DLQ 처리에서 분석을 다시 실행하지는 않는다")
        void doesNotReanalyze() {
            Problem problem = analyzableProblem();

            consumer.handleAnalysisDLQ(analysisMessage(problem.getId()));

            verify(openAIClient, never()).analyzeImages(anyList());
        }

        @Test
        @DisplayName("분석 레코드가 없어도 DLQ 알림은 나간다")
        void notifiesEvenWithoutAnalysisRow() {
            assertThatCode(() -> consumer.handleAnalysisDLQ(analysisMessage(deletedProblemId())))
                    .doesNotThrowAnyException();

            verify(discordWebhookNotificationService)
                    .sendErrorNotification(anyString(), anyString(), anyString(), anyString());
        }

        @Test
        @DisplayName("Discord 알림이 실패해도 DLQ 핸들러는 예외를 던지지 않는다")
        void swallowsDiscordFailure() {
            Problem problem = analyzableProblem();
            willThrow(new IllegalStateException("webhook down"))
                    .given(discordWebhookNotificationService)
                    .sendErrorNotification(anyString(), anyString(), anyString(), anyString());

            assertThatCode(() -> consumer.handleAnalysisDLQ(analysisMessage(problem.getId())))
                    .doesNotThrowAnyException();

            assertThat(statusOf(problem))
                    .as("알림이 막혀도 상태 전이는 이미 끝나 있어야 한다")
                    .isEqualTo(AnalysisStatus.FAILED);
        }
    }
}
