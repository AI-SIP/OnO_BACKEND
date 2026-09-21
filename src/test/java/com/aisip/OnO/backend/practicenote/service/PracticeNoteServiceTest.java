package com.aisip.OnO.backend.practicenote.service;

import com.aisip.OnO.backend.common.emoji.CustomEmojiErrorCase;
import com.aisip.OnO.backend.common.exception.ApplicationException;
import com.aisip.OnO.backend.common.response.CursorPageResponse;
import com.aisip.OnO.backend.folder.entity.Folder;
import com.aisip.OnO.backend.practicenote.dto.PracticeNoteCompleteRequestDto;
import com.aisip.OnO.backend.practicenote.dto.PracticeNoteDetailResponseDto;
import com.aisip.OnO.backend.practicenote.dto.PracticeNoteRegisterDto;
import com.aisip.OnO.backend.practicenote.dto.PracticeNoteThumbnailResponseDto;
import com.aisip.OnO.backend.practicenote.dto.PracticeNoteUpdateDto;
import com.aisip.OnO.backend.practicenote.dto.PracticeNotificationRegisterDto;
import com.aisip.OnO.backend.practicenote.entity.PracticeNote;
import com.aisip.OnO.backend.practicenote.exception.PracticeNoteErrorCase;
import com.aisip.OnO.backend.practicenote.support.PracticeNoteTestSupport;
import com.aisip.OnO.backend.problem.entity.Problem;
import com.aisip.OnO.backend.problem.exception.ProblemErrorCase;
import com.aisip.OnO.backend.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@DisplayName("PracticeNoteService")
class PracticeNoteServiceTest extends PracticeNoteTestSupport {

    private Long userId;
    private Long otherUserId;
    private Folder folder;
    private List<Problem> problems;

    @BeforeEach
    void setUpUsersAndProblems() {
        User user = fixtures.createUser();
        User otherUser = fixtures.createOtherUser();
        userId = user.getId();
        otherUserId = otherUser.getId();
        folder = fixtures.createRootFolder(userId);
        problems = saveProblems(userId, folder, 5);
    }

    @Nested
    @DisplayName("복습노트 등록")
    class RegisterPractice {

        @Test
        @DisplayName("문제를 매핑한 복습노트를 만든다")
        void registersPracticeWithProblems() {
            Long practiceNoteId = practiceNoteService.registerPractice(
                    new PracticeNoteRegisterDto(null, "1회차 복습", problemIdsOf(problems.subList(0, 3)), null),
                    userId);

            PracticeNote saved = practiceNoteRepository.findById(practiceNoteId).orElseThrow();
            assertThat(saved.getTitle()).isEqualTo("1회차 복습");
            assertThat(saved.getUserId()).isEqualTo(userId);
            assertThat(saved.getPracticeCount()).isZero();
            assertThat(saved.getLastSolvedAt()).isNull();
            assertThat(practiceNoteRepository.findProblemIdListByPracticeNoteId(practiceNoteId))
                    .containsExactlyElementsOf(problemIdsOf(problems.subList(0, 3)));
        }

        @Test
        @DisplayName("알림 설정이 있으면 스케줄을 등록한다")
        void registersNotificationSchedule() {
            PracticeNotificationRegisterDto notification = dailyNotification();

            Long practiceNoteId = practiceNoteService.registerPractice(
                    new PracticeNoteRegisterDto(null, "알림 복습", List.of(), notification), userId);

            verify(practiceNotificationScheduler)
                    .schedulePracticeNotification(userId, practiceNoteId, "알림 복습", notification);
            assertThat(practiceNoteRepository.findById(practiceNoteId).orElseThrow().getPracticeNotification())
                    .as("알림 설정이 복습노트에 저장된다")
                    .isNotNull();
        }

        @Test
        @DisplayName("알림 설정이 없으면 스케줄을 등록하지 않는다")
        void doesNotRegisterScheduleWithoutNotification() {
            practiceNoteService.registerPractice(
                    new PracticeNoteRegisterDto(null, "알림 없는 복습", List.of(), null), userId);

            verify(practiceNotificationScheduler, never())
                    .schedulePracticeNotification(anyLong(), anyLong(), anyString(), any());
        }

