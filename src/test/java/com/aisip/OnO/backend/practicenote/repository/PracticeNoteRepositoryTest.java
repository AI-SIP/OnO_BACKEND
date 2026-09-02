package com.aisip.OnO.backend.practicenote.repository;

import com.aisip.OnO.backend.folder.entity.Folder;
import com.aisip.OnO.backend.practicenote.entity.PracticeNote;
import com.aisip.OnO.backend.practicenote.entity.ProblemPracticeNoteMapping;
import com.aisip.OnO.backend.practicenote.support.PracticeNoteTestSupport;
import com.aisip.OnO.backend.problem.entity.Problem;
import com.aisip.OnO.backend.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("PracticeNoteRepository")
class PracticeNoteRepositoryTest extends PracticeNoteTestSupport {

    private Long userId;
    private Long otherUserId;
    private List<Problem> problems;

    @BeforeEach
    void setUpFixtures() {
        User user = fixtures.createUser();
        User otherUser = fixtures.createOtherUser();
        userId = user.getId();
        otherUserId = otherUser.getId();
        Folder folder = fixtures.createRootFolder(userId);
        problems = saveProblems(userId, folder, 5);
    }

    @Nested
    @DisplayName("사용자별 조회")
    class FindByUser {

        @Test
        @DisplayName("본인의 복습노트만 조회된다")
        void findAllByUserId() {
            PracticeNote mine = savePracticeNote(userId, "내 복습", problems.subList(0, 2));
            PracticeNote others = savePracticeNote(otherUserId, "남의 복습", List.of());

            assertThat(practiceNoteRepository.findAllByUserId(userId))
                    .extracting(PracticeNote::getId)
                    .containsExactly(mine.getId())
                    .doesNotContain(others.getId());
        }

        @Test
        @DisplayName("본인의 복습노트 id 만 조회된다")
        void findAllPracticeIdsByUserId() {
            PracticeNote first = savePracticeNote(userId, "복습 1", List.of());
            PracticeNote second = savePracticeNote(userId, "복습 2", List.of());
            savePracticeNote(otherUserId, "남의 복습", List.of());

            assertThat(practiceNoteRepository.findAllPracticeIdsByUserId(userId))
                    .containsExactlyInAnyOrder(first.getId(), second.getId());
        }

        @Test
        @DisplayName("복습노트가 없으면 빈 결과를 준다")
        void findAllByUserIdWithoutPracticeNotes() {
            assertThat(practiceNoteRepository.findAllByUserId(otherUserId)).isEmpty();
            assertThat(practiceNoteRepository.findAllPracticeIdsByUserId(otherUserId)).isEmpty();
        }

        @Test
        @DisplayName("상세 조회는 문제 매핑까지 함께 가져오고 id 오름차순으로 준다")
        void findAllUserPracticeNotesWithDetails() {
            PracticeNote first = savePracticeNote(userId, "복습 1", problems.subList(0, 2));
            PracticeNote second = savePracticeNote(userId, "복습 2", problems.subList(2, 5));
            savePracticeNote(otherUserId, "남의 복습", List.of());

            List<PracticeNote> practiceNotes = practiceNoteRepository.findAllUserPracticeNotesWithDetails(userId);

            assertThat(practiceNotes)
                    .extracting(PracticeNote::getId)
                    .containsExactly(first.getId(), second.getId());
            assertThat(practiceNotes.get(0).getProblemPracticeNoteMappingList())
                    .as("fetch join 이라 트랜잭션 밖에서도 매핑에 접근할 수 있다")
                    .hasSize(2);
            assertThat(practiceNotes.get(1).getProblemPracticeNoteMappingList()).hasSize(3);
        }

        @Test
        @DisplayName("단건 상세 조회도 매핑을 함께 가져온다")
        void findPracticeNoteWithDetails() {
            PracticeNote practiceNote = savePracticeNote(userId, "복습", problems.subList(0, 3));

            assertThat(practiceNoteRepository.findPracticeNoteWithDetails(practiceNote.getId()))
                    .isPresent()
                    .hasValueSatisfying(found ->
                            assertThat(found.getProblemPracticeNoteMappingList()).hasSize(3));
            assertThat(practiceNoteRepository.findPracticeNoteWithDetails(nonExistentPracticeNoteId())).isEmpty();
        }
    }

    @Nested
    @DisplayName("문제 매핑")
    class ProblemMapping {

