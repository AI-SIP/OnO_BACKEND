package com.aisip.OnO.backend.problem.service;

import com.aisip.OnO.backend.common.exception.ApplicationException;
import com.aisip.OnO.backend.common.response.CursorPageResponse;
import com.aisip.OnO.backend.config.rabbitmq.producer.S3DeleteProducer;
import com.aisip.OnO.backend.config.rabbitmq.producer.ProblemAnalysisProducer;
import com.aisip.OnO.backend.mission.service.MissionLogService;
import com.aisip.OnO.backend.problem.entity.ProblemImageType;
import com.aisip.OnO.backend.util.fileupload.service.FileUploadService;
import com.aisip.OnO.backend.problem.dto.ProblemImageDataRegisterDto;
import com.aisip.OnO.backend.problem.dto.ProblemRegisterDto;
import com.aisip.OnO.backend.folder.entity.Folder;
import com.aisip.OnO.backend.problem.entity.ProblemImageData;
import com.aisip.OnO.backend.folder.exception.FolderErrorCase;
import com.aisip.OnO.backend.problem.exception.ProblemErrorCase;
import com.aisip.OnO.backend.folder.repository.FolderRepository;
import com.aisip.OnO.backend.problem.repository.ProblemImageDataRepository;
import com.aisip.OnO.backend.problem.dto.ProblemResponseDto;
import com.aisip.OnO.backend.problem.entity.Problem;
import com.aisip.OnO.backend.problem.repository.ProblemRepository;
import com.aisip.OnO.backend.practicenote.repository.PracticeNoteRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProblemService {
    private final ProblemRepository problemRepository;

    private final ProblemImageDataRepository problemImageDataRepository;

    private final FolderRepository folderRepository;

    private final FileUploadService fileUploadService;

    private final MissionLogService missionLogService;

    private final ProblemAnalysisService analysisService;

    private final PracticeNoteRepository practiceNoteRepository;

    private final S3DeleteProducer s3DeleteProducer;

    private final ProblemAnalysisProducer analysisProducer;

    @Transactional(readOnly = true)
    public ProblemResponseDto findProblem(Long problemId) {
        Problem problem = problemRepository.findProblemWithImageData(problemId)
                .orElseThrow(() -> new ApplicationException(ProblemErrorCase.PROBLEM_NOT_FOUND));

        return ProblemResponseDto.from(problem);
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

        return problemRepository.findAllByUserId(userId)
                .stream()
                .map(ProblemResponseDto::from)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<ProblemResponseDto> findFolderProblemList(Long folderId) {
        return problemRepository.findAllByFolderId(folderId)
                .stream()
                .map(ProblemResponseDto::from)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<ProblemResponseDto> findAllProblems() {
        return problemRepository.findAll()
                .stream()
                .sorted((p1, p2) -> p2.getCreatedAt().compareTo(p1.getCreatedAt())) // 최신순 정렬
                .map(ProblemResponseDto::from)
                .collect(Collectors.toList());
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

        Problem problem = Problem.from(problemRegisterDto, userId);
        problem.updateFolder(folder);
        problemRepository.save(problem);

        analysisService.createSkippedAnalysis(problem.getId());
        missionLogService.registerProblemWriteMission(userId);

        log.info("userId: {} register problemId: {}", userId, problem.getId());

        return problem.getId();
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

            log.info("Uploaded image to S3: {} for problemId: {}", imageUrl, problemId);
        }
    }

    /**
     * GPT 문제 분석 요청 (RabbitMQ 방식)
     * - 이미지 유무 확인 후 상태 업데이트
     * - 이미지 있음: NOT_STARTED → PROCESSING → RabbitMQ 전송
     * - 이미지 없음: NOT_STARTED → NO_IMAGE
     */
    @Transactional
    public void analysisProblem(Long problemId) {
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
            // 이미지 있음 → PROCESSING 상태로 업데이트 후 RabbitMQ 전송
            analysisService.updateToProcessing(problemId);
            analysisProducer.sendAnalysisMessage(problemId);
            log.info("GPT 분석 요청 전송 완료 - problemId: {}, 이미지 개수: {}", problemId, problemImageCount);
        }
    }

    @Transactional
    public void updateProblemInfo(ProblemRegisterDto problemRegisterDto, Long userId) {

        Problem problem = findProblemEntity(problemRegisterDto.problemId(), userId);

        problem.updateProblem(problemRegisterDto);

        log.info("userId: {} update problemId: {}", userId, problem.getId());
    }

    @Transactional
    public void updateProblemFolder(ProblemRegisterDto problemRegisterDto, Long userId) {
        Problem problem = findProblemEntity(problemRegisterDto.problemId(), userId);

        if (problemRegisterDto.folderId() != null) {
            Folder folder = folderRepository.findById(problemRegisterDto.folderId())
                    .orElseThrow(() -> new ApplicationException(FolderErrorCase.FOLDER_NOT_FOUND));

            problem.updateFolder(folder);

            log.info("userId: {} update problem folder, problemId: {}, folderId: {}", userId, problem.getId(), folder.getId());
        }
    }

    /**
     * 문제 삭제 (비동기 S3 파일 삭제 적용)
     * - DB 삭제: 동기 (즉시 완료)
     * - S3 파일 삭제: 비동기 (RabbitMQ Producer로 전송)
     * - PracticeNote 매핑 삭제: 동기 (데이터 정합성)
     */
    @Transactional
    public void deleteProblem(Long problemId) {
        // 1. 이미지 데이터 조회
        List<ProblemImageData> imageDataList = problemImageDataRepository.findAllByProblemId(problemId);

        // 2. DB에서 이미지 메타데이터 삭제 (동기 - 빠름)
        problemImageDataRepository.deleteAll(imageDataList);

        // 3. PracticeNote 매핑 삭제 (동기 - 데이터 정합성 보장)
        practiceNoteRepository.deleteProblemFromAllPractice(problemId);

        // 4. 문제 삭제 (Soft Delete)
        problemRepository.deleteById(problemId);

        log.info("problemId: {} DB 삭제 완료", problemId);

        // 5. S3 파일 삭제는 비동기로 처리 (RabbitMQ Producer)
        imageDataList.forEach(imageData -> {
            try {
                s3DeleteProducer.sendDeleteMessage(imageData.getImageUrl(), problemId);
            } catch (Exception e) {
                log.error("S3 삭제 메시지 전송 실패 - problemId: {}, imageUrl: {}, error: {}",
                        problemId, imageData.getImageUrl(), e.getMessage());
            }
        });

        log.info("problemId: {} S3 삭제 메시지 전송 완료 ({}개)", problemId, imageDataList.size());
    }

    @Transactional
    public void deleteProblemImageData(String imageUrl) {
        fileUploadService.deleteImageFileFromS3(imageUrl);
        problemImageDataRepository.deleteByImageUrl(imageUrl);
    }

    @Transactional
    public void deleteProblemList(List<Long> problemIdList) {
        problemIdList.forEach(this::deleteProblem);
    }

    @Transactional
    public void deleteFolderProblems(Long folderId) {
        problemRepository.findAllByFolderId(folderId)
                .forEach(problem -> {
                    deleteProblem(problem.getId());
                });

        log.info("problem in folderId: {} has deleted", folderId);
    }

    @Transactional
    public void deleteAllByFolderIds(Collection<Long> folderIds) {
        folderIds.forEach(this::deleteFolderProblems);
    }

    @Transactional
    public void deleteAllUserProblems(Long userId) {
        problemRepository.findAllByUserId(userId)
                .forEach(problem -> {
                    deleteProblem(problem.getId());
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
    public CursorPageResponse<ProblemResponseDto> findProblemsByFolderWithCursor(Long folderId, Long cursor, int size) {
        List<Problem> problems = problemRepository.findProblemsByFolderWithCursor(folderId, cursor, size);

        boolean hasNext = problems.size() > size;
        List<Problem> content = hasNext ? problems.subList(0, size) : problems;
        Long nextCursor = hasNext ? content.get(content.size() - 1).getId() : null;

        List<ProblemResponseDto> dtoList = content.stream()
                .map(ProblemResponseDto::from)
                .collect(Collectors.toList());

        log.info("folderId: {} find problems with cursor: {}, size: {}, hasNext: {}", folderId, cursor, size, hasNext);
        return CursorPageResponse.of(dtoList, nextCursor, hasNext, size);
    }
}