        @Test
        @DisplayName("문제 리스트가 null 이어도 복습노트를 만든다")
        void registersPracticeWithNullProblemList() {
            Long practiceNoteId = practiceNoteService.registerPractice(
                    new PracticeNoteRegisterDto(null, "빈 복습", null, null), userId);

            assertThat(practiceNoteRepository.findProblemIdListByPracticeNoteId(practiceNoteId)).isEmpty();
        }

        @Test
        @DisplayName("같은 문제를 두 번 넣어도 매핑은 하나만 생긴다")
        void doesNotDuplicateMapping() {
            Long problemId = problems.get(0).getId();

            Long practiceNoteId = practiceNoteService.registerPractice(
                    new PracticeNoteRegisterDto(null, "중복 복습", List.of(problemId, problemId), null), userId);

            assertThat(problemPracticeNoteMappingRepository.findAllByPracticeNoteId(practiceNoteId)).hasSize(1);
        }

        @Test
        @DisplayName("다른 사용자의 문제를 넣으면 예외가 발생하고 복습노트도 남지 않는다")
        void rejectsOtherUserProblem() {
            Folder otherUserFolder = fixtures.createRootFolder(otherUserId);
            Problem otherUserProblem = saveProblem(otherUserId, otherUserFolder);
            PracticeNoteRegisterDto registerDto =
                    new PracticeNoteRegisterDto(null, "침입 복습", List.of(otherUserProblem.getId()), null);

            assertThatThrownBy(() -> practiceNoteService.registerPractice(registerDto, userId))
                    .isInstanceOf(ApplicationException.class)
                    .hasMessageContaining(ProblemErrorCase.PROBLEM_USER_UNMATCHED.getMessage());

            assertThat(practiceNoteRepository.findAllByUserId(userId))
                    .as("트랜잭션이 롤백되어 복습노트가 만들어지지 않는다")
                    .isEmpty();
        }

        @Test
        @DisplayName("존재하지 않는 문제를 넣으면 PROBLEM_NOT_FOUND 예외가 발생한다")
        void rejectsMissingProblem() {
            Long missingProblemId = problems.get(problems.size() - 1).getId() + 1_000L;
            PracticeNoteRegisterDto registerDto =
                    new PracticeNoteRegisterDto(null, "없는 문제", List.of(missingProblemId), null);

            assertThatThrownBy(() -> practiceNoteService.registerPractice(registerDto, userId))
                    .isInstanceOf(ApplicationException.class)
                    .hasMessageContaining(ProblemErrorCase.PROBLEM_NOT_FOUND.getMessage());
        }

        @Test
        @DisplayName("기본 복습노트는 제목이 복습 세트이고 문제가 없다")
        void registersDefaultPractice() {
            Long practiceNoteId = practiceNoteService.registerDefaultPractice(userId);

            PracticeNote saved = practiceNoteRepository.findById(practiceNoteId).orElseThrow();
            assertThat(saved.getTitle()).isEqualTo("복습 세트");
            assertThat(saved.getPracticeNotification()).isNull();
            assertThat(problemPracticeNoteMappingRepository.findAllByPracticeNoteId(practiceNoteId)).isEmpty();
        }
    }

    @Nested
    @DisplayName("복습노트 조회")
    class FindPractice {

        @Test
        @DisplayName("상세 조회는 문제 id 목록과 알림 설정을 함께 준다")
        void findPracticeNoteDetail() {
            PracticeNote practiceNote = savePracticeNote(
                    userId, "상세 복습", problems.subList(0, 2), weeklyNotification(List.of(1, 3)));

            PracticeNoteDetailResponseDto detail =
                    practiceNoteService.findPracticeNoteDetail(practiceNote.getId(), userId);

            assertThat(detail.practiceNoteId()).isEqualTo(practiceNote.getId());
            assertThat(detail.practiceTitle()).isEqualTo("상세 복습");
            assertThat(detail.practiceCount()).isZero();
            assertThat(detail.lastSolvedAt()).isNull();
            assertThat(detail.problemIdList())
                    .containsExactlyElementsOf(problemIdsOf(problems.subList(0, 2)));
            assertThat(detail.practiceNotification().repeatType()).isEqualTo("weekly");
            assertThat(detail.practiceNotification().weekDays()).containsExactly(1, 3);
            assertThat(detail.practiceNotification().hour()).isEqualTo(21);
        }

