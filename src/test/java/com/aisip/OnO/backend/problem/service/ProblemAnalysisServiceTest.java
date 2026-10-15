package com.aisip.OnO.backend.problem.service;

import com.aisip.OnO.backend.common.exception.ApplicationException;
import com.aisip.OnO.backend.common.exception.ErrorCase;
import com.aisip.OnO.backend.common.ratelimit.RateLimitKeys;
import com.aisip.OnO.backend.common.ratelimit.RateLimitService;
import com.aisip.OnO.backend.config.rabbitmq.RabbitMQConfig;
import com.aisip.OnO.backend.config.rabbitmq.RabbitRetryAttempts;
import com.aisip.OnO.backend.folder.entity.Folder;
import com.aisip.OnO.backend.problem.dto.ProblemAnalysisResponseDto;
import com.aisip.OnO.backend.problem.entity.AnalysisStatus;
import com.aisip.OnO.backend.problem.entity.Problem;
import java.util.Optional;
import com.aisip.OnO.backend.problem.entity.ProblemAnalysis;
import com.aisip.OnO.backend.problem.entity.ProblemImageType;
import com.aisip.OnO.backend.problem.exception.ProblemErrorCase;
import com.aisip.OnO.backend.problem.support.ProblemTestSupport;
import com.aisip.OnO.backend.user.entity.User;
import com.aisip.OnO.backend.util.ai.AnalysisFailureReason;
import com.aisip.OnO.backend.util.ai.AnalysisInput;
import com.aisip.OnO.backend.util.ai.NonRetryableAnalysisException;
import com.aisip.OnO.backend.util.ai.ProblemAnalysisResult;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;
import com.aisip.OnO.backend.util.ai.AnalysisImageLoader;
import com.aisip.OnO.backend.util.ai.OpenAIClient;
import com.aisip.OnO.backend.util.ai.OpenAIHttpClientConfig;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.web.client.HttpClientErrorException;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * AI 분석 서비스 3종(트리거/DB 작업/실패 기록) 통합 테스트.
 *
 * <p>OpenAI 호출은 베이스에서 목으로 잡혀 있으므로 실제 API 를 때리지 않는다.
 * 프로덕션 Sentry 에 쌓인 분석 실패(모델 거절, 4xx)를 여기서 주입해 재현한다.
 */
@DisplayName("ProblemAnalysisService")
class ProblemAnalysisServiceTest extends ProblemTestSupport {

    @Autowired
    private ProblemAnalysisService analysisService;

    @Autowired
    private ProblemAnalysisDbService analysisDbService;

    @Autowired
    private ProblemAnalysisFailureService analysisFailureService;

    @Autowired
    private MeterRegistry meterRegistry;

    @Autowired
    private RateLimitService rateLimitService;

    private User owner;
    private User intruder;
    private Folder ownerRoot;
    private Folder intruderRoot;

    @BeforeEach
    void setUpUsers() {
        owner = fixtures.createUser();
        intruder = fixtures.createOtherUser();
        ownerRoot = fixtures.createRootFolder(owner.getId());
        intruderRoot = fixtures.createRootFolder(intruder.getId());
    }

    private static ErrorCase errorCaseOf(Throwable throwable) {
        return ((ApplicationException) throwable).getErrorCase();
    }

    private static ProblemAnalysisResult successResult() {
        return ProblemAnalysisResult.builder()
                .subject("수학")
                .problemType("계산")
                .keyPoints(List.of("이차방정식", "판별식"))
                .solution("판별식으로 근의 개수를 구한다")
                .commonMistakes("부호 실수")
                .studyTips("공식을 유도해 보기")
                .build();
    }

    private static String guardKey(Long problemId) {
        return "ai_analysis_inflight:" + problemId;
    }

    private double failureCount(AnalysisFailureReason reason) {
        Counter counter = meterRegistry.find("ono.ai.analysis.failures").tag("reason", reason.tag()).counter();
        return counter == null ? 0 : counter.count();
    }

    private String quotaKey(Long userId) {
        return "rate_limit:" + RateLimitKeys.AI_ANALYSIS + ":" + userId;
    }

    /** 요청 경로처럼 하루 한도를 times 번 쓴다 */
    private void consumeQuota(Long userId, int times) {
        for (int i = 0; i < times; i++) {
            rateLimitService.tryConsume(RateLimitKeys.AI_ANALYSIS, userId, 20);
        }
    }

    private Object quotaOf(Long userId) {
        return redisTemplate.opsForValue().get(quotaKey(userId));
    }

    private AnalysisStatus statusOf(Problem problem) {
        return problemAnalysisRepository.findByProblemId(problem.getId()).orElseThrow().getStatus();
    }

    /** 요청 경로를 거친 것처럼 PROCESSING 으로 둔다 */
    private void markProcessing(Problem problem) {
        analysisService.updateToProcessing(problem.getId());
    }

    private Problem problemWithImage() {
        Problem problem = saveProblem(owner.getId(), ownerRoot);
        saveImageData(problem, "https://s3/p.png", ProblemImageType.PROBLEM_IMAGE);
        saveSkippedAnalysis(problem);
        return problem;
    }

    // ════════════════════════════ 분석 결과 조회 ════════════════════════════

    @Nested
    @DisplayName("분석 결과 조회")
    class GetAnalysis {

        @Test
        @DisplayName("분석 레코드가 있으면 그 상태와 내용을 그대로 돌려준다")
        void returnsStoredAnalysis() {
            Problem problem = saveProblem(owner.getId(), ownerRoot);
            saveSkippedAnalysis(problem);
            inTransaction(() -> {
                ProblemAnalysis analysis = problemAnalysisRepository.findByProblemId(problem.getId()).orElseThrow();
                analysis.updateWithSuccess("수학", "계산", "[\"판별식\"]", "풀이", "실수", "팁");
                problemAnalysisRepository.save(analysis);
            });

            ProblemAnalysisResponseDto response = analysisService.getAnalysis(problem.getId(), owner.getId());

            assertThat(response.status()).isEqualTo(AnalysisStatus.COMPLETED.name());
            assertThat(response.subject()).isEqualTo("수학");
            assertThat(response.keyPoints())
                    .as("keyPoints 는 JSON 문자열로 저장되지만 응답에서는 리스트로 나가야 한다")
                    .containsExactly("판별식");
        }

        @Test
        @DisplayName("분석 레코드가 없으면 NOT_STARTED 기본 응답을 만든다")
        void returnsNotStartedWhenNoAnalysisRow() {
            Problem problem = saveProblem(owner.getId(), ownerRoot);

            ProblemAnalysisResponseDto response = analysisService.getAnalysis(problem.getId(), owner.getId());

            assertThat(response.status()).isEqualTo(AnalysisStatus.NOT_STARTED.name());
            assertThat(response.id()).isNull();
            assertThat(response.problemId()).isEqualTo(problem.getId());
        }

