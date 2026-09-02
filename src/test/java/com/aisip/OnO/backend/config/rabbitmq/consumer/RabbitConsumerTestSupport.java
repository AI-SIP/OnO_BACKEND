package com.aisip.OnO.backend.config.rabbitmq.consumer;

import com.aisip.OnO.backend.config.rabbitmq.message.FcmNotificationMessage;
import com.aisip.OnO.backend.config.rabbitmq.message.ProblemAnalysisMessage;
import com.aisip.OnO.backend.config.rabbitmq.message.S3DeleteMessage;
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
import com.aisip.OnO.backend.util.fcm.dto.FcmTokenRequestDto;
import com.aisip.OnO.backend.util.fcm.entity.FcmToken;
import com.aisip.OnO.backend.util.fcm.repository.FcmTokenRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * RabbitMQ Consumer 테스트의 공통 베이스.
 *
 * <p>컨슈머는 실제 큐를 통하지 않고 <b>핸들러 메서드를 직접 호출</b>해서 검증한다.
 * 테스트 프로필은 {@code spring.rabbitmq.listener.simple.auto-startup: false} 라서
 * 리스너 컨테이너가 뜨지 않고, 큐에 넣어 기다리는 방식은 타이밍에 의존해 불안정하다.
 * 직렬화/역직렬화만 {@link RabbitMessageSerializationTest} 에서 별도로 확인한다.
 *
 * <p>외부 발송(FCM, S3, OpenAI, Discord)은 {@link IntegrationTestSupport} 가 이미 목으로 잡는다.
 * 여기서 {@code @MockBean} 을 새로 선언하면 스프링 컨텍스트가 하나 더 뜨므로 절대 추가하지 않는다.
 * {@code FirebaseMessaging} 만은 컨텍스트에 실물 빈이 있으므로,
 * {@link FcmNotificationConsumerTest} 가 컨슈머를 직접 조립하면서 목을 끼워 넣는다.
 */
public abstract class RabbitConsumerTestSupport extends IntegrationTestSupport {

    private static final AtomicLong SEQUENCE = new AtomicLong();

    @Autowired
    protected FcmTokenRepository fcmTokenRepository;

    @Autowired
    protected ProblemRepository problemRepository;

    @Autowired
    protected ProblemImageDataRepository problemImageDataRepository;

    @Autowired
    protected ProblemAnalysisRepository problemAnalysisRepository;

    @Autowired
    protected TransactionTemplate transactionTemplate;

    // ─────────────────────────── 메시지 ───────────────────────────

    protected FcmNotificationMessage fcmMessage(Long userId, Map<String, String> data) {
        return new FcmNotificationMessage(userId, "복습할 시간이에요!", "오늘 복습할 문제가 3개 있어요", data);
    }

    protected FcmNotificationMessage fcmMessage(Long userId) {
        return fcmMessage(userId, Map.of("type", "review_due"));
    }

    /** 재시도 횟수는 로그·DLQ 알림에만 쓰이므로 전체 생성자로 직접 세운다. */
    protected FcmNotificationMessage fcmMessageWithRetryCount(Long userId, int retryCount) {
        return new FcmNotificationMessage(userId, "제목", "본문", Map.of("type", "review_due"), retryCount);
    }

    protected S3DeleteMessage s3Message(String imageUrl, Long problemId) {
        return new S3DeleteMessage(imageUrl, problemId);
    }

    protected S3DeleteMessage s3MessageWithRetryCount(String imageUrl, Long problemId, int retryCount) {
        return new S3DeleteMessage(imageUrl, problemId, retryCount);
    }

    protected ProblemAnalysisMessage analysisMessage(Long problemId) {
        return new ProblemAnalysisMessage(problemId);
    }

    protected ProblemAnalysisMessage analysisMessageWithRetryCount(Long problemId, int retryCount) {
        return new ProblemAnalysisMessage(problemId, retryCount);
    }

    // ─────────────────────────── 픽스처 ───────────────────────────

    protected FcmToken saveFcmToken(Long userId, String token) {
        return fcmTokenRepository.save(FcmToken.From(new FcmTokenRequestDto(token), userId));
    }

    protected String uniqueToken(String prefix) {
        return prefix + "-" + SEQUENCE.incrementAndGet();
    }

    protected Problem saveProblem(Long userId, Folder folder) {
        Problem problem = Problem.from(
                new ProblemRegisterDto(null, "메모", "출처", folder.getId(), LocalDateTime.now()),
                userId
        );
        problem.updateFolder(folder);
        return problemRepository.save(problem);
    }

    /**
     * {@code ProblemImageData.updateProblem} 은 지연 로딩 컬렉션을 건드리므로
     * 트랜잭션 안에서 관리 상태의 Problem 에 대고 호출해야 한다.
     */
    protected ProblemImageData saveImageData(Problem problem, String imageUrl, ProblemImageType imageType) {
        return transactionTemplate.execute(status -> {
            Problem managed = problemRepository.findById(problem.getId()).orElseThrow();
            ProblemImageData imageData = ProblemImageData.from(
                    new ProblemImageDataRegisterDto(managed.getId(), imageUrl, imageType));
            imageData.updateProblem(managed);
            return problemImageDataRepository.saveAndFlush(imageData);
        });
    }

    /** 테스트 본문은 트랜잭션 밖에서 돌기 때문에 엔티티를 고쳐 저장하려면 트랜잭션을 열어야 한다. */
    protected void inTransaction(Runnable action) {
        transactionTemplate.executeWithoutResult(status -> action.run());
    }

    protected ProblemAnalysis saveAnalysisRow(Problem problem) {
        ProblemAnalysis analysis = ProblemAnalysis.createSkipped(problem);
        problem.updateProblemAnalysis(analysis);
        return problemAnalysisRepository.save(analysis);
    }
}
