package com.aisip.OnO.backend.problem.entity;

import com.aisip.OnO.backend.folder.dto.FolderRegisterDto;
import com.aisip.OnO.backend.folder.entity.Folder;
import com.aisip.OnO.backend.practicenote.entity.ProblemPracticeNoteMapping;
import com.aisip.OnO.backend.problem.dto.ProblemImageDataRegisterDto;
import com.aisip.OnO.backend.problem.dto.ProblemRegisterDto;
import com.aisip.OnO.backend.tag.entity.ProblemTagMapping;
import com.aisip.OnO.backend.tag.entity.Tag;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 문제 엔티티의 상태 전이에 대한 단위 테스트.
 *
 * <p>서비스 테스트는 저장 후 다시 읽어 결과만 확인하므로, 엔티티 메서드가 스스로 지키는 규칙
 * — 빈 값으로는 덮어쓰지 않는다, 폴더를 옮기면 이전 폴더에서 빠진다 — 은 그 사이에 묻힌다.
 * 여기서는 DB 없이 엔티티 단독으로 그 규칙을 확인한다.
 */
@DisplayName("Problem 엔티티")
class ProblemEntityTest {

    private static final Long USER_ID = 1L;

    private Problem problem;

    @BeforeEach
    void setUp() {
        problem = Problem.from(registerDto("최초 메모", "최초 출처", LocalDateTime.of(2026, 1, 1, 9, 0)), USER_ID);
    }

    private static ProblemRegisterDto registerDto(String memo, String reference, LocalDateTime solvedAt) {
        return new ProblemRegisterDto(null, memo, reference, null, solvedAt);
    }

    private static Folder folder(String name) {
        return Folder.from(new FolderRegisterDto(name, null, null), USER_ID);
    }

    private static ProblemImageData imageData(String url, ProblemImageType type) {
        return ProblemImageData.from(new ProblemImageDataRegisterDto(null, url, type));
    }

    @Nested
    @DisplayName("생성")
    class Creation {

        @Test
        @DisplayName("등록 정보가 그대로 옮겨지고 연관 컬렉션은 빈 상태로 시작한다")
        void copiesRegisterDto() {
            assertThat(problem.getUserId()).isEqualTo(USER_ID);
            assertThat(problem.getMemo()).isEqualTo("최초 메모");
            assertThat(problem.getReference()).isEqualTo("최초 출처");
            assertThat(problem.getSolvedAt()).isEqualTo(LocalDateTime.of(2026, 1, 1, 9, 0));
            assertThat(problem.getFolder()).as("폴더는 별도로 연결한다").isNull();
            assertThat(problem.getProblemImageDataList()).isEmpty();
            assertThat(problem.getProblemTagMappingList()).isEmpty();
            assertThat(problem.getProblemPracticeNoteMappingList()).isEmpty();
        }

        @Test
        @DisplayName("복습 간격은 1, 연속 정답 수는 0으로 시작한다")
        void startsWithDefaultReviewSchedule() {
            assertThat(problem.getReviewInterval()).isEqualTo(1);
            assertThat(problem.getConsecutiveCorrectCount()).isZero();
            assertThat(problem.getNextReviewAt()).as("등록 시점에는 서비스가 채운다").isNull();
        }
    }

    @Nested
    @DisplayName("정보 수정")
    class UpdateProblem {

        @Test
        @DisplayName("메모·출처·풀이 시각이 모두 갱신된다")
        void updatesEveryField() {
            LocalDateTime newSolvedAt = LocalDateTime.of(2026, 2, 2, 10, 30);

            problem.updateProblem(registerDto("새 메모", "새 출처", newSolvedAt));

            assertThat(problem.getMemo()).isEqualTo("새 메모");
            assertThat(problem.getReference()).isEqualTo("새 출처");
            assertThat(problem.getSolvedAt()).isEqualTo(newSolvedAt);
        }

        @Test
        @DisplayName("solvedAt 이 null 이면 기존 풀이 시각을 지우지 않는다")
        void keepsSolvedAtWhenNull() {
            problem.updateProblem(registerDto("새 메모", "새 출처", null));

            assertThat(problem.getSolvedAt())
                    .as("부분 수정 요청이 기존 값을 지워서는 안 된다")
                    .isEqualTo(LocalDateTime.of(2026, 1, 1, 9, 0));
        }

        @ParameterizedTest(name = "memo=\"{0}\"")
        @ValueSource(strings = {"", " ", "\t", "\n"})
        @DisplayName("메모가 공백뿐이면 기존 메모를 유지한다")
        void keepsMemoOnBlankInput(String blankMemo) {
            problem.updateProblem(registerDto(blankMemo, "새 출처", null));

            assertThat(problem.getMemo()).isEqualTo("최초 메모");
            assertThat(problem.getReference()).as("다른 필드는 갱신된다").isEqualTo("새 출처");
        }