        @Test
        @DisplayName("keyPoints 가 깨진 JSON 이어도 빈 리스트로 방어한다")
        void survivesBrokenKeyPointsJson() {
            Problem problem = saveProblem(owner.getId(), ownerRoot);
            saveSkippedAnalysis(problem);
            inTransaction(() -> {
                ProblemAnalysis analysis = problemAnalysisRepository.findByProblemId(problem.getId()).orElseThrow();
                analysis.updateWithSuccess("수학", "계산", "이건 JSON 이 아니다", "풀이", "실수", "팁");
                problemAnalysisRepository.save(analysis);
            });

            assertThat(analysisService.getAnalysis(problem.getId(), owner.getId()).keyPoints()).isEmpty();
        }

        @Test
        @DisplayName("다른 사용자의 분석 결과는 조회할 수 없다")
        void rejectsOtherUsersAnalysis() {
            Problem othersProblem = saveProblem(intruder.getId(), intruderRoot);
            saveSkippedAnalysis(othersProblem);

            assertThatThrownBy(() -> analysisService.getAnalysis(othersProblem.getId(), owner.getId()))
                    .isInstanceOf(ApplicationException.class)
                    .extracting(ProblemAnalysisServiceTest::errorCaseOf)
                    .isEqualTo(ProblemErrorCase.PROBLEM_USER_UNMATCHED);
        }

        @Test
        @DisplayName("없는 문제의 분석을 조회하면 PROBLEM_NOT_FOUND")
        void rejectsUnknownProblem() {
            assertThatThrownBy(() -> analysisService.getAnalysis(999_999L, owner.getId()))
                    .isInstanceOf(ApplicationException.class)
                    .extracting(ProblemAnalysisServiceTest::errorCaseOf)
                    .isEqualTo(ProblemErrorCase.PROBLEM_NOT_FOUND);
        }
    }

    // ════════════════════════════ 상태 전이 ════════════════════════════

    @Nested
    @DisplayName("분석 레코드 생성 및 상태 전이")
    class StatusTransition {

        @Test
        @DisplayName("createSkippedAnalysis 는 NOT_STARTED 레코드를 한 번만 만든다")
        void createsSkippedOnce() {
            Problem problem = saveProblem(owner.getId(), ownerRoot);

            analysisService.createSkippedAnalysis(problem.getId());
            analysisService.createSkippedAnalysis(problem.getId());

            assertThat(problemAnalysisRepository.findAll()).hasSize(1);
            assertThat(problemAnalysisRepository.findByProblemId(problem.getId()).orElseThrow().getStatus())
                    .isEqualTo(AnalysisStatus.NOT_STARTED);
        }

        @Test
        @DisplayName("없는 문제로 createSkippedAnalysis 를 불러도 예외가 새어 나가지 않는다")
        void swallowsErrorForUnknownProblem() {
            assertThatCode(() -> analysisService.createSkippedAnalysis(999_999L))
                    .as("등록 흐름 중 부가 작업이므로 여기서 터지면 안 된다")
                    .doesNotThrowAnyException();
            assertThat(problemAnalysisRepository.findAll()).isEmpty();
        }

        @Test
        @DisplayName("createPendingAnalysis 는 PROCESSING 레코드를 만든다")
        void createsPendingAnalysis() {
            Problem problem = saveProblem(owner.getId(), ownerRoot);

            analysisService.createPendingAnalysis(problem.getId());

            assertThat(problemAnalysisRepository.findByProblemId(problem.getId()).orElseThrow().getStatus())
                    .isEqualTo(AnalysisStatus.PROCESSING);
        }

        @Test
        @DisplayName("없는 문제로 createPendingAnalysis 를 부르면 PROBLEM_NOT_FOUND")
        void pendingAnalysisRequiresProblem() {
            assertThatThrownBy(() -> analysisService.createPendingAnalysis(999_999L))
                    .isInstanceOf(ApplicationException.class)
                    .extracting(ProblemAnalysisServiceTest::errorCaseOf)
                    .isEqualTo(ProblemErrorCase.PROBLEM_NOT_FOUND);
        }

        @Test
        @DisplayName("updateToProcessing 은 이전 에러 메시지를 지운다")
        void processingClearsErrorMessage() {
            Problem problem = saveProblem(owner.getId(), ownerRoot);
            saveSkippedAnalysis(problem);
            analysisFailureService.markFailed(problem.getId(), AnalysisFailureReason.TRANSIENT);

            analysisService.updateToProcessing(problem.getId());

            ProblemAnalysis analysis = problemAnalysisRepository.findByProblemId(problem.getId()).orElseThrow();
            assertThat(analysis.getStatus()).isEqualTo(AnalysisStatus.PROCESSING);
            assertThat(analysis.getErrorMessage()).isNull();
        }

        @Test
        @DisplayName("분석 레코드가 없는데 updateToProcessing 하면 PROBLEM_NOT_FOUND")
        void processingRequiresAnalysisRow() {
            Problem problem = saveProblem(owner.getId(), ownerRoot);

            assertThatThrownBy(() -> analysisService.updateToProcessing(problem.getId()))
                    .isInstanceOf(ApplicationException.class)
                    .extracting(ProblemAnalysisServiceTest::errorCaseOf)
                    .isEqualTo(ProblemErrorCase.PROBLEM_NOT_FOUND);
        }

        @Test
        @DisplayName("updateToNoImage 는 NO_IMAGE 와 안내 메시지를 남긴다")
        void marksNoImage() {
            Problem problem = saveProblem(owner.getId(), ownerRoot);
            saveSkippedAnalysis(problem);

            analysisService.updateToNoImage(problem.getId());

            ProblemAnalysis analysis = problemAnalysisRepository.findByProblemId(problem.getId()).orElseThrow();
            assertThat(analysis.getStatus()).isEqualTo(AnalysisStatus.NO_IMAGE);
            assertThat(analysis.getErrorMessage()).isNotBlank();
        }

        @Test
        @DisplayName("updateToNoImage 는 완료된 분석의 상태와 결과를 바꾸지 않는다")
        void keepsCompletedAnalysisOnNoImage() {
            Problem problem = saveProblem(owner.getId(), ownerRoot);
            saveSkippedAnalysis(problem);
            inTransaction(() -> {
                ProblemAnalysis analysis = problemAnalysisRepository.findByProblemId(problem.getId()).orElseThrow();
                analysis.updateWithSuccess("수학", "계산", "[\"판별식\"]", "풀이", "실수", "팁");
                problemAnalysisRepository.save(analysis);
            });

            analysisService.updateToNoImage(problem.getId());

            ProblemAnalysis analysis = problemAnalysisRepository.findByProblemId(problem.getId()).orElseThrow();
            assertThat(analysis.getStatus()).isEqualTo(AnalysisStatus.COMPLETED);
            assertThat(analysis.getSubject()).isEqualTo("수학");
            assertThat(analysis.getSolution()).isEqualTo("풀이");
            assertThat(analysis.getErrorMessage()).isNotEqualTo("이미지가 없어 분석을 진행할 수 없습니다.");
        }

