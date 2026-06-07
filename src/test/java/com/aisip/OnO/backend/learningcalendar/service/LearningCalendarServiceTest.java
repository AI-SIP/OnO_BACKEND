package com.aisip.OnO.backend.learningcalendar.service;

import com.aisip.OnO.backend.common.exception.ApplicationException;
import com.aisip.OnO.backend.learningcalendar.dto.LearningCalendarMoodRequestDto;
import com.aisip.OnO.backend.learningcalendar.dto.LearningCalendarResponseDto;
import com.aisip.OnO.backend.learningcalendar.exception.LearningCalendarErrorCase;
import com.aisip.OnO.backend.problem.dto.ProblemRegisterDto;
import com.aisip.OnO.backend.problem.entity.Problem;
import com.aisip.OnO.backend.problem.repository.ProblemRepository;
import com.aisip.OnO.backend.problemsolve.entity.AnswerStatus;
import com.aisip.OnO.backend.problemsolve.entity.ProblemSolve;
import com.aisip.OnO.backend.problemsolve.repository.ProblemSolveRepository;
import com.aisip.OnO.backend.user.dto.UserRegisterDto;
import com.aisip.OnO.backend.user.entity.User;
import com.aisip.OnO.backend.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class LearningCalendarServiceTest {

    @Autowired
    private LearningCalendarService learningCalendarService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProblemRepository problemRepository;

    @Autowired
    private ProblemSolveRepository problemSolveRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    @DisplayName("학습 달력 조회 - 월별 일자 기록과 스트릭을 계산한다")
    void getLearningCalendar_success() {
        User targetUser = createUser("calendar-user");
        User otherUser = createUser("other-calendar-user");
        Long userId = targetUser.getId();

        Problem targetProblem = createProblemWrite(userId, LocalDateTime.of(2026, 5, 1, 8, 0));
        createProblemWrite(userId, LocalDateTime.of(2026, 5, 2, 8, 0));
        createProblemWrite(userId, LocalDateTime.of(2026, 5, 5, 8, 0));

        createSolve(targetProblem, userId, LocalDateTime.of(2026, 5, 1, 9, 0), 600);
        createSolve(targetProblem, userId, LocalDateTime.of(2026, 5, 1, 10, 0), 120);
        createSolve(targetProblem, userId, LocalDateTime.of(2026, 5, 3, 9, 0), 300);
        createSolve(targetProblem, userId, LocalDateTime.of(2026, 5, 4, 9, 0), 60);
        createSolve(targetProblem, userId, LocalDateTime.of(2026, 5, 5, 9, 0), 60);

        Problem previousMonthProblem = createProblemWrite(userId, LocalDateTime.of(2026, 4, 30, 8, 0));
        createSolve(previousMonthProblem, userId, LocalDateTime.of(2026, 4, 30, 9, 0), 60);
        createSolve(previousMonthProblem, userId, LocalDateTime.of(2026, 4, 28, 9, 0), 60);

        Problem otherProblem = createProblemWrite(otherUser.getId(), LocalDateTime.of(2026, 5, 1, 8, 0));
        createSolve(otherProblem, otherUser.getId(), LocalDateTime.of(2026, 5, 1, 9, 0), 999);

        LearningCalendarResponseDto response = learningCalendarService.getLearningCalendar(
                userId,
                2026,
                5,
                LocalDate.of(2026, 5, 6)
        );

        assertThat(response.year()).isEqualTo(2026);
        assertThat(response.month()).isEqualTo(5);
        assertThat(response.records()).hasSize(31);
        assertThat(response.thisMonthStudyDays()).isEqualTo(5);
        assertThat(response.currentStreak()).isEqualTo(6);
        assertThat(response.bestStreak()).isEqualTo(5);

        Map<LocalDate, LearningCalendarResponseDto.DailyStudyRecord> records = response.records().stream()
                .collect(Collectors.toMap(LearningCalendarResponseDto.DailyStudyRecord::date, record -> record));

        assertThat(records.get(LocalDate.of(2026, 5, 1)).hasStudied()).isTrue();
        assertThat(records.get(LocalDate.of(2026, 5, 1)).reviewCount()).isEqualTo(2);
        assertThat(records.get(LocalDate.of(2026, 5, 1)).noteWriteCount()).isEqualTo(1);
        assertThat(records.get(LocalDate.of(2026, 5, 1)).studyMinutes()).isEqualTo(12);
        assertThat(records.get(LocalDate.of(2026, 5, 1)).reviewedItems())
                .containsExactly("ref-2026-05-01T08:00");
        assertThat(records.get(LocalDate.of(2026, 5, 2)).reviewCount()).isZero();
        assertThat(records.get(LocalDate.of(2026, 5, 2)).noteWriteCount()).isEqualTo(1);
        assertThat(records.get(LocalDate.of(2026, 5, 6)).hasStudied()).isFalse();
        assertThat(records.get(LocalDate.of(2026, 5, 6)).reviewedItems()).isEmpty();
        assertThat(records.get(LocalDate.of(2026, 5, 1)).moodEmojiKey()).isNull();
    }

    @Test
    @DisplayName("학습 기록이 있는 날짜에 감정 이모지를 저장하고 조회한다")
    void updateMood_success() {
        User targetUser = createUser("calendar-mood-user");
        Long userId = targetUser.getId();
        createProblemWrite(userId, LocalDateTime.of(2026, 6, 7, 8, 0));

        learningCalendarService.updateMood(userId,
                new LearningCalendarMoodRequestDto(LocalDate.of(2026, 6, 7), "happy_tears"));
        learningCalendarService.updateMood(userId,
                new LearningCalendarMoodRequestDto(LocalDate.of(2026, 6, 7), "success_checkmark"));

        LearningCalendarResponseDto response = learningCalendarService.getLearningCalendar(
                userId,
                2026,
                6,
                LocalDate.of(2026, 6, 8)
        );

        Map<LocalDate, LearningCalendarResponseDto.DailyStudyRecord> records = response.records().stream()
                .collect(Collectors.toMap(LearningCalendarResponseDto.DailyStudyRecord::date, record -> record));
        assertThat(records.get(LocalDate.of(2026, 6, 7)).moodEmojiKey()).isEqualTo("success_checkmark");
    }

    @Test
    @DisplayName("학습 기록이 없는 날짜에는 감정 이모지를 저장할 수 없다")
    void updateMood_recordNotFound() {
        User targetUser = createUser("calendar-mood-empty-user");

        assertThatThrownBy(() -> learningCalendarService.updateMood(targetUser.getId(),
                new LearningCalendarMoodRequestDto(LocalDate.of(2026, 6, 7), "happy_tears")))
                .isInstanceOf(ApplicationException.class)
                .hasMessageContaining(LearningCalendarErrorCase.CALENDAR_RECORD_NOT_FOUND.getMessage());
    }

    @Test
    @DisplayName("학습 달력 조회 - 데이터가 없으면 0 기반 월 레코드를 반환한다")
    void getLearningCalendar_emptyData() {
        User user = createUser("empty-calendar-user");

        LearningCalendarResponseDto response = learningCalendarService.getLearningCalendar(
                user.getId(),
                2026,
                2,
                LocalDate.of(2026, 2, 6)
        );

        assertThat(response.records()).hasSize(28);
        assertThat(response.currentStreak()).isZero();
        assertThat(response.bestStreak()).isZero();
        assertThat(response.thisMonthStudyDays()).isZero();
        assertThat(response.records()).allSatisfy(record -> {
            assertThat(record.hasStudied()).isFalse();
            assertThat(record.reviewCount()).isZero();
            assertThat(record.noteWriteCount()).isZero();
            assertThat(record.studyMinutes()).isZero();
            assertThat(record.reviewedItems()).isEmpty();
        });
    }

    private User createUser(String identifier) {
        return userRepository.save(User.from(UserRegisterDto.builder()
                .email(identifier + "@test.com")
                .name(identifier)
                .identifier(identifier)
                .platform("GOOGLE")
                .password("password")
                .build()));
    }

    private Problem createProblemWrite(Long userId, LocalDateTime createdAt) {
        Problem problemEntity = problemRepository.save(Problem.from(
                new ProblemRegisterDto(null, "memo-" + createdAt, "ref-" + createdAt, null, LocalDateTime.now()),
                userId
        ));
        updateProblemCreatedAt(problemEntity.getId(), createdAt);
        return problemEntity;
    }

    private void createSolve(Problem problemEntity, Long userId, LocalDateTime practicedAt, Integer seconds) {
        problemSolveRepository.save(ProblemSolve.create(
                problemEntity,
                userId,
                practicedAt,
                AnswerStatus.CORRECT,
                null,
                null,
                seconds
        ));
    }

    private void updateProblemCreatedAt(Long problemId, LocalDateTime createdAt) {
        entityManager.flush();
        entityManager.createNativeQuery("UPDATE problem SET created_at = :createdAt WHERE id = :id")
                .setParameter("createdAt", createdAt)
                .setParameter("id", problemId)
                .executeUpdate();
    }
}
