package com.aisip.OnO.backend.problem.service;

import com.aisip.OnO.backend.common.response.CursorSort;
import com.aisip.OnO.backend.admin.dto.AdminProblemResponseDto;
import com.aisip.OnO.backend.common.exception.ApplicationException;
import com.aisip.OnO.backend.common.ratelimit.RateLimitKeys;
import com.aisip.OnO.backend.common.ratelimit.RateLimitService;
import com.aisip.OnO.backend.common.response.CursorPageResponse;
import com.aisip.OnO.backend.config.rabbitmq.message.S3DeleteMessage;
import com.aisip.OnO.backend.config.rabbitmq.producer.S3DeleteAfterCommitSender;
import com.aisip.OnO.backend.mission.entity.MissionMetric;
import com.aisip.OnO.backend.mission.service.MissionLogService;
import com.aisip.OnO.backend.mission.service.MissionProgressUpdater;
import com.aisip.OnO.backend.problem.entity.AnalysisStatus;
import com.aisip.OnO.backend.problem.entity.ProblemAnalysis;
import com.aisip.OnO.backend.problem.entity.ProblemImageType;
import com.aisip.OnO.backend.util.fileupload.service.FileUploadService;
import com.aisip.OnO.backend.util.fileupload.service.ImageUrlOwnershipService;
import com.aisip.OnO.backend.problem.dto.AddProblemImageUrlsRequest;
import com.aisip.OnO.backend.problem.dto.ProblemImageDataRegisterDto;
import com.aisip.OnO.backend.problem.dto.ProblemRegisterDto;
import com.aisip.OnO.backend.problem.dto.ProblemRegisterV2BatchDto;
import com.aisip.OnO.backend.problem.dto.ProblemRegisterV2Dto;
import com.aisip.OnO.backend.problem.dto.ProblemTagUpdateDto;
import com.aisip.OnO.backend.folder.entity.Folder;
import com.aisip.OnO.backend.problem.entity.ProblemImageData;
import com.aisip.OnO.backend.folder.exception.FolderErrorCase;
import com.aisip.OnO.backend.problem.exception.ProblemErrorCase;
import com.aisip.OnO.backend.problem.repository.ProblemAnalysisRepository;
import com.aisip.OnO.backend.folder.repository.FolderRepository;
import com.aisip.OnO.backend.problem.repository.ProblemImageDataRepository;
import com.aisip.OnO.backend.problem.dto.ProblemResponseDto;
import com.aisip.OnO.backend.problem.dto.ReviewDueResponseDto;
import com.aisip.OnO.backend.problem.entity.Problem;
import com.aisip.OnO.backend.problem.repository.ProblemRepository;
import com.aisip.OnO.backend.problem.repository.ReviewDueProblemProjection;
import com.aisip.OnO.backend.practicenote.repository.PracticeNoteRepository;
import com.aisip.OnO.backend.problemsolve.repository.ProblemSolveRepository;
import com.aisip.OnO.backend.problemsolve.repository.ProblemSolveSummary;
import com.aisip.OnO.backend.studyroom.entity.StudyRoomFeedEventType;
import com.aisip.OnO.backend.studyroom.event.StudyRoomActivityEvent;
import com.aisip.OnO.backend.tag.entity.ProblemTagMapping;
import com.aisip.OnO.backend.tag.entity.Tag;
import com.aisip.OnO.backend.tag.exception.TagErrorCase;
import com.aisip.OnO.backend.tag.repository.ProblemTagMappingRepository;
import com.aisip.OnO.backend.tag.repository.TagRepository;
import com.aisip.OnO.backend.problem.event.ProblemCreatedEvent;
import com.aisip.OnO.backend.problem.reminder.ProblemReviewReminderService;
import com.aisip.OnO.backend.util.redis.StreakCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProblemService {
    private static final int AI_ANALYSIS_LIMIT_PER_DAY = 20;
    private static final int MEMO_MAX_LENGTH = 1000;

    private final ProblemRepository problemRepository;

    private final ProblemImageDataRepository problemImageDataRepository;
    private final ProblemAnalysisRepository problemAnalysisRepository;

    private final FolderRepository folderRepository;

    private final FileUploadService fileUploadService;

    private final ImageUrlOwnershipService imageUrlOwnershipService;

    private final MissionLogService missionLogService;

    private final MissionProgressUpdater missionProgressUpdater;

    private final ProblemAnalysisService analysisService;

    private final PracticeNoteRepository practiceNoteRepository;

    private final ProblemSolveRepository problemSolveRepository;

    private final S3DeleteAfterCommitSender s3DeleteAfterCommitSender;

    private final ProblemAnalysisDispatcher analysisDispatcher;
    private final ProblemAnalysisDispatchGuard analysisDispatchGuard;
    private final TagRepository tagRepository;
    private final ProblemTagMappingRepository problemTagMappingRepository;
    private final RateLimitService rateLimitService;
    private final StreakCacheService streakCacheService;
    private final ApplicationEventPublisher eventPublisher;
    private final ProblemReviewReminderService reminderService;

    @Transactional(readOnly = true)
    public ProblemResponseDto findProblemForAdmin(Long problemId) {
        Problem problem = problemRepository.findProblemWithImageData(problemId)
                .orElseThrow(() -> new ApplicationException(ProblemErrorCase.PROBLEM_NOT_FOUND));

        return toProblemResponseDto(problem);
    }

    @Transactional(readOnly = true)
    public ProblemResponseDto findProblem(Long problemId, Long userId) {
        Problem problem = findProblemEntityWithImageData(problemId, userId);

        return toProblemResponseDto(problem);
    }

    @Transactional(readOnly = true)
    public Problem findProblemEntity(Long problemId, Long userId) {
        Problem problem = problemRepository.findById(problemId)
                .orElseThrow(() -> new ApplicationException(ProblemErrorCase.PROBLEM_NOT_FOUND));

        if (!Objects.equals(problem.getUserId(), userId)) {
            throw new ApplicationException(ProblemErrorCase.PROBLEM_USER_UNMATCHED);
        }

        return problem;
    }

    @Transactional(readOnly = true)
    public Problem findProblemEntityWithImageData(Long problemId, Long userId) {
        Problem problem = problemRepository.findProblemWithImageData(problemId)
                .orElseThrow(() -> new ApplicationException(ProblemErrorCase.PROBLEM_NOT_FOUND));

        if (!Objects.equals(problem.getUserId(), userId)) {
            throw new ApplicationException(ProblemErrorCase.PROBLEM_USER_UNMATCHED);
        }

        return problem;
    }

    @Transactional(readOnly = true)
    public List<ProblemResponseDto> findUserProblems(Long userId) {
        log.info("userId: {} find all user problems", userId);

        return toProblemResponseDtos(problemRepository.findAllByUserId(userId));
    }

    @Transactional(readOnly = true)
    public List<ProblemResponseDto> findFolderProblemList(Long folderId, Long userId) {
        validateFolderOwner(folderId, userId);

        return toProblemResponseDtos(problemRepository.findAllByFolderId(folderId));
    }

    @Transactional(readOnly = true)
    public List<ProblemResponseDto> findAllProblems() {
        List<Problem> problems = problemRepository.findAll()
                .stream()
                .sorted((p1, p2) -> p2.getCreatedAt().compareTo(p1.getCreatedAt())) // 최신순 정렬
                .toList();

        return toProblemResponseDtos(problems);
    }

    @Transactional(readOnly = true)
    public long countAllProblems() {
        return problemRepository.count();
    }

    @Transactional(readOnly = true)
    public long countAllProblemAnalyses() {
        return problemRepository.countProblemAnalysesForActiveProblems();
    }

    @Transactional(readOnly = true)
    public Map<LocalDate, Long> getDailyProblemsCount(LocalDate startDate, LocalDate endDate) {
        return problemRepository.countDailyProblems(startDate, endDate);
    }

    @Transactional(readOnly = true)
    public Map<AnalysisStatus, Long> countProblemAnalysesByStatus() {
        return fillMissingAnalysisStatuses(problemRepository.countProblemAnalysesByStatusForActiveProblems());
    }

    @Transactional(readOnly = true)
    public Map<AnalysisStatus, Long> countProblemAnalysesByStatus(LocalDate startDate, LocalDate endDate) {
        return fillMissingAnalysisStatuses(problemRepository.countProblemAnalysesByStatusForActiveProblems(startDate, endDate));
    }

    private Map<AnalysisStatus, Long> fillMissingAnalysisStatuses(Map<AnalysisStatus, Long> source) {
        Map<AnalysisStatus, Long> result = new EnumMap<>(AnalysisStatus.class);

        for (AnalysisStatus status : AnalysisStatus.values()) {
            result.put(status, source.getOrDefault(status, 0L));
        }

        return result;
    }

    @Transactional(readOnly = true)
    public Page<AdminProblemResponseDto> findAdminProblems(int page, int size) {
        return problemRepository.findAdminProblems(PageRequest.of(page, size));
    }

    @Transactional(readOnly = true)
    public Long findProblemCountByUser(Long userId) {
        log.info("userId: {} find problem count", userId);
        return problemRepository.countByUserId(userId);
    }

    @Transactional
    public Long registerProblem(ProblemRegisterDto problemRegisterDto, Long userId) {
        validateProblemContent(problemRegisterDto.memo(), problemRegisterDto.reference());

        // folderId 가 null 이면 findById(null) 이 InvalidDataAccessApiUsageException 을 던져 500 이 나간다.
        // 입력 누락은 서버 오류가 아니므로 400 으로 거절한다.
        if (problemRegisterDto.folderId() == null) {
            throw new ApplicationException(ProblemErrorCase.PROBLEM_FOLDER_ID_REQUIRED);
        }

        Folder folder = folderRepository.findByIdForShare(problemRegisterDto.folderId())
                .orElseThrow(() -> new ApplicationException(FolderErrorCase.FOLDER_NOT_FOUND));
        validateFolderOwner(folder, userId);

        Problem problem = Problem.from(problemRegisterDto, userId);
        problem.updateFolder(folder);
        problemRepository.save(problem);
        syncProblemTags(problem, userId, problemRegisterDto.tagIds());

        problem.updateReviewSchedule(LocalDate.now(java.time.ZoneId.of("Asia/Seoul")), 1, 0);
        analysisService.createSkippedAnalysis(problem.getId());
        streakCacheService.evict(userId);
        missionLogService.registerProblemWriteMission(userId);
        missionProgressUpdater.increase(userId, MissionMetric.PROBLEM_CREATED);
        eventPublisher.publishEvent(new StudyRoomActivityEvent(
                userId, StudyRoomFeedEventType.PROBLEM_REGISTERED, Map.of("count", 1)));
        eventPublisher.publishEvent(new ProblemCreatedEvent(userId, List.of(
                new ProblemCreatedEvent.ProblemData(problem.getId(), problem.getMemo(), problem.getReference(), problem.getCreatedAt())
        )));

        log.info("userId: {} register problemId: {}", userId, problem.getId());

        return problem.getId();
    }

    /**
     * 문제 등록 v2
     * - 문제 등록 + 이미지 URL 기반 이미지 엔티티 생성 + 빈 분석 객체 생성
     */
    @Transactional
    public Long registerProblemV2(ProblemRegisterV2Dto problemRegisterV2Dto, Long userId) {
        validateProblemContent(problemRegisterV2Dto.memo(), problemRegisterV2Dto.reference());
        List<String> problemImageUrls = normalizeImageUrls(problemRegisterV2Dto.problemImageUrls());
        List<String> answerImageUrls = normalizeImageUrls(problemRegisterV2Dto.answerImageUrls());
        imageUrlOwnershipService.validateUploadedBy(concat(problemImageUrls, answerImageUrls), userId);
        Folder folder = resolveRegisterFolder(problemRegisterV2Dto.folderId(), userId);

        ProblemRegisterDto baseDto = new ProblemRegisterDto(
                problemRegisterV2Dto.problemId(),
                problemRegisterV2Dto.memo(),
                problemRegisterV2Dto.reference(),
                problemRegisterV2Dto.folderId(),
                problemRegisterV2Dto.solvedAt(),
                problemRegisterV2Dto.tagIds()
        );

        Problem problem = Problem.from(baseDto, userId);
        problem.updateFolder(folder);
        problemRepository.save(problem);
        syncProblemTags(problem, userId, baseDto.tagIds());

        problemImageUrls.forEach(url -> {
            ProblemImageData imageData = ProblemImageData.from(
                    new ProblemImageDataRegisterDto(problem.getId(), url, ProblemImageType.PROBLEM_IMAGE));
            imageData.updateProblem(problem);
            problemImageDataRepository.save(imageData);
        });

        answerImageUrls.forEach(url -> {
            ProblemImageData imageData = ProblemImageData.from(
                    new ProblemImageDataRegisterDto(problem.getId(), url, ProblemImageType.ANSWER_IMAGE));
            imageData.updateProblem(problem);
            problemImageDataRepository.save(imageData);
        });
        problem.updateReviewSchedule(LocalDate.now(java.time.ZoneId.of("Asia/Seoul")), 1, 0);
        analysisService.createSkippedAnalysis(problem.getId());
        streakCacheService.evict(userId);
        missionLogService.registerProblemWriteMission(userId);
        missionProgressUpdater.increase(userId, MissionMetric.PROBLEM_CREATED);
        eventPublisher.publishEvent(new StudyRoomActivityEvent(
                userId, StudyRoomFeedEventType.PROBLEM_REGISTERED, Map.of("count", 1)));
        eventPublisher.publishEvent(new ProblemCreatedEvent(userId, List.of(
                new ProblemCreatedEvent.ProblemData(problem.getId(), problem.getMemo(), problem.getReference(), problem.getCreatedAt())
        )));

        log.info("userId: {} register problem(v2) problemId: {}", userId, problem.getId());
        return problem.getId();
    }

    /**
     * 문제 배치 등록 v2
     * - 요청 전체를 하나의 트랜잭션으로 처리
     * - 폴더/태그 검증을 저장 전에 수행해 중간 실패 시 전체 롤백
     */
    @Transactional
    public List<Long> registerProblemsV2(ProblemRegisterV2BatchDto problemRegisterV2BatchDto, Long userId) {
        List<ProblemRegisterV2Dto> registerDtos = problemRegisterV2BatchDto.problems();
        if (registerDtos == null || registerDtos.isEmpty()) {
            return List.of();
        }
        registerDtos.forEach(dto -> validateProblemContent(dto.memo(), dto.reference()));
        imageUrlOwnershipService.validateUploadedBy(registerDtos.stream()
                .flatMap(dto -> concat(normalizeImageUrls(dto.problemImageUrls()), normalizeImageUrls(dto.answerImageUrls())).stream())
                .toList(), userId);

        Map<Long, Folder> foldersById = resolveRegisterFolders(registerDtos, userId);
        Folder rootFolder = registerDtos.stream().anyMatch(dto -> dto.folderId() == null)
                ? resolveRootFolder(userId)
                : null;
        Map<Long, Tag> tagsById = findRegisterTagsById(registerDtos, userId);
        LocalDate today = LocalDate.now(java.time.ZoneId.of("Asia/Seoul"));

        List<Problem> problems = registerDtos.stream()
                .map(dto -> {
                    ProblemRegisterDto baseDto = toProblemRegisterDto(dto);
                    Problem problem = Problem.from(baseDto, userId);
                    Folder folder = dto.folderId() == null ? rootFolder : foldersById.get(dto.folderId());
                    problem.updateFolder(folder);
                    problem.updateReviewSchedule(today, 1, 0);
                    return problem;
                })
                .toList();
        problemRepository.saveAll(problems);

        List<ProblemImageData> imageDataList = new ArrayList<>();
        List<ProblemTagMapping> tagMappings = new ArrayList<>();
        List<ProblemAnalysis> analyses = new ArrayList<>();

        for (int i = 0; i < registerDtos.size(); i++) {
            ProblemRegisterV2Dto dto = registerDtos.get(i);
            Problem problem = problems.get(i);

            addImageData(imageDataList, problem, dto.problemImageUrls(), ProblemImageType.PROBLEM_IMAGE);
            addImageData(imageDataList, problem, dto.answerImageUrls(), ProblemImageType.ANSWER_IMAGE);

            for (Long tagId : toDistinctIds(dto.tagIds())) {
                tagMappings.add(ProblemTagMapping.from(problem, tagsById.get(tagId)));
            }

            ProblemAnalysis analysis = ProblemAnalysis.createSkipped(problem);
            problem.updateProblemAnalysis(analysis);
            analyses.add(analysis);
        }

        problemImageDataRepository.saveAll(imageDataList);
        problemTagMappingRepository.saveAll(tagMappings);
        problemAnalysisRepository.saveAll(analyses);

        streakCacheService.evict(userId);
        missionLogService.registerProblemWriteMissionBatch(userId, problems.size());
        // 여러 장을 한 번에 등록하면 장수만큼 오른다. 기존 적립은 하루 3건에서 멈추지만 미션 진행도는 별개다.
        missionProgressUpdater.increase(userId, MissionMetric.PROBLEM_CREATED, problems.size());
        eventPublisher.publishEvent(new StudyRoomActivityEvent(
                userId, StudyRoomFeedEventType.PROBLEM_REGISTERED, Map.of("count", problems.size())));

        List<Long> problemIds = problems.stream()
                .map(Problem::getId)
                .toList();

        eventPublisher.publishEvent(new ProblemCreatedEvent(userId, problems.stream()
                .map(p -> new ProblemCreatedEvent.ProblemData(p.getId(), p.getMemo(), p.getReference(), p.getCreatedAt()))
                .toList()));

        log.info("userId: {} register problems(v2 batch) problemIds: {}", userId, problemIds);
        return problemIds;
    }

    private Folder resolveRegisterFolder(Long folderId, Long userId) {
        if (folderId == null) {
            return resolveRootFolder(userId);
        }

        return folderRepository.findByIdForShare(folderId)
                .map(folder -> {
                    validateFolderOwner(folder, userId);
                    return folder;
                })
                .orElseThrow(() -> new ApplicationException(FolderErrorCase.FOLDER_NOT_FOUND));
    }

    private Folder resolveRootFolder(Long userId) {
        return folderRepository.findByUserIdAndParentFolderIsNull(userId)
                .orElseThrow(() -> new ApplicationException(FolderErrorCase.ROOT_FOLDER_NOT_EXIST));
    }

    private Map<Long, Folder> resolveRegisterFolders(List<ProblemRegisterV2Dto> registerDtos, Long userId) {
        Set<Long> folderIds = registerDtos.stream()
                .map(ProblemRegisterV2Dto::folderId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        if (folderIds.isEmpty()) {
            return Map.of();
        }

        Map<Long, Folder> foldersById = folderRepository.findAllByIdInForShare(folderIds).stream()
                .collect(Collectors.toMap(Folder::getId, folder -> folder));
        if (foldersById.size() != folderIds.size()) {
            throw new ApplicationException(FolderErrorCase.FOLDER_NOT_FOUND);
        }

        foldersById.values().forEach(folder -> validateFolderOwner(folder, userId));
        return foldersById;
    }

    private Map<Long, Tag> findRegisterTagsById(List<ProblemRegisterV2Dto> registerDtos, Long userId) {
        boolean hasTooManyTags = registerDtos.stream()
                .map(dto -> toDistinctIds(dto.tagIds()).size())
                .anyMatch(size -> size > 5);
        if (hasTooManyTags) {
            throw new ApplicationException(TagErrorCase.TAG_LIMIT_EXCEEDED);
        }

        Set<Long> tagIds = registerDtos.stream()
                .flatMap(dto -> toDistinctIds(dto.tagIds()).stream())
                .collect(Collectors.toSet());

        if (tagIds.isEmpty()) {
            return Map.of();
        }

        List<Tag> tags = tagRepository.findAllByIdInAndUserId(new ArrayList<>(tagIds), userId);
        if (tags.size() != tagIds.size()) {
            throw new ApplicationException(TagErrorCase.TAG_NOT_FOUND);
        }

        return tags.stream().collect(Collectors.toMap(Tag::getId, tag -> tag));
    }

    private ProblemRegisterDto toProblemRegisterDto(ProblemRegisterV2Dto dto) {
        return new ProblemRegisterDto(
                dto.problemId(),
                dto.memo(),
                dto.reference(),
                dto.folderId(),
                dto.solvedAt(),
                dto.tagIds()
        );
    }

    private void addImageData(List<ProblemImageData> imageDataList, Problem problem, List<String> imageUrls, ProblemImageType imageType) {
        normalizeImageUrls(imageUrls).forEach(url -> {
            ProblemImageData imageData = ProblemImageData.from(
                    new ProblemImageDataRegisterDto(problem.getId(), url, imageType));
            imageData.updateProblem(problem);
            imageDataList.add(imageData);
        });
    }

    private List<String> normalizeImageUrls(List<String> imageUrls) {
        if (imageUrls == null) {
            return List.of();
        }
        return imageUrls.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(url -> !url.isEmpty())
                .toList();
    }

    private List<String> concat(List<String> first, List<String> second) {
        List<String> merged = new ArrayList<>(first);
        merged.addAll(second);
        return merged;
    }

    /**
     * 문제 이미지 비동기 업로드 및 AI 분석 트리거
     */
    //@Async
    //@Transactional(propagation = Propagation.REQUIRES_NEW)
    @Transactional
    public void uploadProblemImages(Long problemId, Long userId, List<MultipartFile> images, List<String> imageTypeStrings) {

        if (images == null || imageTypeStrings == null) {
            return;
        }

        // 1. 문제 조회 및 권한 확인
        Problem problem = findProblemEntity(problemId, userId);

        // 2. imageType 문자열을 Enum으로 변환
        List<ProblemImageType> imageTypes = imageTypeStrings.stream()
                .map(ProblemImageType::valueOf)
                .toList();

        for (int i = 0; i < images.size(); i++) {
            MultipartFile imageFile = images.get(i);
            ProblemImageType imageType = imageTypes.get(i);

            // S3에 업로드
            String imageUrl = fileUploadService.uploadFileToS3(imageFile, userId);

            // DB에 저장
            ProblemImageData problemImageData = ProblemImageData.from(
                    new ProblemImageDataRegisterDto(problemId, imageUrl, imageType));
            problemImageData.updateProblem(problem);
            problemImageDataRepository.save(problemImageData);

            if (imageType.equals(ProblemImageType.SOLVE_IMAGE)) {
                missionLogService.registerProblemPracticeMission(userId, problemId);
            }

            log.info("Uploaded image to S3 for problemId: {}, imageType: {}", problemId, imageType);
        }
    }

    @Transactional
    public void addImageDataUrls(Long problemId, Long userId, AddProblemImageUrlsRequest request) {
        Problem problem = findProblemEntity(problemId, userId);
        // 이미 이 문제에 붙어 있는 URL 은 예전 형식이어도 그대로 받는다.
        List<String> attachedUrls = problemImageDataRepository.findAllByProblemId(problemId).stream()
                .map(ProblemImageData::getImageUrl)
                .toList();
        imageUrlOwnershipService.validateUploadedBy(request.imageDataList().stream()
                .map(AddProblemImageUrlsRequest.ImageUrlItem::imageUrl)
                .toList(), userId, attachedUrls);
        for (AddProblemImageUrlsRequest.ImageUrlItem item : request.imageDataList()) {
            ProblemImageType imageType = ProblemImageType.valueOf(item.problemImageType());
            ProblemImageData imageData = ProblemImageData.from(
                    new ProblemImageDataRegisterDto(problemId, item.imageUrl(), imageType));
            imageData.updateProblem(problem);
            problemImageDataRepository.save(imageData);
            if (imageType == ProblemImageType.SOLVE_IMAGE) {
                missionLogService.registerProblemPracticeMission(userId, problemId);
            }
        }
    }

    /**
     * GPT 문제 분석 요청 (RabbitMQ 방식)
     * - 이미지 유무 확인 후 상태 업데이트
     * - 이미지 있음: NOT_STARTED → PROCESSING → (커밋 후) RabbitMQ 전송
     * - 이미지 없음: NOT_STARTED → NO_IMAGE
     * - 이미 보낸 메시지가 처리 중이면 한도를 쓰지 않고 아무것도 하지 않는다
     */
    @Transactional
    public void analysisProblem(Long problemId, Long userId) {
        findProblemEntity(problemId, userId);

        analysisProblemWithoutOwnerCheck(problemId, userId);
    }

    // 호출자(analysisProblem)의 트랜잭션 안에서 실행된다.
    // private 메서드에 @Transactional 을 붙여도 Spring 프록시가 가로채지 못해 아무 효과가 없다.
    private void analysisProblemWithoutOwnerCheck(Long problemId, Long userId) {
        // 이미 분석이 완료된 문제는 재요청하지 않음
        if (problemAnalysisRepository.findByProblemId(problemId)
                .map(analysis -> analysis.getStatus() == AnalysisStatus.COMPLETED)
                .orElse(false)) {
            log.info("분석이 이미 완료된 문제이므로 분석을 진행하지 않습니다 - problemId: {}", problemId);
            return;
        }

        // 1. 문제 이미지 개수 확인
        long problemImageCount = problemImageDataRepository.findAllByProblemId(problemId)
                .stream()
                .filter(data -> data.getProblemImageType().equals(ProblemImageType.PROBLEM_IMAGE))
                .count();

        // 2. 이미지 유무에 따라 분기 처리
        if (problemImageCount == 0) {
            // 이미지 없음 → NO_IMAGE 상태로 업데이트
            analysisService.updateToNoImage(problemId);
            log.info("분석 불가 (이미지 없음) - problemId: {}", problemId);
        } else {
            // 앱의 "다시 분석하기"와 이미지 수정 API 는 처리 중에도 다시 요청한다.
            // 이미 보낸 메시지가 있으면 한도를 또 쓰거나 메시지를 겹쳐 보내지 않는다.
            // 한도보다 먼저 확인해야 겹친 요청이 한도를 쓰지 않는다.
            if (!analysisDispatchGuard.tryAcquire(problemId)) {
                log.info("이미 분석 메시지가 처리 중이라 다시 보내지 않습니다 - problemId: {}", problemId);
                return;
            }

            if (!rateLimitService.tryConsume(RateLimitKeys.AI_ANALYSIS, userId, AI_ANALYSIS_LIMIT_PER_DAY)) {
                analysisDispatchGuard.release(problemId);
                analysisService.updateToRateLimitExceeded(problemId);
                log.info("AI 분석 일일 요청 제한 초과 - userId: {}, problemId: {}", userId, problemId);
                return;
            }

            // 이미지 있음 → PROCESSING 상태로 업데이트하고, 커밋된 뒤에 RabbitMQ 로 전송한다.
            // 커밋 전에 보내면 컨슈머가 커밋 전 상태를 읽을 수 있다(ProblemAnalysisDispatcher 참고).
            try {
                analysisService.updateToProcessing(problemId);
            } catch (RuntimeException e) {
                // 아직 전송을 등록하기 전이라 롤백 정리가 걸려 있지 않다. 여기서 직접 되돌린다.
                analysisDispatchGuard.release(problemId);
                rateLimitService.refund(RateLimitKeys.AI_ANALYSIS, userId);
                throw e;
            }
            analysisDispatcher.dispatchAfterCommit(problemId, userId, RateLimitKeys.AI_ANALYSIS);
            log.info("GPT 분석 요청 등록 - problemId: {}, 이미지 개수: {}", problemId, problemImageCount);
        }
    }

    @Transactional
    public void updateProblemAnalysisToNoImage(Long problemId, Long userId) {
        findProblemEntity(problemId, userId);
        analysisService.updateToNoImage(problemId);
    }

    @Transactional
    public void updateProblemInfo(ProblemRegisterDto problemRegisterDto, Long userId) {
        validateProblemContent(problemRegisterDto.memo(), problemRegisterDto.reference());

        Problem problem = findProblemEntity(problemRegisterDto.problemId(), userId);

        problem.updateProblem(problemRegisterDto);
        syncProblemTags(problem, userId, problemRegisterDto.tagIds());
        reminderService.refreshSnapshot(problem.getId(), problem.getMemo(), problem.getReference());

        log.info("userId: {} update problemId: {}", userId, problem.getId());
    }

    @Transactional
    public void updateProblemFolder(ProblemRegisterDto problemRegisterDto, Long userId) {
        Problem problem = findProblemEntity(problemRegisterDto.problemId(), userId);

        if (problemRegisterDto.folderId() != null) {
            Folder folder = folderRepository.findByIdForShare(problemRegisterDto.folderId())
                    .orElseThrow(() -> new ApplicationException(FolderErrorCase.FOLDER_NOT_FOUND));
            validateFolderOwner(folder, userId);

            problem.updateFolder(folder);

            log.info("userId: {} update problem folder, problemId: {}, folderId: {}", userId, problem.getId(), folder.getId());
        }
    }

    @Transactional
    public void updateProblemTags(Long problemId, Long userId, ProblemTagUpdateDto problemTagUpdateDto) {
        Problem problem = findProblemEntity(problemId, userId);

        Set<Long> addTagIds = toDistinctIds(problemTagUpdateDto.addTagIds());
        Set<Long> removeTagIds = toDistinctIds(problemTagUpdateDto.removeTagIds());

        List<ProblemTagMapping> existingMappings = problemTagMappingRepository.findAllByProblemId(problemId);
        Set<Long> existingTagIds = existingMappings.stream()
                .map(mapping -> mapping.getTag().getId())
                .collect(Collectors.toSet());

        List<ProblemTagMapping> mappingsToDelete = existingMappings.stream()
                .filter(mapping -> removeTagIds.contains(mapping.getTag().getId()))
                .toList();
        if (!mappingsToDelete.isEmpty()) {
            problemTagMappingRepository.deleteAll(mappingsToDelete);
        }

        Set<Long> currentTagIds = new LinkedHashSet<>(existingTagIds);
        currentTagIds.removeAll(mappingsToDelete.stream()
                .map(mapping -> mapping.getTag().getId())
                .collect(Collectors.toSet()));

        Set<Long> candidateAddTagIds = new LinkedHashSet<>(addTagIds);
        candidateAddTagIds.removeAll(currentTagIds);

        if (!candidateAddTagIds.isEmpty()) {
            List<Tag> tagsToAdd = tagRepository.findAllByIdInAndUserId(new ArrayList<>(candidateAddTagIds), userId);
            if (tagsToAdd.size() != candidateAddTagIds.size()) {
                throw new ApplicationException(TagErrorCase.TAG_NOT_FOUND);
            }

            if (currentTagIds.size() + tagsToAdd.size() > 5) {
                throw new ApplicationException(TagErrorCase.TAG_LIMIT_EXCEEDED);
            }

            for (Tag tag : tagsToAdd) {
                problemTagMappingRepository.save(ProblemTagMapping.from(problem, tag));
            }
        }

        log.info("userId: {} updated tags for problemId: {}, addCount: {}, removeCount: {}",
                userId, problemId, addTagIds.size(), removeTagIds.size());
    }

    private Set<Long> toDistinctIds(List<Long> ids) {
        if (ids == null) {
            return Set.of();
        }
        return ids.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private void syncProblemTags(Problem problem, Long userId, List<Long> requestedTagIds) {
        // null이면 기존 태그 유지, 빈 배열이면 전체 해제
        if (requestedTagIds == null) {
            return;
        }

        Set<Long> targetTagIds = toDistinctIds(requestedTagIds);
        if (targetTagIds.size() > 5) {
            throw new ApplicationException(TagErrorCase.TAG_LIMIT_EXCEEDED);
        }

        List<ProblemTagMapping> existingMappings = problemTagMappingRepository.findAllByProblemId(problem.getId());
        Set<Long> existingTagIds = existingMappings.stream()
                .map(mapping -> mapping.getTag().getId())
                .collect(Collectors.toSet());

        if (!targetTagIds.isEmpty()) {
            List<Tag> targetTags = tagRepository.findAllByIdInAndUserId(new ArrayList<>(targetTagIds), userId);
            if (targetTags.size() != targetTagIds.size()) {
                throw new ApplicationException(TagErrorCase.TAG_NOT_FOUND);
            }

            Set<Long> targetTagIdSet = targetTags.stream().map(Tag::getId).collect(Collectors.toSet());

            List<ProblemTagMapping> mappingsToDelete = existingMappings.stream()
                    .filter(mapping -> !targetTagIdSet.contains(mapping.getTag().getId()))
                    .toList();
            if (!mappingsToDelete.isEmpty()) {
                problemTagMappingRepository.deleteAll(mappingsToDelete);
            }

            for (Tag tag : targetTags) {
                if (!existingTagIds.contains(tag.getId())) {
                    problemTagMappingRepository.save(ProblemTagMapping.from(problem, tag));
                }
            }
        } else {
            // [] 전달 시 전체 해제
            if (!existingMappings.isEmpty()) {
                problemTagMappingRepository.deleteAll(existingMappings);
            }
        }
    }

    /**
     * 문제 삭제 (비동기 S3 파일 삭제 적용)
     * - DB 삭제: 동기 (즉시 완료)
     * - S3 파일 삭제: 지울 이미지 목록만 돌려준다. 호출자가 모아서 커밋 뒤에 한 번에 보낸다
     * - PracticeNote 매핑 삭제: 동기 (데이터 정합성)
     */
    // 호출자(deleteProblem / deleteProblemList / deleteFolderProblems / deleteAllUserProblems)의 트랜잭션 안에서 실행된다.
    private List<S3DeleteMessage> deleteProblemWithoutOwnerCheck(Long problemId) {
        // 1. 이미지 데이터 조회
        List<ProblemImageData> imageDataList = problemImageDataRepository.findAllByProblemId(problemId);
        // 1-1. 태그 매핑 조회
        List<ProblemTagMapping> problemTagMappings = problemTagMappingRepository.findAllByProblemId(problemId);

        // 2. DB에서 이미지 메타데이터 삭제 (동기 - 빠름)
        problemImageDataRepository.deleteAll(imageDataList);

        // 3. 태그 매핑 삭제 (동기 - 데이터 정합성 보장)
        if (!problemTagMappings.isEmpty()) {
            problemTagMappingRepository.deleteAll(problemTagMappings);
        }

        // 4. PracticeNote 매핑 삭제 (동기 - 데이터 정합성 보장)
        practiceNoteRepository.deleteProblemFromAllPractice(problemId);

        // 5. 미발송 알림 예약 취소
        reminderService.cancelPendingByProblem(problemId);

        // 6. 문제 삭제 (Soft Delete)
        problemRepository.deleteById(problemId);

        log.info("problemId: {} DB 삭제 완료", problemId);

        // 7. S3 파일은 여기서 지우지 않는다. 같은 트랜잭션의 뒤쪽 작업이 실패해 롤백되면 파일도 남아야 한다.
        return imageDataList.stream()
                .map(imageData -> new S3DeleteMessage(imageData.getImageUrl(), problemId))
                .toList();
    }

    /**
     * ownerUserId 의 문제에서 지운 이미지 중 다른 사용자가 쓰지 않는 파일만 남긴다.
     * 다른 사용자의 행이 같은 URL 을 가리키거나 key 의 사용자가 다르면 그 파일은 S3 에서 지우지 않는다.
     * 같은 URL 이 여러 번 나오면 한 번만 보낸다.
     */
    private List<S3DeleteMessage> filterDeletableMessages(List<S3DeleteMessage> messages, Long ownerUserId) {
        if (messages.isEmpty()) {
            return messages;
        }
        Map<String, S3DeleteMessage> byUrl = new LinkedHashMap<>();
        messages.forEach(message -> byUrl.putIfAbsent(message.getImageUrl(), message));
        List<String> deletableUrls = imageUrlOwnershipService.filterDeletable(byUrl.keySet(), ownerUserId);
        log.info("userId: {} S3 삭제 대상 {}/{}개", ownerUserId, deletableUrls.size(), byUrl.size());
        return deletableUrls.stream()
                .map(byUrl::get)
                .toList();
    }

    @Transactional
    public void deleteProblem(Long problemId, Long userId) {
        s3DeleteAfterCommitSender.sendAfterCommit(filterDeletableMessages(deleteOwnedProblem(problemId, userId), userId));
    }

    private List<S3DeleteMessage> deleteOwnedProblem(Long problemId, Long userId) {
        findProblemEntity(problemId, userId);
        List<S3DeleteMessage> s3DeleteMessages = deleteProblemWithoutOwnerCheck(problemId);
        streakCacheService.evict(userId);
        return s3DeleteMessages;
    }

    /**
     * 이 사용자의 문제에 붙은 이미지 행만 지운다. 같은 URL 을 가진 다른 사용자의 행은 남기고,
     * 다른 사용자가 그 파일을 쓰고 있으면 S3 파일도 남긴다.
     */
    @Transactional
    public void deleteProblemImageData(String imageUrl, Long userId) {
        List<ProblemImageData> imageDataList = problemImageDataRepository.findAllByImageUrlAndUserId(imageUrl, userId);
        if (imageDataList.isEmpty()) {
            if (problemImageDataRepository.existsByImageUrl(imageUrl)) {
                throw new ApplicationException(ProblemErrorCase.PROBLEM_USER_UNMATCHED);
            }
            throw new ApplicationException(ProblemErrorCase.PROBLEM_NOT_FOUND);
        }

        if (imageUrlOwnershipService.isDeletable(imageUrl, userId)) {
            fileUploadService.deleteImageFileFromS3(imageUrl);
        }
        problemImageDataRepository.deleteAll(imageDataList);
    }

    @Transactional
    public void deleteProblemList(Long userId, List<Long> problemIdList) {
        // 중간에 하나라도 실패하면 전체가 롤백되므로, S3 삭제는 모두 지운 뒤 커밋되면 한 번에 보낸다.
        List<S3DeleteMessage> s3DeleteMessages = new ArrayList<>();
        problemIdList.forEach(problemId -> s3DeleteMessages.addAll(deleteOwnedProblem(problemId, userId)));
        s3DeleteAfterCommitSender.sendAfterCommit(filterDeletableMessages(s3DeleteMessages, userId));
    }

    // 호출자(deleteAllByFolderIds)의 트랜잭션 안에서 실행된다.
    private List<S3DeleteMessage> deleteFolderProblems(Long folderId) {
        List<S3DeleteMessage> s3DeleteMessages = new ArrayList<>();
        problemRepository.findAllByFolderId(folderId)
                .forEach(problem -> s3DeleteMessages.addAll(deleteProblemWithoutOwnerCheck(problem.getId())));

        log.info("problem in folderId: {} has deleted", folderId);
        return s3DeleteMessages;
    }

    @Transactional
    public void deleteAllByFolderIds(Long userId, Collection<Long> folderIds) {
        List<S3DeleteMessage> s3DeleteMessages = new ArrayList<>();
        folderIds.forEach(folderId -> {
            validateFolderOwner(folderId, userId);
            s3DeleteMessages.addAll(deleteFolderProblems(folderId));
        });
        s3DeleteAfterCommitSender.sendAfterCommit(filterDeletableMessages(s3DeleteMessages, userId));
    }

    @Transactional
    public void deleteAllUserProblems(Long userId) {
        List<S3DeleteMessage> s3DeleteMessages = new ArrayList<>();
        problemRepository.findAllByUserId(userId)
                .forEach(problem -> s3DeleteMessages.addAll(deleteProblemWithoutOwnerCheck(problem.getId())));
        s3DeleteAfterCommitSender.sendAfterCommit(filterDeletableMessages(s3DeleteMessages, userId));

        streakCacheService.evict(userId);
        log.info("userId: {} delete all user problems", userId);
    }

    /**
     * V2 API: 커서 기반 폴더의 문제 조회
     * @param folderId 폴더 ID
     * @param cursor 마지막으로 조회한 문제 ID (null이면 처음부터)
     * @param size 조회할 개수
     * @return 커서 기반 페이징 응답
     */
    @Transactional(readOnly = true)
    public CursorPageResponse<ProblemResponseDto> findProblemsByFolderWithCursor(Long folderId, Long userId, Long cursor, int size) {
        return findProblemsByFolderWithCursor(folderId, userId, cursor, size, CursorSort.OLDEST);
    }

    @Transactional(readOnly = true)
    public CursorPageResponse<ProblemResponseDto> findProblemsByFolderWithCursor(Long folderId, Long userId, Long cursor, int size, CursorSort sort) {
        validateFolderOwner(folderId, userId);
        List<Problem> problems = problemRepository.findProblemsByFolderWithCursor(folderId, cursor, size, sort);

        boolean hasNext = problems.size() > size;
        List<Problem> content = hasNext ? problems.subList(0, size) : problems;
        Long nextCursor = hasNext ? content.get(content.size() - 1).getId() : null;

        List<ProblemResponseDto> dtoList = toProblemResponseDtos(content);

        log.info("folderId: {} find problems with cursor: {}, size: {}, hasNext: {}", folderId, cursor, size, hasNext);
        return CursorPageResponse.of(dtoList, nextCursor, hasNext, size);
    }

    /**
     * V2 API: 커서 기반 태그의 문제 조회
     * @param tagId 태그 ID
     * @param userId 유저 ID
     * @param cursor 마지막으로 조회한 문제 ID (null이면 처음부터)
     * @param size 조회할 개수
     * @return 커서 기반 페이징 응답
     */
    @Transactional(readOnly = true)
    public CursorPageResponse<ProblemResponseDto> findProblemsByTagWithCursor(Long tagId, Long userId, Long cursor, int size) {
        return findProblemsByTagWithCursor(tagId, userId, cursor, size, CursorSort.OLDEST);
    }

    @Transactional(readOnly = true)
    public CursorPageResponse<ProblemResponseDto> findProblemsByTagWithCursor(Long tagId, Long userId, Long cursor, int size, CursorSort sort) {
        Tag tag = tagRepository.findById(tagId)
                .orElseThrow(() -> new ApplicationException(TagErrorCase.TAG_NOT_FOUND));

        if (!Objects.equals(tag.getUserId(), userId)) {
            throw new ApplicationException(TagErrorCase.TAG_USER_UNMATCHED);
        }

        List<Problem> problems = problemRepository.findProblemsByTagWithCursor(tagId, userId, cursor, size, sort);

        boolean hasNext = problems.size() > size;
        List<Problem> content = hasNext ? problems.subList(0, size) : problems;
        Long nextCursor = hasNext ? content.get(content.size() - 1).getId() : null;

        List<ProblemResponseDto> dtoList = toProblemResponseDtos(content);

        log.info("userId: {} find problems by tagId: {} with cursor: {}, size: {}, hasNext: {}",
                userId, tagId, cursor, size, hasNext);
        return CursorPageResponse.of(dtoList, nextCursor, hasNext, size);
    }

    /**
     * V2 API: 커서 기반 제목 contains 문제 조회
     * - 제목은 현재 Problem.reference 필드를 사용
     */
    @Transactional(readOnly = true)
    public CursorPageResponse<ProblemResponseDto> findProblemsByTitleWithCursor(
            String titleQuery, Long userId, Long cursor, int size
    ) {
        return findProblemsByTitleWithCursor(titleQuery, userId, cursor, size, CursorSort.OLDEST);
    }

    @Transactional(readOnly = true)
    public CursorPageResponse<ProblemResponseDto> findProblemsByTitleWithCursor(
            String titleQuery, Long userId, Long cursor, int size, CursorSort sort
    ) {
        String query = titleQuery == null ? "" : titleQuery.trim();
        if (query.isEmpty()) {
            return CursorPageResponse.of(List.of(), null, false, size);
        }

        List<Problem> problems = problemRepository.findProblemsByTitleWithCursor(query, userId, cursor, size, sort);

        boolean hasNext = problems.size() > size;
        List<Problem> content = hasNext ? problems.subList(0, size) : problems;
        Long nextCursor = hasNext ? content.get(content.size() - 1).getId() : null;

        List<ProblemResponseDto> dtoList = toProblemResponseDtos(content);

        log.info("userId: {} find problems by title query: '{}' with cursor: {}, size: {}, hasNext: {}",
                userId, query, cursor, size, hasNext);
        return CursorPageResponse.of(dtoList, nextCursor, hasNext, size);
    }

    private ProblemResponseDto toProblemResponseDto(Problem problem) {
        Long solveCount = problemSolveRepository.countByProblemId(problem.getId());
        return ProblemResponseDto.from(
                problem,
                solveCount != null ? solveCount : 0L,
                problemSolveRepository.findLastSolvedAtByProblemId(problem.getId())
        );
    }

    private List<ProblemResponseDto> toProblemResponseDtos(List<Problem> problems) {
        if (problems.isEmpty()) {
            return List.of();
        }

        List<Long> problemIds = problems.stream()
                .map(Problem::getId)
                .toList();

        Map<Long, ProblemSolveSummary> solveSummariesByProblemId = problemSolveRepository.findSolveSummariesByProblemIds(problemIds)
                .stream()
                .collect(Collectors.toMap(
                        ProblemSolveSummary::getProblemId,
                        solveSummary -> solveSummary
                ));

        return problems.stream()
                .map(problem -> {
                    ProblemSolveSummary solveSummary = solveSummariesByProblemId.get(problem.getId());
                    return ProblemResponseDto.from(
                            problem,
                            solveSummary != null ? solveSummary.getSolveCount() : 0L,
                            solveSummary != null ? solveSummary.getLastSolvedAt() : null
                    );
                })
                .collect(Collectors.toList());
    }

    /**
     * 컬럼 길이를 넘는 입력은 DB에서 Data truncation 으로 500 이 나므로 저장 전에 400 으로 거절한다.
     */
    private void validateProblemContent(String memo, String reference) {
        if (memo != null && memo.length() > Problem.MEMO_MAX_LENGTH) {
            throw new ApplicationException(ProblemErrorCase.PROBLEM_MEMO_TOO_LONG);
        }

        if (reference != null && reference.length() > Problem.REFERENCE_MAX_LENGTH) {
            throw new ApplicationException(ProblemErrorCase.PROBLEM_REFERENCE_TOO_LONG);
        }
    }

    private void validateFolderOwner(Long folderId, Long userId) {
        Folder folder = folderRepository.findById(folderId)
                .orElseThrow(() -> new ApplicationException(FolderErrorCase.FOLDER_NOT_FOUND));
        validateFolderOwner(folder, userId);
    }

    private void validateFolderOwner(Folder folder, Long userId) {
        if (!Objects.equals(folder.getUserId(), userId)) {
            throw new ApplicationException(FolderErrorCase.FOLDER_USER_UNMATCHED);
        }
    }

    /**
     * 오늘 복습 대상 문제 조회.
     *
     * <p>엔티티를 그대로 조회하면 {@code Problem.problemAnalysis} 가 mappedBy OneToOne 이라
     * 행마다 존재 여부 확인 쿼리가 한 번씩 더 나간다(N+1). 응답에 필요한 값은 스칼라뿐이므로
     * 프로젝션으로 한 번에 읽는다.
     */
    @Transactional(readOnly = true)
    public ReviewDueResponseDto getReviewDueProblems(Long userId) {
        LocalDate today = LocalDate.now(java.time.ZoneId.of("Asia/Seoul"));
        List<ReviewDueProblemProjection> dueProblems = problemRepository.findReviewDueProblems(
                userId, today, ReviewIntervalCalculator.MASTERY_THRESHOLD);

        long overdueCount = dueProblems.stream()
                .filter(p -> p.nextReviewAt().isBefore(today))
                .count();

        return ReviewDueResponseDto.builder()
                .dueCount(dueProblems.size())
                .overdueCount(overdueCount)
                .requiredCorrectCount(ReviewIntervalCalculator.MASTERY_THRESHOLD)
                .problems(dueProblems.stream()
                        .map(ReviewDueResponseDto.ReviewDueProblemDto::from)
                        .toList())
                .build();
    }
}
