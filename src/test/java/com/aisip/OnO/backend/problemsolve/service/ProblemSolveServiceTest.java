package com.aisip.OnO.backend.problemsolve.service;

import com.aisip.OnO.backend.common.exception.ApplicationException;
import com.aisip.OnO.backend.common.exception.ErrorCase;
import com.aisip.OnO.backend.problem.entity.Problem;
import com.aisip.OnO.backend.problem.exception.ProblemErrorCase;
import com.aisip.OnO.backend.problemsolve.ProblemSolveTestSupport;
import com.aisip.OnO.backend.problemsolve.dto.ProblemSolveRegisterDto;
import com.aisip.OnO.backend.problemsolve.dto.ProblemSolveResponseDto;
import com.aisip.OnO.backend.problemsolve.dto.ProblemSolveUpdateDto;
import com.aisip.OnO.backend.problemsolve.entity.AnswerStatus;
import com.aisip.OnO.backend.problemsolve.entity.ImprovementType;
import com.aisip.OnO.backend.problemsolve.entity.ProblemSolve;
import com.aisip.OnO.backend.problemsolve.exception.ProblemSolveErrorCase;
import com.aisip.OnO.backend.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@DisplayName("ProblemSolveService")
class ProblemSolveServiceTest extends ProblemSolveTestSupport {

    @Autowired
    private ProblemSolveService problemSolveService;

    private User user;
    private User other;
    private Problem problem;
    private Problem othersProblem;

    @BeforeEach
    void setUpFixtures() {
        user = fixtures.createUser();
        other = fixtures.createOtherUser();
        problem = saveProblem(user.getId());
        othersProblem = saveProblem(other.getId());
    }

    private void assertErrorCase(ErrorCase expected, Runnable action) {
        assertThatThrownBy(action::run)
                .isInstanceOf(ApplicationException.class)
                .extracting(thrown -> ((ApplicationException) thrown).getErrorCase())
                .as("에러 코드가 달라지면 앱이 다른 처리를 한다")
                .isEqualTo(expected);
    }

    private ProblemSolveRegisterDto registerDto(Long problemId, AnswerStatus status) {
        return new ProblemSolveRegisterDto(problemId, PRACTICED_AT, status, "회고", List.of(), 90);
    }

    @Nested
    @DisplayName("복습 기록 생성")
    class CreateProblemSolve {

        @Test
        @DisplayName("보낸 값 그대로 저장되고 id를 돌려준다")
        void createsSolveWithGivenValues() {
            Long solveId = problemSolveService.createProblemSolve(
                    new ProblemSolveRegisterDto(problem.getId(), PRACTICED_AT, AnswerStatus.PARTIAL, "계산에서 막혔다",
                            List.of(ImprovementType.FASTER_SOLVING, ImprovementType.BETTER_UNDERSTANDING), 300),
                    user.getId());

            ProblemSolveResponseDto saved = problemSolveService.getProblemSolve(solveId, user.getId());
            assertThat(saved.problemId()).isEqualTo(problem.getId());
            assertThat(saved.userId()).isEqualTo(user.getId());
            assertThat(saved.practicedAt()).isEqualTo(PRACTICED_AT);
            assertThat(saved.answerStatus()).isEqualTo(AnswerStatus.PARTIAL);
            assertThat(saved.reflection()).isEqualTo("계산에서 막혔다");
            assertThat(saved.improvements())
                    .as("개선 사항은 JSON 문자열로 저장했다가 다시 enum 으로 돌려줘야 한다")
                    .containsExactly(ImprovementType.FASTER_SOLVING, ImprovementType.BETTER_UNDERSTANDING);
            assertThat(saved.timeSpentSeconds()).isEqualTo(300);
            assertThat(saved.migratedFromLegacy()).isFalse();
        }

        @Test
        @DisplayName("개선 사항이 null 이거나 비어 있으면 빈 목록으로 돌려준다")
        void handlesEmptyImprovements() {
            Long withNull = problemSolveService.createProblemSolve(
                    new ProblemSolveRegisterDto(problem.getId(), PRACTICED_AT, AnswerStatus.WRONG, null, null, null),
                    user.getId());
            Long withEmpty = problemSolveService.createProblemSolve(
                    new ProblemSolveRegisterDto(problem.getId(), PRACTICED_AT.plusHours(1), AnswerStatus.WRONG, null, List.of(), null),
                    user.getId());

            assertThat(problemSolveService.getProblemSolve(withNull, user.getId()).improvements()).isEmpty();
            assertThat(problemSolveService.getProblemSolve(withEmpty, user.getId()).improvements()).isEmpty();
        }

