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

    @Transactional(readOnly = true)
    public ProblemResponseDto findProblem(Long problemId, Long userId) {
        Problem problem = problemRepository.findProblemWithImageData(problemId)
                .orElseThrow(() -> new ApplicationException(ProblemErrorCase.PROBLEM_NOT_FOUND));

        log.info("userId: {} find problemId: {}", userId, problemId);
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

            log.info("Uploaded image to S3: {} for problemId: {}", imageUrl, problemId);
        }
    }

    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void analysisProblem(Long problemId) {
        analysisService.analyzeProblemAsync(problemId);
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
}