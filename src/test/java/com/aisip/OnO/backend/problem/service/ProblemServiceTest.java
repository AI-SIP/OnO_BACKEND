package com.aisip.OnO.backend.problem.service;

import com.aisip.OnO.backend.common.exception.ApplicationException;
import com.aisip.OnO.backend.common.exception.ErrorCase;
import com.aisip.OnO.backend.common.ratelimit.RateLimitService;
import com.aisip.OnO.backend.common.response.CursorPageResponse;
import com.aisip.OnO.backend.folder.entity.Folder;
import com.aisip.OnO.backend.folder.exception.FolderErrorCase;
import com.aisip.OnO.backend.problem.dto.AddProblemImageUrlsRequest;
import com.aisip.OnO.backend.problem.dto.ProblemRegisterDto;
import com.aisip.OnO.backend.problem.dto.ProblemRegisterV2BatchDto;
import com.aisip.OnO.backend.problem.dto.ProblemRegisterV2Dto;
import com.aisip.OnO.backend.problem.dto.ProblemResponseDto;
import com.aisip.OnO.backend.problem.dto.ProblemTagUpdateDto;
import com.aisip.OnO.backend.problem.dto.ReviewDueResponseDto;
import com.aisip.OnO.backend.problem.entity.AnalysisStatus;
import com.aisip.OnO.backend.problem.entity.Problem;
import com.aisip.OnO.backend.problem.entity.ProblemAnalysis;
import com.aisip.OnO.backend.problem.entity.ProblemImageType;
import com.aisip.OnO.backend.problem.exception.ProblemErrorCase;
import com.aisip.OnO.backend.problem.reminder.ProblemReviewReminderRepository;
import com.aisip.OnO.backend.problem.reminder.ProblemReviewReminderStatus;
import com.aisip.OnO.backend.problem.support.ProblemTestSupport;
import com.aisip.OnO.backend.tag.entity.Tag;
import com.aisip.OnO.backend.tag.exception.TagErrorCase;
import com.aisip.OnO.backend.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * ProblemService 통합 테스트.
 *
 * <p>이 서비스가 지키는 가장 중요한 불변식은 "사용자는 자기 데이터에만 접근할 수 있다"이므로
 * 조회·수정·삭제·이동 모든 경로에 대해 남의 데이터 접근 케이스를 함께 검증한다.
 */
@DisplayName("ProblemService")
class ProblemServiceTest extends ProblemTestSupport {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    @Autowired
    private ProblemService problemService;

    @Autowired
    private ProblemReviewReminderRepository reminderRepository;

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

    // ════════════════════════════ 조회 ════════════════════════════

    @Nested
    @DisplayName("문제 단건 조회")
    class FindProblem {

        @Test
        @DisplayName("본인이 등록한 문제를 조회하면 저장한 내용이 그대로 나온다")
        void returnsOwnProblem() {
            Problem problem = saveProblem(owner.getId(), ownerRoot, "이차방정식 실수", "수학의 정석 p.31");

            ProblemResponseDto response = problemService.findProblem(problem.getId(), owner.getId());

            assertThat(response.problemId()).isEqualTo(problem.getId());
            assertThat(response.memo()).isEqualTo("이차방정식 실수");
            assertThat(response.reference()).isEqualTo("수학의 정석 p.31");
            assertThat(response.folderId()).isEqualTo(ownerRoot.getId());
        }

        @Test
        @DisplayName("다른 사용자의 문제는 조회할 수 없다")
        void rejectsOtherUsersProblem() {
            Problem othersProblem = saveProblem(intruder.getId(), intruderRoot);

            assertThatThrownBy(() -> problemService.findProblem(othersProblem.getId(), owner.getId()))
                    .as("남의 오답노트가 새어 나가면 안 된다")
                    .isInstanceOf(ApplicationException.class)
                    .extracting(ProblemServiceTest::errorCaseOf)
                    .isEqualTo(ProblemErrorCase.PROBLEM_USER_UNMATCHED);
        }

        @Test
        @DisplayName("없는 문제 ID로 조회하면 PROBLEM_NOT_FOUND")
        void rejectsUnknownProblemId() {
            assertThatThrownBy(() -> problemService.findProblem(999_999L, owner.getId()))
                    .isInstanceOf(ApplicationException.class)
                    .extracting(ProblemServiceTest::errorCaseOf)
                    .isEqualTo(ProblemErrorCase.PROBLEM_NOT_FOUND);
        }

        @Test
        @DisplayName("삭제된 문제는 조회되지 않는다")
        void rejectsDeletedProblem() {
            Problem problem = saveProblem(owner.getId(), ownerRoot);
            problemService.deleteProblem(problem.getId(), owner.getId());

            assertThatThrownBy(() -> problemService.findProblem(problem.getId(), owner.getId()))
                    .isInstanceOf(ApplicationException.class)
                    .extracting(ProblemServiceTest::errorCaseOf)
                    .isEqualTo(ProblemErrorCase.PROBLEM_NOT_FOUND);
        }

        @Test
        @DisplayName("이미지가 함께 조회된다")
        void returnsImageData() {
            Problem problem = saveProblem(owner.getId(), ownerRoot);
            saveImageData(problem, "https://s3/problem.png", ProblemImageType.PROBLEM_IMAGE);
            saveImageData(problem, "https://s3/answer.png", ProblemImageType.ANSWER_IMAGE);

            ProblemResponseDto response = problemService.findProblem(problem.getId(), owner.getId());

            assertThat(response.imageUrlList())
                    .as("문제 이미지와 정답 이미지가 모두 내려가야 한다")
                    .hasSize(2)
                    .extracting(dto -> dto.problemImageType())
                    .containsExactlyInAnyOrder(ProblemImageType.PROBLEM_IMAGE, ProblemImageType.ANSWER_IMAGE);
        }

        @Test
        @DisplayName("findProblemEntity 는 소유자가 다르면 PROBLEM_USER_UNMATCHED")
        void findProblemEntityChecksOwner() {
            Problem othersProblem = saveProblem(intruder.getId(), intruderRoot);

            assertThatThrownBy(() -> problemService.findProblemEntity(othersProblem.getId(), owner.getId()))
                    .isInstanceOf(ApplicationException.class)
                    .extracting(ProblemServiceTest::errorCaseOf)
                    .isEqualTo(ProblemErrorCase.PROBLEM_USER_UNMATCHED);
        }

        @Test
        @DisplayName("findProblemForAdmin 은 소유자와 무관하게 조회된다")
        void adminLookupIgnoresOwner() {
            Problem othersProblem = saveProblem(intruder.getId(), intruderRoot, "관리자 조회 대상", null);

            ProblemResponseDto response = problemService.findProblemForAdmin(othersProblem.getId());

            assertThat(response.memo()).isEqualTo("관리자 조회 대상");
        }
    }

    @Nested
    @DisplayName("문제 목록 조회")
    class FindProblemList {

        @Test
        @DisplayName("본인 문제만 조회된다 - 다른 사용자 문제는 섞이지 않는다")
        void returnsOnlyOwnProblems() {
            saveProblem(owner.getId(), ownerRoot, "내 문제1", null);
            saveProblem(owner.getId(), ownerRoot, "내 문제2", null);
            saveProblem(intruder.getId(), intruderRoot, "남의 문제", null);

            List<ProblemResponseDto> problems = problemService.findUserProblems(owner.getId());

            assertThat(problems)
                    .as("사용자별 격리")
                    .hasSize(2)
                    .extracting(ProblemResponseDto::memo)
                    .containsExactlyInAnyOrder("내 문제1", "내 문제2");
        }

        @Test
        @DisplayName("문제가 없으면 빈 리스트를 반환한다")
        void returnsEmptyListWhenNoProblem() {
            assertThat(problemService.findUserProblems(owner.getId())).isEmpty();
        }

        @Test
        @DisplayName("폴더 내 문제 목록은 해당 폴더 문제만 반환한다")
        void returnsFolderProblems() {
            Folder subFolder = fixtures.createFolder(owner.getId(), "미적분", ownerRoot);
            saveProblem(owner.getId(), ownerRoot, "루트 문제", null);
            saveProblem(owner.getId(), subFolder, "미적분 문제", null);

            List<ProblemResponseDto> problems = problemService.findFolderProblemList(subFolder.getId(), owner.getId());

            assertThat(problems)
                    .hasSize(1)
                    .extracting(ProblemResponseDto::memo)
                    .containsExactly("미적분 문제");
        }

        @Test
        @DisplayName("다른 사용자의 폴더 내 문제는 조회할 수 없다")
        void rejectsOtherUsersFolder() {
            saveProblem(intruder.getId(), intruderRoot);

            assertThatThrownBy(() -> problemService.findFolderProblemList(intruderRoot.getId(), owner.getId()))
                    .isInstanceOf(ApplicationException.class)
                    .extracting(ProblemServiceTest::errorCaseOf)
                    .isEqualTo(FolderErrorCase.FOLDER_USER_UNMATCHED);
        }

        @Test
        @DisplayName("없는 폴더의 문제를 조회하면 FOLDER_NOT_FOUND")
        void rejectsUnknownFolder() {
            assertThatThrownBy(() -> problemService.findFolderProblemList(999_999L, owner.getId()))
                    .isInstanceOf(ApplicationException.class)
                    .extracting(ProblemServiceTest::errorCaseOf)
                    .isEqualTo(FolderErrorCase.FOLDER_NOT_FOUND);
        }