        @ParameterizedTest(name = "reference=\"{0}\"")
        @ValueSource(strings = {"", " ", "\t"})
        @DisplayName("출처가 공백뿐이면 기존 출처를 유지한다")
        void keepsReferenceOnBlankInput(String blankReference) {
            problem.updateProblem(registerDto("새 메모", blankReference, null));

            assertThat(problem.getReference()).isEqualTo("최초 출처");
            assertThat(problem.getMemo()).as("다른 필드는 갱신된다").isEqualTo("새 메모");
        }

        @Test
        @DisplayName("모든 값이 null 이면 아무것도 바뀌지 않는다")
        void keepsEverythingWhenAllNull() {
            problem.updateProblem(registerDto(null, null, null));

            assertThat(problem.getMemo()).isEqualTo("최초 메모");
            assertThat(problem.getReference()).isEqualTo("최초 출처");
            assertThat(problem.getSolvedAt()).isEqualTo(LocalDateTime.of(2026, 1, 1, 9, 0));
        }
    }

    @Nested
    @DisplayName("폴더 연결")
    class FolderLink {

        @Test
        @DisplayName("폴더를 연결하면 폴더의 문제 목록에도 들어간다")
        void linksBothSides() {
            Folder target = folder("수학");

            problem.updateFolder(target);

            assertThat(problem.getFolder()).isSameAs(target);
            assertThat(target.getProblemList()).containsExactly(problem);
        }

        @Test
        @DisplayName("폴더를 옮기면 이전 폴더의 문제 목록에서 빠진다")
        void movesBetweenFolders() {
            Folder before = folder("이전");
            Folder after = folder("이후");
            problem.updateFolder(before);

            problem.updateFolder(after);

            assertThat(problem.getFolder()).isSameAs(after);
            assertThat(before.getProblemList())
                    .as("옮긴 뒤에도 이전 폴더에 남아 있으면 폴더별 조회에서 중복으로 보인다")
                    .isEmpty();
            assertThat(after.getProblemList()).containsExactly(problem);
        }
    }

    @Nested
    @DisplayName("이미지 목록")
    class ImageDataList {

        @Test
        @DisplayName("이미지를 추가하면 목록에 쌓인다")
        void addsImageData() {
            ProblemImageData first = imageData("https://s3/problem.png", ProblemImageType.PROBLEM_IMAGE);
            ProblemImageData second = imageData("https://s3/answer.png", ProblemImageType.ANSWER_IMAGE);

            problem.addImageData(first);
            problem.addImageData(second);

            assertThat(problem.getProblemImageDataList()).containsExactly(first, second);
        }

        @Test
        @DisplayName("ProblemImageData 쪽에서 연결해도 양방향이 함께 세워진다")
        void updateProblemLinksBothSides() {
            ProblemImageData image = imageData("https://s3/problem.png", ProblemImageType.PROBLEM_IMAGE);

            image.updateProblem(problem);

            assertThat(image.getProblem()).isSameAs(problem);
            assertThat(problem.getProblemImageDataList()).containsExactly(image);
        }

        @Test
        @DisplayName("새 목록으로 교체하면 기존 항목이 사라진다")
        void replacesList() {
            ProblemImageData old = imageData("https://s3/old.png", ProblemImageType.PROBLEM_IMAGE);
            ProblemImageData replacement = imageData("https://s3/new.png", ProblemImageType.PROBLEM_IMAGE);
            problem.addImageData(old);

            problem.updateImageDataList(List.of(replacement));

            assertThat(problem.getProblemImageDataList())
                    .as("orphanRemoval 이 걸려 있어 목록에서 빠진 이미지는 삭제된다")
                    .containsExactly(replacement);
        }

        @Test
        @DisplayName("null 을 주면 기존 목록을 건드리지 않는다")
        void ignoresNullList() {
            ProblemImageData image = imageData("https://s3/keep.png", ProblemImageType.PROBLEM_IMAGE);
            problem.addImageData(image);

            problem.updateImageDataList(null);

            assertThat(problem.getProblemImageDataList()).containsExactly(image);
        }

        @Test
        @DisplayName("빈 목록으로 교체하면 이미지가 모두 빠진다")
        void clearsWithEmptyList() {
            problem.addImageData(imageData("https://s3/old.png", ProblemImageType.PROBLEM_IMAGE));

            problem.updateImageDataList(List.of());

            assertThat(problem.getProblemImageDataList()).isEmpty();
        }
    }

