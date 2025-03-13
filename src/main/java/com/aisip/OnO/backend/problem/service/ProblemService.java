package com.aisip.OnO.backend.problem.service;

import com.aisip.OnO.backend.common.exception.ApplicationException;
import com.aisip.OnO.backend.fileupload.service.FileUploadService;
import com.aisip.OnO.backend.problem.dto.ProblemDeleteRequestDto;
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
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class ProblemService {
    private final ProblemRepository problemRepository;

    private final ProblemImageDataRepository problemImageDataRepository;

    private final FolderRepository folderRepository;

    private final FileUploadService fileUploadService;

    public ProblemResponseDto findProblem(Long problemId, Long userId) {
        Problem problem = findProblemEntity(problemId, userId);

        return ProblemResponseDto.from(problem);
    }

    public Problem findProblemEntity(Long problemId, Long userId) {
        Problem problem = problemRepository.findById(problemId)
                .orElseThrow(() -> new ApplicationException(ProblemErrorCase.PROBLEM_NOT_FOUND));

        if (!Objects.equals(problem.getUserId(), userId)) {
            throw new ApplicationException(ProblemErrorCase.PROBLEM_USER_UNMATCHED);
        }

        return problem;
    }

    public List<ProblemResponseDto> findUserProblems(Long userId) {
        return problemRepository.findAllByUserId(userId)
                .stream()
                .map(ProblemResponseDto::from)
                .collect(Collectors.toList());
    }

    public List<ProblemResponseDto> findFolderProblemList(Long folderId) {
        return problemRepository.findAllByFolderId(folderId)
                .stream()
                .map(ProblemResponseDto::from)
                .collect(Collectors.toList());
    }

    public List<ProblemResponseDto> findAllProblems() {
        return problemRepository.findAll()
                .stream()
                .map(ProblemResponseDto::from)
                .collect(Collectors.toList());
    }

    public Long findProblemCountByUser(Long userId) {
        return problemRepository.countByUserId(userId);
    }

    public void registerProblem(ProblemRegisterDto problemRegisterDto, Long userId) {

        Folder folder = folderRepository.findById(problemRegisterDto.folderId())
                .orElseThrow(() -> new ApplicationException(FolderErrorCase.FOLDER_NOT_FOUND));

        Problem problem = Problem.from(problemRegisterDto, userId, folder);
        problemRepository.save(problem);

        problemRegisterDto.imageDataList()
                .forEach(problemImageDataRegisterDto -> {
                    ProblemImageData problemImageData = ProblemImageData.from(problemImageDataRegisterDto, problem);
                    problemImageDataRepository.save(problemImageData);
                });
    }

    public void registerProblemImageData(ProblemImageDataRegisterDto problemImageDataRegisterDto, Long userId) {
        Problem problem = findProblemEntity(problemImageDataRegisterDto.problemId(), userId);

        ProblemImageData problemImageData = ProblemImageData.from(problemImageDataRegisterDto, problem);
        problemImageDataRepository.save(problemImageData);
    }

    public void updateProblemInfo(ProblemRegisterDto problemRegisterDto, Long userId) {

        Problem problem = findProblemEntity(problemRegisterDto.problemId(), userId);

        problem.updateProblem(problemRegisterDto);
    }

    public void updateProblemFolder(ProblemRegisterDto problemRegisterDto, Long userId) {
        Problem problem = findProblemEntity(problemRegisterDto.problemId(), userId);

        if (problemRegisterDto.folderId() != null) {
            Folder folder = folderRepository.findById(problemRegisterDto.folderId())
                    .orElseThrow(() -> new ApplicationException(FolderErrorCase.FOLDER_NOT_FOUND));

            problem.updateFolder(folder);
        }

    }

    @Transactional
    public void deleteProblems(ProblemDeleteRequestDto deleteRequestDto) {
        Long userId = deleteRequestDto.userId();
        List<Long> problemIdList = deleteRequestDto.problemIdList();
        List<Long> folderIdList = deleteRequestDto.folderIdList();

        if (userId != null) {
            // 유저 ID가 있으면 해당 유저의 모든 문제 삭제
            deleteAllUserProblems(userId);
        }

        if (problemIdList != null && !problemIdList.isEmpty()) {
            // 특정 problemId 리스트에 해당하는 문제 삭제
            deleteProblemList(problemIdList);
        }

        if (folderIdList != null && !folderIdList.isEmpty()) {
            // 특정 folderId 리스트에 포함된 모든 문제 삭제
            deleteAllByFolderIds(folderIdList);
        }
    }

    public void deleteProblem(Long problemId) {

        List<ProblemImageData> imageDataList= problemImageDataRepository.findAllByProblemId(problemId);

        imageDataList.forEach(imageData -> {
            fileUploadService.deleteImageFileFromS3(imageData.getImageUrl());
            problemImageDataRepository.delete(imageData);
        });

        problemRepository.deleteById(problemId);
    }

    public void deleteProblemImageData(String imageUrl) {
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
    }

    public void deleteAllByFolderIds(Collection<Long> folderIds) {
        folderIds.forEach(this::deleteFolderProblems);
    }

    public void deleteAllUserProblems(Long userId) {
        problemRepository.findAllByUserId(userId)
                .forEach(problem -> {
                    deleteProblem(problem.getId());
                });
    }
}