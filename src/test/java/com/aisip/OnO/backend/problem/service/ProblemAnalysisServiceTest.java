package com.aisip.OnO.backend.problem.service;

import com.aisip.OnO.backend.common.exception.ApplicationException;
import com.aisip.OnO.backend.common.exception.ErrorCase;
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
import com.aisip.OnO.backend.util.ai.NonRetryableAnalysisException;
import com.aisip.OnO.backend.util.ai.ProblemAnalysisResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

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
            analysisFailureService.markFailed(problem.getId(), "이전 실패");

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
            given(openAIClient.analyzeImages(anyList())).willReturn(successResult());

            analysisService.analyzeProblemSync(problem.getId());

            ProblemAnalysis analysis = problemAnalysisRepository.findByProblemId(problem.getId()).orElseThrow();
            assertThat(analysis.getStatus()).isEqualTo(AnalysisStatus.COMPLETED);
            assertThat(analysis.getSubject()).isEqualTo("수학");
            assertThat(analysis.getKeyPoints())
                    .as("keyPoints 는 JSON 배열 문자열로 직렬화된다")
                    .contains("판별식");
        }

        @Test
        @DisplayName("PROBLEM_IMAGE 만 분석 대상으로 넘긴다")
        void sendsOnlyProblemImages() {
            Problem problem = saveProblem(owner.getId(), ownerRoot);
            saveImageData(problem, "https://s3/p.png", ProblemImageType.PROBLEM_IMAGE);
            saveImageData(problem, "https://s3/a.png", ProblemImageType.ANSWER_IMAGE);
            saveImageData(problem, "https://s3/s.png", ProblemImageType.SOLVE_IMAGE);
            saveSkippedAnalysis(problem);
            given(openAIClient.analyzeImages(anyList())).willReturn(successResult());

            analysisService.analyzeProblemSync(problem.getId());

            verify(openAIClient).analyzeImages(List.of("https://s3/p.png"));
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

            verify(openAIClient, never()).analyzeImages(anyList());
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
            given(openAIClient.analyzeImages(anyList()))
                    .willThrow(new NonRetryableAnalysisException("이미지에서 문제를 인식할 수 없습니다"));

            assertThatThrownBy(() -> analysisService.analyzeProblemSync(problem.getId()))
                    .as("재시도해도 소용없는 실패는 상위에서 DLQ 처리해야 하므로 그대로 전파된다")
                    .isInstanceOf(NonRetryableAnalysisException.class);

            ProblemAnalysis analysis = problemAnalysisRepository.findByProblemId(problem.getId()).orElseThrow();
            assertThat(analysis.getStatus()).isEqualTo(AnalysisStatus.FAILED);
            assertThat(analysis.getErrorMessage()).isEqualTo("이미지에서 문제를 인식할 수 없습니다");
        }

        @Test
        @DisplayName("OpenAI 가 400 을 내면 FAILED 로 기록하고 예외를 다시 던진다")
        void marksFailedOnBadRequest() {
            Problem problem = problemWithImage();
            given(openAIClient.analyzeImages(anyList()))
                    .willThrow(new HttpClientErrorException(HttpStatus.BAD_REQUEST, "Bad Request"));

            assertThatThrownBy(() -> analysisService.analyzeProblemSync(problem.getId()))
                    .isInstanceOf(HttpClientErrorException.class);

            assertThat(problemAnalysisRepository.findByProblemId(problem.getId()).orElseThrow().getStatus())
                    .isEqualTo(AnalysisStatus.FAILED);
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

            verify(openAIClient, never()).analyzeImages(anyList());

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

            assertThat(prep.imageUrls()).containsExactly("https://s3/p1.png");
            assertThat(prep.alreadyCompleted()).isFalse();
        }

        @Test
        @DisplayName("이미지가 하나도 없으면 빈 URL 목록을 준다")
        void fetchesEmptyImageList() {
            Problem problem = saveProblem(owner.getId(), ownerRoot);
            saveSkippedAnalysis(problem);

            assertThat(analysisDbService.fetchAnalysisPreparation(problem.getId()).imageUrls()).isEmpty();
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

            analysisFailureService.markFailed(problem.getId(), "OpenAI 400");

            ProblemAnalysis analysis = problemAnalysisRepository.findByProblemId(problem.getId()).orElseThrow();
            assertThat(analysis.getStatus()).isEqualTo(AnalysisStatus.FAILED);
            assertThat(analysis.getErrorMessage()).isEqualTo("OpenAI 400");
        }

        @Test
        @DisplayName("분석 레코드가 없으면 아무 일도 하지 않는다")
        void ignoresMissingAnalysisRow() {
            assertThatCode(() -> analysisFailureService.markFailed(999_999L, "사라진 문제"))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("에러 메시지가 null 이어도 상태는 FAILED 로 남는다")
        void acceptsNullErrorMessage() {
            Problem problem = saveProblem(owner.getId(), ownerRoot);
            saveSkippedAnalysis(problem);

            analysisFailureService.markFailed(problem.getId(), null);

            assertThat(problemAnalysisRepository.findByProblemId(problem.getId()).orElseThrow().getStatus())
                    .isEqualTo(AnalysisStatus.FAILED);
        }
    }
}