        @Test
        @DisplayName("알림이 없는 복습노트는 알림 정보가 비어 있다")
        void findPracticeNoteDetailWithoutNotification() {
            PracticeNote practiceNote = savePracticeNote(userId, "알림 없음", List.of());

            PracticeNoteDetailResponseDto detail =
                    practiceNoteService.findPracticeNoteDetail(practiceNote.getId(), userId);

            assertThat(detail.practiceNotification()).isNull();
            assertThat(detail.problemIdList()).isEmpty();
        }

        @Test
        @DisplayName("다른 사용자의 복습노트는 조회할 수 없다")
        void cannotFindOtherUserPractice() {
            PracticeNote practiceNote = savePracticeNote(userId, "내 복습", problems.subList(0, 1));

            assertThatThrownBy(() -> practiceNoteService.findPracticeNoteDetail(practiceNote.getId(), otherUserId))
                    .isInstanceOf(ApplicationException.class)
                    .hasMessageContaining(PracticeNoteErrorCase.PRACTICE_NOTE_USER_UNMATCHED.getMessage());
        }

        @Test
        @DisplayName("존재하지 않는 복습노트를 조회하면 PRACTICE_NOTE_NOT_FOUND 예외가 발생한다")
        void findMissingPractice() {
            Long missingId = nonExistentPracticeNoteId();

            assertThatThrownBy(() -> practiceNoteService.findPracticeNoteDetail(missingId, userId))
                    .isInstanceOf(ApplicationException.class)
                    .hasMessageContaining(PracticeNoteErrorCase.PRACTICE_NOTE_NOT_FOUND.getMessage());
        }

        @Test
        @DisplayName("썸네일 목록에는 본인 복습노트만 담긴다")
        void findAllPracticeThumbnailsByUser() {
            PracticeNote mine = savePracticeNote(userId, "내 복습", problems.subList(0, 1));
            PracticeNote others = savePracticeNote(otherUserId, "남의 복습", List.of());

            List<PracticeNoteThumbnailResponseDto> thumbnails =
                    practiceNoteService.findAllPracticeThumbnailsByUser(userId);

            assertThat(thumbnails)
                    .extracting(PracticeNoteThumbnailResponseDto::practiceNoteId)
                    .containsExactly(mine.getId())
                    .doesNotContain(others.getId());
        }

        @Test
        @DisplayName("전체 상세 목록은 복습노트마다 문제 id 를 채워 준다")
        void findAllPracticesByUser() {
            PracticeNote first = savePracticeNote(userId, "복습 1", problems.subList(0, 2));
            PracticeNote second = savePracticeNote(userId, "복습 2", problems.subList(2, 5));
            savePracticeNote(otherUserId, "남의 복습", List.of());

            List<PracticeNoteDetailResponseDto> details = practiceNoteService.findAllPracticesByUser(userId);

            assertThat(details)
                    .extracting(PracticeNoteDetailResponseDto::practiceNoteId)
                    .containsExactly(first.getId(), second.getId());
            assertThat(details.get(0).problemIdList())
                    .containsExactlyElementsOf(problemIdsOf(problems.subList(0, 2)));
            assertThat(details.get(1).problemIdList())
                    .containsExactlyElementsOf(problemIdsOf(problems.subList(2, 5)));
        }