        @Test
        @DisplayName("updateToRateLimitExceeded 는 분석 레코드가 없으면 새로 만들어 표시한다")
        void createsRowWhenMarkingRateLimit() {
            Problem problem = saveProblem(owner.getId(), ownerRoot);

            analysisService.updateToRateLimitExceeded(problem.getId());

            assertThat(problemAnalysisRepository.findByProblemId(problem.getId()).orElseThrow().getStatus())
                    .isEqualTo(AnalysisStatus.RATE_LIMIT_EXCEEDED);
        }

        @Test
        @DisplayName("deleteAnalysis 는 분석 레코드를 지우고, 없으면 조용히 지나간다")
        void deletesAnalysis() {
            Problem problem = saveProblem(owner.getId(), ownerRoot);
            saveSkippedAnalysis(problem);

            analysisService.deleteAnalysis(problem.getId());
            assertThat(problemAnalysisRepository.findByProblemId(problem.getId())).isEmpty();

            assertThatCode(() -> analysisService.deleteAnalysis(problem.getId())).doesNotThrowAnyException();
        }
    }

    // ════════════════════════════ 동기 분석 ════════════════════════════

    @Nested
    @DisplayName("analyzeProblemSync 성공 경로")
    class AnalyzeSuccess {

        @Test
        @DisplayName("OpenAI 결과가 COMPLETED 로 저장된다")
        void savesAnalysisResult() {
            Problem problem = problemWithImage();
            given(openAIClient.analyze(any())).willReturn(successResult());

            analysisService.analyzeProblemSync(problem.getId());

            ProblemAnalysis analysis = problemAnalysisRepository.findByProblemId(problem.getId()).orElseThrow();
            assertThat(analysis.getStatus()).isEqualTo(AnalysisStatus.COMPLETED);
            assertThat(analysis.getSubject()).isEqualTo("수학");
            assertThat(analysis.getKeyPoints())
                    .as("keyPoints 는 JSON 배열 문자열로 직렬화된다")
                    .contains("판별식");
        }

        @Test
        @DisplayName("분석이 끝나면 처리 중 표시를 지운다")
        void releasesDispatchGuardOnSuccess() {
            Problem problem = problemWithImage();
            redisTemplate.opsForValue().set(guardKey(problem.getId()), "1");
            given(openAIClient.analyze(any())).willReturn(successResult());

            analysisService.analyzeProblemSync(problem.getId());

            assertThat(redisTemplate.hasKey(guardKey(problem.getId()))).isFalse();
        }

        @Test
        @DisplayName("문제 이미지와 해설 이미지를 나눠 넘기고, 학생 풀이 이미지(SOLVE_IMAGE)는 넘기지 않는다")
        void sendsProblemAndAnswerImagesButNotSolveImages() {
            Problem problem = saveProblem(owner.getId(), ownerRoot);
            saveImageData(problem, "https://s3/p.png", ProblemImageType.PROBLEM_IMAGE);
            saveImageData(problem, "https://s3/a.png", ProblemImageType.ANSWER_IMAGE);
            saveImageData(problem, "https://s3/s.png", ProblemImageType.SOLVE_IMAGE);
            saveSkippedAnalysis(problem);
            given(openAIClient.analyze(any())).willReturn(successResult());

            analysisService.analyzeProblemSync(problem.getId());

            ArgumentCaptor<AnalysisInput> captor = ArgumentCaptor.forClass(AnalysisInput.class);
            verify(openAIClient).analyze(captor.capture());
            assertThat(captor.getValue().problemImageUrls()).containsExactly("https://s3/p.png");
            assertThat(captor.getValue().answerImageUrls()).containsExactly("https://s3/a.png");
            assertThat(captor.getValue().problemImageUrls()).doesNotContain("https://s3/s.png");
            assertThat(captor.getValue().answerImageUrls()).doesNotContain("https://s3/s.png");
        }

        @Test
        @DisplayName("제목(reference)과 메모를 분석 입력에 함께 넘기고, 이미지는 종류별로 등록 순서를 지킨다")
        void sendsReferenceMemoAndImagesInOrder() {
            Problem problem = saveProblem(owner.getId(), ownerRoot, "부호를 거꾸로 봐서 틀렸다", "2024 수능 21번");
            saveImageData(problem, "https://s3/p1.png", ProblemImageType.PROBLEM_IMAGE);
            saveImageData(problem, "https://s3/a1.png", ProblemImageType.ANSWER_IMAGE);
            saveImageData(problem, "https://s3/p2.png", ProblemImageType.PROBLEM_IMAGE);
            saveImageData(problem, "https://s3/a2.png", ProblemImageType.ANSWER_IMAGE);
            saveSkippedAnalysis(problem);
            given(openAIClient.analyze(any())).willReturn(successResult());

            analysisService.analyzeProblemSync(problem.getId());

            ArgumentCaptor<AnalysisInput> captor = ArgumentCaptor.forClass(AnalysisInput.class);
            verify(openAIClient).analyze(captor.capture());
            AnalysisInput input = captor.getValue();
            assertThat(input.problemImageUrls()).containsExactly("https://s3/p1.png", "https://s3/p2.png");
            assertThat(input.answerImageUrls()).containsExactly("https://s3/a1.png", "https://s3/a2.png");
            assertThat(input.reference()).isEqualTo("2024 수능 21번");
            assertThat(input.memo()).isEqualTo("부호를 거꾸로 봐서 틀렸다");
        }

        @Test
        @DisplayName("해설 이미지만 있고 문제 이미지가 0장이면 OpenAI 를 부르지 않고 NO_IMAGE 로 끝낸다")
        void answerImageOnlyIsNoImage() {
            Problem problem = saveProblem(owner.getId(), ownerRoot);
            saveImageData(problem, "https://s3/a.png", ProblemImageType.ANSWER_IMAGE);
            saveSkippedAnalysis(problem);
            markProcessing(problem);

            analysisService.analyzeProblemSync(problem.getId());

            verify(openAIClient, never()).analyze(any());
            assertThat(statusOf(problem)).isEqualTo(AnalysisStatus.NO_IMAGE);
        }

        @Test
        @DisplayName("이미 COMPLETED 면 OpenAI 를 호출하지 않는다")
        void skipsWhenAlreadyCompleted() {
            Problem problem = problemWithImage();
            inTransaction(() -> {
                ProblemAnalysis analysis = problemAnalysisRepository.findByProblemId(problem.getId()).orElseThrow();
                analysis.updateWithSuccess("기존", "기존", "[]", "기존", "기존", "기존");
                problemAnalysisRepository.save(analysis);
            });

            analysisService.analyzeProblemSync(problem.getId());

            verify(openAIClient, never()).analyze(any());
            assertThat(problemAnalysisRepository.findByProblemId(problem.getId()).orElseThrow().getSubject())
                    .isEqualTo("기존");
        }
    }

    @Nested
    @DisplayName("analyzeProblemSync 실패 경로")
    class AnalyzeFailure {

