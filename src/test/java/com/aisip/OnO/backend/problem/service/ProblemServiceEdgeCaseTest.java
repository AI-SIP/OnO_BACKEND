package com.aisip.OnO.backend.problem.service;

import com.aisip.OnO.backend.common.exception.ApplicationException;
import com.aisip.OnO.backend.common.exception.ErrorCase;
import com.aisip.OnO.backend.common.response.CursorPageResponse;
import com.aisip.OnO.backend.folder.entity.Folder;
import com.aisip.OnO.backend.folder.exception.FolderErrorCase;
import com.aisip.OnO.backend.problem.dto.ProblemRegisterDto;
import com.aisip.OnO.backend.problem.dto.ProblemRegisterV2BatchDto;
import com.aisip.OnO.backend.problem.dto.ProblemRegisterV2Dto;
import com.aisip.OnO.backend.problem.dto.ProblemResponseDto;
import com.aisip.OnO.backend.problem.entity.AnalysisStatus;
import com.aisip.OnO.backend.problem.entity.Problem;
import com.aisip.OnO.backend.problem.entity.ProblemImageType;
import com.aisip.OnO.backend.problem.exception.ProblemErrorCase;
import com.aisip.OnO.backend.problem.support.ProblemTestSupport;
import com.aisip.OnO.backend.problemsolve.entity.AnswerStatus;
import com.aisip.OnO.backend.problemsolve.entity.ProblemSolve;
import com.aisip.OnO.backend.problemsolve.repository.ProblemSolveRepository;
import com.aisip.OnO.backend.tag.entity.Tag;
import com.aisip.OnO.backend.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

/**
 * ProblemService 의 덜 밟히는 갈래를 채우는 테스트.
 *
 * <p>{@link ProblemServiceTest} 가 주요 경로와 소유권 검증을 담당하므로, 여기서는 그쪽에서
 * 한쪽 갈래만 밟고 지나간 조건들 — 커서가 이어질 때의 다음 페이지, 배치 등록의 태그·이미지 정리,
 * null 과 빈 값이 섞인 입력 — 을 반대편까지 확인한다.
 */
@DisplayName("ProblemService — 경계 · 예외 갈래")
class ProblemServiceEdgeCaseTest extends ProblemTestSupport {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    @Autowired
    private ProblemService problemService;

    @Autowired
    private ProblemSolveRepository problemSolveRepository;

    private User owner;
    private Folder ownerRoot;

    @BeforeEach
    void setUpUser() {
        owner = fixtures.createUser();
        ownerRoot = fixtures.createRootFolder(owner.getId());
    }

    private static ErrorCase errorCaseOf(Throwable throwable) {
        return ((ApplicationException) throwable).getErrorCase();
    }

    private Long nonExistentProblemId() {
        return problemRepository.findAll().stream()
                .mapToLong(Problem::getId)
                .max()
                .orElse(0L) + 1_000L;
    }

    private Long nonExistentFolderId() {
        return ownerRoot.getId() + 1_000L;
    }

    private ProblemRegisterV2Dto v2Dto(String memo, Long folderId, List<String> problemImages,
                                       List<String> answerImages, List<Long> tagIds) {
        return new ProblemRegisterV2Dto(null, memo, "출처", folderId, LocalDateTime.now(),
                problemImages, answerImages, tagIds);
    }

    @Nested
    @DisplayName("관리자 조회 · 집계")
    class AdminLookup {

        @Test
        @DisplayName("없는 문제를 관리자 조회하면 PROBLEM_NOT_FOUND")
        void adminLookupRejectsUnknownProblem() {
            Long unknownId = nonExistentProblemId();

            assertThatThrownBy(() -> problemService.findProblemForAdmin(unknownId))
                    .isInstanceOf(ApplicationException.class)
                    .extracting(ProblemServiceEdgeCaseTest::errorCaseOf)
                    .isEqualTo(ProblemErrorCase.PROBLEM_NOT_FOUND);
        }

