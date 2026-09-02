package com.aisip.OnO.backend.problem.support;

import com.aisip.OnO.backend.folder.entity.Folder;
import com.aisip.OnO.backend.problem.dto.ProblemImageDataRegisterDto;
import com.aisip.OnO.backend.problem.dto.ProblemRegisterDto;
import com.aisip.OnO.backend.problem.entity.Problem;
import com.aisip.OnO.backend.problem.entity.ProblemAnalysis;
import com.aisip.OnO.backend.problem.entity.ProblemImageData;
import com.aisip.OnO.backend.problem.entity.ProblemImageType;
import com.aisip.OnO.backend.problem.repository.ProblemAnalysisRepository;
import com.aisip.OnO.backend.problem.repository.ProblemImageDataRepository;
import com.aisip.OnO.backend.problem.repository.ProblemRepository;
import com.aisip.OnO.backend.support.IntegrationTestSupport;
import com.aisip.OnO.backend.tag.entity.ProblemTagMapping;
import com.aisip.OnO.backend.tag.entity.Tag;
import com.aisip.OnO.backend.tag.repository.ProblemTagMappingRepository;
import com.aisip.OnO.backend.tag.repository.TagRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

/**
 * problem 도메인 테스트의 공통 베이스.
 *
 * <p>{@link IntegrationTestSupport} 의 애노테이션 조합을 그대로 물려받으므로
 * 스프링 컨텍스트는 다른 도메인 테스트와 공유된다. 여기에는 문제/이미지/태그처럼
 * problem 도메인에서만 쓰는 픽스처만 둔다.
 *
 * <p>{@code @MockBean} 을 새로 선언하면 컨텍스트가 분리되므로 절대 추가하지 않는다.
 * 목이 더 필요하면 {@link IntegrationTestSupport} 에 이미 있는 것을 쓴다.
 */
public abstract class ProblemTestSupport extends IntegrationTestSupport {

    private static final AtomicLong TAG_SEQUENCE = new AtomicLong();

    @Autowired
    protected ProblemRepository problemRepository;

    @Autowired
    protected ProblemImageDataRepository problemImageDataRepository;

    @Autowired
    protected ProblemAnalysisRepository problemAnalysisRepository;

    @Autowired
    protected ProblemTagMappingRepository problemTagMappingRepository;

    @Autowired
    protected TagRepository tagRepository;

    @Autowired
    protected TransactionTemplate transactionTemplate;

    @Autowired
    protected RedisTemplate<String, Object> redisTemplate;

    @PersistenceContext
    protected EntityManager entityManager;

    /**
     * AI 분석 요청 제한 카운터는 Redis 에 남는다. DB 는 테스트마다 truncate 되면서
     * auto_increment 도 초기화되므로 다음 테스트가 같은 userId 를 다시 쓰게 되고,
     * 앞선 테스트가 소진한 카운터를 그대로 물려받아 엉뚱하게 RATE_LIMIT_EXCEEDED 가 된다.
     */
    @BeforeEach
    void resetAiAnalysisRateLimit() {
        Set<String> keys = redisTemplate.keys("rate_limit:ai_analysis:*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }

    // ─────────────────────────── 문제 ───────────────────────────

    protected Problem saveProblem(Long userId, Folder folder) {
        return saveProblem(userId, folder, "메모", "출처");
    }

    protected Problem saveProblem(Long userId, Folder folder, String memo, String reference) {
        Problem problem = Problem.from(
                new ProblemRegisterDto(null, memo, reference, folder.getId(), LocalDateTime.now()),
                userId
        );
        problem.updateFolder(folder);
        return problemRepository.save(problem);
    }

    /** 복습 예정일이 지정된 문제. review-due 조회 검증에 쓴다. */
    protected Problem saveProblemWithReviewSchedule(
            Long userId, Folder folder, LocalDate nextReviewAt, int reviewInterval, int consecutiveCorrectCount) {
        Problem problem = saveProblem(userId, folder);
        problem.updateReviewSchedule(nextReviewAt, reviewInterval, consecutiveCorrectCount);
        return problemRepository.saveAndFlush(problem);
    }

    protected ProblemImageData saveImageData(Problem problem, String imageUrl, ProblemImageType imageType) {
        ProblemImageData imageData = ProblemImageData.from(
                new ProblemImageDataRegisterDto(problem.getId(), imageUrl, imageType));
        imageData.updateProblem(problem);
        return problemImageDataRepository.save(imageData);
    }

    protected ProblemAnalysis saveSkippedAnalysis(Problem problem) {
        ProblemAnalysis analysis = ProblemAnalysis.createSkipped(problem);
        problem.updateProblemAnalysis(analysis);
        return problemAnalysisRepository.save(analysis);
    }

    // ─────────────────────────── 태그 ───────────────────────────

    protected Tag saveTag(Long userId, String name) {
        String unique = name + "-" + TAG_SEQUENCE.incrementAndGet();
        return tagRepository.save(Tag.from(userId, unique, unique.toLowerCase(Locale.ROOT)));
    }

    protected ProblemTagMapping saveTagMapping(Problem problem, Tag tag) {
        return problemTagMappingRepository.save(ProblemTagMapping.from(problem, tag));
    }

    // ─────────────────────────── 영속성 ───────────────────────────

    /**
     * 테스트 본문은 트랜잭션 밖에서 돌기 때문에 리포지토리 호출마다 영속성 컨텍스트가 새로 생긴다.
     * 지연 로딩이나 쿼리 수를 검증하려면 하나의 트랜잭션 안에서 실행해야 한다.
     */
    protected void inTransaction(Runnable action) {
        transactionTemplate.executeWithoutResult(status -> action.run());
    }
}