        @Test
        @DisplayName("아주 긴 회고도 잘리지 않고 저장된다 - reflection 은 TEXT 컬럼이다")
        void storesLongReflection() {
            String longReflection = "가".repeat(3000);

            Long solveId = problemSolveService.createProblemSolve(
                    new ProblemSolveRegisterDto(problem.getId(), PRACTICED_AT, AnswerStatus.WRONG, longReflection, List.of(), 60),
                    user.getId());

            assertThat(problemSolveService.getProblemSolve(solveId, user.getId()).reflection())
                    .as("varchar(255) 였다면 여기서 Data truncation 으로 500이 난다")
                    .isEqualTo(longReflection);
        }

        @Test
        @DisplayName("다른 사용자의 문제에는 복습 기록을 남길 수 없다")
        void rejectsOtherUsersProblem() {
            assertErrorCase(ProblemErrorCase.PROBLEM_USER_UNMATCHED,
                    () -> problemSolveService.createProblemSolve(registerDto(othersProblem.getId(), AnswerStatus.CORRECT), user.getId()));

            assertThat(problemSolveRepository.countByProblemId(othersProblem.getId())).isZero();
        }

        @Test
        @DisplayName("없는 문제 id면 PROBLEM_NOT_FOUND 다")
        void rejectsUnknownProblem() {
            assertErrorCase(ProblemErrorCase.PROBLEM_NOT_FOUND,
                    () -> problemSolveService.createProblemSolve(registerDto(999_999L, AnswerStatus.CORRECT), user.getId()));
        }

        @Test
        @DisplayName("problemId 가 없으면 500이 아니라 400으로 거절한다")
        void rejectsNullProblemId() {
            assertErrorCase(ProblemSolveErrorCase.PROBLEM_SOLVE_INVALID_INPUT,
                    () -> problemSolveService.createProblemSolve(registerDto(null, AnswerStatus.CORRECT), user.getId()));
        }

        @Test
        @DisplayName("answerStatus 가 없으면 500이 아니라 400으로 거절한다")
        void rejectsNullAnswerStatus() {
            assertErrorCase(ProblemSolveErrorCase.PROBLEM_SOLVE_INVALID_INPUT,
                    () -> problemSolveService.createProblemSolve(registerDto(problem.getId(), null), user.getId()));

            assertThat(problemSolveRepository.countByProblemId(problem.getId())).isZero();
        }

        @Test
        @DisplayName("요청 자체가 null 이어도 400으로 거절한다")
        void rejectsNullDto() {
            assertErrorCase(ProblemSolveErrorCase.PROBLEM_SOLVE_INVALID_INPUT,
                    () -> problemSolveService.createProblemSolve(null, user.getId()));
        }

        @Test
        @DisplayName("practicedAt 을 안 보내면 지금 푼 것으로 저장한다 - not-null 컬럼이라 예전에는 500이었다")
        void defaultsPracticedAtToNow() {
            Long solveId = problemSolveService.createProblemSolve(
                    new ProblemSolveRegisterDto(problem.getId(), null, AnswerStatus.CORRECT, null, List.of(), null),
                    user.getId());

            assertThat(problemSolveService.getProblemSolve(solveId, user.getId()).practicedAt())
                    .isNotNull()
                    .isAfter(java.time.LocalDateTime.now(ZoneId.of("Asia/Seoul")).minusMinutes(5));
        }

        @Test
        @DisplayName("오답으로 기록하면 문제의 다음 복습일이 내일로, 연속 정답 수는 0으로 초기화된다")
        void resetsReviewScheduleOnWrongAnswer() {
            problemSolveService.createProblemSolve(registerDto(problem.getId(), AnswerStatus.WRONG), user.getId());

            Problem updated = problemRepository.findById(problem.getId()).orElseThrow();
            assertThat(updated.getNextReviewAt()).isEqualTo(LocalDate.now(ZoneId.of("Asia/Seoul")).plusDays(1));
            assertThat(updated.getReviewInterval()).isEqualTo(1);
            assertThat(updated.getConsecutiveCorrectCount()).isZero();
        }

