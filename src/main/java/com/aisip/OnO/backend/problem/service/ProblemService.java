package com.aisip.OnO.backend.problem.service;

import com.aisip.OnO.backend.common.exception.ApplicationException;
import com.aisip.OnO.backend.problem.dto.ProblemImageDataRegisterDto;
import com.aisip.OnO.backend.problem.dto.ProblemRegisterDto;
import com.aisip.OnO.backend.problem.entity.Folder;
import com.aisip.OnO.backend.problem.entity.ProblemImageData;
import com.aisip.OnO.backend.problem.exception.ProblemErrorCase;
import com.aisip.OnO.backend.problem.repository.problem.ProblemImageDataRepository;
import com.aisip.OnO.backend.problem.dto.ProblemResponseDto;
import com.aisip.OnO.backend.problem.entity.Problem;
import com.aisip.OnO.backend.problem.repository.problem.ProblemRepository;
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

    public void registerProblem(ProblemRegisterDto problemRegisterDto, Folder folder, Long userId) {

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

    public void updateProblemFolder(Long problemId, Folder folder, Long userId) {
        Problem problem = findProblemEntity(problemId, userId);

        problem.updateFolder(folder);
    }

    public void deleteProblem(Long problemId) {
        problemImageDataRepository.deleteAllByProblemId(problemId);
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