        @Test
        @DisplayName("문제 개수는 본인 것만 센다")
        void countsOnlyOwnProblems() {
            saveProblem(owner.getId(), ownerRoot);
            saveProblem(owner.getId(), ownerRoot);
            saveProblem(intruder.getId(), intruderRoot);

            assertThat(problemService.findProblemCountByUser(owner.getId())).isEqualTo(2L);
            assertThat(problemService.findProblemCountByUser(intruder.getId())).isEqualTo(1L);
        }

        @Test
        @DisplayName("전체 문제 조회는 모든 사용자 문제를 반환한다 (관리자용)")
        void findAllProblemsReturnsEveryUsersProblem() {
            saveProblem(owner.getId(), ownerRoot);
            saveProblem(intruder.getId(), intruderRoot);

            assertThat(problemService.findAllProblems()).hasSize(2);
            assertThat(problemService.countAllProblems()).isEqualTo(2L);
        }
    }

    // ════════════════════════════ 등록 ════════════════════════════

    @Nested
    @DisplayName("문제 등록 (v1)")
    class RegisterProblem {

        @Test
        @DisplayName("문제를 등록하면 폴더에 연결되고 복습 스케줄이 오늘로 초기화된다")
        void registersProblemWithInitialReviewSchedule() {
            Long problemId = problemService.registerProblem(
                    new ProblemRegisterDto(null, "메모", "출처", ownerRoot.getId(), null),
                    owner.getId()
            );

            Problem saved = problemRepository.findById(problemId).orElseThrow();
            assertThat(saved.getUserId()).isEqualTo(owner.getId());
            assertThat(saved.getFolder().getId()).isEqualTo(ownerRoot.getId());
            assertThat(saved.getNextReviewAt())
                    .as("등록 직후에는 오늘이 복습 예정일이다")
                    .isEqualTo(LocalDate.now(SEOUL));
            assertThat(saved.getReviewInterval()).isEqualTo(1);
            assertThat(saved.getConsecutiveCorrectCount()).isZero();
        }

        @Test
        @DisplayName("등록과 동시에 NOT_STARTED 분석 레코드가 생성된다")
        void createsSkippedAnalysis() {
            Long problemId = problemService.registerProblem(
                    new ProblemRegisterDto(null, "메모", null, ownerRoot.getId(), null),
                    owner.getId()
            );

            ProblemAnalysis analysis = problemAnalysisRepository.findByProblemId(problemId).orElseThrow();
            assertThat(analysis.getStatus()).isEqualTo(AnalysisStatus.NOT_STARTED);
        }

        @ParameterizedTest(name = "memo {0}자")
        @ValueSource(ints = {0, 1, 254, 255, 256, 999, 1000})
        @DisplayName("허용 범위 안의 메모 길이는 잘리지 않고 저장된다")
        void savesMemoWithinLimit(int length) {
            String memo = "가".repeat(length);

            Long problemId = problemService.registerProblem(
                    new ProblemRegisterDto(null, memo, null, ownerRoot.getId(), null),
                    owner.getId()
            );

            assertThat(problemService.findProblem(problemId, owner.getId()).memo())
                    .as("varchar(255) 경계(%d자)에서 Data truncation 이 나면 안 된다", length)
                    .hasSize(length);
        }

        @Test
        @DisplayName("1001자 메모는 500이 아니라 400(PROBLEM_MEMO_TOO_LONG)으로 거절한다")
        void rejectsMemoOverLimit() {
            String memo = "가".repeat(1001);

            assertThatThrownBy(() -> problemService.registerProblem(
                    new ProblemRegisterDto(null, memo, null, ownerRoot.getId(), null),
                    owner.getId()
            ))
                    .isInstanceOf(ApplicationException.class)
                    .extracting(ProblemServiceTest::errorCaseOf)
                    .isEqualTo(ProblemErrorCase.PROBLEM_MEMO_TOO_LONG);

            assertThat(problemRepository.findAllByUserId(owner.getId()))
                    .as("거절된 요청은 아무것도 저장하지 않는다")
                    .isEmpty();
        }

        @Test
        @DisplayName("255자 reference 는 저장되고 256자는 400으로 거절한다")
        void enforcesReferenceLimit() {
            String maxReference = "a".repeat(255);
            Long problemId = problemService.registerProblem(
                    new ProblemRegisterDto(null, null, maxReference, ownerRoot.getId(), null),
                    owner.getId()
            );
            assertThat(problemService.findProblem(problemId, owner.getId()).reference()).hasSize(255);

            assertThatThrownBy(() -> problemService.registerProblem(
                    new ProblemRegisterDto(null, null, "a".repeat(256), ownerRoot.getId(), null),
                    owner.getId()
            ))
                    .isInstanceOf(ApplicationException.class)
                    .extracting(ProblemServiceTest::errorCaseOf)
                    .isEqualTo(ProblemErrorCase.PROBLEM_REFERENCE_TOO_LONG);
        }

        @Test
        @DisplayName("memo/reference/solvedAt 이 모두 null 이어도 등록된다")
        void allowsNullOptionalFields() {
            Long problemId = problemService.registerProblem(
                    new ProblemRegisterDto(null, null, null, ownerRoot.getId(), null),
                    owner.getId()
            );

            ProblemResponseDto response = problemService.findProblem(problemId, owner.getId());
            assertThat(response.memo()).isNull();
            assertThat(response.reference()).isNull();
            assertThat(response.solvedAt()).isNull();
        }

        @Test
        @DisplayName("folderId 가 null 이면 500이 아니라 400(PROBLEM_FOLDER_ID_REQUIRED)")
        void rejectsNullFolderId() {
            assertThatThrownBy(() -> problemService.registerProblem(
                    new ProblemRegisterDto(null, "메모", null, null, null),
                    owner.getId()
            ))
                    .as("findById(null) 이 InvalidDataAccessApiUsageException 으로 500을 내던 지점")
                    .isInstanceOf(ApplicationException.class)
                    .extracting(ProblemServiceTest::errorCaseOf)
                    .isEqualTo(ProblemErrorCase.PROBLEM_FOLDER_ID_REQUIRED);
        }

        @Test
        @DisplayName("없는 폴더에 등록하면 FOLDER_NOT_FOUND")
        void rejectsUnknownFolder() {
            assertThatThrownBy(() -> problemService.registerProblem(
                    new ProblemRegisterDto(null, "메모", null, 999_999L, null),
                    owner.getId()
            ))
                    .isInstanceOf(ApplicationException.class)
                    .extracting(ProblemServiceTest::errorCaseOf)
                    .isEqualTo(FolderErrorCase.FOLDER_NOT_FOUND);
        }

        @Test
        @DisplayName("다른 사용자의 폴더에는 등록할 수 없다")
        void rejectsOtherUsersFolder() {
            assertThatThrownBy(() -> problemService.registerProblem(
                    new ProblemRegisterDto(null, "메모", null, intruderRoot.getId(), null),
                    owner.getId()
            ))
                    .isInstanceOf(ApplicationException.class)
                    .extracting(ProblemServiceTest::errorCaseOf)
                    .isEqualTo(FolderErrorCase.FOLDER_USER_UNMATCHED);

            assertThat(problemRepository.findAllByUserId(intruder.getId()))
                    .as("남의 폴더에 문제가 생기면 안 된다")
                    .isEmpty();
        }

        @Test
        @DisplayName("tagIds 를 주면 태그가 함께 연결된다")
        void linksTags() {
            Tag tag1 = saveTag(owner.getId(), "계산실수");
            Tag tag2 = saveTag(owner.getId(), "개념부족");

            Long problemId = problemService.registerProblem(
                    new ProblemRegisterDto(null, "메모", null, ownerRoot.getId(), null,
                            List.of(tag1.getId(), tag2.getId())),
                    owner.getId()
            );

            assertThat(problemService.findProblem(problemId, owner.getId()).tagIdList())
                    .containsExactlyInAnyOrder(tag1.getId(), tag2.getId());
        }

        @Test
        @DisplayName("tagIds 가 빈 배열이면 태그 없이 등록된다")
        void allowsEmptyTagIds() {
            Long problemId = problemService.registerProblem(
                    new ProblemRegisterDto(null, "메모", null, ownerRoot.getId(), null, List.of()),
                    owner.getId()
            );

            assertThat(problemService.findProblem(problemId, owner.getId()).tagIdList()).isEmpty();
        }

        @Test
        @DisplayName("태그를 6개 지정하면 TAG_LIMIT_EXCEEDED")
        void rejectsMoreThanFiveTags() {
            List<Long> tagIds = new ArrayList<>();
            for (int i = 0; i < 6; i++) {
                tagIds.add(saveTag(owner.getId(), "태그" + i).getId());
            }

            assertThatThrownBy(() -> problemService.registerProblem(
                    new ProblemRegisterDto(null, "메모", null, ownerRoot.getId(), null, tagIds),
                    owner.getId()
            ))
                    .isInstanceOf(ApplicationException.class)
                    .extracting(ProblemServiceTest::errorCaseOf)
                    .isEqualTo(TagErrorCase.TAG_LIMIT_EXCEEDED);
        }