        @Test
        @DisplayName("연속 3회 정답이면 마스터로 보고 다음 복습일을 비운다")
        void marksProblemAsMasteredAfterThreeCorrectAnswers() {
            for (int i = 0; i < 3; i++) {
                problemSolveService.createProblemSolve(
                        new ProblemSolveRegisterDto(problem.getId(), PRACTICED_AT.plusDays(i), AnswerStatus.CORRECT, null, List.of(), null),
                        user.getId());
            }

            Problem updated = problemRepository.findById(problem.getId()).orElseThrow();
            assertThat(updated.getConsecutiveCorrectCount()).isEqualTo(3);
            assertThat(updated.getNextReviewAt())
                    .as("마스터한 문제는 더 이상 복습 대상이 아니다")
                    .isNull();
        }

        @Test
        @DisplayName("복습 기록을 남겨도 실제 푸시 발송은 일어나지 않는다")
        void neverSendsPushNotification() {
            problemSolveService.createProblemSolve(registerDto(problem.getId(), AnswerStatus.CORRECT), user.getId());

            verify(fcmService, never()).sendNotificationToAllUserDevice(any(), any());
        }
    }

    @Nested
    @DisplayName("복습 기록 조회")
    class GetProblemSolve {

        @Test
        @DisplayName("자기 기록은 조회된다")
        void returnsOwnSolve() {
            ProblemSolve solve = saveSolve(problem, user.getId(), PRACTICED_AT);

            assertThat(problemSolveService.getProblemSolve(solve.getId(), user.getId()).problemSolveId())
                    .isEqualTo(solve.getId());
        }

        @Test
        @DisplayName("다른 사용자의 기록은 조회할 수 없다")
        void rejectsOtherUsersSolve() {
            ProblemSolve othersSolve = saveSolve(othersProblem, other.getId(), PRACTICED_AT);

            assertErrorCase(ProblemSolveErrorCase.PROBLEM_SOLVE_USER_UNMATCHED,
                    () -> problemSolveService.getProblemSolve(othersSolve.getId(), user.getId()));
        }

        @Test
        @DisplayName("없는 기록 id면 PROBLEM_SOLVE_NOT_FOUND 다")
        void rejectsUnknownSolve() {
            assertErrorCase(ProblemSolveErrorCase.PROBLEM_SOLVE_NOT_FOUND,
                    () -> problemSolveService.getProblemSolve(999_999L, user.getId()));
        }

        @Test
        @DisplayName("삭제된 기록은 조회되지 않는다")
        void rejectsDeletedSolve() {
            ProblemSolve solve = saveSolve(problem, user.getId(), PRACTICED_AT);
            problemSolveService.deleteProblemSolve(solve.getId(), user.getId());

            assertErrorCase(ProblemSolveErrorCase.PROBLEM_SOLVE_NOT_FOUND,
                    () -> problemSolveService.getProblemSolve(solve.getId(), user.getId()));
        }
    }

    @Nested
    @DisplayName("문제별 복습 기록 조회")
    class GetProblemSolvesByProblemId {

        @Test
        @DisplayName("최근에 푼 순서로 돌려준다")
        void returnsSolvesOrderedByPracticedAtDesc() {
            saveSolve(problem, user.getId(), PRACTICED_AT);
            saveSolve(problem, user.getId(), PRACTICED_AT.plusDays(2));
            saveSolve(problem, user.getId(), PRACTICED_AT.plusDays(1));

            assertThat(problemSolveService.getProblemSolvesByProblemId(problem.getId(), user.getId()))
                    .extracting(ProblemSolveResponseDto::practicedAt)
                    .containsExactly(PRACTICED_AT.plusDays(2), PRACTICED_AT.plusDays(1), PRACTICED_AT);
        }

        @Test
        @DisplayName("다른 사용자의 문제는 조회할 수 없다")
        void rejectsOtherUsersProblem() {
            saveSolve(othersProblem, other.getId(), PRACTICED_AT);

            assertErrorCase(ProblemErrorCase.PROBLEM_USER_UNMATCHED,
                    () -> problemSolveService.getProblemSolvesByProblemId(othersProblem.getId(), user.getId()));
        }