        @Test
        @DisplayName("모델이 판독을 거절하면(NonRetryableAnalysisException) FAILED 로 기록하고 예외를 다시 던진다")
        void marksFailedOnNonRetryableRejection() {
            Problem problem = problemWithImage();
            given(openAIClient.analyze(any()))
                    .willThrow(new NonRetryableAnalysisException(AnalysisFailureReason.UNREADABLE_IMAGE, "이미지에서 문제를 인식할 수 없습니다"));

            assertThatThrownBy(() -> analysisService.analyzeProblemSync(problem.getId()))
                    .as("재시도해도 소용없는 실패는 상위에서 DLQ 처리해야 하므로 그대로 전파된다")
                    .isInstanceOf(NonRetryableAnalysisException.class);

            ProblemAnalysis analysis = problemAnalysisRepository.findByProblemId(problem.getId()).orElseThrow();
            assertThat(analysis.getStatus()).isEqualTo(AnalysisStatus.FAILED);
            assertThat(analysis.getErrorMessage())
                    .as("앱이 error_message 를 그대로 보여 주므로 예외 원문이 아니라 사유별 사용자 문구를 저장한다")
                    .isEqualTo(AnalysisFailureReason.UNREADABLE_IMAGE.userMessage())
                    .doesNotContain("인식할 수 없습니다");
        }

        @Test
        @DisplayName("OpenAI 가 400 을 내면 FAILED 로 기록하고 예외를 다시 던진다")
        void marksFailedOnBadRequest() {
            Problem problem = problemWithImage();
            given(openAIClient.analyze(any()))
                    .willThrow(new HttpClientErrorException(HttpStatus.BAD_REQUEST, "Bad Request"));

            assertThatThrownBy(() -> analysisService.analyzeProblemSync(problem.getId()))
                    .isInstanceOf(HttpClientErrorException.class);

            ProblemAnalysis analysis = problemAnalysisRepository.findByProblemId(problem.getId()).orElseThrow();
            assertThat(analysis.getStatus()).isEqualTo(AnalysisStatus.FAILED);
            assertThat(analysis.getErrorMessage())
                    .as("재시도 대상 예외는 일시적 실패 문구로 기록한다. HTTP 응답 원문을 사용자에게 보여 주지 않는다")
                    .isEqualTo(AnalysisFailureReason.TRANSIENT.userMessage())
                    .doesNotContain("Bad Request");
        }

        @Test
        @DisplayName("재시도 인터셉터 안의 일시적 실패는 1, 2회차에 PROCESSING 을 두고 마지막 3회차에 FAILED(MAX_RETRIES) 로 바꾼다")
        void marksFailedOnLastAttemptOnly() {
            Problem problem = problemWithImage();
            markProcessing(problem);
            given(openAIClient.analyze(any()))
                    .willThrow(new HttpClientErrorException(HttpStatus.INTERNAL_SERVER_ERROR, "Server Error"));
            double maxRetriesBefore = failureCount(AnalysisFailureReason.MAX_RETRIES);
            double transientBefore = failureCount(AnalysisFailureReason.TRANSIENT);
            List<AnalysisStatus> statusAfterAttempt = new ArrayList<>();

            // 리스너 컨테이너의 재시도 인터셉터처럼 같은 스레드에 RetryContext 를 걸고 세 번 시도한다.
            RetryTemplate retryTemplate = RetryTemplate.builder()
                    .maxAttempts(RabbitRetryAttempts.MAX_ATTEMPTS).noBackoff().build();
            assertThatThrownBy(() -> retryTemplate.execute(context -> {
                try {
                    analysisService.analyzeProblemSync(problem.getId());
                    return null;
                } finally {
                    statusAfterAttempt.add(statusOf(problem));
                }
            })).isInstanceOf(HttpClientErrorException.class);

            verify(openAIClient, times(3)).analyze(any());
            assertThat(statusAfterAttempt)
                    .as("중간에 FAILED 가 보이면 앱이 폴링을 멈춘다. 마지막 시도에서는 DLQ 를 기다리지 않고 바로 FAILED")
                    .containsExactly(AnalysisStatus.PROCESSING, AnalysisStatus.PROCESSING, AnalysisStatus.FAILED);
            assertThat(problemAnalysisRepository.findByProblemId(problem.getId()).orElseThrow().getErrorMessage())
                    .isEqualTo(AnalysisFailureReason.MAX_RETRIES.userMessage());
            assertThat(failureCount(AnalysisFailureReason.MAX_RETRIES)).isEqualTo(maxRetriesBefore + 1);
            assertThat(failureCount(AnalysisFailureReason.TRANSIENT))
                    .as("중간 시도의 실패는 세지 않는다").isEqualTo(transientBefore);
        }

        @Test
        @DisplayName("마지막 시도에서 FAILED 로 바꾸면 처리 중 표시도 지워 바로 다시 요청할 수 있다")
        void releasesGuardOnLastAttempt() {
            Problem problem = problemWithImage();
            markProcessing(problem);
            redisTemplate.opsForValue().set(guardKey(problem.getId()), "1");
            given(openAIClient.analyze(any()))
                    .willThrow(new HttpClientErrorException(HttpStatus.INTERNAL_SERVER_ERROR, "Server Error"));

            RetryTemplate retryTemplate = RetryTemplate.builder()
                    .maxAttempts(RabbitRetryAttempts.MAX_ATTEMPTS).noBackoff().build();
            List<Boolean> guardAfterAttempt = new ArrayList<>();
            assertThatThrownBy(() -> retryTemplate.execute(context -> {
                try {
                    analysisService.analyzeProblemSync(problem.getId());
                    return null;
                } finally {
                    guardAfterAttempt.add(redisTemplate.hasKey(guardKey(problem.getId())));
                }
            })).isInstanceOf(HttpClientErrorException.class);

            assertThat(guardAfterAttempt).containsExactly(true, true, false);
        }

        @Test
        @DisplayName("재시도 인터셉터 안이라도 재시도하지 않는 실패는 바로 FAILED 로 기록한다")
        void marksNonRetryableFailedEvenInsideRetryContext() {
            Problem problem = problemWithImage();
            given(openAIClient.analyze(any()))
                    .willThrow(new NonRetryableAnalysisException(AnalysisFailureReason.REFUSAL, "I'm sorry"));

            RetryTemplate retryTemplate = RetryTemplate.builder().maxAttempts(1).noBackoff().build();
            assertThatThrownBy(() -> retryTemplate.execute(context -> {
                analysisService.analyzeProblemSync(problem.getId());
                return null;
            })).isInstanceOf(NonRetryableAnalysisException.class);

            ProblemAnalysis analysis = problemAnalysisRepository.findByProblemId(problem.getId()).orElseThrow();
            assertThat(analysis.getStatus()).isEqualTo(AnalysisStatus.FAILED);
            assertThat(analysis.getErrorMessage()).isEqualTo(AnalysisFailureReason.REFUSAL.userMessage());
        }