        @Test
        @DisplayName("다른 사용자의 태그는 연결할 수 없다")
        void rejectsOtherUsersTag() {
            Tag othersTag = saveTag(intruder.getId(), "남의태그");

            assertThatThrownBy(() -> problemService.registerProblem(
                    new ProblemRegisterDto(null, "메모", null, ownerRoot.getId(), null, List.of(othersTag.getId())),
                    owner.getId()
            ))
                    .isInstanceOf(ApplicationException.class)
                    .extracting(ProblemServiceTest::errorCaseOf)
                    .isEqualTo(TagErrorCase.TAG_NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("문제 등록 (v2)")
    class RegisterProblemV2 {

        @Test
        @DisplayName("이미지 URL 을 함께 주면 문제 이미지와 정답 이미지가 생성된다")
        void savesImageUrls() {
            Long problemId = problemService.registerProblemV2(new ProblemRegisterV2Dto(
                    null, "메모", "출처", ownerRoot.getId(), null,
                    List.of("https://s3/p1.png", "https://s3/p2.png"),
                    List.of("https://s3/a1.png")
            ), owner.getId());

            assertThat(problemImageDataRepository.findAllByProblemId(problemId))
                    .hasSize(3)
                    .filteredOn(image -> image.getProblemImageType() == ProblemImageType.PROBLEM_IMAGE)
                    .hasSize(2);
        }

        @Test
        @DisplayName("빈 이미지 리스트를 주면 이미지 없이 등록된다")
        void allowsEmptyImageLists() {
            Long problemId = problemService.registerProblemV2(new ProblemRegisterV2Dto(
                    null, "메모", null, ownerRoot.getId(), null, List.of(), List.of()
            ), owner.getId());

            assertThat(problemImageDataRepository.findAllByProblemId(problemId)).isEmpty();
        }

        @Test
        @DisplayName("이미지 리스트가 null 이어도 등록된다")
        void allowsNullImageLists() {
            Long problemId = problemService.registerProblemV2(new ProblemRegisterV2Dto(
                    null, "메모", null, ownerRoot.getId(), null, null, null
            ), owner.getId());

            assertThat(problemImageDataRepository.findAllByProblemId(problemId)).isEmpty();
        }

        @Test
        @DisplayName("공백/빈 문자열 URL 은 걸러진다")
        void filtersBlankUrls() {
            Long problemId = problemService.registerProblemV2(new ProblemRegisterV2Dto(
                    null, "메모", null, ownerRoot.getId(), null,
                    java.util.Arrays.asList("  ", "", null, " https://s3/p.png "),
                    List.of()
            ), owner.getId());

            assertThat(problemImageDataRepository.findAllByProblemId(problemId))
                    .extracting(image -> image.getImageUrl())
                    .containsExactly("https://s3/p.png");
        }

        @Test
        @DisplayName("folderId 가 null 이면 루트 폴더에 등록된다")
        void fallsBackToRootFolder() {
            Long problemId = problemService.registerProblemV2(new ProblemRegisterV2Dto(
                    null, "메모", null, null, null, null, null
            ), owner.getId());

            assertThat(problemService.findProblem(problemId, owner.getId()).folderId())
                    .isEqualTo(ownerRoot.getId());
        }

        @Test
        @DisplayName("루트 폴더가 없는 사용자가 folderId 없이 등록하면 ROOT_FOLDER_NOT_EXIST")
        void rejectsWhenRootFolderMissing() {
            User folderless = fixtures.createUser("folderless");

            assertThatThrownBy(() -> problemService.registerProblemV2(new ProblemRegisterV2Dto(
                    null, "메모", null, null, null, null, null
            ), folderless.getId()))
                    .isInstanceOf(ApplicationException.class)
                    .extracting(ProblemServiceTest::errorCaseOf)
                    .isEqualTo(FolderErrorCase.ROOT_FOLDER_NOT_EXIST);
        }

        @Test
        @DisplayName("다른 사용자의 폴더에는 등록할 수 없다")
        void rejectsOtherUsersFolder() {
            assertThatThrownBy(() -> problemService.registerProblemV2(new ProblemRegisterV2Dto(
                    null, "메모", null, intruderRoot.getId(), null, null, null
            ), owner.getId()))
                    .isInstanceOf(ApplicationException.class)
                    .extracting(ProblemServiceTest::errorCaseOf)
                    .isEqualTo(FolderErrorCase.FOLDER_USER_UNMATCHED);
        }

        @Test
        @DisplayName("1001자 메모는 400으로 거절한다")
        void rejectsMemoOverLimit() {
            assertThatThrownBy(() -> problemService.registerProblemV2(new ProblemRegisterV2Dto(
                    null, "가".repeat(1001), null, ownerRoot.getId(), null, null, null
            ), owner.getId()))
                    .isInstanceOf(ApplicationException.class)
                    .extracting(ProblemServiceTest::errorCaseOf)
                    .isEqualTo(ProblemErrorCase.PROBLEM_MEMO_TOO_LONG);
        }
    }

    @Nested
    @DisplayName("문제 배치 등록 (v2 batch)")
    class RegisterProblemsV2Batch {

        private ProblemRegisterV2Dto dto(String memo, Long folderId) {
            return new ProblemRegisterV2Dto(null, memo, null, folderId, null, null, null);
        }

        @Test
        @DisplayName("50건을 한 번에 등록하면 모두 저장되고 ID 가 순서대로 반환된다")
        void registersLargeBatch() {
            List<ProblemRegisterV2Dto> dtos = new ArrayList<>();
            for (int i = 0; i < 50; i++) {
                dtos.add(dto("배치메모" + i, ownerRoot.getId()));
            }

            List<Long> problemIds = problemService.registerProblemsV2(
                    new ProblemRegisterV2BatchDto(dtos), owner.getId());

            assertThat(problemIds).hasSize(50).doesNotHaveDuplicates();
            assertThat(problemRepository.findAllByUserId(owner.getId())).hasSize(50);
            assertThat(problemAnalysisRepository.findAll())
                    .as("배치 등록도 문제마다 분석 레코드를 만든다")
                    .hasSize(50);
        }

        @Test
        @DisplayName("빈 배치는 빈 리스트를 반환하고 아무것도 저장하지 않는다")
        void returnsEmptyForEmptyBatch() {
            assertThat(problemService.registerProblemsV2(
                    new ProblemRegisterV2BatchDto(List.of()), owner.getId())).isEmpty();
            assertThat(problemRepository.findAllByUserId(owner.getId())).isEmpty();
        }

        @Test
        @DisplayName("problems 가 null 이어도 예외 없이 빈 리스트를 반환한다")
        void returnsEmptyForNullBatch() {
            assertThat(problemService.registerProblemsV2(
                    new ProblemRegisterV2BatchDto(null), owner.getId())).isEmpty();
        }

        @Test
        @DisplayName("folderId 가 null 인 항목은 루트 폴더로 들어간다")
        void nullFolderIdFallsBackToRoot() {
            Folder subFolder = fixtures.createFolder(owner.getId(), "하위", ownerRoot);

            List<Long> problemIds = problemService.registerProblemsV2(new ProblemRegisterV2BatchDto(List.of(
                    dto("루트행", null),
                    dto("하위행", subFolder.getId())
            )), owner.getId());

            assertThat(problemService.findProblem(problemIds.get(0), owner.getId()).folderId())
                    .isEqualTo(ownerRoot.getId());
            assertThat(problemService.findProblem(problemIds.get(1), owner.getId()).folderId())
                    .isEqualTo(subFolder.getId());
        }

        @Test
        @DisplayName("한 건이라도 남의 폴더면 전체가 롤백된다")
        void rollsBackWholeBatchOnForeignFolder() {
            assertThatThrownBy(() -> problemService.registerProblemsV2(new ProblemRegisterV2BatchDto(List.of(
                    dto("정상", ownerRoot.getId()),
                    dto("남의폴더", intruderRoot.getId())
            )), owner.getId()))
                    .isInstanceOf(ApplicationException.class)
                    .extracting(ProblemServiceTest::errorCaseOf)
                    .isEqualTo(FolderErrorCase.FOLDER_USER_UNMATCHED);

            assertThat(problemRepository.findAllByUserId(owner.getId()))
                    .as("검증은 저장 전에 끝나야 한다")
                    .isEmpty();
        }

        @Test
        @DisplayName("한 건이라도 메모 길이를 넘기면 전체가 저장되지 않는다")
        void rollsBackWholeBatchOnTooLongMemo() {
            assertThatThrownBy(() -> problemService.registerProblemsV2(new ProblemRegisterV2BatchDto(List.of(
                    dto("정상", ownerRoot.getId()),
                    dto("가".repeat(1001), ownerRoot.getId())
            )), owner.getId()))
                    .isInstanceOf(ApplicationException.class)
                    .extracting(ProblemServiceTest::errorCaseOf)
                    .isEqualTo(ProblemErrorCase.PROBLEM_MEMO_TOO_LONG);

            assertThat(problemRepository.findAllByUserId(owner.getId())).isEmpty();
        }

        @Test
        @DisplayName("없는 폴더 ID 가 섞이면 FOLDER_NOT_FOUND")
        void rejectsUnknownFolderInBatch() {
            assertThatThrownBy(() -> problemService.registerProblemsV2(new ProblemRegisterV2BatchDto(List.of(
                    dto("정상", ownerRoot.getId()),
                    dto("없는폴더", 999_999L)
            )), owner.getId()))
                    .isInstanceOf(ApplicationException.class)
                    .extracting(ProblemServiceTest::errorCaseOf)
                    .isEqualTo(FolderErrorCase.FOLDER_NOT_FOUND);
        }

        @Test
        @DisplayName("배치 항목의 태그가 6개면 TAG_LIMIT_EXCEEDED")
        void rejectsMoreThanFiveTagsInBatch() {
            List<Long> tagIds = new ArrayList<>();
            for (int i = 0; i < 6; i++) {
                tagIds.add(saveTag(owner.getId(), "배치태그" + i).getId());
            }

            assertThatThrownBy(() -> problemService.registerProblemsV2(new ProblemRegisterV2BatchDto(List.of(
                    new ProblemRegisterV2Dto(null, "메모", null, ownerRoot.getId(), null, null, null, tagIds)
            )), owner.getId()))
                    .isInstanceOf(ApplicationException.class)
                    .extracting(ProblemServiceTest::errorCaseOf)
                    .isEqualTo(TagErrorCase.TAG_LIMIT_EXCEEDED);
        }

        @Test
        @DisplayName("배치 항목에 남의 태그가 섞이면 TAG_NOT_FOUND")
        void rejectsOtherUsersTagInBatch() {
            Tag othersTag = saveTag(intruder.getId(), "남의배치태그");

            assertThatThrownBy(() -> problemService.registerProblemsV2(new ProblemRegisterV2BatchDto(List.of(
                    new ProblemRegisterV2Dto(null, "메모", null, ownerRoot.getId(), null, null, null,
                            List.of(othersTag.getId()))
            )), owner.getId()))
                    .isInstanceOf(ApplicationException.class)
                    .extracting(ProblemServiceTest::errorCaseOf)
                    .isEqualTo(TagErrorCase.TAG_NOT_FOUND);
        }

        @Test
        @DisplayName("배치 등록된 문제도 복습 예정일이 오늘로 설정된다")
        void setsReviewScheduleForBatch() {
            List<Long> problemIds = problemService.registerProblemsV2(
                    new ProblemRegisterV2BatchDto(List.of(dto("메모", ownerRoot.getId()))), owner.getId());

            assertThat(problemRepository.findById(problemIds.get(0)).orElseThrow().getNextReviewAt())
                    .isEqualTo(LocalDate.now(SEOUL));
        }
    }

    // ════════════════════════════ 수정 ════════════════════════════

    @Nested
    @DisplayName("문제 정보 수정")
    class UpdateProblemInfo {

        @Test
        @DisplayName("메모와 출처가 갱신된다")
        void updatesMemoAndReference() {
            Problem problem = saveProblem(owner.getId(), ownerRoot, "이전 메모", "이전 출처");

            problemService.updateProblemInfo(
                    new ProblemRegisterDto(problem.getId(), "새 메모", "새 출처", null, null),
                    owner.getId()
            );

            ProblemResponseDto updated = problemService.findProblem(problem.getId(), owner.getId());
            assertThat(updated.memo()).isEqualTo("새 메모");
            assertThat(updated.reference()).isEqualTo("새 출처");
        }

        @Test
        @DisplayName("null/공백 메모는 기존 값을 덮어쓰지 않는다")
        void keepsExistingValueOnBlankInput() {
            Problem problem = saveProblem(owner.getId(), ownerRoot, "원래 메모", "원래 출처");

            problemService.updateProblemInfo(
                    new ProblemRegisterDto(problem.getId(), "   ", null, null, null),
                    owner.getId()
            );

            ProblemResponseDto updated = problemService.findProblem(problem.getId(), owner.getId());
            assertThat(updated.memo()).isEqualTo("원래 메모");
            assertThat(updated.reference()).isEqualTo("원래 출처");
        }

        @Test
        @DisplayName("1000자 메모로 수정해도 500이 나지 않는다")
        void updatesToMaxLengthMemo() {
            Problem problem = saveProblem(owner.getId(), ownerRoot, "짧은 메모", null);
            String longMemo = "나".repeat(1000);

            assertThatCode(() -> problemService.updateProblemInfo(
                    new ProblemRegisterDto(problem.getId(), longMemo, null, null, null),
                    owner.getId()
            )).doesNotThrowAnyException();

            assertThat(problemService.findProblem(problem.getId(), owner.getId()).memo()).isEqualTo(longMemo);
        }

        @Test
        @DisplayName("1001자 메모로 수정하면 400으로 거절하고 기존 값이 유지된다")
        void rejectsTooLongMemoOnUpdate() {
            Problem problem = saveProblem(owner.getId(), ownerRoot, "원래 메모", null);

            assertThatThrownBy(() -> problemService.updateProblemInfo(
                    new ProblemRegisterDto(problem.getId(), "가".repeat(1001), null, null, null),
                    owner.getId()
            ))
                    .isInstanceOf(ApplicationException.class)
                    .extracting(ProblemServiceTest::errorCaseOf)
                    .isEqualTo(ProblemErrorCase.PROBLEM_MEMO_TOO_LONG);

            assertThat(problemService.findProblem(problem.getId(), owner.getId()).memo()).isEqualTo("원래 메모");
        }

        @Test
        @DisplayName("다른 사용자의 문제는 수정할 수 없다")
        void rejectsOtherUsersProblem() {
            Problem othersProblem = saveProblem(intruder.getId(), intruderRoot, "남의 메모", null);

            assertThatThrownBy(() -> problemService.updateProblemInfo(
                    new ProblemRegisterDto(othersProblem.getId(), "탈취 시도", null, null, null),
                    owner.getId()
            ))
                    .isInstanceOf(ApplicationException.class)
                    .extracting(ProblemServiceTest::errorCaseOf)
                    .isEqualTo(ProblemErrorCase.PROBLEM_USER_UNMATCHED);

            assertThat(problemService.findProblem(othersProblem.getId(), intruder.getId()).memo())
                    .isEqualTo("남의 메모");
        }

        @Test
        @DisplayName("없는 문제를 수정하면 PROBLEM_NOT_FOUND")
        void rejectsUnknownProblem() {
            assertThatThrownBy(() -> problemService.updateProblemInfo(
                    new ProblemRegisterDto(999_999L, "메모", null, null, null), owner.getId()))
                    .isInstanceOf(ApplicationException.class)
                    .extracting(ProblemServiceTest::errorCaseOf)
                    .isEqualTo(ProblemErrorCase.PROBLEM_NOT_FOUND);
        }

        @Test
        @DisplayName("tagIds 를 빈 배열로 주면 기존 태그가 모두 해제된다")
        void clearsTagsOnEmptyArray() {
            Problem problem = saveProblem(owner.getId(), ownerRoot);
            Tag tag = saveTag(owner.getId(), "해제대상");
            saveTagMapping(problem, tag);

            problemService.updateProblemInfo(
                    new ProblemRegisterDto(problem.getId(), null, null, null, null, List.of()),
                    owner.getId()
            );

            assertThat(problemTagMappingRepository.findAllByProblemId(problem.getId())).isEmpty();
        }

        @Test
        @DisplayName("tagIds 가 null 이면 기존 태그가 유지된다")
        void keepsTagsWhenTagIdsNull() {
            Problem problem = saveProblem(owner.getId(), ownerRoot);
            Tag tag = saveTag(owner.getId(), "유지대상");
            saveTagMapping(problem, tag);

            problemService.updateProblemInfo(
                    new ProblemRegisterDto(problem.getId(), "메모만 수정", null, null, null, null),
                    owner.getId()
            );

            assertThat(problemTagMappingRepository.findAllByProblemId(problem.getId())).hasSize(1);
        }
    }

    @Nested
    @DisplayName("문제 폴더 이동")
    class UpdateProblemFolder {

        @Test
        @DisplayName("본인 폴더로 이동할 수 있다")
        void movesToOwnFolder() {
            Folder target = fixtures.createFolder(owner.getId(), "이동 대상", ownerRoot);
            Problem problem = saveProblem(owner.getId(), ownerRoot);

            problemService.updateProblemFolder(
                    new ProblemRegisterDto(problem.getId(), null, null, target.getId(), null),
                    owner.getId()
            );

            assertThat(problemService.findProblem(problem.getId(), owner.getId()).folderId())
                    .isEqualTo(target.getId());
        }

        @Test
        @DisplayName("다른 사용자의 폴더로는 이동할 수 없다")
        void rejectsMoveIntoOtherUsersFolder() {
            Problem problem = saveProblem(owner.getId(), ownerRoot);

            assertThatThrownBy(() -> problemService.updateProblemFolder(
                    new ProblemRegisterDto(problem.getId(), null, null, intruderRoot.getId(), null),
                    owner.getId()
            ))
                    .isInstanceOf(ApplicationException.class)
                    .extracting(ProblemServiceTest::errorCaseOf)
                    .isEqualTo(FolderErrorCase.FOLDER_USER_UNMATCHED);

            assertThat(problemService.findProblem(problem.getId(), owner.getId()).folderId())
                    .isEqualTo(ownerRoot.getId());
        }

        @Test
        @DisplayName("다른 사용자의 문제는 이동할 수 없다")
        void rejectsMovingOtherUsersProblem() {
            Problem othersProblem = saveProblem(intruder.getId(), intruderRoot);
            Folder myFolder = fixtures.createFolder(owner.getId(), "내 폴더", ownerRoot);

            assertThatThrownBy(() -> problemService.updateProblemFolder(
                    new ProblemRegisterDto(othersProblem.getId(), null, null, myFolder.getId(), null),
                    owner.getId()
            ))
                    .as("남의 문제를 내 폴더로 끌어올 수 없어야 한다")
                    .isInstanceOf(ApplicationException.class)
                    .extracting(ProblemServiceTest::errorCaseOf)
                    .isEqualTo(ProblemErrorCase.PROBLEM_USER_UNMATCHED);
        }

        @Test
        @DisplayName("folderId 가 null 이면 폴더를 바꾸지 않는다")
        void ignoresNullFolderId() {
            Problem problem = saveProblem(owner.getId(), ownerRoot);

            assertThatCode(() -> problemService.updateProblemFolder(
                    new ProblemRegisterDto(problem.getId(), null, null, null, null), owner.getId()))
                    .doesNotThrowAnyException();

            assertThat(problemService.findProblem(problem.getId(), owner.getId()).folderId())
                    .isEqualTo(ownerRoot.getId());
        }

        @Test
        @DisplayName("없는 폴더로 이동하면 FOLDER_NOT_FOUND")
        void rejectsUnknownFolder() {
            Problem problem = saveProblem(owner.getId(), ownerRoot);

            assertThatThrownBy(() -> problemService.updateProblemFolder(
                    new ProblemRegisterDto(problem.getId(), null, null, 999_999L, null), owner.getId()))
                    .isInstanceOf(ApplicationException.class)
                    .extracting(ProblemServiceTest::errorCaseOf)
                    .isEqualTo(FolderErrorCase.FOLDER_NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("문제 태그 수정")
    class UpdateProblemTags {

        @Test
        @DisplayName("태그를 추가하고 제거할 수 있다")
        void addsAndRemovesTags() {
            Problem problem = saveProblem(owner.getId(), ownerRoot);
            Tag keep = saveTag(owner.getId(), "유지");
            Tag remove = saveTag(owner.getId(), "제거");
            Tag add = saveTag(owner.getId(), "추가");
            saveTagMapping(problem, keep);
            saveTagMapping(problem, remove);

            problemService.updateProblemTags(problem.getId(), owner.getId(),
                    new ProblemTagUpdateDto(List.of(add.getId()), List.of(remove.getId())));

            assertThat(problemTagMappingRepository.findAllByProblemId(problem.getId()))
                    .extracting(mapping -> mapping.getTag().getId())
                    .containsExactlyInAnyOrder(keep.getId(), add.getId());
        }

        @Test
        @DisplayName("이미 붙어 있는 태그를 다시 추가해도 중복 생성되지 않는다")
        void ignoresDuplicateAdd() {
            Problem problem = saveProblem(owner.getId(), ownerRoot);
            Tag tag = saveTag(owner.getId(), "중복");
            saveTagMapping(problem, tag);

            problemService.updateProblemTags(problem.getId(), owner.getId(),
                    new ProblemTagUpdateDto(List.of(tag.getId()), null));

            assertThat(problemTagMappingRepository.findAllByProblemId(problem.getId())).hasSize(1);
        }

        @Test
        @DisplayName("추가/제거 목록이 모두 null 이면 아무 변화가 없다")
        void handlesNullLists() {
            Problem problem = saveProblem(owner.getId(), ownerRoot);
            Tag tag = saveTag(owner.getId(), "그대로");
            saveTagMapping(problem, tag);

            problemService.updateProblemTags(problem.getId(), owner.getId(),
                    new ProblemTagUpdateDto(null, null));

            assertThat(problemTagMappingRepository.findAllByProblemId(problem.getId())).hasSize(1);
        }

        @Test
        @DisplayName("태그 총합이 5개를 넘으면 TAG_LIMIT_EXCEEDED")
        void rejectsOverFiveTags() {
            Problem problem = saveProblem(owner.getId(), ownerRoot);
            for (int i = 0; i < 5; i++) {
                saveTagMapping(problem, saveTag(owner.getId(), "기존" + i));
            }
            Tag extra = saveTag(owner.getId(), "여섯번째");

            assertThatThrownBy(() -> problemService.updateProblemTags(problem.getId(), owner.getId(),
                    new ProblemTagUpdateDto(List.of(extra.getId()), null)))
                    .isInstanceOf(ApplicationException.class)
                    .extracting(ProblemServiceTest::errorCaseOf)
                    .isEqualTo(TagErrorCase.TAG_LIMIT_EXCEEDED);
        }

        @Test
        @DisplayName("다른 사용자의 태그는 붙일 수 없다")
        void rejectsOtherUsersTag() {
            Problem problem = saveProblem(owner.getId(), ownerRoot);
            Tag othersTag = saveTag(intruder.getId(), "남의태그");

            assertThatThrownBy(() -> problemService.updateProblemTags(problem.getId(), owner.getId(),
                    new ProblemTagUpdateDto(List.of(othersTag.getId()), null)))
                    .isInstanceOf(ApplicationException.class)
                    .extracting(ProblemServiceTest::errorCaseOf)
                    .isEqualTo(TagErrorCase.TAG_NOT_FOUND);
        }

        @Test
        @DisplayName("다른 사용자의 문제 태그는 수정할 수 없다")
        void rejectsOtherUsersProblem() {
            Problem othersProblem = saveProblem(intruder.getId(), intruderRoot);
            Tag myTag = saveTag(owner.getId(), "내태그");

            assertThatThrownBy(() -> problemService.updateProblemTags(othersProblem.getId(), owner.getId(),
                    new ProblemTagUpdateDto(List.of(myTag.getId()), null)))
                    .isInstanceOf(ApplicationException.class)
                    .extracting(ProblemServiceTest::errorCaseOf)
                    .isEqualTo(ProblemErrorCase.PROBLEM_USER_UNMATCHED);
        }
    }

    // ════════════════════════════ 삭제 ════════════════════════════

    @Nested
    @DisplayName("문제 삭제")
    class DeleteProblem {

        @Test
        @DisplayName("문제를 삭제하면 이미지·태그 매핑도 함께 정리된다")
        void deletesProblemWithRelations() {
            Problem problem = saveProblem(owner.getId(), ownerRoot);
            saveImageData(problem, "https://s3/p.png", ProblemImageType.PROBLEM_IMAGE);
            saveTagMapping(problem, saveTag(owner.getId(), "삭제대상"));

            problemService.deleteProblem(problem.getId(), owner.getId());

            assertThat(problemRepository.findById(problem.getId())).isEmpty();
            assertThat(problemImageDataRepository.findAllByProblemId(problem.getId())).isEmpty();
            assertThat(problemTagMappingRepository.findAllByProblemId(problem.getId())).isEmpty();
        }

        @Test
        @DisplayName("다른 사용자의 문제는 삭제할 수 없다")
        void rejectsOtherUsersProblem() {
            Problem othersProblem = saveProblem(intruder.getId(), intruderRoot);

            assertThatThrownBy(() -> problemService.deleteProblem(othersProblem.getId(), owner.getId()))
                    .isInstanceOf(ApplicationException.class)
                    .extracting(ProblemServiceTest::errorCaseOf)
                    .isEqualTo(ProblemErrorCase.PROBLEM_USER_UNMATCHED);

            assertThat(problemRepository.findById(othersProblem.getId()))
                    .as("남의 문제는 그대로 남아 있어야 한다")
                    .isPresent();
        }

        @Test
        @DisplayName("없는 문제를 삭제하면 PROBLEM_NOT_FOUND")
        void rejectsUnknownProblem() {
            assertThatThrownBy(() -> problemService.deleteProblem(999_999L, owner.getId()))
                    .isInstanceOf(ApplicationException.class)
                    .extracting(ProblemServiceTest::errorCaseOf)
                    .isEqualTo(ProblemErrorCase.PROBLEM_NOT_FOUND);
        }

        @Test
        @DisplayName("목록 삭제 중 남의 문제가 섞이면 전체가 롤백된다")
        void rollsBackListDeleteOnForeignProblem() {
            Problem mine = saveProblem(owner.getId(), ownerRoot);
            Problem theirs = saveProblem(intruder.getId(), intruderRoot);

            assertThatThrownBy(() -> problemService.deleteProblemList(
                    owner.getId(), List.of(mine.getId(), theirs.getId())))
                    .isInstanceOf(ApplicationException.class);

            assertThat(problemRepository.findById(mine.getId()))
                    .as("한 트랜잭션이므로 내 문제도 삭제되면 안 된다")
                    .isPresent();
            assertThat(problemRepository.findById(theirs.getId())).isPresent();
        }

        @Test
        @DisplayName("본인 문제 목록은 한 번에 삭제된다")
        void deletesOwnProblemList() {
            Problem first = saveProblem(owner.getId(), ownerRoot);
            Problem second = saveProblem(owner.getId(), ownerRoot);

            problemService.deleteProblemList(owner.getId(), List.of(first.getId(), second.getId()));

            assertThat(problemRepository.findAllByUserId(owner.getId())).isEmpty();
        }

        @Test
        @DisplayName("사용자의 모든 문제 삭제는 다른 사용자 문제를 건드리지 않는다")
        void deleteAllKeepsOtherUsersProblems() {
            saveProblem(owner.getId(), ownerRoot);
            saveProblem(owner.getId(), ownerRoot);
            Problem theirs = saveProblem(intruder.getId(), intruderRoot);

            problemService.deleteAllUserProblems(owner.getId());

            assertThat(problemRepository.findAllByUserId(owner.getId())).isEmpty();
            assertThat(problemRepository.findById(theirs.getId())).isPresent();
        }

        @Test
        @DisplayName("폴더 단위 삭제는 폴더 소유자를 검증한다")
        void deleteByFolderIdsChecksOwner() {
            saveProblem(intruder.getId(), intruderRoot);

            assertThatThrownBy(() -> problemService.deleteAllByFolderIds(owner.getId(), List.of(intruderRoot.getId())))
                    .isInstanceOf(ApplicationException.class)
                    .extracting(ProblemServiceTest::errorCaseOf)
                    .isEqualTo(FolderErrorCase.FOLDER_USER_UNMATCHED);

            assertThat(problemRepository.findAllByUserId(intruder.getId())).hasSize(1);
        }

        @Test
        @DisplayName("본인 폴더 단위 삭제는 그 폴더의 문제만 지운다")
        void deletesProblemsInOwnFolder() {
            Folder subFolder = fixtures.createFolder(owner.getId(), "정리 대상", ownerRoot);
            saveProblem(owner.getId(), subFolder);
            Problem keep = saveProblem(owner.getId(), ownerRoot);

            problemService.deleteAllByFolderIds(owner.getId(), List.of(subFolder.getId()));

            assertThat(problemRepository.findAllByFolderId(subFolder.getId())).isEmpty();
            assertThat(problemRepository.findById(keep.getId())).isPresent();
        }
    }

    @Nested
    @DisplayName("이미지 데이터 삭제")
    class DeleteImageData {

        @Test
        @DisplayName("본인 문제의 이미지를 URL 로 삭제한다")
        void deletesOwnImage() {
            Problem problem = saveProblem(owner.getId(), ownerRoot);
            saveImageData(problem, "https://s3/mine.png", ProblemImageType.PROBLEM_IMAGE);

            problemService.deleteProblemImageData("https://s3/mine.png", owner.getId());

            assertThat(problemImageDataRepository.findByImageUrl("https://s3/mine.png")).isEmpty();
            verify(fileUploadService).deleteImageFileFromS3("https://s3/mine.png");
        }

        @Test
        @DisplayName("다른 사용자의 이미지는 삭제할 수 없다")
        void rejectsOtherUsersImage() {
            Problem othersProblem = saveProblem(intruder.getId(), intruderRoot);
            saveImageData(othersProblem, "https://s3/theirs.png", ProblemImageType.PROBLEM_IMAGE);

            assertThatThrownBy(() -> problemService.deleteProblemImageData("https://s3/theirs.png", owner.getId()))
                    .isInstanceOf(ApplicationException.class)
                    .extracting(ProblemServiceTest::errorCaseOf)
                    .isEqualTo(ProblemErrorCase.PROBLEM_USER_UNMATCHED);

            assertThat(problemImageDataRepository.findByImageUrl("https://s3/theirs.png")).isPresent();
            verify(fileUploadService, never()).deleteImageFileFromS3(any());
        }

        @Test
        @DisplayName("없는 이미지 URL 이면 PROBLEM_NOT_FOUND")
        void rejectsUnknownImageUrl() {
            assertThatThrownBy(() -> problemService.deleteProblemImageData("https://s3/none.png", owner.getId()))
                    .isInstanceOf(ApplicationException.class)
                    .extracting(ProblemServiceTest::errorCaseOf)
                    .isEqualTo(ProblemErrorCase.PROBLEM_NOT_FOUND);
        }
    }

    // ════════════════════════════ 이미지 등록 ════════════════════════════

    @Nested
    @DisplayName("이미지 업로드 / URL 추가")
    class ImageRegistration {

        @Test
        @DisplayName("멀티파트 업로드 시 S3 URL 이 이미지 데이터로 저장된다")
        void uploadsImagesToS3() {
            Problem problem = saveProblem(owner.getId(), ownerRoot);
            given(fileUploadService.uploadFileToS3(any())).willReturn("https://s3/uploaded.png");

            problemService.uploadProblemImages(
                    problem.getId(), owner.getId(),
                    List.of(new MockMultipartFile("problemImages", "p.png", "image/png", "img".getBytes())),
                    List.of(ProblemImageType.PROBLEM_IMAGE.name())
            );

            assertThat(problemImageDataRepository.findAllByProblemId(problem.getId()))
                    .extracting(image -> image.getImageUrl())
                    .containsExactly("https://s3/uploaded.png");
        }

        @Test
        @DisplayName("images 가 null 이면 아무 일도 하지 않는다")
        void ignoresNullImages() {
            Problem problem = saveProblem(owner.getId(), ownerRoot);

            assertThatCode(() -> problemService.uploadProblemImages(problem.getId(), owner.getId(), null, null))
                    .doesNotThrowAnyException();
            verify(fileUploadService, never()).uploadFileToS3(any());
        }

        @Test
        @DisplayName("다른 사용자의 문제에는 이미지를 올릴 수 없다")
        void rejectsUploadToOtherUsersProblem() {
            Problem othersProblem = saveProblem(intruder.getId(), intruderRoot);

            assertThatThrownBy(() -> problemService.uploadProblemImages(
                    othersProblem.getId(), owner.getId(),
                    List.of(new MockMultipartFile("problemImages", "p.png", "image/png", "img".getBytes())),
                    List.of(ProblemImageType.PROBLEM_IMAGE.name())
            ))
                    .isInstanceOf(ApplicationException.class)
                    .extracting(ProblemServiceTest::errorCaseOf)
                    .isEqualTo(ProblemErrorCase.PROBLEM_USER_UNMATCHED);

            verify(fileUploadService, never()).uploadFileToS3(any());
        }

        @Test
        @DisplayName("URL 목록으로 이미지를 추가할 수 있다")
        void addsImageUrls() {
            Problem problem = saveProblem(owner.getId(), ownerRoot);

            problemService.addImageDataUrls(problem.getId(), owner.getId(), new AddProblemImageUrlsRequest(List.of(
                    new AddProblemImageUrlsRequest.ImageUrlItem("https://s3/p.png", "PROBLEM_IMAGE"),
                    new AddProblemImageUrlsRequest.ImageUrlItem("https://s3/a.png", "ANSWER_IMAGE")
            )));

            assertThat(problemImageDataRepository.findAllByProblemId(problem.getId())).hasSize(2);
        }

        @Test
        @DisplayName("빈 이미지 목록을 주면 아무것도 저장되지 않는다")
        void addsNothingForEmptyList() {
            Problem problem = saveProblem(owner.getId(), ownerRoot);

            problemService.addImageDataUrls(problem.getId(), owner.getId(),
                    new AddProblemImageUrlsRequest(List.of()));

            assertThat(problemImageDataRepository.findAllByProblemId(problem.getId())).isEmpty();
        }

        @Test
        @DisplayName("같은 날 SOLVE_IMAGE 를 두 번 올리면 PROBLEM_SOLVE_IMAGE_ALREADY_REGISTERED")
        void rejectsSecondSolveImageOnSameDay() {
            Problem problem = saveProblem(owner.getId(), ownerRoot);
            problemService.addImageDataUrls(problem.getId(), owner.getId(), new AddProblemImageUrlsRequest(List.of(
                    new AddProblemImageUrlsRequest.ImageUrlItem("https://s3/s1.png", "SOLVE_IMAGE")
            )));

            assertThatThrownBy(() -> problemService.addImageDataUrls(problem.getId(), owner.getId(),
                    new AddProblemImageUrlsRequest(List.of(
                            new AddProblemImageUrlsRequest.ImageUrlItem("https://s3/s2.png", "SOLVE_IMAGE")
                    ))))
                    .isInstanceOf(ApplicationException.class)
                    .extracting(ProblemServiceTest::errorCaseOf)
                    .isEqualTo(ProblemErrorCase.PROBLEM_SOLVE_IMAGE_ALREADY_REGISTERED);
        }

        @Test
        @DisplayName("다른 사용자의 문제에는 URL 도 추가할 수 없다")
        void rejectsAddUrlToOtherUsersProblem() {
            Problem othersProblem = saveProblem(intruder.getId(), intruderRoot);

            assertThatThrownBy(() -> problemService.addImageDataUrls(
                    othersProblem.getId(), owner.getId(), new AddProblemImageUrlsRequest(List.of(
                            new AddProblemImageUrlsRequest.ImageUrlItem("https://s3/p.png", "PROBLEM_IMAGE")
                    ))))
                    .isInstanceOf(ApplicationException.class)
                    .extracting(ProblemServiceTest::errorCaseOf)
                    .isEqualTo(ProblemErrorCase.PROBLEM_USER_UNMATCHED);

            assertThat(problemImageDataRepository.findAllByProblemId(othersProblem.getId())).isEmpty();
        }
    }

    // ════════════════════════════ 분석 트리거 ════════════════════════════

    @Nested
    @DisplayName("AI 분석 요청")
    class AnalysisTrigger {

        private String rateLimitKey(Long userId) {
            return "rate_limit:ai_analysis:" + userId;
        }

        @Test
        @DisplayName("문제 이미지가 없으면 큐로 보내지 않고 NO_IMAGE 로 끝낸다")
        void marksNoImageWhenNoProblemImage() {
            Long problemId = problemService.registerProblem(
                    new ProblemRegisterDto(null, "메모", null, ownerRoot.getId(), null), owner.getId());

            problemService.analysisProblem(problemId, owner.getId());

            assertThat(problemAnalysisRepository.findByProblemId(problemId).orElseThrow().getStatus())
                    .isEqualTo(AnalysisStatus.NO_IMAGE);
            verify(problemAnalysisProducer, never()).sendAnalysisMessage(any());
        }

        @Test
        @DisplayName("정답 이미지만 있어도 문제 이미지가 없으면 NO_IMAGE 다")
        void answerImageAloneCountsAsNoImage() {
            Long problemId = problemService.registerProblem(
                    new ProblemRegisterDto(null, "메모", null, ownerRoot.getId(), null), owner.getId());
            saveImageData(problemRepository.findById(problemId).orElseThrow(),
                    "https://s3/a.png", ProblemImageType.ANSWER_IMAGE);

            problemService.analysisProblem(problemId, owner.getId());

            assertThat(problemAnalysisRepository.findByProblemId(problemId).orElseThrow().getStatus())
                    .isEqualTo(AnalysisStatus.NO_IMAGE);
        }

        @Test
        @DisplayName("문제 이미지가 있으면 PROCESSING 으로 바뀌고 분석 큐로 전송된다")
        void enqueuesAnalysisWhenProblemImageExists() {
            Long problemId = problemService.registerProblem(
                    new ProblemRegisterDto(null, "메모", null, ownerRoot.getId(), null), owner.getId());
            saveImageData(problemRepository.findById(problemId).orElseThrow(),
                    "https://s3/p.png", ProblemImageType.PROBLEM_IMAGE);
            redisTemplate.delete(rateLimitKey(owner.getId()));

            problemService.analysisProblem(problemId, owner.getId());

            assertThat(problemAnalysisRepository.findByProblemId(problemId).orElseThrow().getStatus())
                    .isEqualTo(AnalysisStatus.PROCESSING);
            verify(problemAnalysisProducer).sendAnalysisMessage(problemId);
        }

        @Test
        @DisplayName("이미 COMPLETED 인 문제는 다시 분석하지 않는다")
        void skipsCompletedAnalysis() {
            Long problemId = problemService.registerProblem(
                    new ProblemRegisterDto(null, "메모", null, ownerRoot.getId(), null), owner.getId());
            saveImageData(problemRepository.findById(problemId).orElseThrow(),
                    "https://s3/p.png", ProblemImageType.PROBLEM_IMAGE);
            inTransaction(() -> {
                ProblemAnalysis analysis = problemAnalysisRepository.findByProblemId(problemId).orElseThrow();
                analysis.updateWithSuccess("수학", "계산", "[]", "풀이", "실수", "팁");
                problemAnalysisRepository.save(analysis);
            });

            problemService.analysisProblem(problemId, owner.getId());

            verify(problemAnalysisProducer, never()).sendAnalysisMessage(any());
            assertThat(problemAnalysisRepository.findByProblemId(problemId).orElseThrow().getStatus())
                    .isEqualTo(AnalysisStatus.COMPLETED);
        }

        @Test
        @DisplayName("일일 요청 한도를 넘기면 큐로 보내지 않고 RATE_LIMIT_EXCEEDED 로 표시한다")
        void marksRateLimitExceeded() {
            Long problemId = problemService.registerProblem(
                    new ProblemRegisterDto(null, "메모", null, ownerRoot.getId(), null), owner.getId());
            saveImageData(problemRepository.findById(problemId).orElseThrow(),
                    "https://s3/p.png", ProblemImageType.PROBLEM_IMAGE);

            try {
                redisTemplate.delete(rateLimitKey(owner.getId()));
                for (int i = 0; i < 20; i++) {
                    rateLimitService.tryConsume("ai_analysis", owner.getId(), 20);
                }

                problemService.analysisProblem(problemId, owner.getId());

                assertThat(problemAnalysisRepository.findByProblemId(problemId).orElseThrow().getStatus())
                        .isEqualTo(AnalysisStatus.RATE_LIMIT_EXCEEDED);
                verify(problemAnalysisProducer, never()).sendAnalysisMessage(any());
            } finally {
                // Redis 는 테스트마다 초기화되지 않으므로 소진한 카운터를 되돌린다.
                redisTemplate.delete(rateLimitKey(owner.getId()));
            }
        }

        @Test
        @DisplayName("다른 사용자의 문제는 분석 요청할 수 없다")
        void rejectsOtherUsersProblem() {
            Long othersProblemId = problemService.registerProblem(
                    new ProblemRegisterDto(null, "메모", null, intruderRoot.getId(), null), intruder.getId());

            assertThatThrownBy(() -> problemService.analysisProblem(othersProblemId, owner.getId()))
                    .isInstanceOf(ApplicationException.class)
                    .extracting(ProblemServiceTest::errorCaseOf)
                    .isEqualTo(ProblemErrorCase.PROBLEM_USER_UNMATCHED);

            verify(problemAnalysisProducer, never()).sendAnalysisMessage(any());
        }

        @Test
        @DisplayName("no-image 로 직접 전환할 수 있고, 남의 문제는 전환할 수 없다")
        void updatesToNoImageWithOwnershipCheck() {
            Long problemId = problemService.registerProblem(
                    new ProblemRegisterDto(null, "메모", null, ownerRoot.getId(), null), owner.getId());

            problemService.updateProblemAnalysisToNoImage(problemId, owner.getId());
            assertThat(problemAnalysisRepository.findByProblemId(problemId).orElseThrow().getStatus())
                    .isEqualTo(AnalysisStatus.NO_IMAGE);

            assertThatThrownBy(() -> problemService.updateProblemAnalysisToNoImage(problemId, intruder.getId()))
                    .isInstanceOf(ApplicationException.class)
                    .extracting(ProblemServiceTest::errorCaseOf)
                    .isEqualTo(ProblemErrorCase.PROBLEM_USER_UNMATCHED);
        }
    }

    // ════════════════════════════ 커서 페이징 ════════════════════════════

    @Nested
    @DisplayName("커서 기반 조회")
    class CursorSearch {

        @Test
        @DisplayName("폴더 커서 조회는 size 만큼 끊어 주고 다음 커서를 알려준다")
        void paginatesFolderProblems() {
            List<Problem> problems = new ArrayList<>();
            for (int i = 0; i < 5; i++) {
                problems.add(saveProblem(owner.getId(), ownerRoot, "문제" + i, null));
            }

            CursorPageResponse<ProblemResponseDto> first =
                    problemService.findProblemsByFolderWithCursor(ownerRoot.getId(), owner.getId(), null, 2);

            assertThat(first.content()).hasSize(2);
            assertThat(first.hasNext()).isTrue();
            assertThat(first.nextCursor()).isEqualTo(problems.get(1).getId());

            CursorPageResponse<ProblemResponseDto> last =
                    problemService.findProblemsByFolderWithCursor(ownerRoot.getId(), owner.getId(), problems.get(2).getId(), 2);

            assertThat(last.content()).hasSize(2);
            assertThat(last.hasNext()).isFalse();
            assertThat(last.nextCursor()).isNull();
        }

        @Test
        @DisplayName("빈 폴더를 커서 조회하면 빈 페이지가 나온다")
        void returnsEmptyPageForEmptyFolder() {
            CursorPageResponse<ProblemResponseDto> page =
                    problemService.findProblemsByFolderWithCursor(ownerRoot.getId(), owner.getId(), null, 20);

            assertThat(page.content()).isEmpty();
            assertThat(page.hasNext()).isFalse();
        }

        @Test
        @DisplayName("다른 사용자의 폴더는 커서 조회할 수 없다")
        void rejectsOtherUsersFolderCursor() {
            assertThatThrownBy(() -> problemService.findProblemsByFolderWithCursor(
                    intruderRoot.getId(), owner.getId(), null, 20))
                    .isInstanceOf(ApplicationException.class)
                    .extracting(ProblemServiceTest::errorCaseOf)
                    .isEqualTo(FolderErrorCase.FOLDER_USER_UNMATCHED);
        }

        @Test
        @DisplayName("태그 커서 조회는 해당 태그가 붙은 본인 문제만 반환한다")
        void paginatesTagProblems() {
            Tag tag = saveTag(owner.getId(), "커서태그");
            Problem tagged = saveProblem(owner.getId(), ownerRoot, "태그 있음", null);
            saveTagMapping(tagged, tag);
            saveProblem(owner.getId(), ownerRoot, "태그 없음", null);

            CursorPageResponse<ProblemResponseDto> page =
                    problemService.findProblemsByTagWithCursor(tag.getId(), owner.getId(), null, 20);

            assertThat(page.content())
                    .extracting(ProblemResponseDto::memo)
                    .containsExactly("태그 있음");
        }

        @Test
        @DisplayName("다른 사용자의 태그로는 조회할 수 없다")
        void rejectsOtherUsersTagCursor() {
            Tag othersTag = saveTag(intruder.getId(), "남의커서태그");

            assertThatThrownBy(() -> problemService.findProblemsByTagWithCursor(
                    othersTag.getId(), owner.getId(), null, 20))
                    .isInstanceOf(ApplicationException.class)
                    .extracting(ProblemServiceTest::errorCaseOf)
                    .isEqualTo(TagErrorCase.TAG_USER_UNMATCHED);
        }

        @Test
        @DisplayName("없는 태그로 조회하면 TAG_NOT_FOUND")
        void rejectsUnknownTagCursor() {
            assertThatThrownBy(() -> problemService.findProblemsByTagWithCursor(
                    999_999L, owner.getId(), null, 20))
                    .isInstanceOf(ApplicationException.class)
                    .extracting(ProblemServiceTest::errorCaseOf)
                    .isEqualTo(TagErrorCase.TAG_NOT_FOUND);
        }

        @Test
        @DisplayName("제목 검색은 reference 부분 일치이며 대소문자를 구분하지 않는다")
        void searchesTitleIgnoringCase() {
            saveProblem(owner.getId(), ownerRoot, "메모", "Calculus Chapter 3");
            saveProblem(owner.getId(), ownerRoot, "메모", "대수학 1단원");

            CursorPageResponse<ProblemResponseDto> page =
                    problemService.findProblemsByTitleWithCursor("calculus", owner.getId(), null, 20);

            assertThat(page.content())
                    .extracting(ProblemResponseDto::reference)
                    .containsExactly("Calculus Chapter 3");
        }

        @Test
        @DisplayName("제목 검색은 다른 사용자의 문제를 반환하지 않는다")
        void titleSearchIsUserScoped() {
            saveProblem(intruder.getId(), intruderRoot, "메모", "공유되면 안 되는 제목");

            CursorPageResponse<ProblemResponseDto> page =
                    problemService.findProblemsByTitleWithCursor("공유", owner.getId(), null, 20);

            assertThat(page.content()).isEmpty();
        }

        @Test
        @DisplayName("검색어가 null 이거나 공백이면 빈 페이지를 반환한다")
        void returnsEmptyPageForBlankQuery() {
            saveProblem(owner.getId(), ownerRoot, "메모", "아무 제목");

            assertThat(problemService.findProblemsByTitleWithCursor(null, owner.getId(), null, 20).content())
                    .isEmpty();
            assertThat(problemService.findProblemsByTitleWithCursor("   ", owner.getId(), null, 20).content())
                    .isEmpty();
        }
    }

    // ════════════════════════════ 복습 대상 ════════════════════════════

    @Nested
    @DisplayName("복습 대상 조회")
    class ReviewDue {

        @Test
        @DisplayName("오늘 이전/오늘인 문제만 대상이고, 지난 문제는 overdue 로 센다")
        void countsDueAndOverdue() {
            LocalDate today = LocalDate.now(SEOUL);
            saveProblemWithReviewSchedule(owner.getId(), ownerRoot, today.minusDays(3), 2, 1);
            saveProblemWithReviewSchedule(owner.getId(), ownerRoot, today, 1, 0);
            saveProblemWithReviewSchedule(owner.getId(), ownerRoot, today.plusDays(1), 4, 2);

            ReviewDueResponseDto response = problemService.getReviewDueProblems(owner.getId());

            assertThat(response.dueCount()).isEqualTo(2);
            assertThat(response.overdueCount())
                    .as("오늘보다 이전이면 밀린 복습이다")
                    .isEqualTo(1);
        }

        @Test
        @DisplayName("복습 스케줄이 없는(마스터한) 문제는 대상에서 빠진다")
        void excludesMasteredProblems() {
            saveProblemWithReviewSchedule(owner.getId(), ownerRoot, null, 8, 3);

            assertThat(problemService.getReviewDueProblems(owner.getId()).dueCount()).isZero();
        }

        @Test
        @DisplayName("다른 사용자의 복습 대상은 포함되지 않는다")
        void isUserScoped() {
            LocalDate today = LocalDate.now(SEOUL);
            saveProblemWithReviewSchedule(intruder.getId(), intruderRoot, today.minusDays(1), 1, 0);

            ReviewDueResponseDto response = problemService.getReviewDueProblems(owner.getId());

            assertThat(response.dueCount()).isZero();
            assertThat(response.problems()).isEmpty();
        }

        @Test
        @DisplayName("응답에는 메모·출처·복습 간격·연속 정답 수가 담긴다")
        void exposesReviewFields() {
            LocalDate today = LocalDate.now(SEOUL);
            Problem problem = saveProblem(owner.getId(), ownerRoot, "복습 메모", "복습 출처");
            problem.updateReviewSchedule(today, 4, 2);
            problemRepository.saveAndFlush(problem);

            ReviewDueResponseDto.ReviewDueProblemDto dto =
                    problemService.getReviewDueProblems(owner.getId()).problems().get(0);

            assertThat(dto.problemId()).isEqualTo(problem.getId());
            assertThat(dto.memo()).isEqualTo("복습 메모");
            assertThat(dto.reference()).isEqualTo("복습 출처");
            assertThat(dto.nextReviewAt()).isEqualTo(today);
            assertThat(dto.reviewInterval()).isEqualTo(4);
            assertThat(dto.consecutiveCorrectCount()).isEqualTo(2);
        }

        @Test
        @DisplayName("삭제된 문제는 복습 대상에서 빠진다")
        void excludesDeletedProblems() {
            LocalDate today = LocalDate.now(SEOUL);
            Problem problem = saveProblemWithReviewSchedule(owner.getId(), ownerRoot, today, 1, 0);
            problemService.deleteProblem(problem.getId(), owner.getId());

            assertThat(problemService.getReviewDueProblems(owner.getId()).dueCount()).isZero();
        }
    }

    // ════════════════════════════ 관리자 통계 ════════════════════════════

    @Nested
    @DisplayName("관리자 통계 집계")
    class AdminStatistics {

        @Test
        @DisplayName("분석 상태별 집계는 값이 없는 상태도 0으로 채워 반환한다")
        void fillsMissingAnalysisStatuses() {
            problemService.registerProblem(
                    new ProblemRegisterDto(null, "메모", null, ownerRoot.getId(), null), owner.getId());

            var counts = problemService.countProblemAnalysesByStatus();

            assertThat(counts.keySet())
                    .as("AnalysisStatus 전체 값이 키로 있어야 프론트에서 분기 없이 쓸 수 있다")
                    .containsExactlyInAnyOrder(AnalysisStatus.values());
            assertThat(counts.get(AnalysisStatus.NOT_STARTED)).isEqualTo(1L);
            assertThat(counts.get(AnalysisStatus.COMPLETED)).isZero();
        }

        @Test
        @DisplayName("활성 문제의 분석 개수만 센다 - 삭제된 문제는 제외")
        void countsOnlyActiveProblemAnalyses() {
            Long keptId = problemService.registerProblem(
                    new ProblemRegisterDto(null, "유지", null, ownerRoot.getId(), null), owner.getId());
            Long deletedId = problemService.registerProblem(
                    new ProblemRegisterDto(null, "삭제", null, ownerRoot.getId(), null), owner.getId());
            problemService.deleteProblem(deletedId, owner.getId());

            assertThat(problemService.countAllProblemAnalyses()).isEqualTo(1L);
            assertThat(problemRepository.findById(keptId)).isPresent();
        }

        @Test
        @DisplayName("일자별 문제 등록 수는 조회 구간의 모든 날짜를 채워 반환한다")
        void fillsEveryDayInRange() {
            problemService.registerProblem(
                    new ProblemRegisterDto(null, "메모", null, ownerRoot.getId(), null), owner.getId());
            LocalDate today = LocalDate.now();

            var daily = problemService.getDailyProblemsCount(today.minusDays(2), today);

            assertThat(daily).hasSize(3);
            assertThat(daily.get(today)).isEqualTo(1L);
            assertThat(daily.get(today.minusDays(2))).isZero();
        }

        @Test
        @DisplayName("관리자 문제 목록은 페이지 크기를 지킨다")
        void paginatesAdminProblems() {
            for (int i = 0; i < 3; i++) {
                saveProblem(owner.getId(), ownerRoot, "관리자 목록" + i, null);
            }

            var page = problemService.findAdminProblems(0, 2);

            assertThat(page.getContent()).hasSize(2);
            assertThat(page.getTotalElements()).isEqualTo(3);
        }
    }

    // ════════════════════════════ 복습 알림 연동 ════════════════════════════

    @Nested
    @DisplayName("복습 알림 예약 연동")
    class ReminderIntegration {

        @Test
        @DisplayName("문제를 등록하면 커밋 후 복습 알림 5건이 예약된다")
        void schedulesRemindersAfterRegister() {
            Long problemId = problemService.registerProblem(
                    new ProblemRegisterDto(null, "메모", "출처", ownerRoot.getId(), null), owner.getId());

            assertThat(reminderRepository.findAll())
                    .filteredOn(reminder -> reminder.getProblemId().equals(problemId))
                    .as("망각곡선 간격 5개만큼 예약된다")
                    .hasSize(5);
        }

        @Test
        @DisplayName("1000자 메모로 등록해도 알림 스냅샷 저장이 깨지지 않는다")
        void schedulesRemindersWithLongMemo() {
            String memo = "가".repeat(1000);

            Long problemId = problemService.registerProblem(
                    new ProblemRegisterDto(null, memo, null, ownerRoot.getId(), null), owner.getId());

            assertThat(reminderRepository.findAll())
                    .filteredOn(reminder -> reminder.getProblemId().equals(problemId))
                    .as("스냅샷 컬럼도 memo 와 같은 길이를 담을 수 있어야 한다")
                    .hasSize(5)
                    .allSatisfy(reminder ->
                            assertThat(reminder.getProblemMemoSnapshot()).hasSize(1000));
        }

        @Test
        @DisplayName("문제를 삭제하면 예약된 알림이 CANCELED 로 바뀐다")
        void cancelsRemindersOnDelete() {
            Long problemId = problemService.registerProblem(
                    new ProblemRegisterDto(null, "메모", null, ownerRoot.getId(), null), owner.getId());

            problemService.deleteProblem(problemId, owner.getId());

            assertThat(reminderRepository.findAll())
                    .filteredOn(reminder -> reminder.getProblemId().equals(problemId))
                    .allSatisfy(reminder -> assertThat(reminder.getStatus())
                            .isEqualTo(ProblemReviewReminderStatus.CANCELED));
        }

        @Test
        @DisplayName("메모를 수정하면 예약된 알림 스냅샷도 갱신된다")
        void refreshesSnapshotOnUpdate() {
            Long problemId = problemService.registerProblem(
                    new ProblemRegisterDto(null, "이전 메모", null, ownerRoot.getId(), null), owner.getId());

            problemService.updateProblemInfo(
                    new ProblemRegisterDto(problemId, "갱신된 메모", null, null, null), owner.getId());

            assertThat(reminderRepository.findAll())
                    .filteredOn(reminder -> reminder.getProblemId().equals(problemId))
                    .allSatisfy(reminder ->
                            assertThat(reminder.getProblemMemoSnapshot()).isEqualTo("갱신된 메모"));
        }
    }
}