        @Test
        @DisplayName("없는 문제면 PROBLEM_NOT_FOUND 다")
        void rejectsUnknownProblem() {
            assertErrorCase(ProblemErrorCase.PROBLEM_NOT_FOUND,
                    () -> problemSolveService.getProblemSolvesByProblemId(999_999L, user.getId()));
        }

        @Test
        @DisplayName("기록이 없으면 빈 목록이다")
        void returnsEmptyList() {
            assertThat(problemSolveService.getProblemSolvesByProblemId(problem.getId(), user.getId())).isEmpty();
        }

        @Test
        @DisplayName("개수 조회도 소유권을 확인한다")
        void countChecksOwnership() {
            saveSolve(problem, user.getId(), PRACTICED_AT);

            assertThat(problemSolveService.getProblemSolveCountByProblemId(problem.getId(), user.getId())).isEqualTo(1L);
            assertErrorCase(ProblemErrorCase.PROBLEM_USER_UNMATCHED,
                    () -> problemSolveService.getProblemSolveCountByProblemId(problem.getId(), other.getId()));
        }
    }

    @Nested
    @DisplayName("사용자별 복습 기록 조회")
    class GetUserProblemSolves {

        @Test
        @DisplayName("자기 기록만 최근 순으로 돌려준다")
        void returnsOwnSolvesOnly() {
            saveSolve(problem, user.getId(), PRACTICED_AT);
            saveSolve(problem, user.getId(), PRACTICED_AT.plusDays(1));
            saveSolve(othersProblem, other.getId(), PRACTICED_AT.plusDays(5));

            List<ProblemSolveResponseDto> solves = problemSolveService.getUserProblemSolves(user.getId());

            assertThat(solves)
                    .as("남의 복습 기록이 섞이면 오답노트 통계가 전부 틀어진다")
                    .hasSize(2)
                    .allSatisfy(solve -> assertThat(solve.userId()).isEqualTo(user.getId()));
            assertThat(solves).extracting(ProblemSolveResponseDto::practicedAt)
                    .containsExactly(PRACTICED_AT.plusDays(1), PRACTICED_AT);
        }

        @Test
        @DisplayName("총 개수도 사용자별로 센다")
        void countsPerUser() {
            saveSolve(problem, user.getId(), PRACTICED_AT);
            saveSolve(othersProblem, other.getId(), PRACTICED_AT);

            assertThat(problemSolveService.getUserProblemSolveCount(user.getId())).isEqualTo(1L);
            assertThat(problemSolveService.getUserProblemSolveCount(other.getId())).isEqualTo(1L);
        }

        @Test
        @DisplayName("기록이 없으면 0이다")
        void countsZero() {
            assertThat(problemSolveService.getUserProblemSolveCount(user.getId())).isZero();
        }
    }

    @Nested
    @DisplayName("복습 기록 수정")
    class UpdateProblemSolve {

        @Test
        @DisplayName("자기 기록의 채점 결과와 회고를 바꾼다")
        void updatesOwnSolve() {
            ProblemSolve solve = saveSolve(problem, user.getId(), PRACTICED_AT);

            problemSolveService.updateProblemSolve(
                    new ProblemSolveUpdateDto(solve.getId(), AnswerStatus.CORRECT, "이제 이해했다",
                            List.of(ImprovementType.NO_REPEAT_MISTAKE), 45),
                    user.getId());

            ProblemSolveResponseDto updated = problemSolveService.getProblemSolve(solve.getId(), user.getId());
            assertThat(updated.answerStatus()).isEqualTo(AnswerStatus.CORRECT);
            assertThat(updated.reflection()).isEqualTo("이제 이해했다");
            assertThat(updated.improvements()).containsExactly(ImprovementType.NO_REPEAT_MISTAKE);
            assertThat(updated.timeSpentSeconds()).isEqualTo(45);
        }

        @Test
        @DisplayName("개선 사항을 비우면 빈 목록이 된다")
        void clearsImprovements() {
            ProblemSolve solve = saveSolve(problem, user.getId(), PRACTICED_AT);
            problemSolveService.updateProblemSolve(
                    new ProblemSolveUpdateDto(solve.getId(), AnswerStatus.CORRECT, null,
                            List.of(ImprovementType.FASTER_SOLVING), null),
                    user.getId());

            problemSolveService.updateProblemSolve(
                    new ProblemSolveUpdateDto(solve.getId(), AnswerStatus.CORRECT, null, List.of(), null),
                    user.getId());

            assertThat(problemSolveService.getProblemSolve(solve.getId(), user.getId()).improvements()).isEmpty();
        }