        @Test
        @DisplayName("분석 도중 문제가 삭제됐으면 FAILED 로 덮어쓰지 않고 그대로 전파한다")
        void doesNotMarkFailedWhenProblemGone() {
            Problem problem = problemWithImage();
            Long problemId = problem.getId();
            problemRepository.deleteById(problemId);

            assertThatThrownBy(() -> analysisService.analyzeProblemSync(problemId))
                    .isInstanceOf(ApplicationException.class)
                    .extracting(ProblemAnalysisServiceTest::errorCaseOf)
                    .isEqualTo(ProblemErrorCase.PROBLEM_NOT_FOUND);

            verify(openAIClient, never()).analyze(any());

            // Problem 은 소프트 삭제(@SQLDelete)지만 ProblemAnalysis 는 cascade = ALL 이라
            // 실제로 행이 삭제된다. 따라서 "행이 남아 NOT_STARTED 다"로 단정할 수 없다.
            // 검증하려는 것은 "FAILED 로 덮어쓰지 않는다" 이므로 그것만 확인한다.
            assertThat(problemAnalysisRepository.findByProblemId(problemId).map(ProblemAnalysis::getStatus))
                    .as("삭제된 문제의 분석을 FAILED 로 바꿔 봐야 의미가 없다")
                    .isNotEqualTo(Optional.of(AnalysisStatus.FAILED));
        }

        @Test
        @DisplayName("분석 레코드가 없으면 PROBLEM_ANALYSIS_NOT_FOUND 를 그대로 전파한다")
        void propagatesMissingAnalysisRow() {
            Problem problem = saveProblem(owner.getId(), ownerRoot);
            saveImageData(problem, "https://s3/p.png", ProblemImageType.PROBLEM_IMAGE);

            assertThatThrownBy(() -> analysisService.analyzeProblemSync(problem.getId()))
                    .isInstanceOf(ApplicationException.class)
                    .extracting(ProblemAnalysisServiceTest::errorCaseOf)
                    .isEqualTo(ProblemErrorCase.PROBLEM_ANALYSIS_NOT_FOUND);
        }
    }

    // ════════════════════════════ 2단계: 실제 클라이언트 응답의 저장 모양 ════════════════════════════

    /**
     * OpenAIClient 빈은 베이스에서 목이다. 여기서는 그 목이 실물 OpenAIClient(HTTP 는 MockRestServiceServer, S3 는 목 로더)에
     * 위임하게 해서, 모델 응답의 내부 필드가 DB 와 조회 응답까지 가지 않는지 끝까지 확인한다. 실제 OpenAI 로는 나가지 않는다.
     */
    @Nested
    @DisplayName("2단계 - 내부 필드와 정답 노출 문장은 DB 와 조회 응답에 남지 않는다")
    class StoredAnalysisWithRealClient {

        private static final String API_URL = "https://api.openai.com/v1/chat/completions";
        private static final String MARKER = "INTERNAL_SOLUTION_MARKER";

        private String modelResponse() throws Exception {
            Map<String, Object> content = new LinkedHashMap<>();
            content.put("analyzable", true);
            content.put("internalSolution", MARKER + " 판별식이 0 이므로 중근이고 x = 2 이다");
            content.put("finalAnswer", "x = 2");
            content.put("explanationCheck", "matches");
            content.put("subject", "수학");
            content.put("problemType", "이차방정식의 판별식");
            content.put("keyPoints", List.of("판별식", "중근의 뜻"));
            content.put("solution", "먼저 판별식을 세워 보세요. 따라서 답은 x = 2 입니다. 근의 개수를 판별식의 부호로 판단하세요.");
            content.put("commonMistakes", "메모처럼 부호를 거꾸로 보기 쉽습니다.");
            content.put("studyTips", "판별식 단원을 다시 보세요.");
            String message = "{\"role\":\"assistant\",\"content\":" + objectMapper.writeValueAsString(objectMapper.writeValueAsString(content)) + "}";
            return "{\"choices\":[{\"index\":0,\"message\":" + message + ",\"finish_reason\":\"stop\"}]}";
        }

        @Test
        @DisplayName("COMPLETED 로 저장되고, 학생용 필드에는 내부 풀이와 정답 문장이 없으며, 조회 응답 필드도 그대로다")
        void storesOnlyVisibleFields() throws Exception {
            Problem problem = saveProblem(owner.getId(), ownerRoot, "부호를 거꾸로 봄", "모의고사 3월");
            String problemUrl = "https://s3/p.png";
            String answerUrl = "https://s3/a.png";
            saveImageData(problem, problemUrl, ProblemImageType.PROBLEM_IMAGE);
            saveImageData(problem, answerUrl, ProblemImageType.ANSWER_IMAGE);
            saveSkippedAnalysis(problem);

            RestTemplate restTemplate = new RestTemplate();
            MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
            server.expect(requestTo(API_URL)).andRespond(withSuccess(modelResponse(), MediaType.APPLICATION_JSON));
            AnalysisImageLoader loader = Mockito.mock(AnalysisImageLoader.class);
            given(loader.loadForAnalysis(List.of(problemUrl), List.of(answerUrl))).willReturn(
                    new AnalysisImageLoader.LoadedImages(List.of("data:image/png;base64,UFJPQg=="), List.of("data:image/png;base64,QU5T")));
            OpenAIClient realClient = new OpenAIClient(restTemplate, objectMapper, new SimpleMeterRegistry(), loader);
            ReflectionTestUtils.setField(realClient, "model", "gpt-4o");
            ReflectionTestUtils.setField(realClient, "apiKey", "test-openai-api-key");
            ReflectionTestUtils.setField(realClient, "apiUrl", API_URL);
            given(openAIClient.analyze(any())).willAnswer(invocation -> realClient.analyze(invocation.getArgument(0)));

            analysisService.analyzeProblemSync(problem.getId());

            server.verify();
            ProblemAnalysis saved = problemAnalysisRepository.findByProblemId(problem.getId()).orElseThrow();
            assertThat(saved.getStatus()).isEqualTo(AnalysisStatus.COMPLETED);
            List<String> stored = java.util.Arrays.asList(saved.getSubject(), saved.getProblemType(), saved.getKeyPoints(),
                    saved.getSolution(), saved.getCommonMistakes(), saved.getStudyTips());
            assertThat(stored).noneMatch(value -> value != null && value.contains(MARKER));
            assertThat(saved.getSolution())
                    .isEqualTo("먼저 판별식을 세워 보세요. 근의 개수를 판별식의 부호로 판단하세요.")
                    .doesNotContain("x = 2");
            assertThat(saved.getCommonMistakes()).isEqualTo("메모처럼 부호를 거꾸로 보기 쉽습니다.");

            ProblemAnalysisResponseDto response = analysisService.getAnalysis(problem.getId(), owner.getId());
            String json = objectMapper.writeValueAsString(response);
            assertThat(json).doesNotContain(MARKER).doesNotContain("internalSolution")
                    .doesNotContain("finalAnswer").doesNotContain("explanationCheck");
            List<String> fieldNames = new ArrayList<>();
            objectMapper.readTree(json).fieldNames().forEachRemaining(fieldNames::add);
            assertThat(fieldNames).containsExactlyInAnyOrder("id", "problemId", "subject", "problemType", "keyPoints",
                    "solution", "commonMistakes", "studyTips", "status", "errorMessage");
            assertThat(response.keyPoints()).containsExactly("판별식", "중근의 뜻");
        }
    }