        @Test
        @DisplayName("커서 조회는 size 만큼 끊고 다음 커서를 준다")
        void findPracticeThumbnailsByUserWithCursor() {
            PracticeNote first = savePracticeNote(userId, "복습 1", problems.subList(0, 2));
            PracticeNote second = savePracticeNote(userId, "복습 2", problems.subList(2, 4));
            PracticeNote third = savePracticeNote(userId, "복습 3", List.of());
            savePracticeNote(otherUserId, "남의 복습", List.of());

            CursorPageResponse<PracticeNoteThumbnailResponseDto> firstPage =
                    practiceNoteService.findPracticeThumbnailsByUserWithCursor(userId, null, 2);

            assertThat(firstPage.content())
                    .extracting(PracticeNoteThumbnailResponseDto::practiceNoteId)
                    .containsExactly(first.getId(), second.getId());
            assertThat(firstPage.hasNext()).isTrue();
            assertThat(firstPage.nextCursor()).isEqualTo(second.getId());

            CursorPageResponse<PracticeNoteThumbnailResponseDto> secondPage =
                    practiceNoteService.findPracticeThumbnailsByUserWithCursor(userId, firstPage.nextCursor(), 2);

            assertThat(secondPage.content())
                    .extracting(PracticeNoteThumbnailResponseDto::practiceNoteId)
                    .as("다른 사용자의 복습노트는 섞이지 않는다")
                    .containsExactly(third.getId());
            assertThat(secondPage.hasNext()).isFalse();
            assertThat(secondPage.nextCursor()).isNull();
        }
    }

    @Nested
    @DisplayName("복습 완료 처리")
    class CompletePractice {

        @Test
        @DisplayName("복습 완료 시 횟수가 늘고 마지막 복습 시각이 기록된다")
        void addPracticeNoteCount() {
            PracticeNote practiceNote = savePracticeNote(userId, "완료 복습", problems.subList(0, 1));

            practiceNoteService.addPracticeNoteCount(userId, practiceNote.getId());

            PracticeNote updated = practiceNoteRepository.findById(practiceNote.getId()).orElseThrow();
            assertThat(updated.getPracticeCount()).isEqualTo(1L);
            assertThat(updated.getLastSolvedAt()).isNotNull();
            assertThat(updated.getLastSessionMoodEmojiKey()).isNull();
        }

        @Test
        @DisplayName("여러 번 완료하면 횟수가 누적된다")
        void addPracticeNoteCountTwice() {
            PracticeNote practiceNote = savePracticeNote(userId, "완료 복습", List.of());

            practiceNoteService.addPracticeNoteCount(userId, practiceNote.getId());
            practiceNoteService.addPracticeNoteCount(userId, practiceNote.getId());

            assertThat(practiceNoteRepository.findById(practiceNote.getId()).orElseThrow().getPracticeCount())
                    .isEqualTo(2L);
        }

        @Test
        @DisplayName("완료 소감 이모지는 상세·썸네일 응답에 그대로 실린다")
        void addPracticeNoteCountWithMoodEmoji() {
            PracticeNote practiceNote = savePracticeNote(userId, "완료 복습", List.of());

            practiceNoteService.addPracticeNoteCount(userId, practiceNote.getId(),
                    new PracticeNoteCompleteRequestDto("success_checkmark"));

            assertThat(practiceNoteService.findPracticeNoteDetail(practiceNote.getId(), userId)
                    .lastSessionMoodEmojiKey())
                    .isEqualTo("success_checkmark");
            assertThat(practiceNoteService.findAllPracticeThumbnailsByUser(userId))
                    .singleElement()
                    .extracting(PracticeNoteThumbnailResponseDto::lastSessionMoodEmojiKey)
                    .isEqualTo("success_checkmark");
        }

        @Test
        @DisplayName("허용되지 않은 이모지는 예외를 내고 완료 횟수도 늘지 않는다")
        void rejectsInvalidMoodEmoji() {
            PracticeNote practiceNote = savePracticeNote(userId, "완료 복습", List.of());
            PracticeNoteCompleteRequestDto request = new PracticeNoteCompleteRequestDto("not_supported");

            assertThatThrownBy(() -> practiceNoteService.addPracticeNoteCount(userId, practiceNote.getId(), request))
                    .isInstanceOf(ApplicationException.class)
                    .hasMessageContaining(CustomEmojiErrorCase.INVALID_EMOJI_KEY.getMessage());

            assertThat(practiceNoteRepository.findById(practiceNote.getId()).orElseThrow().getPracticeCount())
                    .isZero();
        }