    @Nested
    @DisplayName("복습노트 · 태그 매핑")
    class Mappings {

        @Test
        @DisplayName("복습노트 매핑을 붙였다 뗄 수 있다")
        void addsAndRemovesPracticeMapping() {
            ProblemPracticeNoteMapping mapping = ProblemPracticeNoteMapping.from();

            problem.addPracticeMappingToProblem(mapping);
            assertThat(problem.getProblemPracticeNoteMappingList()).containsExactly(mapping);

            problem.removePracticeMappingFromProblem(mapping);
            assertThat(problem.getProblemPracticeNoteMappingList())
                    .as("복습노트에서 문제를 빼면 매핑도 함께 끊겨야 한다")
                    .isEmpty();
        }

        @Test
        @DisplayName("태그 매핑을 만들면 문제 쪽 목록에 한 번만 들어가고, 떼면 빠진다")
        void addsAndRemovesTagMapping() {
            Tag tag = Tag.from(USER_ID, "미분", "미분");
            ProblemTagMapping mapping = ProblemTagMapping.from(problem, tag);

            assertThat(problem.getProblemTagMappingList())
                    .as("ProblemTagMapping.from 이 연관관계를 세우므로 별도로 add 하면 중복이 된다")
                    .containsExactly(mapping);

            problem.removeTagMappingFromProblem(mapping);
            assertThat(problem.getProblemTagMappingList()).isEmpty();
        }
    }

    @Nested
    @DisplayName("복습 스케줄")
    class ReviewSchedule {

        @Test
        @DisplayName("예정일·간격·연속 정답 수가 한 번에 갱신된다")
        void updatesSchedule() {
            LocalDate nextReviewAt = LocalDate.of(2026, 3, 10);

            problem.updateReviewSchedule(nextReviewAt, 7, 3);

            assertThat(problem.getNextReviewAt()).isEqualTo(nextReviewAt);
            assertThat(problem.getReviewInterval()).isEqualTo(7);
            assertThat(problem.getConsecutiveCorrectCount()).isEqualTo(3);
        }

        @Test
        @DisplayName("예정일을 null 로 두면 복습 대상에서 빠진다(마스터 처리)")
        void clearsScheduleWhenMastered() {
            problem.updateReviewSchedule(LocalDate.of(2026, 3, 10), 7, 3);

            problem.updateReviewSchedule(null, 30, 5);

            assertThat(problem.getNextReviewAt()).isNull();
            assertThat(problem.getReviewInterval()).isEqualTo(30);
            assertThat(problem.getConsecutiveCorrectCount()).isEqualTo(5);
        }
    }

    @Nested
    @DisplayName("분석 상태 전이")
    class AnalysisTransition {

        @Test
        @DisplayName("연결한 분석 객체가 그대로 보관된다")
        void keepsAnalysis() {
            ProblemAnalysis analysis = ProblemAnalysis.createSkipped(problem);

            problem.updateProblemAnalysis(analysis);

            assertThat(problem.getProblemAnalysis()).isSameAs(analysis);
        }

        @Test
        @DisplayName("createSkipped 는 NOT_STARTED 와 안내 메시지로 시작한다")
        void createsSkipped() {
            ProblemAnalysis analysis = ProblemAnalysis.createSkipped(problem);

            assertThat(analysis.getStatus()).isEqualTo(AnalysisStatus.NOT_STARTED);
            assertThat(analysis.getErrorMessage()).isNotBlank();
            assertThat(analysis.getProblem()).isSameAs(problem);
        }

        @Test
        @DisplayName("createProcessing 은 PROCESSING 으로 시작하고 에러 메시지가 없다")
        void createsProcessing() {
            ProblemAnalysis analysis = ProblemAnalysis.createProcessing(problem);

            assertThat(analysis.getStatus()).isEqualTo(AnalysisStatus.PROCESSING);
            assertThat(analysis.getErrorMessage()).isNull();
        }

        @Test
        @DisplayName("성공 결과를 담으면 COMPLETED 가 되고 분석 내용이 채워진다")
        void completesWithResult() {
            ProblemAnalysis analysis = ProblemAnalysis.createProcessing(problem);

            analysis.updateWithSuccess("수학", "계산", "[\"미분\"]", "풀이", "실수", "팁");

            assertThat(analysis.getStatus()).isEqualTo(AnalysisStatus.COMPLETED);
            assertThat(analysis.getSubject()).isEqualTo("수학");
            assertThat(analysis.getProblemType()).isEqualTo("계산");
            assertThat(analysis.getKeyPoints()).isEqualTo("[\"미분\"]");
            assertThat(analysis.getSolution()).isEqualTo("풀이");
            assertThat(analysis.getCommonMistakes()).isEqualTo("실수");
            assertThat(analysis.getStudyTips()).isEqualTo("팁");
        }