    // ════════════════════════════ DB 서비스 ════════════════════════════

    @Nested
    @DisplayName("ProblemAnalysisDbService")
    class DbService {

        @Test
        @DisplayName("분석 준비 조회는 PROBLEM_IMAGE URL 과 완료 여부를 함께 준다")
        void fetchesPreparation() {
            Problem problem = saveProblem(owner.getId(), ownerRoot);
            saveImageData(problem, "https://s3/p1.png", ProblemImageType.PROBLEM_IMAGE);
            saveImageData(problem, "https://s3/a1.png", ProblemImageType.ANSWER_IMAGE);
            saveSkippedAnalysis(problem);

            ProblemAnalysisDbService.AnalysisPreparation prep =
                    analysisDbService.fetchAnalysisPreparation(problem.getId());

            assertThat(prep.input().problemImageUrls()).containsExactly("https://s3/p1.png");
            assertThat(prep.input().answerImageUrls()).containsExactly("https://s3/a1.png");
            assertThat(prep.input().reference()).isEqualTo("출처");
            assertThat(prep.input().memo()).isEqualTo("메모");
            assertThat(prep.alreadyCompleted()).isFalse();
        }

        @Test
        @DisplayName("제목과 메모가 없으면 null 로 준다")
        void fetchesNullReferenceAndMemo() {
            Problem problem = saveProblem(owner.getId(), ownerRoot, null, null);
            saveImageData(problem, "https://s3/p1.png", ProblemImageType.PROBLEM_IMAGE);
            saveSkippedAnalysis(problem);

            ProblemAnalysisDbService.AnalysisPreparation prep =
                    analysisDbService.fetchAnalysisPreparation(problem.getId());

            assertThat(prep.input().reference()).isNull();
            assertThat(prep.input().memo()).isNull();
            assertThat(prep.input().answerImageUrls()).isEmpty();
        }

        @Test
        @DisplayName("이미지가 하나도 없으면 빈 URL 목록을 준다")
        void fetchesEmptyImageList() {
            Problem problem = saveProblem(owner.getId(), ownerRoot);
            saveSkippedAnalysis(problem);

            ProblemAnalysisDbService.AnalysisPreparation prep = analysisDbService.fetchAnalysisPreparation(problem.getId());
            assertThat(prep.input().problemImageUrls()).isEmpty();
            assertThat(prep.input().answerImageUrls()).isEmpty();
        }

        @Test
        @DisplayName("다른 스레드가 먼저 COMPLETED 로 만들었으면 결과를 덮어쓰지 않는다")
        void doesNotOverwriteCompleted() {
            Problem problem = saveProblem(owner.getId(), ownerRoot);
            saveSkippedAnalysis(problem);
            inTransaction(() -> {
                ProblemAnalysis analysis = problemAnalysisRepository.findByProblemId(problem.getId()).orElseThrow();
                analysis.updateWithSuccess("먼저 저장", "계산", "[]", "풀이", "실수", "팁");
                problemAnalysisRepository.save(analysis);
            });

            analysisDbService.saveAnalysisSuccess(problem.getId(), successResult(), "[]");

            assertThat(problemAnalysisRepository.findByProblemId(problem.getId()).orElseThrow().getSubject())
                    .isEqualTo("먼저 저장");
        }

        @Test
        @DisplayName("없는 문제의 분석 준비를 조회하면 PROBLEM_NOT_FOUND")
        void rejectsUnknownProblem() {
            assertThatThrownBy(() -> analysisDbService.fetchAnalysisPreparation(999_999L))
                    .isInstanceOf(ApplicationException.class)
                    .extracting(ProblemAnalysisServiceTest::errorCaseOf)
                    .isEqualTo(ProblemErrorCase.PROBLEM_NOT_FOUND);
        }

        @Test
        @DisplayName("분석 레코드 없이 결과를 저장하면 PROBLEM_ANALYSIS_NOT_FOUND")
        void rejectsSaveWithoutAnalysisRow() {
            Problem problem = saveProblem(owner.getId(), ownerRoot);

            assertThatThrownBy(() -> analysisDbService.saveAnalysisSuccess(problem.getId(), successResult(), "[]"))
                    .isInstanceOf(ApplicationException.class)
                    .extracting(ProblemAnalysisServiceTest::errorCaseOf)
                    .isEqualTo(ProblemErrorCase.PROBLEM_ANALYSIS_NOT_FOUND);
        }
    }

    // ════════════════════════════ 실패 기록 ════════════════════════════

    @Nested
    @DisplayName("ProblemAnalysisFailureService")
    class FailureService {

        @Test
        @DisplayName("실패 사유를 FAILED 상태로 기록한다")
        void marksFailed() {
            Problem problem = saveProblem(owner.getId(), ownerRoot);
            saveSkippedAnalysis(problem);

            analysisFailureService.markFailed(problem.getId(), AnalysisFailureReason.QUOTA);

            ProblemAnalysis analysis = problemAnalysisRepository.findByProblemId(problem.getId()).orElseThrow();
            assertThat(analysis.getStatus()).isEqualTo(AnalysisStatus.FAILED);
            assertThat(analysis.getErrorMessage()).isEqualTo(AnalysisFailureReason.QUOTA.userMessage());
        }