        @Test
        @DisplayName("다른 사용자의 기록은 수정할 수 없고 값도 그대로다")
        void rejectsOtherUsersSolve() {
            ProblemSolve othersSolve = saveSolve(othersProblem, other.getId(), PRACTICED_AT, AnswerStatus.WRONG);

            assertErrorCase(ProblemSolveErrorCase.PROBLEM_SOLVE_USER_UNMATCHED,
                    () -> problemSolveService.updateProblemSolve(
                            new ProblemSolveUpdateDto(othersSolve.getId(), AnswerStatus.CORRECT, "남의 기록 조작", List.of(), 1),
                            user.getId()));

            assertThat(problemSolveService.getProblemSolve(othersSolve.getId(), other.getId()).answerStatus())
                    .isEqualTo(AnswerStatus.WRONG);
        }

        @Test
        @DisplayName("없는 기록이면 PROBLEM_SOLVE_NOT_FOUND 다")
        void rejectsUnknownSolve() {
            assertErrorCase(ProblemSolveErrorCase.PROBLEM_SOLVE_NOT_FOUND,
                    () -> problemSolveService.updateProblemSolve(
                            new ProblemSolveUpdateDto(999_999L, AnswerStatus.CORRECT, null, List.of(), null),
                            user.getId()));
        }

        @Test
        @DisplayName("id 나 채점 결과가 비어 있으면 500이 아니라 400으로 거절한다")
        void rejectsNullInput() {
            assertErrorCase(ProblemSolveErrorCase.PROBLEM_SOLVE_INVALID_INPUT,
                    () -> problemSolveService.updateProblemSolve(
                            new ProblemSolveUpdateDto(null, AnswerStatus.CORRECT, null, List.of(), null), user.getId()));

            ProblemSolve solve = saveSolve(problem, user.getId(), PRACTICED_AT);
            assertErrorCase(ProblemSolveErrorCase.PROBLEM_SOLVE_INVALID_INPUT,
                    () -> problemSolveService.updateProblemSolve(
                            new ProblemSolveUpdateDto(solve.getId(), null, null, List.of(), null), user.getId()));
            assertErrorCase(ProblemSolveErrorCase.PROBLEM_SOLVE_INVALID_INPUT,
                    () -> problemSolveService.updateProblemSolve(null, user.getId()));
        }
    }

    @Nested
    @DisplayName("복습 기록 삭제")
    class DeleteProblemSolve {

        @Test
        @DisplayName("자기 기록을 지우면 조회되지 않고 개수도 줄어든다")
        void deletesOwnSolve() {
            ProblemSolve solve = saveSolve(problem, user.getId(), PRACTICED_AT);
            saveSolve(problem, user.getId(), PRACTICED_AT.plusDays(1));

            problemSolveService.deleteProblemSolve(solve.getId(), user.getId());

            assertThat(problemSolveService.getUserProblemSolveCount(user.getId())).isEqualTo(1L);
            assertThat(countRowsIncludingDeleted(problem.getId()))
                    .as("소프트 삭제라 행 자체는 남는다")
                    .isEqualTo(2);
        }

        @Test
        @DisplayName("다른 사용자의 기록은 지울 수 없고 그대로 남는다")
        void rejectsOtherUsersSolve() {
            ProblemSolve othersSolve = saveSolve(othersProblem, other.getId(), PRACTICED_AT);

            assertErrorCase(ProblemSolveErrorCase.PROBLEM_SOLVE_USER_UNMATCHED,
                    () -> problemSolveService.deleteProblemSolve(othersSolve.getId(), user.getId()));

            assertThat(problemSolveService.getUserProblemSolveCount(other.getId())).isEqualTo(1L);
        }

        @Test
        @DisplayName("없는 기록이면 PROBLEM_SOLVE_NOT_FOUND 다")
        void rejectsUnknownSolve() {
            assertErrorCase(ProblemSolveErrorCase.PROBLEM_SOLVE_NOT_FOUND,
                    () -> problemSolveService.deleteProblemSolve(999_999L, user.getId()));
        }