        @Test
        @DisplayName("다른 사용자의 복습노트는 완료 처리할 수 없다")
        void cannotCompleteOtherUserPractice() {
            PracticeNote practiceNote = savePracticeNote(userId, "내 복습", List.of());

            assertThatThrownBy(() -> practiceNoteService.addPracticeNoteCount(otherUserId, practiceNote.getId()))
                    .isInstanceOf(ApplicationException.class)
                    .hasMessageContaining(PracticeNoteErrorCase.PRACTICE_NOTE_USER_UNMATCHED.getMessage());

            assertThat(practiceNoteRepository.findById(practiceNote.getId()).orElseThrow().getPracticeCount())
                    .isZero();
        }

        @Test
        @DisplayName("존재하지 않는 복습노트는 완료 처리할 수 없다")
        void cannotCompleteMissingPractice() {
            Long missingId = nonExistentPracticeNoteId();

            assertThatThrownBy(() -> practiceNoteService.addPracticeNoteCount(userId, missingId))
                    .isInstanceOf(ApplicationException.class)
                    .hasMessageContaining(PracticeNoteErrorCase.PRACTICE_NOTE_NOT_FOUND.getMessage());
        }
    }

    @Nested
    @DisplayName("복습노트 수정")
    class UpdatePractice {

        @Test
        @DisplayName("제목과 문제 구성을 함께 바꾼다")
        void updatesTitleAndProblems() {
            PracticeNote practiceNote = savePracticeNote(userId, "예전 제목", problems.subList(0, 3));

            practiceNoteService.updatePracticeInfo(userId, new PracticeNoteUpdateDto(
                    practiceNote.getId(),
                    "새 제목",
                    List.of(problems.get(3).getId(), problems.get(4).getId()),
                    List.of(problems.get(0).getId()),
                    null));

            assertThat(practiceNoteRepository.findById(practiceNote.getId()).orElseThrow().getTitle())
                    .isEqualTo("새 제목");
            assertThat(practiceNoteRepository.findProblemIdListByPracticeNoteId(practiceNote.getId()))
                    .as("빠진 문제 하나, 더해진 문제 둘")
                    .containsExactlyElementsOf(problemIdsOf(problems.subList(1, 5)));
        }

        @Test
        @DisplayName("제목이 공백이면 기존 제목을 유지한다")
        void keepsTitleWhenBlank() {
            PracticeNote practiceNote = savePracticeNote(userId, "원래 제목", List.of());

            practiceNoteService.updatePracticeInfo(userId, new PracticeNoteUpdateDto(
                    practiceNote.getId(), "   ", List.of(), List.of(), null));

            assertThat(practiceNoteRepository.findById(practiceNote.getId()).orElseThrow().getTitle())
                    .isEqualTo("원래 제목");
        }

        @Test
        @DisplayName("문제 id 리스트가 null 이어도 수정할 수 있다")
        void handlesNullProblemLists() {
            PracticeNote practiceNote = savePracticeNote(userId, "원래 제목", problems.subList(0, 1));

            practiceNoteService.updatePracticeInfo(userId, new PracticeNoteUpdateDto(
                    practiceNote.getId(), "새 제목", null, null, null));

            assertThat(practiceNoteRepository.findById(practiceNote.getId()).orElseThrow().getTitle())
                    .isEqualTo("새 제목");
            assertThat(practiceNoteRepository.findProblemIdListByPracticeNoteId(practiceNote.getId()))
                    .as("문제 구성은 그대로")
                    .containsExactly(problems.get(0).getId());
        }

        @Test
        @DisplayName("매핑되지 않은 문제를 빼도 아무 일도 일어나지 않는다")
        void removingUnmappedProblemIsNoOp() {
            PracticeNote practiceNote = savePracticeNote(userId, "복습", problems.subList(0, 1));

            practiceNoteService.updatePracticeInfo(userId, new PracticeNoteUpdateDto(
                    practiceNote.getId(), null, List.of(), List.of(problems.get(4).getId()), null));

            assertThat(practiceNoteRepository.findProblemIdListByPracticeNoteId(practiceNote.getId()))
                    .containsExactly(problems.get(0).getId());
        }

