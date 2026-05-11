package com.aisip.OnO.backend.problem.service;

import com.aisip.OnO.backend.admin.dto.AdminProblemResponseDto;
import com.aisip.OnO.backend.common.exception.ApplicationException;
import com.aisip.OnO.backend.common.ratelimit.RateLimitService;
import com.aisip.OnO.backend.common.response.CursorPageResponse;
import com.aisip.OnO.backend.config.rabbitmq.producer.S3DeleteProducer;
import com.aisip.OnO.backend.config.rabbitmq.producer.ProblemAnalysisProducer;
import com.aisip.OnO.backend.mission.service.MissionLogService;
import com.aisip.OnO.backend.problem.entity.AnalysisStatus;
import com.aisip.OnO.backend.problem.entity.ProblemAnalysis;
import com.aisip.OnO.backend.problem.entity.ProblemImageType;
import com.aisip.OnO.backend.util.fileupload.service.FileUploadService;
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
import com.aisip.OnO.backend.practicenote.repository.PracticeNoteRepository;
import com.aisip.OnO.backend.problemsolve.repository.ProblemSolveRepository;
import com.aisip.OnO.backend.problemsolve.repository.ProblemSolveSummary;
import com.aisip.OnO.backend.tag.entity.ProblemTagMapping;
import com.aisip.OnO.backend.tag.entity.Tag;
import com.aisip.OnO.backend.tag.exception.TagErrorCase;
import com.aisip.OnO.backend.tag.repository.ProblemTagMappingRepository;
import com.aisip.OnO.backend.tag.repository.TagRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDate;
import java.time.LocalTime;
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
    private static final String AI_ANALYSIS_RATE_LIMIT_KEY = "ai_analysis";
    private static final int AI_ANALYSIS_LIMIT_PER_DAY = 20;

    private final ProblemRepository problemRepository;

    private final ProblemImageDataRepository problemImageDataRepository;
    private final ProblemAnalysisRepository problemAnalysisRepository;

    private final FolderRepository folderRepository;

    private final FileUploadService fileUploadService;

    private final MissionLogService missionLogService;

    private final ProblemAnalysisService analysisService;

    private final PracticeNoteRepository practiceNoteRepository;

    private final ProblemSolveRepository problemSolveRepository;

    private final S3DeleteProducer s3DeleteProducer;

    private final ProblemAnalysisProducer analysisProducer;
    private final TagRepository tagRepository;
    private final ProblemTagMappingRepository problemTagMappingRepository;
    private final RateLimitService rateLimitService;

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

        Folder folder = folderRepository.findById(problemRegisterDto.folderId())
                .orElseThrow(() -> new ApplicationException(FolderErrorCase.FOLDER_NOT_FOUND));
        validateFolderOwner(folder, userId);

        Problem problem = Problem.from(problemRegisterDto, userId);
        problem.updateFolder(folder);
        problemRepository.save(problem);
        syncProblemTags(problem, userId, problemRegisterDto.tagIds());

        problem.updateReviewSchedule(LocalDate.now(java.time.ZoneId.of("Asia/Seoul")), 1, 0);
        analysisService.createSkippedAnalysis(problem.getId());
        missionLogService.registerProblemWriteMission(userId);

        log.info("userId: {} register problemId: {}", userId, problem.getId());

        return problem.getId();
    }

    /**
     * 문제 등록 v2
     * - 문제 등록 + 이미지 URL 기반 이미지 엔티티 생성 + 빈 분석 객체 생성
     */
    @Transactional
    public Long registerProblemV2(ProblemRegisterV2Dto problemRegisterV2Dto, Long userId) {
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

        if (problemRegisterV2Dto.problemImageUrls() != null) {
            problemRegisterV2Dto.problemImageUrls().stream()
                    .filter(Objects::nonNull)
                    .map(String::trim)
                    .filter(url -> !url.isEmpty())
                    .forEach(url -> {
                        ProblemImageData imageData = ProblemImageData.from(
                                new ProblemImageDataRegisterDto(problem.getId(), url, ProblemImageType.PROBLEM_IMAGE));
                        imageData.updateProblem(problem);
                        problemImageDataRepository.save(imageData);
                    });
        }

        if (problemRegisterV2Dto.answerImageUrls() != null) {
            problemRegisterV2Dto.answerImageUrls().stream()
                    .filter(Objects::nonNull)
                    .map(String::trim)
                    .filter(url -> !url.isEmpty())
                    .forEach(url -> {
                        ProblemImageData imageData = ProblemImageData.from(
                                new ProblemImageDataRegisterDto(problem.getId(), url, ProblemImageType.ANSWER_IMAGE));
                        imageData.updateProblem(problem);
                        problemImageDataRepository.save(imageData);
                    });
        }
        problem.updateReviewSchedule(LocalDate.now(java.time.ZoneId.of("Asia/Seoul")), 1, 0);
        analysisService.createSkippedAnalysis(problem.getId());
        missionLogService.registerProblemWriteMission(userId);

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

        problems.forEach(problem -> missionLogService.registerProblemWriteMission(userId));

        List<Long> problemIds = problems.stream()
                .map(Problem::getId)
                .toList();
        log.info("userId: {} register problems(v2 batch) problemIds: {}", userId, problemIds);
        return problemIds;
    }

    private Folder resolveRegisterFolder(Long folderId, Long userId) {
        if (folderId == null) {
            return resolveRootFolder(userId);
        }

        return folderRepository.findById(folderId)
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

        Map<Long, Folder> foldersById = folderRepository.findAllById(folderIds).stream()
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
        if (imageUrls == null) {
            return;
        }

        imageUrls.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(url -> !url.isEmpty())
                .forEach(url -> {
                    ProblemImageData imageData = ProblemImageData.from(
                            new ProblemImageDataRegisterDto(problem.getId(), url, imageType));
                    imageData.updateProblem(problem);
                    imageDataList.add(imageData);
                });
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
            String imageUrl = fileUploadService.uploadFileToS3(imageFile);

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

    /**
     * GPT 문제 분석 요청 (RabbitMQ 방식)
     * - 이미지 유무 확인 후 상태 업데이트
     * - 이미지 있음: NOT_STARTED → PROCESSING → RabbitMQ 전송
     * - 이미지 없음: NOT_STARTED → NO_IMAGE
     */
    @Transactional
    public void analysisProblem(Long problemId, Long userId) {
        findProblemEntity(problemId, userId);

        analysisProblemWithoutOwnerCheck(problemId, userId);
    }

    @Transactional
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
            if (!rateLimitService.tryConsume(AI_ANALYSIS_RATE_LIMIT_KEY, userId, AI_ANALYSIS_LIMIT_PER_DAY)) {
                analysisService.updateToRateLimitExceeded(problemId);
                log.info("AI 분석 일일 요청 제한 초과 - userId: {}, problemId: {}", userId, problemId);
                return;
            }

            // 이미지 있음 → PROCESSING 상태로 업데이트 후 RabbitMQ 전송
            analysisService.updateToProcessing(problemId);
            analysisProducer.sendAnalysisMessage(problemId);
            log.info("GPT 분석 요청 전송 완료 - problemId: {}, 이미지 개수: {}", problemId, problemImageCount);
        }
    }

    @Transactional
    public void updateProblemAnalysisToNoImage(Long problemId, Long userId) {
        findProblemEntity(problemId, userId);
        analysisService.updateToNoImage(problemId);
    }

    @Transactional
    public void updateProblemInfo(ProblemRegisterDto problemRegisterDto, Long userId) {

        Problem problem = findProblemEntity(problemRegisterDto.problemId(), userId);

        problem.updateProblem(problemRegisterDto);
        syncProblemTags(problem, userId, problemRegisterDto.tagIds());

        log.info("userId: {} update problemId: {}", userId, problem.getId());
    }

    @Transactional
    public void updateProblemFolder(ProblemRegisterDto problemRegisterDto, Long userId) {
        Problem problem = findProblemEntity(problemRegisterDto.problemId(), userId);

        if (problemRegisterDto.folderId() != null) {
            Folder folder = folderRepository.findById(problemRegisterDto.folderId())
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
     * - S3 파일 삭제: 비동기 (RabbitMQ Producer로 전송)
     * - PracticeNote 매핑 삭제: 동기 (데이터 정합성)
     */
    @Transactional
    private void deleteProblemWithoutOwnerCheck(Long problemId) {
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

        // 5. 문제 삭제 (Soft Delete)
        problemRepository.deleteById(problemId);

        log.info("problemId: {} DB 삭제 완료", problemId);

        // 6. S3 파일 삭제는 비동기로 처리 (RabbitMQ Producer)
        imageDataList.forEach(imageData -> {
            try {
                s3DeleteProducer.sendDeleteMessage(imageData.getImageUrl(), problemId);
            } catch (Exception e) {
                log.error("S3 삭제 메시지 전송 실패 - problemId: {}, error: {}",
                        problemId, e.getMessage());
            }
        });

        log.info("problemId: {} S3 삭제 메시지 전송 완료 ({}개)", problemId, imageDataList.size());
    }

    @Transactional
    public void deleteProblem(Long problemId, Long userId) {
        findProblemEntity(problemId, userId);
        deleteProblemWithoutOwnerCheck(problemId);
    }

    @Transactional
    public void deleteProblemImageData(String imageUrl, Long userId) {
        ProblemImageData imageData = problemImageDataRepository.findByImageUrl(imageUrl)
                .orElseThrow(() -> new ApplicationException(ProblemErrorCase.PROBLEM_NOT_FOUND));
        if (!Objects.equals(imageData.getProblem().getUserId(), userId)) {
            throw new ApplicationException(ProblemErrorCase.PROBLEM_USER_UNMATCHED);
        }

        fileUploadService.deleteImageFileFromS3(imageUrl);
        problemImageDataRepository.deleteByImageUrl(imageUrl);
    }

    @Transactional
    public void deleteProblemList(Long userId, List<Long> problemIdList) {
        problemIdList.forEach(problemId -> deleteProblem(problemId, userId));
    }

    @Transactional
    private void deleteFolderProblems(Long folderId) {
        problemRepository.findAllByFolderId(folderId)
                .forEach(problem -> {
                    deleteProblemWithoutOwnerCheck(problem.getId());
                });

        log.info("problem in folderId: {} has deleted", folderId);
    }

    @Transactional
    public void deleteAllByFolderIds(Long userId, Collection<Long> folderIds) {
        folderIds.forEach(folderId -> {
            validateFolderOwner(folderId, userId);
            deleteFolderProblems(folderId);
        });
    }

    @Transactional
    public void deleteAllUserProblems(Long userId) {
        problemRepository.findAllByUserId(userId)
                .forEach(problem -> {
                    deleteProblemWithoutOwnerCheck(problem.getId());
                });

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
        validateFolderOwner(folderId, userId);
        List<Problem> problems = problemRepository.findProblemsByFolderWithCursor(folderId, cursor, size);

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
        Tag tag = tagRepository.findById(tagId)
                .orElseThrow(() -> new ApplicationException(TagErrorCase.TAG_NOT_FOUND));

        if (!Objects.equals(tag.getUserId(), userId)) {
            throw new ApplicationException(TagErrorCase.TAG_USER_UNMATCHED);
        }

        List<Problem> problems = problemRepository.findProblemsByTagWithCursor(tagId, userId, cursor, size);

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
        String query = titleQuery == null ? "" : titleQuery.trim();
        if (query.isEmpty()) {
            return CursorPageResponse.of(List.of(), null, false, size);
        }

        List<Problem> problems = problemRepository.findProblemsByTitleWithCursor(query, userId, cursor, size);

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

    @Transactional(readOnly = true)
    public ReviewDueResponseDto getReviewDueProblems(Long userId) {
        LocalDate today = LocalDate.now(java.time.ZoneId.of("Asia/Seoul"));
        List<Problem> dueProblems = problemRepository.findReviewDueProblems(userId, today);

        long overdueCount = dueProblems.stream()
                .filter(p -> p.getNextReviewAt().isBefore(today))
                .count();

        List<ReviewDueResponseDto.ReviewDueProblemDto> problemDtos = dueProblems.stream()
                .map(ReviewDueResponseDto.ReviewDueProblemDto::from)
                .collect(Collectors.toList());

        return ReviewDueResponseDto.builder()
                .dueCount(dueProblems.size())
                .overdueCount(overdueCount)
                .problems(problemDtos)
                .build();
    }
}