        @Test
        @DisplayName("이미지가 달린 기록을 지워도 예외 없이 끝난다")
        void deletesSolveWithImages() {
            ProblemSolve solve = saveSolve(problem, user.getId(), PRACTICED_AT);
            saveImage(solve, "https://test-ono-bucket.s3.amazonaws.com/a.png", 0);
            saveImage(solve, "https://test-ono-bucket.s3.amazonaws.com/b.png", 1);

            problemSolveService.deleteProblemSolve(solve.getId(), user.getId());

            assertErrorCase(ProblemSolveErrorCase.PROBLEM_SOLVE_NOT_FOUND,
                    () -> problemSolveService.getProblemSolve(solve.getId(), user.getId()));
        }

        @Test
        @DisplayName("문제의 모든 복습 기록을 한 번에 지운다")
        void deletesAllSolvesOfProblem() {
            saveSolve(problem, user.getId(), PRACTICED_AT);
            saveSolve(problem, user.getId(), PRACTICED_AT.plusDays(1));
            saveSolve(othersProblem, other.getId(), PRACTICED_AT);

            problemSolveService.deleteAllProblemSolvesByProblemId(problem.getId());

            assertThat(problemSolveRepository.countByProblemId(problem.getId())).isZero();
            assertThat(problemSolveRepository.countByProblemId(othersProblem.getId()))
                    .as("다른 문제의 기록까지 지우면 안 된다")
                    .isEqualTo(1L);
        }
    }

    @Nested
    @DisplayName("복습 기록 이미지")
    class ProblemSolveImages {

        @Test
        @DisplayName("업로드한 이미지가 순서대로 저장된다")
        void uploadsImagesInOrder() {
            ProblemSolve solve = saveSolve(problem, user.getId(), PRACTICED_AT);
            given(fileUploadService.uploadFileToS3(any(MultipartFile.class)))
                    .willReturn("https://test-ono-bucket.s3.amazonaws.com/first.png",
                            "https://test-ono-bucket.s3.amazonaws.com/second.png");

            problemSolveService.uploadProblemSolveImages(solve.getId(), user.getId(), List.of(
                    new MockMultipartFile("images", "a.png", "image/png", "a".getBytes()),
                    new MockMultipartFile("images", "b.png", "image/png", "b".getBytes())));

            assertThat(problemSolveService.getProblemSolve(solve.getId(), user.getId()).imageUrls()).hasSize(2);
        }

        @Test
        @DisplayName("남의 기록에는 이미지를 올릴 수 없고 S3 업로드도 시도하지 않는다")
        void rejectsUploadToOtherUsersSolve() {
            ProblemSolve othersSolve = saveSolve(othersProblem, other.getId(), PRACTICED_AT);

            assertErrorCase(ProblemSolveErrorCase.PROBLEM_SOLVE_USER_UNMATCHED,
                    () -> problemSolveService.uploadProblemSolveImages(othersSolve.getId(), user.getId(),
                            List.of(new MockMultipartFile("images", "a.png", "image/png", "a".getBytes()))));

            verify(fileUploadService, never()).uploadFileToS3(any());
        }

        @Test
        @DisplayName("빈 이미지 목록이면 아무것도 저장하지 않는다")
        void handlesEmptyUpload() {
            ProblemSolve solve = saveSolve(problem, user.getId(), PRACTICED_AT);

            problemSolveService.uploadProblemSolveImages(solve.getId(), user.getId(), List.of());

            assertThat(problemSolveService.getProblemSolve(solve.getId(), user.getId()).imageUrls()).isEmpty();
        }

        @Test
        @DisplayName("이미지 목록이 null 이면 500이 아니라 400이다")
        void rejectsNullUploadList() {
            ProblemSolve solve = saveSolve(problem, user.getId(), PRACTICED_AT);

            assertErrorCase(ProblemSolveErrorCase.PROBLEM_SOLVE_INVALID_INPUT,
                    () -> problemSolveService.uploadProblemSolveImages(solve.getId(), user.getId(), null));
        }