        @Test
        @DisplayName("기간을 지정한 분석 상태 집계는 그 기간의 분석만 세고 없는 상태는 0으로 채운다")
        void countsAnalysesByStatusWithinPeriod() {
            problemService.registerProblem(
                    new ProblemRegisterDto(null, "메모", "출처", ownerRoot.getId(), LocalDateTime.now()),
                    owner.getId());
            LocalDate today = LocalDate.now(SEOUL);

            Map<AnalysisStatus, Long> inRange = problemService.countProblemAnalysesByStatus(today, today);
            Map<AnalysisStatus, Long> outOfRange =
                    problemService.countProblemAnalysesByStatus(today.minusDays(10), today.minusDays(5));

            assertThat(inRange)
                    .as("모든 상태 키가 채워져야 관리자 화면에서 누락 없이 보인다")
                    .containsOnlyKeys(AnalysisStatus.values());
            assertThat(inRange.get(AnalysisStatus.NOT_STARTED)).isEqualTo(1L);
            assertThat(outOfRange.values())
                    .as("기간 밖 집계는 모두 0이어야 한다")
                    .containsOnly(0L);
        }
    }

    @Nested
    @DisplayName("문제 등록 (v2) 입력 정리")
    class RegisterV2Input {

        @Test
        @DisplayName("정답 이미지 URL 중 null·공백은 걸러지고 나머지만 저장된다")
        void filtersBlankAnswerImageUrls() {
            Long problemId = problemService.registerProblemV2(
                    v2Dto("메모", ownerRoot.getId(),
                            List.of(" https://s3/problem.png "),
                            Arrays.asList(null, "   ", "", " https://s3/answer.png "),
                            null),
                    owner.getId());

            assertThat(problemImageDataRepository.findAllByProblemId(problemId))
                    .extracting(image -> image.getProblemImageType() + ":" + image.getImageUrl())
                    .containsExactlyInAnyOrder(
                            ProblemImageType.PROBLEM_IMAGE + ":https://s3/problem.png",
                            ProblemImageType.ANSWER_IMAGE + ":https://s3/answer.png");
        }

