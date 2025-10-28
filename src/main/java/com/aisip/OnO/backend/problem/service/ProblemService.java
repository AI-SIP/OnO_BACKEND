package com.aisip.OnO.backend.problem.service;

import com.aisip.OnO.backend.common.exception.ApplicationException;
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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

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

    @Transactional
    public ProblemResponseDto findProblem(Long problemId, Long userId) {
        Problem problem = problemRepository.findProblemWithImageData(problemId)
                .orElseThrow(() -> new ApplicationException(ProblemErrorCase.PROBLEM_NOT_FOUND));

        log.info("userId: {} find problemId: {}", userId, problemId);
        return ProblemResponseDto.from(problem);
    }

    @Transactional
    public Problem findProblemEntity(Long problemId, Long userId) {
        Problem problem = problemRepository.findById(problemId)
                .orElseThrow(() -> new ApplicationException(ProblemErrorCase.PROBLEM_NOT_FOUND));

        if (!Objects.equals(problem.getUserId(), userId)) {
            throw new ApplicationException(ProblemErrorCase.PROBLEM_USER_UNMATCHED);
        }

        return problem;
    }

    @Transactional
    public Problem findProblemEntityWithImageData(Long problemId, Long userId) {
        Problem problem = problemRepository.findProblemWithImageData(problemId)
                .orElseThrow(() -> new ApplicationException(ProblemErrorCase.PROBLEM_NOT_FOUND));

        if (!Objects.equals(problem.getUserId(), userId)) {
            throw new ApplicationException(ProblemErrorCase.PROBLEM_USER_UNMATCHED);
        }

        return problem;
    }

    @Transactional
    public List<ProblemResponseDto> findUserProblems(Long userId) {
        log.info("userId: {} find all user problems", userId);

        return problemRepository.findAllByUserId(userId)
                .stream()
                .map(ProblemResponseDto::from)
                .collect(Collectors.toList());
    }

    @Transactional
    public List<ProblemResponseDto> findFolderProblemList(Long folderId) {
        return problemRepository.findAllByFolderId(folderId)
                .stream()
                .map(ProblemResponseDto::from)
                .collect(Collectors.toList());
    }

    @Transactional
    public List<ProblemResponseDto> findAllProblems() {
        return problemRepository.findAll()
                .stream()
                .map(ProblemResponseDto::from)
                .collect(Collectors.toList());
    }

    @Transactional
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

        missionLogService.registerProblemWriteMission(userId);

        log.info("userId: {} register problemId: {}", userId, problem.getId());

        return problem.getId();
    }

    @Transactional
    public Long registerProblemWithAnalysis(ProblemRegisterDto problemRegisterDto, Long userId) {
        // 1. 문제 등록
        Long problemId = registerProblem(problemRegisterDto, userId);

        // 2. 이미지가 있는지 확인
        boolean hasProblemImage = problemRegisterDto.imageDataDtoList() != null &&
                problemRegisterDto.imageDataDtoList().stream()
                        .anyMatch(imageDto -> imageDto.problemImageType() == ProblemImageType.PROBLEM_IMAGE);

        // 3. 빈 분석 객체 생성
        if (hasProblemImage) {
            // 이미지가 있으면 PROCESSING 상태로 생성
            analysisService.createPendingAnalysis(problemId);
        } else {
            // 이미지가 없으면 FAILED(스킵) 상태로 생성
            analysisService.createSkippedAnalysis(problemId);
        }

        return problemId;
    }

    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveProblemImages(ProblemRegisterDto problemRegisterDto, Long problemId) {
        Problem problem = problemRepository.findById(problemId)
                .orElseThrow(() -> new ApplicationException(ProblemErrorCase.PROBLEM_NOT_FOUND));

        // 이미지 저장 및 PROBLEM_IMAGE 타입 수집
        List<String> problemImageUrls = new ArrayList<>();
        for (var imageDto : problemRegisterDto.imageDataDtoList()) {
            ProblemImageData problemImageData = ProblemImageData.from(imageDto);
            problemImageData.updateProblem(problem);
            problemImageDataRepository.save(problemImageData);

            // PROBLEM_IMAGE 타입의 이미지 URL 수집 (여러 장 가능)
            if (imageDto.problemImageType() == ProblemImageType.PROBLEM_IMAGE) {
                problemImageUrls.add(imageDto.imageUrl());
            }
        }

        // AI 분석 처리 (분석 객체는 이미 registerProblemWithAnalysis에서 생성됨)
        if (!problemImageUrls.isEmpty()) {
            // PROBLEM_IMAGE가 있으면 비동기 분석 시작
            analysisService.analyzeProblemAsync(problem.getId(), problemImageUrls);
            log.info("Started AI analysis for problemId: {} with {} PROBLEM_IMAGE(s)", problem.getId(), problemImageUrls.size());
        } else {
            log.info("No PROBLEM_IMAGE for problemId: {}, analysis already set to SKIPPED", problem.getId());
        }
    }

    @Transactional
    public void registerProblemImageData(ProblemImageDataRegisterDto problemImageDataRegisterDto, Long userId) {
        Problem problem = findProblemEntityWithImageData(problemImageDataRegisterDto.problemId(), userId);

        if (problemImageDataRegisterDto.problemImageType().equals(ProblemImageType.SOLVE_IMAGE)) {
            boolean hasTodaySolveImage = problem.getProblemImageDataList().stream()
                    .anyMatch(imageData ->
                            imageData.getProblemImageType().equals(ProblemImageType.SOLVE_IMAGE) &&
                            imageData.getCreatedAt().toLocalDate().equals(java.time.LocalDate.now()));

            if (hasTodaySolveImage) {
                throw new ApplicationException(ProblemErrorCase.PROBLEM_SOLVE_IMAGE_ALREADY_REGISTERED);
            }

            // 문제 복습 미션 등록 (SOLVE_IMAGE 등록 시)
            missionLogService.registerProblemPracticeMission(userId, problem.getId());
        }

        ProblemImageData problemImageData = ProblemImageData.from(problemImageDataRegisterDto);
        problemImageData.updateProblem(problem);

        problemImageDataRepository.save(problemImageData);
        log.info("userId: {} register problem image data for problemId: {}", userId, problem.getId());
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

    @Transactional
    public void updateProblemImageData(ProblemRegisterDto problemRegisterDto, Long userId) {
        Problem problem = findProblemEntityWithImageData(problemRegisterDto.problemId(), userId);

        if (problemRegisterDto.imageDataDtoList() != null) {
            for(ProblemImageData problemImageData : problem.getProblemImageDataList()){
                deleteProblemImageData(problemImageData.getImageUrl());
            }
            problem.getProblemImageDataList().clear();

            // 이미지 업데이트 및 PROBLEM_IMAGE 수집
            List<String> problemImageUrls = new ArrayList<>();
            for (var imageDto : problemRegisterDto.imageDataDtoList()) {
                ProblemImageData problemImageData = ProblemImageData.from(imageDto);
                problemImageData.updateProblem(problem);
                problemImageDataRepository.save(problemImageData);

                // PROBLEM_IMAGE 타입의 이미지 URL 수집 (여러 장 가능)
                if (imageDto.problemImageType() == ProblemImageType.PROBLEM_IMAGE) {
                    problemImageUrls.add(imageDto.imageUrl());
                }
            }

            // 기존 분석 삭제 후 재분석
            analysisService.deleteAnalysis(problem.getId());

            if (!problemImageUrls.isEmpty()) {
                // PROBLEM_IMAGE가 있으면 먼저 PROCESSING 상태 생성 후 재분석
                analysisService.createPendingAnalysis(problem.getId());
                analysisService.analyzeProblemAsync(problem.getId(), problemImageUrls);
                log.info("Restarted AI analysis for problemId: {} with {} new PROBLEM_IMAGE(s)", problem.getId(), problemImageUrls.size());
            } else {
                // PROBLEM_IMAGE가 없으면 스킵 상태로 저장
                analysisService.createSkippedAnalysis(problem.getId());
                log.info("Skipped AI analysis for problemId: {} (no PROBLEM_IMAGE after update)", problem.getId());
            }
        }

        log.info("userId: {} update problem Image Data", userId);
    }

    public void deleteProblem(Long problemId) {

        List<ProblemImageData> imageDataList= problemImageDataRepository.findAllByProblemId(problemId);

        imageDataList.forEach(imageData -> {
            fileUploadService.deleteImageFileFromS3(imageData.getImageUrl());
            problemImageDataRepository.delete(imageData);
        });

        problemRepository.deleteById(problemId);

        log.info("problemId: {} has deleted", problemId);
    }

    @Transactional
    public void deleteProblemImageData(String imageUrl) {
        fileUploadService.deleteImageFileFromS3(imageUrl);
        problemImageDataRepository.deleteByImageUrl(imageUrl);
    }

    public void deleteProblemList(List<Long> problemIdList) {
        problemIdList.forEach(this::deleteProblem);
    }

    public void deleteFolderProblems(Long folderId) {
        problemRepository.findAllByFolderId(folderId)
                .forEach(problem -> {
                    deleteProblem(problem.getId());
                });

        log.info("problem in folderId: {} has deleted", folderId);
    }

    public void deleteAllByFolderIds(Collection<Long> folderIds) {
        folderIds.forEach(this::deleteFolderProblems);
    }

    public void deleteAllUserProblems(Long userId) {
        problemRepository.findAllByUserId(userId)
                .forEach(problem -> {
                    deleteProblem(problem.getId());
                });

        log.info("userId: {} delete all user problems", userId);
    }
}