package com.aisip.OnO.backend.practicenote.support;

import com.aisip.OnO.backend.folder.entity.Folder;
import com.aisip.OnO.backend.practicenote.dto.PracticeNoteRegisterDto;
import com.aisip.OnO.backend.practicenote.dto.PracticeNotificationRegisterDto;
import com.aisip.OnO.backend.practicenote.entity.PracticeNote;
import com.aisip.OnO.backend.practicenote.entity.ProblemPracticeNoteMapping;
import com.aisip.OnO.backend.practicenote.repository.PracticeNoteRepository;
import com.aisip.OnO.backend.practicenote.repository.ProblemPracticeNoteMappingRepository;
import com.aisip.OnO.backend.practicenote.service.PracticeNoteService;
import com.aisip.OnO.backend.practicenote.service.PracticeNotificationScheduler;
import com.aisip.OnO.backend.problem.dto.ProblemRegisterDto;
import com.aisip.OnO.backend.problem.entity.Problem;
import com.aisip.OnO.backend.problem.repository.ProblemRepository;
import com.aisip.OnO.backend.support.IntegrationTestSupport;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.IntStream;

/**
 * practicenote 도메인 테스트의 공통 베이스.
 *
 * <p><b>Quartz 스케줄러를 목으로 잡는 이유.</b> {@code QuartzConfig} 가
 * {@code SchedulerFactoryBean} 에 DataSource 를 직접 물려 JDBC JobStore 를 강제하므로,
 * 테스트 프로필의 {@code spring.quartz.job-store-type: memory} 설정이 전혀 먹지 않는다.
 * 그 결과 테스트 DB 에 {@code QRTZ_*} 테이블이 없어 스케줄 등록/삭제가
 * {@code RuntimeException("스케줄 등록 실패")} 로 떨어졌고, 복습노트 등록·수정 경로를
 * 아예 검증할 수 없었다. 스케줄러 자체의 크론 변환 로직은 순수 단위 테스트
 * ({@code PracticeNotificationSchedulerTest}) 에서 실제 구현으로 검증하고,
 * 서비스 레벨에서는 "언제 스케줄을 걸고 언제 지우는지"를 목으로 확인한다.
 *
 * <p>{@code @MockBean} 은 스프링 컨텍스트를 하나 더 만든다. 그래서 practicenote 테스트는
 * 모두 이 클래스를 상속해 <b>같은</b> 목 구성을 공유한다. 여기에 목을 더 추가하지 않는다.
 */
public abstract class PracticeNoteTestSupport extends IntegrationTestSupport {

    private static final AtomicLong SEQUENCE = new AtomicLong();

    /** Quartz 스케줄 등록/삭제 호출만 검증한다. 실제 발송은 FcmService 목이 이중으로 막는다. */
    @MockBean
    protected PracticeNotificationScheduler practiceNotificationScheduler;

    @Autowired
    protected PracticeNoteService practiceNoteService;

    @Autowired
    protected PracticeNoteRepository practiceNoteRepository;

    @Autowired
    protected ProblemPracticeNoteMappingRepository problemPracticeNoteMappingRepository;

    @Autowired
    protected ProblemRepository problemRepository;

    @Autowired
    protected TransactionTemplate transactionTemplate;

    @PersistenceContext
    protected EntityManager entityManager;

    // ─────────────────────────── 픽스처 ───────────────────────────

    protected List<Problem> saveProblems(Long userId, Folder folder, int count) {
        return IntStream.range(0, count)
                .mapToObj(i -> saveProblem(userId, folder))
                .toList();
    }

    protected Problem saveProblem(Long userId, Folder folder) {
        long seq = SEQUENCE.incrementAndGet();
        Problem problem = Problem.from(
                new ProblemRegisterDto(null, "메모" + seq, "출처" + seq, folder.getId(), LocalDateTime.now()),
                userId
        );
        problem.updateFolder(folder);
        return problemRepository.save(problem);
    }

    /** 알림 없이 복습노트를 만들고 문제를 매핑한다. */
    protected PracticeNote savePracticeNote(Long userId, String title, List<Problem> problems) {
        return savePracticeNote(userId, title, problems, null);
    }

    protected PracticeNote savePracticeNote(
            Long userId, String title, List<Problem> problems, PracticeNotificationRegisterDto notification) {
        PracticeNote practiceNote = practiceNoteRepository.save(PracticeNote.from(
                new PracticeNoteRegisterDto(null, title, List.of(), notification), userId));

        problems.forEach(problem -> {
            ProblemPracticeNoteMapping mapping = ProblemPracticeNoteMapping.from();
            mapping.addMappingToProblemAndPractice(problem, practiceNote);
            problemPracticeNoteMappingRepository.save(mapping);
        });

        return practiceNote;
    }

    protected PracticeNotificationRegisterDto dailyNotification() {
        return new PracticeNotificationRegisterDto(1, 9, 30, "daily", null);
    }

    protected PracticeNotificationRegisterDto weeklyNotification(List<Integer> weekDays) {
        return new PracticeNotificationRegisterDto(7, 21, 0, "weekly", weekDays);
    }

    /**
     * 어떤 복습노트에도 부여되지 않은 ID.
     *
     * <p>DatabaseCleaner 가 DELETE 로 테이블을 비우므로 auto_increment 는 되돌아가지 않는다.
     * "999L 은 없는 id" 같은 가정을 쓰면 안 된다.
     */
    protected Long nonExistentPracticeNoteId() {
        return practiceNoteRepository.findAll().stream()
                .mapToLong(PracticeNote::getId)
                .max()
                .orElse(0L) + 1_000L;
    }

    protected List<Long> problemIdsOf(List<Problem> problems) {
        return problems.stream().map(Problem::getId).toList();
    }

    protected void inTransaction(Runnable action) {
        transactionTemplate.executeWithoutResult(status -> action.run());
    }
}