        @Test
        @DisplayName("분석 레코드가 없으면 아무 일도 하지 않는다")
        void ignoresMissingAnalysisRow() {
            assertThatCode(() -> analysisFailureService.markFailed(999_999L, AnalysisFailureReason.TRANSIENT))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("사유가 null 이어도 상태는 FAILED 로 남고 일시적 실패 문구를 쓴다")
        void acceptsNullReason() {
            Problem problem = saveProblem(owner.getId(), ownerRoot);
            saveSkippedAnalysis(problem);

            analysisFailureService.markFailed(problem.getId(), null);

            ProblemAnalysis analysis = problemAnalysisRepository.findByProblemId(problem.getId()).orElseThrow();
            assertThat(analysis.getStatus()).isEqualTo(AnalysisStatus.FAILED);
            assertThat(analysis.getErrorMessage()).isEqualTo(AnalysisFailureReason.TRANSIENT.userMessage());
        }

        @Test
        @DisplayName("이미 COMPLETED 인 분석은 FAILED 로 덮지 않는다")
        void doesNotOverwriteCompleted() {
            Problem problem = saveProblem(owner.getId(), ownerRoot);
            saveSkippedAnalysis(problem);
            inTransaction(() -> {
                ProblemAnalysis analysis = problemAnalysisRepository.findByProblemId(problem.getId()).orElseThrow();
                analysis.updateWithSuccess("수학", "계산", "[]", "풀이", "실수", "팁");
                problemAnalysisRepository.save(analysis);
            });
            double before = failureCount(AnalysisFailureReason.MAX_RETRIES);

            analysisFailureService.markFailed(problem.getId(), AnalysisFailureReason.MAX_RETRIES);

            ProblemAnalysis analysis = problemAnalysisRepository.findByProblemId(problem.getId()).orElseThrow();
            assertThat(analysis.getStatus())
                    .as("겹쳐 나간 다른 메시지의 실패가 이미 나온 결과를 가리면 안 된다")
                    .isEqualTo(AnalysisStatus.COMPLETED);
            assertThat(failureCount(AnalysisFailureReason.MAX_RETRIES))
                    .as("사용자에게 실패로 보이지 않았으니 세지 않는다")
                    .isEqualTo(before);
        }

        @Test
        @DisplayName("실패 메트릭을 reason 태그로 한 번 올린다")
        void countsFailureByReasonTag() {
            Problem problem = saveProblem(owner.getId(), ownerRoot);
            saveSkippedAnalysis(problem);
            double before = failureCount(AnalysisFailureReason.UNSUPPORTED_IMAGE);
            double otherBefore = failureCount(AnalysisFailureReason.TRANSIENT);

            analysisFailureService.markFailed(problem.getId(), AnalysisFailureReason.UNSUPPORTED_IMAGE);

            assertThat(failureCount(AnalysisFailureReason.UNSUPPORTED_IMAGE)).isEqualTo(before + 1);
            assertThat(failureCount(AnalysisFailureReason.TRANSIENT)).isEqualTo(otherBefore);
            assertThat(meterRegistry.find("ono.ai.analysis.failures").tag("reason", "unsupported_image").counter())
                    .as("태그 값은 AnalysisFailureReason.tag() 다")
                    .isNotNull();
        }

        @Test
        @DisplayName("이미 FAILED 인 행에 다시 오면(마지막 시도 뒤 DLQ) 문구와 메트릭을 바꾸지 않는다")
        void skipsDuplicateFailure() {
            Problem problem = saveProblem(owner.getId(), ownerRoot);
            saveSkippedAnalysis(problem);
            markProcessing(problem);
            analysisFailureService.markFailed(problem.getId(), AnalysisFailureReason.MAX_RETRIES);
            double maxRetriesAfterFirst = failureCount(AnalysisFailureReason.MAX_RETRIES);
            double quotaBefore = failureCount(AnalysisFailureReason.QUOTA);

            analysisFailureService.markFailed(problem.getId(), AnalysisFailureReason.MAX_RETRIES);
            analysisFailureService.markFailed(problem.getId(), AnalysisFailureReason.QUOTA);

            ProblemAnalysis analysis = problemAnalysisRepository.findByProblemId(problem.getId()).orElseThrow();
            assertThat(analysis.getStatus()).isEqualTo(AnalysisStatus.FAILED);
            assertThat(analysis.getErrorMessage()).isEqualTo(AnalysisFailureReason.MAX_RETRIES.userMessage());
            assertThat(failureCount(AnalysisFailureReason.MAX_RETRIES)).isEqualTo(maxRetriesAfterFirst);
            assertThat(failureCount(AnalysisFailureReason.QUOTA)).isEqualTo(quotaBefore);
        }

        @Test
        @DisplayName("이미 FAILED 인 행에 다시 와도 처리 중 표시는 지운다")
        void releasesGuardEvenWhenAlreadyFailed() {
            Problem problem = saveProblem(owner.getId(), ownerRoot);
            saveSkippedAnalysis(problem);
            analysisFailureService.markFailed(problem.getId(), AnalysisFailureReason.MAX_RETRIES);
            redisTemplate.opsForValue().set(guardKey(problem.getId()), "1");

            analysisFailureService.markFailed(problem.getId(), AnalysisFailureReason.MAX_RETRIES);

            assertThat(redisTemplate.hasKey(guardKey(problem.getId()))).isFalse();
        }

        @Test
        @DisplayName("FAILED 로 기록하면 처리 중 표시를 지워 바로 다시 요청할 수 있게 한다")
        void releasesDispatchGuard() {
            Problem problem = saveProblem(owner.getId(), ownerRoot);
            saveSkippedAnalysis(problem);
            redisTemplate.opsForValue().set(guardKey(problem.getId()), "1");

            analysisFailureService.markFailed(problem.getId(), AnalysisFailureReason.MAX_RETRIES);

            assertThat(redisTemplate.hasKey(guardKey(problem.getId()))).isFalse();
        }
    }

    // ════════════════════════════ 한도 환불 ════════════════════════════

    @Nested
    @DisplayName("이미지 쪽 실패의 한도 환불")
    class QuotaRefund {

        @ParameterizedTest(name = "{0}")
        @EnumSource(value = AnalysisFailureReason.class,
                names = {"INVALID_IMAGE_SOURCE", "UNSUPPORTED_IMAGE", "TOO_MANY_IMAGES", "IMAGE_TOO_LARGE"})
        @DisplayName("OpenAI 를 부르기 전에 끝난 이미지 실패는 하루 한도 1회를 돌려준다")
        void refundsImageSideFailure(AnalysisFailureReason reason) {
            Problem problem = problemWithImage();
            markProcessing(problem);
            consumeQuota(owner.getId(), 3);
            given(openAIClient.analyze(any()))
                    .willThrow(new NonRetryableAnalysisException(reason, "image side failure"));

            assertThatThrownBy(() -> analysisService.analyzeProblemSync(problem.getId()))
                    .isInstanceOf(NonRetryableAnalysisException.class);

            assertThat(statusOf(problem)).isEqualTo(AnalysisStatus.FAILED);
            assertThat(quotaOf(owner.getId())).isEqualTo("2");
            assertThat(reason.refundsQuota()).isTrue();
        }

        @ParameterizedTest(name = "{0}")
        @EnumSource(value = AnalysisFailureReason.class,
                names = {"INVALID_IMAGE_SOURCE", "UNSUPPORTED_IMAGE", "TOO_MANY_IMAGES", "IMAGE_TOO_LARGE"},
                mode = EnumSource.Mode.EXCLUDE)
        @DisplayName("AI 를 쓴 실패(거절, 판독 불가, 요청 거부, 재시도 소진 등)는 한도를 돌려주지 않는다")
        void keepsQuotaForOtherFailures(AnalysisFailureReason reason) {
            Problem problem = problemWithImage();
            markProcessing(problem);
            consumeQuota(owner.getId(), 3);

            analysisFailureService.markFailed(problem.getId(), reason);

            assertThat(statusOf(problem)).isEqualTo(AnalysisStatus.FAILED);
            assertThat(quotaOf(owner.getId())).isEqualTo("3");
            assertThat(reason.refundsQuota()).isFalse();
        }