        @Test
        @DisplayName("복습노트에 이미 담긴 문제인지 확인한다")
        void checkProblemAlreadyMatchingWithPractice() {
            PracticeNote practiceNote = savePracticeNote(userId, "복습", problems.subList(0, 1));

            assertThat(practiceNoteRepository
                    .checkProblemAlreadyMatchingWithPractice(practiceNote.getId(), problems.get(0).getId()))
                    .isTrue();
            assertThat(practiceNoteRepository
                    .checkProblemAlreadyMatchingWithPractice(practiceNote.getId(), problems.get(1).getId()))
                    .isFalse();
        }

        @Test
        @DisplayName("문제 id 목록은 중복 없이 오름차순으로 나온다")
        void findProblemIdListByPracticeNoteId() {
            PracticeNote practiceNote = savePracticeNote(userId, "복습", problems.subList(0, 3));

            assertThat(practiceNoteRepository.findProblemIdListByPracticeNoteId(practiceNote.getId()))
                    .containsExactlyElementsOf(problemIdsOf(problems.subList(0, 3)));
        }

        @Test
        @DisplayName("문제가 없는 복습노트는 빈 목록을 준다")
        void findProblemIdListForEmptyPracticeNote() {
            PracticeNote practiceNote = savePracticeNote(userId, "복습", List.of());

            assertThat(practiceNoteRepository.findProblemIdListByPracticeNoteId(practiceNote.getId())).isEmpty();
        }

        @Test
        @DisplayName("문제 id 와 복습노트 id 로 매핑 한 건을 찾는다")
        void findMappingByProblemAndPracticeNote() {
            PracticeNote practiceNote = savePracticeNote(userId, "복습", problems.subList(0, 1));

            assertThat(problemPracticeNoteMappingRepository
                    .findProblemPracticeNoteMappingByProblemIdAndPracticeNoteId(
                            problems.get(0).getId(), practiceNote.getId()))
                    .isPresent();
            assertThat(problemPracticeNoteMappingRepository
                    .findProblemPracticeNoteMappingByProblemIdAndPracticeNoteId(
                            problems.get(1).getId(), practiceNote.getId()))
                    .isEmpty();
        }

        @Test
        @DisplayName("복습노트별 문제 수를 집계한다")
        void countProblemsByPracticeNoteIds() {
            PracticeNote first = savePracticeNote(userId, "복습 1", problems.subList(0, 2));
            PracticeNote second = savePracticeNote(userId, "복습 2", problems.subList(2, 5));
            PracticeNote empty = savePracticeNote(userId, "빈 복습", List.of());

            Map<Long, Long> counts = practiceNoteRepository
                    .countProblemsByPracticeNoteIds(List.of(first.getId(), second.getId(), empty.getId())).stream()
                    .collect(Collectors.toMap(row -> (Long) row[0], row -> (Long) row[1]));

            assertThat(counts.get(first.getId())).isEqualTo(2L);
            assertThat(counts.get(second.getId())).isEqualTo(3L);
            assertThat(counts)
                    .as("문제가 없는 복습노트는 집계에 포함되지 않는다")
                    .doesNotContainKey(empty.getId());
        }
    }

    @Nested
    @DisplayName("매핑 삭제")
    class DeleteMapping {

        @Test
        @DisplayName("특정 복습노트에서 문제 하나를 뺀다")
        void deleteProblemFromPractice() {
            PracticeNote first = savePracticeNote(userId, "복습 1", problems.subList(0, 2));
            PracticeNote second = savePracticeNote(userId, "복습 2", problems.subList(0, 2));

            inTransaction(() ->
                    practiceNoteRepository.deleteProblemFromPractice(first.getId(), problems.get(0).getId()));

            assertThat(practiceNoteRepository.findProblemIdListByPracticeNoteId(first.getId()))
                    .containsExactly(problems.get(1).getId());
            assertThat(practiceNoteRepository.findProblemIdListByPracticeNoteId(second.getId()))
                    .as("다른 복습노트의 매핑은 그대로")
                    .hasSize(2);
        }

        @Test
        @DisplayName("모든 복습노트에서 문제 하나를 뺀다")
        void deleteProblemFromAllPractice() {
            PracticeNote first = savePracticeNote(userId, "복습 1", problems.subList(0, 2));
            PracticeNote second = savePracticeNote(userId, "복습 2", problems.subList(0, 3));

            inTransaction(() -> practiceNoteRepository.deleteProblemFromAllPractice(problems.get(0).getId()));

            assertThat(practiceNoteRepository.findProblemIdListByPracticeNoteId(first.getId()))
                    .containsExactly(problems.get(1).getId());
            assertThat(practiceNoteRepository.findProblemIdListByPracticeNoteId(second.getId()))
                    .containsExactlyElementsOf(problemIdsOf(problems.subList(1, 3)));
        }