        @Test
        @DisplayName("실패를 기록하면 FAILED 와 사유가 남는다")
        void failsWithMessage() {
            ProblemAnalysis analysis = ProblemAnalysis.createProcessing(problem);

            analysis.updateWithFailure("모델 응답 오류");

            assertThat(analysis.getStatus()).isEqualTo(AnalysisStatus.FAILED);
            assertThat(analysis.getErrorMessage()).isEqualTo("모델 응답 오류");
        }

        @Test
        @DisplayName("재시도를 위해 PROCESSING 으로 되돌리면 이전 실패 사유가 지워진다")
        void processingClearsPreviousError() {
            ProblemAnalysis analysis = ProblemAnalysis.createProcessing(problem);
            analysis.updateWithFailure("모델 응답 오류");

            analysis.updateToProcessing();

            assertThat(analysis.getStatus()).isEqualTo(AnalysisStatus.PROCESSING);
            assertThat(analysis.getErrorMessage())
                    .as("낡은 실패 사유가 남으면 재시도 중인 문제가 실패한 것처럼 보인다")
                    .isNull();
        }

        @Test
        @DisplayName("이미지가 없으면 NO_IMAGE 와 사유가 남는다")
        void marksNoImage() {
            ProblemAnalysis analysis = ProblemAnalysis.createSkipped(problem);

            analysis.updateToNoImage();

            assertThat(analysis.getStatus()).isEqualTo(AnalysisStatus.NO_IMAGE);
            assertThat(analysis.getErrorMessage()).contains("이미지가 없어");
        }

        @Test
        @DisplayName("일일 한도를 넘기면 RATE_LIMIT_EXCEEDED 와 사유가 남는다")
        void marksRateLimitExceeded() {
            ProblemAnalysis analysis = ProblemAnalysis.createSkipped(problem);

            analysis.updateToRateLimitExceeded();

            assertThat(analysis.getStatus()).isEqualTo(AnalysisStatus.RATE_LIMIT_EXCEEDED);
            assertThat(analysis.getErrorMessage()).contains("일일 요청 횟수");
        }
    }

    @Nested
    @DisplayName("이미지 타입 코드 변환")
    class ImageTypeCode {

        @ParameterizedTest(name = "code {0} -> {1}")
        @CsvSource({"1, PROBLEM_IMAGE", "2, ANSWER_IMAGE", "3, SOLVE_IMAGE", "4, PROCESS_IMAGE"})
        @DisplayName("코드로 이미지 타입을 찾는다")
        void resolvesByCode(int code, ProblemImageType expected) {
            assertThat(ProblemImageType.valueOf(code)).isEqualTo(expected);
        }

        @ParameterizedTest(name = "code {0}")
        @ValueSource(ints = {0, 5, -1})
        @DisplayName("없는 코드는 IllegalArgumentException 이다")
        void rejectsUnknownCode(int code) {
            assertThatThrownBy(() -> ProblemImageType.valueOf(code))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining(String.valueOf(code));
        }

        @ParameterizedTest
        @EnumSource(ProblemImageType.class)
        @DisplayName("모든 타입은 코드와 설명을 가지며 코드로 되찾을 수 있다")
        void roundTrips(ProblemImageType type) {
            assertThat(type.getDescription()).isEqualTo(type.name());
            assertThat(ProblemImageType.valueOf(type.getCode())).isEqualTo(type);
        }
    }

    @Nested
    @DisplayName("템플릿 타입 코드 변환")
    class TemplateTypeCode {

        @ParameterizedTest(name = "code {0} -> {1}")
        @CsvSource({"1, SIMPLE_TEMPLATE", "2, CLEAN_TEMPLATE", "3, SPECIAL_TEMPLATE"})
        @DisplayName("코드로 템플릿 타입을 찾는다")
        void resolvesByCode(long code, ProblemTemplateType expected) {
            assertThat(ProblemTemplateType.valueOf(code)).isEqualTo(expected);
        }

        @Test
        @DisplayName("없는 코드는 IllegalArgumentException 이다")
        void rejectsUnknownCode() {
            assertThatThrownBy(() -> ProblemTemplateType.valueOf(99L))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("99");
        }

        @ParameterizedTest
        @EnumSource(ProblemTemplateType.class)
        @DisplayName("모든 타입은 설명을 가지며 코드로 되찾을 수 있다")
        void roundTrips(ProblemTemplateType type) {
            assertThat(type.getDescription()).isNotBlank();
            assertThat(ProblemTemplateType.valueOf(type.getCode())).isEqualTo(type);
        }
    }
}