        @Test
        @DisplayName("알림 설정을 주면 스케줄을 갱신하고 복습노트에도 저장한다")
        void updatesNotificationSchedule() {
            PracticeNote practiceNote = savePracticeNote(userId, "복습", List.of(), dailyNotification());
            PracticeNotificationRegisterDto newNotification = weeklyNotification(List.of(2, 4, 6));

            practiceNoteService.updatePracticeInfo(userId, new PracticeNoteUpdateDto(
                    practiceNote.getId(), null, List.of(), List.of(), newNotification));

            verify(practiceNotificationScheduler)
                    .updateNotification(userId, practiceNote.getId(), "복습", newNotification);
            assertThat(practiceNoteRepository.findById(practiceNote.getId()).orElseThrow()
                    .getPracticeNotification().getRepeatType())
                    .isEqualTo("weekly");
        }

        @Test
        @DisplayName("알림 설정을 비우면 스케줄을 지우고 복습노트 알림도 사라진다")
        void clearsNotificationSchedule() {
            PracticeNote practiceNote = savePracticeNote(userId, "복습", List.of(), dailyNotification());

            practiceNoteService.updatePracticeInfo(userId, new PracticeNoteUpdateDto(
                    practiceNote.getId(), null, List.of(), List.of(), null));

            verify(practiceNotificationScheduler).deleteNotification(practiceNote.getId());
            assertThat(practiceNoteRepository.findById(practiceNote.getId()).orElseThrow().getPracticeNotification())
                    .isNull();
        }

        @Test
        @DisplayName("다른 사용자의 문제는 복습노트에 넣을 수 없다")
        void cannotAddOtherUserProblem() {
            PracticeNote practiceNote = savePracticeNote(userId, "복습", List.of());
            Folder otherUserFolder = fixtures.createRootFolder(otherUserId);
            Problem otherUserProblem = saveProblem(otherUserId, otherUserFolder);
            PracticeNoteUpdateDto updateDto = new PracticeNoteUpdateDto(
                    practiceNote.getId(), null, List.of(otherUserProblem.getId()), List.of(), null);

            assertThatThrownBy(() -> practiceNoteService.updatePracticeInfo(userId, updateDto))
                    .isInstanceOf(ApplicationException.class)
                    .hasMessageContaining(ProblemErrorCase.PROBLEM_USER_UNMATCHED.getMessage());

            assertThat(problemPracticeNoteMappingRepository.findAllByPracticeNoteId(practiceNote.getId())).isEmpty();
        }

        @Test
        @DisplayName("다른 사용자의 복습노트는 수정할 수 없다")
        void cannotUpdateOtherUserPractice() {
            PracticeNote practiceNote = savePracticeNote(userId, "내 복습", List.of());
            PracticeNoteUpdateDto updateDto = new PracticeNoteUpdateDto(
                    practiceNote.getId(), "가로채기", List.of(), List.of(), null);

            assertThatThrownBy(() -> practiceNoteService.updatePracticeInfo(otherUserId, updateDto))
                    .isInstanceOf(ApplicationException.class)
                    .hasMessageContaining(PracticeNoteErrorCase.PRACTICE_NOTE_USER_UNMATCHED.getMessage());

            assertThat(practiceNoteRepository.findById(practiceNote.getId()).orElseThrow().getTitle())
                    .isEqualTo("내 복습");
        }

        @Test
        @DisplayName("존재하지 않는 복습노트는 수정할 수 없다")
        void cannotUpdateMissingPractice() {
            PracticeNoteUpdateDto updateDto = new PracticeNoteUpdateDto(
                    nonExistentPracticeNoteId(), "제목", List.of(), List.of(), null);

            assertThatThrownBy(() -> practiceNoteService.updatePracticeInfo(userId, updateDto))
                    .isInstanceOf(ApplicationException.class)
                    .hasMessageContaining(PracticeNoteErrorCase.PRACTICE_NOTE_NOT_FOUND.getMessage());
        }
    }

    @Nested
    @DisplayName("복습노트 삭제")
    class DeletePractice {

        @Test
        @DisplayName("복습노트를 지우면 매핑도 지워지고 문제는 남는다")
        void deletePracticeRemovesMappingsOnly() {
            PracticeNote practiceNote = savePracticeNote(userId, "복습", problems.subList(0, 2));

            practiceNoteService.deletePractice(practiceNote.getId(), userId);

            assertThat(practiceNoteRepository.findById(practiceNote.getId())).isEmpty();
            assertThat(problemPracticeNoteMappingRepository.findAllByPracticeNoteId(practiceNote.getId())).isEmpty();
            assertThat(problemRepository.findAllByUserId(userId))
                    .as("문제 자체는 지워지지 않는다")
                    .hasSize(problems.size());
        }