        @Test
        @DisplayName("여러 문제의 매핑을 한 번에 뺀다")
        void deleteProblemsFromAllPractice() {
            PracticeNote practiceNote = savePracticeNote(userId, "복습", problems.subList(0, 4));

            inTransaction(() -> practiceNoteRepository.deleteProblemsFromAllPractice(
                    List.of(problems.get(0).getId(), problems.get(2).getId())));

            assertThat(practiceNoteRepository.findProblemIdListByPracticeNoteId(practiceNote.getId()))
                    .containsExactly(problems.get(1).getId(), problems.get(3).getId());
        }

        @Test
        @DisplayName("복습노트의 모든 매핑을 조회한다")
        void findAllByPracticeNoteId() {
            PracticeNote practiceNote = savePracticeNote(userId, "복습", problems.subList(0, 3));

            assertThat(problemPracticeNoteMappingRepository.findAllByPracticeNoteId(practiceNote.getId()))
                    .extracting(ProblemPracticeNoteMapping::getId)
                    .hasSize(3);
        }
    }

    @Nested
    @DisplayName("커서 기반 조회")
    class CursorQuery {

        @Test
        @DisplayName("문제가 여러 개 매핑돼 있어도 복습노트 단위로 페이지를 끊는다")
        void findPracticeNotesByUserWithCursor() {
            PracticeNote first = savePracticeNote(userId, "복습 1", problems.subList(0, 3));
            PracticeNote second = savePracticeNote(userId, "복습 2", problems.subList(1, 4));
            PracticeNote third = savePracticeNote(userId, "복습 3", problems.subList(2, 5));
            savePracticeNote(otherUserId, "남의 복습", List.of());

            List<PracticeNote> firstPage = practiceNoteRepository.findPracticeNotesByUserWithCursor(userId, null, 2);

            assertThat(firstPage)
                    .as("hasNext 판단을 위해 size + 1 개를 준다")
                    .extracting(PracticeNote::getId)
                    .containsExactly(first.getId(), second.getId(), third.getId());

            List<PracticeNote> secondPage =
                    practiceNoteRepository.findPracticeNotesByUserWithCursor(userId, second.getId(), 2);

            assertThat(secondPage)
                    .extracting(PracticeNote::getId)
                    .containsExactly(third.getId());
        }

        @Test
        @DisplayName("커서가 마지막 복습노트면 빈 결과를 준다")
        void findPracticeNotesByUserWithCursorAtEnd() {
            PracticeNote practiceNote = savePracticeNote(userId, "복습", List.of());

            assertThat(practiceNoteRepository.findPracticeNotesByUserWithCursor(userId, practiceNote.getId(), 10))
                    .isEmpty();
        }
    }

    @Nested
    @DisplayName("통계 집계")
    class Statistics {

        @Test
        @DisplayName("기간 내 생성된 복습노트 수를 센다")
        void countByCreatedAtBetween() {
            savePracticeNote(userId, "복습 1", List.of());
            savePracticeNote(otherUserId, "복습 2", List.of());

            LocalDateTime from = LocalDate.now().minusDays(1).atStartOfDay();
            LocalDateTime to = LocalDate.now().atTime(LocalTime.MAX);

            assertThat(practiceNoteRepository.countByCreatedAtBetween(from, to)).isEqualTo(2L);
            assertThat(practiceNoteRepository.countByCreatedAtBetween(
                    from.minusDays(10), from.minusDays(9)))
                    .isZero();
        }

        @Test
        @DisplayName("일자별 생성 수를 집계한다")
        void countDailyPracticeNotes() {
            savePracticeNote(userId, "복습 1", List.of());
            savePracticeNote(userId, "복습 2", List.of());

            List<Object[]> rows = practiceNoteRepository.countDailyPracticeNotes(
                    LocalDate.now().minusDays(1).atStartOfDay(), LocalDate.now().atTime(LocalTime.MAX));

            assertThat(rows).singleElement().satisfies(row -> assertThat((Long) row[1]).isEqualTo(2L));
        }
    }
}
