package com.aisip.OnO.backend.problemsolve.service;

import com.aisip.OnO.backend.common.emoji.CustomEmojiValidator;
import com.aisip.OnO.backend.common.exception.ApplicationException;
import com.aisip.OnO.backend.mission.entity.MissionMetric;
import com.aisip.OnO.backend.mission.service.MissionLogService;
import com.aisip.OnO.backend.mission.service.MissionProgressUpdater;
import com.aisip.OnO.backend.problem.reminder.ProblemReviewReminderService;
import com.aisip.OnO.backend.util.redis.StreakCacheService;
import com.aisip.OnO.backend.problem.service.ReviewIntervalCalculator;
import com.aisip.OnO.backend.problemsolve.dto.ProblemSolveRegisterDto;
import com.aisip.OnO.backend.problemsolve.dto.ProblemSolveResponseDto;
import com.aisip.OnO.backend.problemsolve.dto.ProblemSolveUpdateDto;
import com.aisip.OnO.backend.problemsolve.entity.AnswerStatus;
import com.aisip.OnO.backend.problemsolve.entity.ProblemSolve;
import com.aisip.OnO.backend.problemsolve.entity.ProblemSolveImageData;
import com.aisip.OnO.backend.problemsolve.exception.ProblemSolveErrorCase;
import com.aisip.OnO.backend.problemsolve.repository.ProblemSolveImageDataRepository;
import com.aisip.OnO.backend.problemsolve.repository.ProblemSolveRepository;
import com.aisip.OnO.backend.problem.entity.Problem;
import com.aisip.OnO.backend.problem.exception.ProblemErrorCase;
import com.aisip.OnO.backend.problem.repository.ProblemRepository;
import com.aisip.OnO.backend.studyroom.entity.StudyRoomFeedEventType;
import com.aisip.OnO.backend.studyroom.event.StudyRoomActivityEvent;
import com.aisip.OnO.backend.util.fileupload.service.FileUploadService;
import com.aisip.OnO.backend.config.rabbitmq.producer.S3DeleteProducer;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.springframework.beans.factory.annotation.Qualifier;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProblemSolveService {

    private final ProblemSolveRepository problemSolveRepository;
    private final ProblemSolveImageDataRepository problemSolveImageDataRepository;
    private final ProblemRepository problemRepository;
    private final MissionLogService missionLogService;
    private final MissionProgressUpdater missionProgressUpdater;
    private final FileUploadService fileUploadService;
    private final S3DeleteProducer s3DeleteProducer;
    private final ObjectMapper objectMapper;
    private final StreakCacheService streakCacheService;
    private final ApplicationEventPublisher eventPublisher;
    private final ProblemReviewReminderService reminderService;
    private final CustomEmojiValidator customEmojiValidator;
    @Qualifier("s3UploadExecutor")
    private final Executor s3UploadExecutor;

    @Transactional(readOnly = true)
    public ProblemSolveResponseDto getProblemSolve(Long problemSolveId, Long userId) {
        ProblemSolve problemSolve = problemSolveRepository.findByIdWithImages(problemSolveId)
                .orElseThrow(() -> new ApplicationException(ProblemSolveErrorCase.PROBLEM_SOLVE_NOT_FOUND));

        if (!Objects.equals(problemSolve.getUserId(), userId)) {
            throw new ApplicationException(ProblemSolveErrorCase.PROBLEM_SOLVE_USER_UNMATCHED);
        }

        return ProblemSolveResponseDto.from(problemSolve);
    }

    @Transactional(readOnly = true)
    public List<ProblemSolveResponseDto> getProblemSolvesByProblemId(Long problemId, Long userId) {
        Problem problem = problemRepository.findById(problemId)
                .orElseThrow(() -> new ApplicationException(ProblemErrorCase.PROBLEM_NOT_FOUND));

        if (!Objects.equals(problem.getUserId(), userId)) {
            throw new ApplicationException(ProblemErrorCase.PROBLEM_USER_UNMATCHED);
        }

        List<ProblemSolve> problemSolves = problemSolveRepository.findAllByProblemIdWithImages(problemId);

        return problemSolves.stream()
                .map(ProblemSolveResponseDto::from)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<ProblemSolveResponseDto> getAdminProblemSolvesByProblemId(Long problemId) {
        List<ProblemSolve> problemSolves = problemSolveRepository.findAllByProblemIdWithImages(problemId);

        return problemSolves.stream()
                .map(ProblemSolveResponseDto::from)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<ProblemSolveResponseDto> getUserProblemSolves(Long userId) {
        List<ProblemSolve> problemSolves = problemSolveRepository.findAllByUserId(userId);

        return problemSolves.stream()
                .map(ProblemSolveResponseDto::from)
                .collect(Collectors.toList());
    }

    @Transactional
    public Long createProblemSolve(ProblemSolveRegisterDto dto, Long userId) {
        // problemId 가 없으면 findById(null) 이 InvalidDataAccessApiUsageException 으로 터져 500 이 나간다.
        // answerStatus 가 없으면 복습 주기 계산에서 NPE, 저장 시 not-null 위반으로 역시 500 이다.
        if (dto == null || dto.problemId() == null || dto.answerStatus() == null) {
            throw new ApplicationException(ProblemSolveErrorCase.PROBLEM_SOLVE_INVALID_INPUT);
        }

        Problem problem = problemRepository.findById(dto.problemId())
                .orElseThrow(() -> new ApplicationException(ProblemErrorCase.PROBLEM_NOT_FOUND));

        if (!Objects.equals(problem.getUserId(), userId)) {
            throw new ApplicationException(ProblemErrorCase.PROBLEM_USER_UNMATCHED);
        }

        // 이모지는 선택 사항이라 null 은 허용하고, 값이 있으면 화이트리스트에 있는 키인지만 본다.
        customEmojiValidator.validateNullable(dto.moodEmojiKey());

        // improvements를 JSON 문자열로 변환
        String improvementsJson = null;
        if (dto.improvements() != null && !dto.improvements().isEmpty()) {
            try {
                improvementsJson = objectMapper.writeValueAsString(dto.improvements());
            } catch (Exception e) {
                log.error("JSON 변환 실패 for improvements", e);
                throw new RuntimeException("개선 사항 JSON 변환 실패", e);
            }
        }

        // practicedAt 은 not-null 컬럼이다. 앱이 값을 안 보내면 "지금 푼 것"으로 본다.
        LocalDateTime practicedAt = dto.practicedAt() != null
                ? dto.practicedAt()
                : LocalDateTime.now(ZoneId.of("Asia/Seoul"));

        ProblemSolve problemSolve = ProblemSolve.create(
                problem,
                userId,
                practicedAt,
                dto.answerStatus(),
                dto.reflection(),
                improvementsJson,
                dto.timeSpentSeconds(),
                dto.moodEmojiKey()
        );

        problemSolveRepository.save(problemSolve);
        streakCacheService.evict(userId);
        missionLogService.registerProblemPracticeMission(userId, problem.getId());
        missionProgressUpdater.increase(userId, MissionMetric.SOLVE_RECORDED);
        if (dto.answerStatus() == AnswerStatus.CORRECT) {
            missionProgressUpdater.increase(userId, MissionMetric.SOLVE_CORRECT);
        }

        ReviewIntervalCalculator.ReviewSchedule schedule = ReviewIntervalCalculator.calculate(
                dto.answerStatus(),
                problem.getReviewInterval(),
                problem.getConsecutiveCorrectCount()
        );
        problem.updateReviewSchedule(schedule.nextReviewAt(), schedule.reviewInterval(), schedule.consecutiveCorrectCount());
        eventPublisher.publishEvent(new StudyRoomActivityEvent(
                userId, StudyRoomFeedEventType.PRACTICE_COMPLETED, java.util.Map.of()));

        reminderService.skipDuePendingByProblemSolve(userId, dto.problemId(), practicedAt);

        log.info("userId: {} created problem solve: {}, nextReviewAt: {}, mastered: {}",
                userId, problemSolve.getId(), schedule.nextReviewAt(), schedule.isMastered());

        return problemSolve.getId();
    }

    @Transactional
    public void uploadProblemSolveImages(Long problemSolveId, Long userId, List<MultipartFile> images) {
        if (images == null) {
            throw new ApplicationException(ProblemSolveErrorCase.PROBLEM_SOLVE_INVALID_INPUT);
        }

        ProblemSolve problemSolve = problemSolveRepository.findById(problemSolveId)
                .orElseThrow(() -> new ApplicationException(ProblemSolveErrorCase.PROBLEM_SOLVE_NOT_FOUND));

        if (!Objects.equals(problemSolve.getUserId(), userId)) {
            throw new ApplicationException(ProblemSolveErrorCase.PROBLEM_SOLVE_USER_UNMATCHED);
        }

        // S3 업로드를 병렬로 실행 (트랜잭션과 무관한 네트워크 I/O)
        List<CompletableFuture<String>> uploadFutures = IntStream.range(0, images.size())
                .mapToObj(i -> CompletableFuture.supplyAsync(
                        () -> fileUploadService.uploadFileToS3(images.get(i)),
                        s3UploadExecutor
                ))
                .toList();

        List<String> imageUrls = uploadFutures.stream()
                .map(CompletableFuture::join)
                .collect(Collectors.toList());

        // DB 저장은 메인 스레드에서 배치로 처리 (트랜잭션 컨텍스트 유지)
        List<ProblemSolveImageData> imageDataList = IntStream.range(0, imageUrls.size())
                .mapToObj(i -> ProblemSolveImageData.create(imageUrls.get(i), i))
                .collect(Collectors.toList());

        imageDataList.forEach(problemSolve::addImage);
        problemSolveImageDataRepository.saveAll(imageDataList);

        log.info("Uploaded {} images in parallel for problemSolveId: {}", images.size(), problemSolveId);
    }

    @Transactional
    public void addImageUrls(Long problemSolveId, Long userId, List<String> imageUrls) {
        if (imageUrls == null) {
            throw new ApplicationException(ProblemSolveErrorCase.PROBLEM_SOLVE_INVALID_INPUT);
        }

        ProblemSolve problemSolve = problemSolveRepository.findById(problemSolveId)
                .orElseThrow(() -> new ApplicationException(ProblemSolveErrorCase.PROBLEM_SOLVE_NOT_FOUND));
        if (!Objects.equals(problemSolve.getUserId(), userId)) {
            throw new ApplicationException(ProblemSolveErrorCase.PROBLEM_SOLVE_USER_UNMATCHED);
        }
        int existingCount = problemSolve.getImages() == null ? 0 : problemSolve.getImages().size();
        List<ProblemSolveImageData> imageDataList = IntStream.range(0, imageUrls.size())
                .mapToObj(i -> {
                    fileUploadService.validateS3Url(imageUrls.get(i));
                    return ProblemSolveImageData.create(imageUrls.get(i), existingCount + i);
                })
                .collect(Collectors.toList());
        imageDataList.forEach(problemSolve::addImage);
        problemSolveImageDataRepository.saveAll(imageDataList);
    }

    @Transactional
    public void updateProblemSolve(ProblemSolveUpdateDto dto, Long userId) {
        // problemSolveId 가 없으면 findById(null) 이 500 으로, answerStatus 가 없으면
        // not-null 컬럼 위반으로 500 이 난다. 둘 다 클라이언트 입력 오류다.
        if (dto == null || dto.problemSolveId() == null || dto.answerStatus() == null) {
            throw new ApplicationException(ProblemSolveErrorCase.PROBLEM_SOLVE_INVALID_INPUT);
        }

        ProblemSolve problemSolve = problemSolveRepository.findById(dto.problemSolveId())
                .orElseThrow(() -> new ApplicationException(ProblemSolveErrorCase.PROBLEM_SOLVE_NOT_FOUND));

        if (!Objects.equals(problemSolve.getUserId(), userId)) {
            throw new ApplicationException(ProblemSolveErrorCase.PROBLEM_SOLVE_USER_UNMATCHED);
        }

        customEmojiValidator.validateNullable(dto.moodEmojiKey());

        // improvements를 JSON 문자열로 변환
        String improvementsJson = null;
        if (dto.improvements() != null && !dto.improvements().isEmpty()) {
            try {
                improvementsJson = objectMapper.writeValueAsString(dto.improvements());
            } catch (Exception e) {
                log.error("JSON 변환 실패 for improvements", e);
                throw new RuntimeException("개선 사항 JSON 변환 실패", e);
            }
        }

        problemSolve.updateSolve(
                dto.answerStatus(),
                dto.reflection(),
                improvementsJson,
                dto.timeSpentSeconds(),
                dto.moodEmojiKey()
        );

        log.info("userId: {} updated problem solve: {}", userId, problemSolve.getId());
    }

    @Transactional
    public void deleteProblemSolve(Long problemSolveId, Long userId) {
        ProblemSolve problemSolve = problemSolveRepository.findByIdWithImages(problemSolveId)
                .orElseThrow(() -> new ApplicationException(ProblemSolveErrorCase.PROBLEM_SOLVE_NOT_FOUND));

        if (!Objects.equals(problemSolve.getUserId(), userId)) {
            throw new ApplicationException(ProblemSolveErrorCase.PROBLEM_SOLVE_USER_UNMATCHED);
        }

        List<ProblemSolveImageData> images = problemSolve.getImages();

        problemSolveRepository.delete(problemSolve);
        streakCacheService.evict(userId);
        log.info("userId: {} deleted problem solve: {}", userId, problemSolveId);

        images.forEach(image -> {
            try {
                s3DeleteProducer.sendDeleteMessage(image.getImageUrl(), problemSolveId);
            } catch (Exception e) {
                log.error("S3 삭제 메시지 전송 실패 - problemSolveId: {}, error: {}",
                        problemSolveId, e.getMessage());
            }
        });

        log.info("problemSolveId: {} S3 삭제 메시지 전송 완료 ({}개)", problemSolveId, images.size());
    }

    @Transactional
    public void deleteAllProblemSolvesByProblemId(Long problemId) {
        List<ProblemSolve> problemSolves = problemSolveRepository.findAllByProblemIdWithImages(problemId);

        problemSolves.forEach(record -> {
            List<ProblemSolveImageData> images = record.getImages();

            images.forEach(image -> {
                try {
                    s3DeleteProducer.sendDeleteMessage(image.getImageUrl(), record.getId());
                } catch (Exception e) {
                    log.error("S3 삭제 메시지 전송 실패 - problemSolveId: {}, error: {}",
                            record.getId(), e.getMessage());
                }
            });
        });

        problemSolveRepository.deleteAllByProblemId(problemId);
        log.info("problemId: {} 의 모든 복습 기록 삭제 완료", problemId);
    }

    @Transactional(readOnly = true)
    public Long getProblemSolveCountByProblemId(Long problemId, Long userId) {
        Problem problem = problemRepository.findById(problemId)
                .orElseThrow(() -> new ApplicationException(ProblemErrorCase.PROBLEM_NOT_FOUND));

        if (!Objects.equals(problem.getUserId(), userId)) {
            throw new ApplicationException(ProblemErrorCase.PROBLEM_USER_UNMATCHED);
        }

        return problemSolveRepository.countByProblemId(problemId);
    }

    @Transactional(readOnly = true)
    public Long getUserProblemSolveCount(Long userId) {
        return problemSolveRepository.countByUserId(userId);
    }
}
