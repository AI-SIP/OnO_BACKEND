package com.aisip.OnO.backend.learningcalendar.support;

import com.aisip.OnO.backend.learningcalendar.dto.LearningCalendarResponseDto;
import com.aisip.OnO.backend.learningcalendar.repository.LearningCalendarMoodRepository;
import com.aisip.OnO.backend.problem.dto.ProblemRegisterDto;
import com.aisip.OnO.backend.problem.entity.Problem;
import com.aisip.OnO.backend.problem.repository.ProblemRepository;
import com.aisip.OnO.backend.problemsolve.entity.AnswerStatus;
import com.aisip.OnO.backend.problemsolve.entity.ProblemSolve;
import com.aisip.OnO.backend.problemsolve.repository.ProblemSolveRepository;
import com.aisip.OnO.backend.support.IntegrationTestSupport;
import com.aisip.OnO.backend.util.redis.StreakCacheService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * learningcalendar 도메인 테스트의 공통 픽스처.
 *
 * <p>달력 집계는 {@code problem.createdAt}(오답노트 작성)과 {@code problem_solve.practicedAt}(복습)
 * 두 축으로 계산된다. {@code createdAt} 은 JPA Auditing 이 채우므로 원하는 시각으로 만들려면
 * 저장 후 네이티브 UPDATE 로 덮어써야 한다.
 *
 * <p>{@link IntegrationTestSupport} 의 애노테이션 조합을 그대로 물려받아 스프링 컨텍스트를 공유한다.
 * {@code @MockBean} 을 추가하면 컨텍스트가 갈라지므로 절대 선언하지 않는다.
 */
public abstract class LearningCalendarTestSupport extends IntegrationTestSupport {

    @Autowired
    protected LearningCalendarMoodRepository moodRepository;

    @Autowired
    protected ProblemRepository problemRepository;

    @Autowired
    protected ProblemSolveRepository problemSolveRepository;

    @Autowired
    protected StreakCacheService streakCacheService;

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    /** 지정한 시각에 작성된 오답노트(문제). 달력의 noteWriteCount 축을 만든다. */
    protected Problem saveNoteWrittenAt(Long userId, LocalDateTime createdAt) {
        return saveNoteWrittenAt(userId, createdAt, "memo-" + createdAt, "ref-" + createdAt);
    }

    protected Problem saveNoteWrittenAt(Long userId, LocalDateTime createdAt, String memo, String reference) {
        Problem problem = problemRepository.save(Problem.from(
                new ProblemRegisterDto(null, memo, reference, null, LocalDateTime.now()),
                userId
        ));
        jdbcTemplate.update("UPDATE problem SET created_at = ? WHERE id = ?", createdAt, problem.getId());
        return problem;
    }

    /** 지정한 시각의 복습 기록. 달력의 reviewCount / studyMinutes 축을 만든다. */
    protected ProblemSolve saveSolveAt(Long userId, Problem problem, LocalDateTime practicedAt, Integer seconds) {
        return saveSolveAt(userId, problem, practicedAt, seconds, AnswerStatus.CORRECT);
    }

    protected ProblemSolve saveSolveAt(
            Long userId, Problem problem, LocalDateTime practicedAt, Integer seconds, AnswerStatus answerStatus
    ) {
        return problemSolveRepository.save(
                ProblemSolve.create(problem, userId, practicedAt, answerStatus, null, null, seconds)
        );
    }

    /**
     * 스트릭은 Redis 에 캐시된다. 한 테스트 안에서 기록을 추가한 뒤 다시 조회하려면
     * 캐시를 비워야 방금 넣은 기록이 반영된다.
     */
    protected void evictStreakCache(Long userId) {
        streakCacheService.evict(userId);
    }

    protected Map<LocalDate, LearningCalendarResponseDto.DailyStudyRecord> recordsByDate(
            LearningCalendarResponseDto response
    ) {
        return response.records().stream()
                .collect(Collectors.toMap(
                        LearningCalendarResponseDto.DailyStudyRecord::date,
                        record -> record
                ));
    }
}