        @Test
        @DisplayName("복습노트를 지우면 예약된 복습 알림도 함께 지운다")
        void deletePracticeCancelsNotification() {
            PracticeNote practiceNote = savePracticeNote(userId, "알림 복습", List.of(), dailyNotification());

            practiceNoteService.deletePractice(practiceNote.getId(), userId);

            verify(practiceNotificationScheduler).deleteNotification(practiceNote.getId());
        }

        @Test
        @DisplayName("여러 복습노트를 한 번에 지운다")
        void deletePractices() {
            PracticeNote first = savePracticeNote(userId, "복습 1", problems.subList(0, 1));
            PracticeNote second = savePracticeNote(userId, "복습 2", problems.subList(1, 2));
            PracticeNote kept = savePracticeNote(userId, "복습 3", List.of());

            practiceNoteService.deletePractices(userId, List.of(first.getId(), second.getId()));

            assertThat(practiceNoteRepository.findAllByUserId(userId))
                    .extracting(PracticeNote::getId)
                    .containsExactly(kept.getId());
        }

        @Test
        @DisplayName("다른 사용자의 복습노트는 지울 수 없다")
        void cannotDeleteOtherUserPractice() {
            PracticeNote practiceNote = savePracticeNote(userId, "내 복습", List.of());
            List<Long> deleteIds = List.of(practiceNote.getId());

            assertThatThrownBy(() -> practiceNoteService.deletePractices(otherUserId, deleteIds))
                    .isInstanceOf(ApplicationException.class)
                    .hasMessageContaining(PracticeNoteErrorCase.PRACTICE_NOTE_USER_UNMATCHED.getMessage());

            assertThat(practiceNoteRepository.findById(practiceNote.getId())).isPresent();
            verify(practiceNotificationScheduler, never()).deleteNotification(eq(practiceNote.getId()));
        }

        @Test
        @DisplayName("존재하지 않는 복습노트를 지우면 PRACTICE_NOTE_NOT_FOUND 예외가 발생한다")
        void cannotDeleteMissingPractice() {
            Long missingId = nonExistentPracticeNoteId();

            assertThatThrownBy(() -> practiceNoteService.deletePractice(missingId, userId))
                    .isInstanceOf(ApplicationException.class)
                    .hasMessageContaining(PracticeNoteErrorCase.PRACTICE_NOTE_NOT_FOUND.getMessage());
        }

        @Test
        @DisplayName("전체 삭제는 본인 복습노트만 지운다")
        void deleteAllPracticesByUser() {
            savePracticeNote(userId, "복습 1", problems.subList(0, 1));
            savePracticeNote(userId, "복습 2", problems.subList(1, 2));
            PracticeNote otherUserPractice = savePracticeNote(otherUserId, "남의 복습", List.of());

            practiceNoteService.deleteAllPracticesByUser(userId);

            assertThat(practiceNoteRepository.findAllByUserId(userId)).isEmpty();
            assertThat(practiceNoteRepository.findAllByUserId(otherUserId))
                    .extracting(PracticeNote::getId)
                    .containsExactly(otherUserPractice.getId());
        }

        @Test
        @DisplayName("문제를 지우면 모든 복습노트에서 그 문제 매핑이 빠진다")
        void deleteProblemsFromAllPractice() {
            PracticeNote first = savePracticeNote(userId, "복습 1", problems.subList(0, 3));
            PracticeNote second = savePracticeNote(userId, "복습 2", problems.subList(2, 5));

            practiceNoteService.deleteProblemsFromAllPractice(
                    List.of(problems.get(2).getId(), problems.get(3).getId()));

            assertThat(practiceNoteRepository.findProblemIdListByPracticeNoteId(first.getId()))
                    .containsExactlyElementsOf(problemIdsOf(problems.subList(0, 2)));
            assertThat(practiceNoteRepository.findProblemIdListByPracticeNoteId(second.getId()))
                    .containsExactly(problems.get(4).getId());
        }
    }
}