        @Test
        @DisplayName("같은 실패가 두 번 와도 한 번만 돌려준다")
        void refundsOnlyOnce() {
            Problem problem = problemWithImage();
            markProcessing(problem);
            consumeQuota(owner.getId(), 3);

            analysisFailureService.markFailed(problem.getId(), AnalysisFailureReason.INVALID_IMAGE_SOURCE);
            analysisFailureService.markFailed(problem.getId(), AnalysisFailureReason.INVALID_IMAGE_SOURCE);

            assertThat(quotaOf(owner.getId())).isEqualTo("2");
        }

        @Test
        @DisplayName("PROCESSING 이 아니던 행(메시지로 한도를 쓰지 않은 행)은 돌려주지 않는다")
        void refundsOnlyFromProcessing() {
            Problem problem = problemWithImage();
            consumeQuota(owner.getId(), 3);

            analysisFailureService.markFailed(problem.getId(), AnalysisFailureReason.INVALID_IMAGE_SOURCE);

            assertThat(statusOf(problem)).isEqualTo(AnalysisStatus.FAILED);
            assertThat(quotaOf(owner.getId())).isEqualTo("3");
        }

        @Test
        @DisplayName("문제 주인의 한도만 돌려주고 다른 사용자의 한도는 그대로다")
        void refundsOnlyOwnersQuota() {
            Problem problem = problemWithImage();
            markProcessing(problem);
            consumeQuota(owner.getId(), 3);
            consumeQuota(intruder.getId(), 3);

            analysisFailureService.markFailed(problem.getId(), AnalysisFailureReason.IMAGE_TOO_LARGE);

            assertThat(quotaOf(owner.getId())).isEqualTo("2");
            assertThat(quotaOf(intruder.getId())).isEqualTo("3");
        }
    }

    // ════════════════════════════ 처리 시점 이미지 0장 ════════════════════════════

    @Nested
    @DisplayName("처리 시점에 문제 이미지가 0장")
    class NoImageAtConsume {

        @Test
        @DisplayName("FAILED 대신 NO_IMAGE 로 끝내고, OpenAI 를 부르지 않고, 처리 중 표시를 지우고, 한도를 돌려준다")
        void finishesAsNoImage() {
            Problem problem = saveProblem(owner.getId(), ownerRoot);
            saveImageData(problem, "https://s3/a.png", ProblemImageType.ANSWER_IMAGE);
            saveSkippedAnalysis(problem);
            markProcessing(problem);
            redisTemplate.opsForValue().set(guardKey(problem.getId()), "1");
            consumeQuota(owner.getId(), 3);
            double unreadableBefore = failureCount(AnalysisFailureReason.UNREADABLE_IMAGE);

            analysisService.analyzeProblemSync(problem.getId());

            verify(openAIClient, never()).analyze(any());
            assertThat(statusOf(problem)).isEqualTo(AnalysisStatus.NO_IMAGE);
            assertThat(redisTemplate.hasKey(guardKey(problem.getId()))).isFalse();
            assertThat(quotaOf(owner.getId())).isEqualTo("2");
            assertThat(failureCount(AnalysisFailureReason.UNREADABLE_IMAGE))
                    .as("이미지가 지워진 것은 분석 실패가 아니다").isEqualTo(unreadableBefore);
        }

        @Test
        @DisplayName("겹친 메시지로 두 번 처리돼도 한도는 한 번만 돌려준다")
        void refundsOnceForDuplicateMessage() {
            Problem problem = saveProblem(owner.getId(), ownerRoot);
            saveSkippedAnalysis(problem);
            markProcessing(problem);
            consumeQuota(owner.getId(), 3);

            analysisService.analyzeProblemSync(problem.getId());
            analysisService.analyzeProblemSync(problem.getId());

            assertThat(statusOf(problem)).isEqualTo(AnalysisStatus.NO_IMAGE);
            assertThat(quotaOf(owner.getId())).isEqualTo("2");
        }
    }

    // ════════════════════════════ 사유와 가드 계약 ════════════════════════════

    @Nested
    @DisplayName("실패 사유 문구와 처리 중 표시 TTL")
    class Contracts {

        @Test
        @DisplayName("사용자 문구는 앱 FAILED 카드 제목(분석 중 오류가 발생했어요)을 반복하지 않는다")
        void messagesDoNotRepeatCardTitle() {
            for (AnalysisFailureReason reason : AnalysisFailureReason.values()) {
                assertThat(reason.userMessage()).as(reason.name())
                        .doesNotContain("오류가 발생했어요")
                        .endsWith("요.");
            }
        }

        @Test
        @DisplayName("새 사유와 바뀐 문구")
        void newReasonsAndMessages() {
            assertThat(AnalysisFailureReason.IMAGE_TOO_LARGE.tag()).isEqualTo("image_too_large");
            assertThat(AnalysisFailureReason.IMAGE_TOO_LARGE.userMessage())
                    .isEqualTo("이미지가 너무 커서 분석하지 못했어요. 사진 수를 줄이거나 더 작은 사진으로 다시 올려 주세요.");
            assertThat(AnalysisFailureReason.TRANSIENT.userMessage())
                    .isEqualTo("일시적인 문제로 분석하지 못했어요. 잠시 후 다시 시도해 주세요.");
            assertThat(AnalysisFailureReason.UNSUPPORTED_IMAGE.userMessage())
                    .as("20MB 넘는 JPG 도 올 수 있으니 형식을 단정하지 않는다")
                    .doesNotContain("형식")
                    .contains("JPG나 PNG");
            assertThat(AnalysisFailureReason.TOO_MANY_IMAGES.userMessage())
                    .as("장수 상한은 설정값이라 문구에 숫자를 넣지 않는다")
                    .doesNotContainPattern("[0-9]");
        }

        @Test
        @DisplayName("처리 중 표시 TTL 은 40분으로, 큐 TTL 30분과 처리 최대 시간을 더한 것보다 길다")
        void guardOutlivesQueueTtlPlusProcessing() {
            assertThat(ProblemAnalysisDispatchGuard.TTL).isEqualTo(Duration.ofMinutes(40));
            Duration queueTtl = Duration.ofMillis(RabbitMQConfig.GPT_ANALYSIS_QUEUE_TTL_MS);
            // OpenAI 타임아웃 기본값을 직접 참조해, 타임아웃만 올리고 TTL 을 따라 올리지 않으면 이 테스트가 깨지게 한다
            Duration perAttempt = Duration.ofMillis(OpenAIHttpClientConfig.DEFAULT_CONNECT_TIMEOUT_MS
                    + OpenAIHttpClientConfig.DEFAULT_READ_TIMEOUT_MS);
            Duration backoff = Duration.ofSeconds(1 + 2);
            Duration maxProcessing = perAttempt.multipliedBy(RabbitRetryAttempts.MAX_ATTEMPTS).plus(backoff);
            assertThat(ProblemAnalysisDispatchGuard.TTL)
                    .as("S3 읽기 시간까지 버틸 여유가 있어야 한다")
                    .isGreaterThan(queueTtl.plus(maxProcessing).plusMinutes(3));
        }
    }
}