        @Test
        @DisplayName("없는 폴더 ID 로 등록하면 FOLDER_NOT_FOUND")
        void rejectsUnknownFolderId() {
            ProblemRegisterV2Dto dto = v2Dto("메모", nonExistentFolderId(), null, null, null);

            assertThatThrownBy(() -> problemService.registerProblemV2(dto, owner.getId()))
                    .isInstanceOf(ApplicationException.class)
                    .extracting(ProblemServiceEdgeCaseTest::errorCaseOf)
                    .isEqualTo(FolderErrorCase.FOLDER_NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("문제 배치 등록 (v2 batch)")
    class RegisterBatch {

        @Test
        @DisplayName("모든 항목의 folderId 가 null 이면 폴더 조회 없이 전부 루트 폴더로 들어간다")
        void allNullFolderIdsGoToRootFolder() {
            List<Long> problemIds = problemService.registerProblemsV2(
                    new ProblemRegisterV2BatchDto(List.of(
                            v2Dto("첫째", null, null, null, null),
                            v2Dto("둘째", null, null, null, null))),
                    owner.getId());

            assertThat(problemIds).hasSize(2);
            assertThat(problemRepository.findAllById(problemIds))
                    .allSatisfy(problem -> assertThat(problem.getFolder().getId()).isEqualTo(ownerRoot.getId()));
        }

        @Test
        @DisplayName("본인 태그를 지정하면 배치 등록된 문제마다 태그 매핑이 만들어진다")
        void linksTagsForEveryProblem() {
            Tag algebra = saveTag(owner.getId(), "대수");
            Tag geometry = saveTag(owner.getId(), "기하");

            List<Long> problemIds = problemService.registerProblemsV2(
                    new ProblemRegisterV2BatchDto(List.of(
                            v2Dto("첫째", ownerRoot.getId(), null, null, List.of(algebra.getId(), geometry.getId())),
                            v2Dto("둘째", ownerRoot.getId(), null, null, List.of(algebra.getId())))),
                    owner.getId());

            assertThat(problemTagMappingRepository.findAllByProblemId(problemIds.get(0)))
                    .extracting(mapping -> mapping.getTag().getId())
                    .containsExactlyInAnyOrder(algebra.getId(), geometry.getId());
            assertThat(problemTagMappingRepository.findAllByProblemId(problemIds.get(1)))
                    .extracting(mapping -> mapping.getTag().getId())
                    .containsExactly(algebra.getId());
        }

        @Test
        @DisplayName("같은 태그를 중복으로 보내도 매핑은 하나만 만들어진다")
        void deduplicatesTagIds() {
            Tag algebra = saveTag(owner.getId(), "대수");

            List<Long> problemIds = problemService.registerProblemsV2(
                    new ProblemRegisterV2BatchDto(List.of(
                            v2Dto("첫째", ownerRoot.getId(), null, null,
                                    Arrays.asList(algebra.getId(), algebra.getId(), null)))),
                    owner.getId());

            assertThat(problemTagMappingRepository.findAllByProblemId(problemIds.get(0)))
                    .hasSize(1);
        }

        @Test
        @DisplayName("배치 등록의 이미지 URL 도 null·공백은 걸러진다")
        void filtersBlankImageUrlsInBatch() {
            List<Long> problemIds = problemService.registerProblemsV2(
                    new ProblemRegisterV2BatchDto(List.of(
                            v2Dto("첫째", ownerRoot.getId(),
                                    Arrays.asList(null, "  ", " https://s3/p.png "),
                                    Arrays.asList("", " https://s3/a.png "),
                                    null))),
                    owner.getId());

            assertThat(problemImageDataRepository.findAllByProblemId(problemIds.get(0)))
                    .extracting(image -> image.getProblemImageType() + ":" + image.getImageUrl())
                    .containsExactlyInAnyOrder(
                            ProblemImageType.PROBLEM_IMAGE + ":https://s3/p.png",
                            ProblemImageType.ANSWER_IMAGE + ":https://s3/a.png");
        }
    }

    @Nested
    @DisplayName("이미지 업로드")
    class UploadImages {

        private final MockMultipartFile image =
                new MockMultipartFile("images", "problem.png", "image/png", new byte[]{1, 2, 3});

        @Test
        @DisplayName("이미지 타입 목록이 null 이면 업로드하지 않고 조용히 끝낸다")
        void ignoresNullImageTypes() {
            Problem problem = saveProblem(owner.getId(), ownerRoot);

            problemService.uploadProblemImages(problem.getId(), owner.getId(), List.of(image), null);

            assertThat(problemImageDataRepository.findAllByProblemId(problem.getId())).isEmpty();
        }

        @Test
        @DisplayName("풀이 이미지를 올리면 저장되고, 같은 날 두 번째는 거절된다")
        void solveImageIsLimitedToOncePerDay() {
            given(fileUploadService.uploadFileToS3(any())).willReturn("https://s3/solve.png");
            Problem problem = saveProblem(owner.getId(), ownerRoot);

            problemService.uploadProblemImages(problem.getId(), owner.getId(),
                    List.of(image), List.of(ProblemImageType.SOLVE_IMAGE.name()));

            assertThat(problemImageDataRepository.findAllByProblemId(problem.getId()))
                    .singleElement()
                    .satisfies(saved -> assertThat(saved.getProblemImageType()).isEqualTo(ProblemImageType.SOLVE_IMAGE));

            assertThatThrownBy(() -> problemService.uploadProblemImages(problem.getId(), owner.getId(),
                    List.of(image), List.of(ProblemImageType.SOLVE_IMAGE.name())))
                    .isInstanceOf(ApplicationException.class)
                    .extracting(ProblemServiceEdgeCaseTest::errorCaseOf)
                    .isEqualTo(ProblemErrorCase.PROBLEM_SOLVE_IMAGE_ALREADY_REGISTERED);
        }

        @Test
        @DisplayName("문제 이미지는 같은 날 여러 장 올릴 수 있다")
        void problemImageHasNoDailyLimit() {
            given(fileUploadService.uploadFileToS3(any())).willReturn("https://s3/problem.png");
            Problem problem = saveProblem(owner.getId(), ownerRoot);

            assertThatCode(() -> problemService.uploadProblemImages(problem.getId(), owner.getId(),
                    List.of(image, image),
                    List.of(ProblemImageType.PROBLEM_IMAGE.name(), ProblemImageType.PROBLEM_IMAGE.name())))
                    .doesNotThrowAnyException();

            assertThat(problemImageDataRepository.findAllByProblemId(problem.getId())).hasSize(2);
        }
    }

    @Nested
    @DisplayName("태그 동기화")
    class SyncTags {

        @Test
        @DisplayName("수정 시 태그 목록을 주면 빠진 태그는 끊고 새 태그만 붙인다")
        void replacesTagSet() {
            Tag keep = saveTag(owner.getId(), "유지");
            Tag drop = saveTag(owner.getId(), "제거");
            Tag add = saveTag(owner.getId(), "추가");
            Long problemId = problemService.registerProblem(
                    new ProblemRegisterDto(null, "메모", "출처", ownerRoot.getId(), LocalDateTime.now(),
                            List.of(keep.getId(), drop.getId())),
                    owner.getId());

            problemService.updateProblemInfo(
                    new ProblemRegisterDto(problemId, "새 메모", "새 출처", null, null,
                            List.of(keep.getId(), add.getId())),
                    owner.getId());

            assertThat(problemTagMappingRepository.findAllByProblemId(problemId))
                    .extracting(mapping -> mapping.getTag().getId())
                    .as("빠진 태그는 끊기고 유지 태그는 중복 생성되지 않아야 한다")
                    .containsExactlyInAnyOrder(keep.getId(), add.getId());
        }

        @Test
        @DisplayName("같은 태그 목록으로 다시 저장해도 매핑이 늘어나지 않는다")
        void isIdempotent() {
            Tag tag = saveTag(owner.getId(), "미분");
            Long problemId = problemService.registerProblem(
                    new ProblemRegisterDto(null, "메모", "출처", ownerRoot.getId(), LocalDateTime.now(),
                            List.of(tag.getId())),
                    owner.getId());

            problemService.updateProblemInfo(
                    new ProblemRegisterDto(problemId, "메모2", "출처2", null, null, List.of(tag.getId())),
                    owner.getId());

            assertThat(problemTagMappingRepository.findAllByProblemId(problemId)).hasSize(1);
        }
    }

    @Nested
    @DisplayName("커서 페이징 이어받기")
    class CursorPaging {

        @Test
        @DisplayName("태그 커서 조회는 다음 커서를 주고, 그 커서로 이어 받으면 나머지가 나온다")
        void followsTagCursor() {
            Tag tag = saveTag(owner.getId(), "미분");
            List<Long> problemIds = new ArrayList<>();
            for (int i = 0; i < 3; i++) {
                Problem problem = saveProblem(owner.getId(), ownerRoot);
                saveTagMapping(problem, tag);
                problemIds.add(problem.getId());
            }

            CursorPageResponse<ProblemResponseDto> firstPage =
                    problemService.findProblemsByTagWithCursor(tag.getId(), owner.getId(), null, 2);

            assertThat(firstPage.hasNext()).as("3건 중 2건만 줬으므로 다음 페이지가 있다").isTrue();
            assertThat(firstPage.content()).extracting(ProblemResponseDto::problemId)
                    .containsExactly(problemIds.get(0), problemIds.get(1));
            assertThat(firstPage.nextCursor()).isEqualTo(problemIds.get(1));

            CursorPageResponse<ProblemResponseDto> secondPage =
                    problemService.findProblemsByTagWithCursor(tag.getId(), owner.getId(), firstPage.nextCursor(), 2);

            assertThat(secondPage.hasNext()).isFalse();
            assertThat(secondPage.nextCursor()).isNull();
            assertThat(secondPage.content()).extracting(ProblemResponseDto::problemId)
                    .containsExactly(problemIds.get(2));
        }

        @Test
        @DisplayName("제목 커서 조회도 다음 커서로 이어 받을 수 있다")
        void followsTitleCursor() {
            List<Long> problemIds = new ArrayList<>();
            for (int i = 0; i < 3; i++) {
                problemIds.add(saveProblem(owner.getId(), ownerRoot, "메모", "수능 기출 " + i).getId());
            }
            saveProblem(owner.getId(), ownerRoot, "메모", "모의고사");

            CursorPageResponse<ProblemResponseDto> firstPage =
                    problemService.findProblemsByTitleWithCursor("수능", owner.getId(), null, 2);

            assertThat(firstPage.hasNext()).isTrue();
            assertThat(firstPage.nextCursor()).isEqualTo(problemIds.get(1));

            CursorPageResponse<ProblemResponseDto> secondPage =
                    problemService.findProblemsByTitleWithCursor("수능", owner.getId(), firstPage.nextCursor(), 2);

            assertThat(secondPage.content()).extracting(ProblemResponseDto::problemId)
                    .as("검색어에 맞지 않는 문제는 이어 받은 페이지에도 섞이지 않는다")
                    .containsExactly(problemIds.get(2));
            assertThat(secondPage.hasNext()).isFalse();
        }

        @Test
        @DisplayName("남은 건수가 정확히 size 와 같으면 다음 페이지는 없다")
        void noNextPageWhenExactlyFull() {
            Tag tag = saveTag(owner.getId(), "미분");
            for (int i = 0; i < 2; i++) {
                saveTagMapping(saveProblem(owner.getId(), ownerRoot), tag);
            }

            CursorPageResponse<ProblemResponseDto> page =
                    problemService.findProblemsByTagWithCursor(tag.getId(), owner.getId(), null, 2);

            assertThat(page.content()).hasSize(2);
            assertThat(page.hasNext()).as("경계에서 빈 다음 페이지를 만들면 안 된다").isFalse();
            assertThat(page.nextCursor()).isNull();
        }
    }

    @Nested
    @DisplayName("풀이 기록 요약")
    class SolveSummary {

        @Test
        @DisplayName("목록 조회에는 문제별 풀이 횟수와 마지막 풀이 시각이 함께 담긴다")
        void includesSolveCountAndLastSolvedAt() {
            Problem solved = saveProblem(owner.getId(), ownerRoot, "푼 문제", "출처");
            Problem untouched = saveProblem(owner.getId(), ownerRoot, "안 푼 문제", "출처");
            LocalDateTime firstSolvedAt = LocalDateTime.now().minusDays(2).withNano(0);
            LocalDateTime lastSolvedAt = LocalDateTime.now().minusDays(1).withNano(0);
            problemSolveRepository.save(ProblemSolve.create(solved, owner.getId(), firstSolvedAt,
                    AnswerStatus.CORRECT, null, null, 60, null));
            problemSolveRepository.saveAndFlush(ProblemSolve.create(solved, owner.getId(), lastSolvedAt,
                    AnswerStatus.WRONG, null, null, 90, null));

            List<ProblemResponseDto> problems = problemService.findUserProblems(owner.getId());

            assertThat(problems)
                    .filteredOn(dto -> dto.problemId().equals(solved.getId()))
                    .singleElement()
                    .satisfies(dto -> {
                        assertThat(dto.solveCount()).isEqualTo(2L);
                        assertThat(dto.lastSolvedAt()).isEqualTo(lastSolvedAt);
                    });
            assertThat(problems)
                    .filteredOn(dto -> dto.problemId().equals(untouched.getId()))
                    .singleElement()
                    .satisfies(dto -> {
                        assertThat(dto.solveCount()).as("풀이 기록이 없으면 0이어야 한다").isZero();
                        assertThat(dto.lastSolvedAt()).isNull();
                    });
        }

        @Test
        @DisplayName("단건 조회에도 풀이 횟수와 마지막 풀이 시각이 담긴다")
        void singleLookupIncludesSolveSummary() {
            Problem problem = saveProblem(owner.getId(), ownerRoot);
            LocalDateTime solvedAt = LocalDateTime.now().minusHours(3).withNano(0);
            problemSolveRepository.saveAndFlush(ProblemSolve.create(problem, owner.getId(), solvedAt,
                    AnswerStatus.CORRECT, null, null, 30, null));

            ProblemResponseDto dto = problemService.findProblem(problem.getId(), owner.getId());

            assertThat(dto.solveCount()).isEqualTo(1L);
            assertThat(dto.lastSolvedAt()).isEqualTo(solvedAt);
        }
    }
}