        @Test
        @DisplayName("presigned 방식으로 넘어온 URL 은 검증 후 기존 이미지 뒤에 이어 붙는다")
        void appendsImageUrlsAfterExistingOnes() {
            ProblemSolve solve = saveSolve(problem, user.getId(), PRACTICED_AT);
            saveImage(solve, "https://test-ono-bucket.s3.amazonaws.com/existing.png", 0);

            problemSolveService.addImageUrls(solve.getId(), user.getId(),
                    List.of("https://test-ono-bucket.s3.amazonaws.com/new.png"));

            assertThat(problemSolveService.getProblemSolve(solve.getId(), user.getId()).imageUrls())
                    .as("imageOrder 가 겹치면 이미지 순서가 뒤섞인다")
                    .containsExactly("https://test-ono-bucket.s3.amazonaws.com/existing.png",
                            "https://test-ono-bucket.s3.amazonaws.com/new.png");
            verify(fileUploadService).validateS3Url("https://test-ono-bucket.s3.amazonaws.com/new.png");
        }

        @Test
        @DisplayName("남의 기록에는 이미지 URL 을 붙일 수 없다")
        void rejectsAddingUrlsToOtherUsersSolve() {
            ProblemSolve othersSolve = saveSolve(othersProblem, other.getId(), PRACTICED_AT);

            assertErrorCase(ProblemSolveErrorCase.PROBLEM_SOLVE_USER_UNMATCHED,
                    () -> problemSolveService.addImageUrls(othersSolve.getId(), user.getId(),
                            List.of("https://test-ono-bucket.s3.amazonaws.com/new.png")));
        }

        @Test
        @DisplayName("URL 목록이 null 이면 500이 아니라 400이다")
        void rejectsNullImageUrls() {
            ProblemSolve solve = saveSolve(problem, user.getId(), PRACTICED_AT);

            assertErrorCase(ProblemSolveErrorCase.PROBLEM_SOLVE_INVALID_INPUT,
                    () -> problemSolveService.addImageUrls(solve.getId(), user.getId(), null));
        }

        @Test
        @DisplayName("없는 기록에 이미지를 붙이면 PROBLEM_SOLVE_NOT_FOUND 다")
        void rejectsUnknownSolve() {
            assertErrorCase(ProblemSolveErrorCase.PROBLEM_SOLVE_NOT_FOUND,
                    () -> problemSolveService.addImageUrls(999_999L, user.getId(),
                            List.of("https://test-ono-bucket.s3.amazonaws.com/new.png")));
        }
    }

    @Nested
    @DisplayName("관리자 조회")
    class AdminAccess {

        @Test
        @DisplayName("관리자 조회는 소유자 검증 없이 문제의 모든 기록을 돌려준다")
        void returnsAllSolvesWithoutOwnershipCheck() {
            saveSolve(othersProblem, other.getId(), PRACTICED_AT);

            assertThat(problemSolveService.getAdminProblemSolvesByProblemId(othersProblem.getId()))
                    .as("관리자 전용 경로라 /admin 권한으로만 접근한다")
                    .hasSize(1);
        }
    }

    @Nested
    @DisplayName("사용자 간 격리 종합")
    class UserIsolation {

        @Test
        @DisplayName("한 사용자의 기록은 다른 사용자의 어떤 조회에도 나타나지 않는다")
        void keepsSolvesFullyIsolated() {
            ProblemSolve mine = saveSolve(problem, user.getId(), PRACTICED_AT);
            List<Long> othersView = problemSolveService.getUserProblemSolves(other.getId()).stream()
                    .map(ProblemSolveResponseDto::problemSolveId)
                    .toList();

            assertThat(othersView).doesNotContain(mine.getId());
            assertThat(problemSolveService.getUserProblemSolveCount(other.getId())).isZero();
            assertErrorCase(ProblemSolveErrorCase.PROBLEM_SOLVE_USER_UNMATCHED,
                    () -> problemSolveService.getProblemSolve(mine.getId(), other.getId()));
            assertErrorCase(ProblemSolveErrorCase.PROBLEM_SOLVE_USER_UNMATCHED,
                    () -> problemSolveService.deleteProblemSolve(mine.getId(), other.getId()));
            assertErrorCase(ProblemSolveErrorCase.PROBLEM_SOLVE_USER_UNMATCHED,
                    () -> problemSolveService.updateProblemSolve(
                            new ProblemSolveUpdateDto(mine.getId(), AnswerStatus.CORRECT, null, List.of(), null),
                            other.getId()));
        }
    }
}
