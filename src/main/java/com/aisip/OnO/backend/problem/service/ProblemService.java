package com.aisip.OnO.backend.problem.service;

import com.aisip.OnO.backend.common.exception.ApplicationException;
import com.aisip.OnO.backend.problem.dto.ProblemImageDataRegisterDto;
import com.aisip.OnO.backend.problem.dto.ProblemRegisterDto;
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

    public ProblemResponseDto findProblem(Long userId, Long problemId) {
        Problem problem = getProblemEntity(userId, problemId);

        return ProblemResponseDto.from(problem);
    }

    private Problem getProblemEntity(Long userId, Long problemId) {
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

    public List<ProblemResponseDto> findFolderProblems(Long folderId) {
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

    public Long getProblemCountByUser(Long userId) {
        return problemRepository.countByUserId(userId);
    }

    public void registerProblem(ProblemRegisterDto problemRegisterDto, Long userId) {

        Problem problem = Problem.from(problemRegisterDto, userId);
        problemRepository.save(problem);

        problemRegisterDto.imageDataList()
                .forEach(problemImageDataRegisterDto -> {
                    registerProblemImageData(problemImageDataRegisterDto, problem);
                });
    }

    public void registerProblemImageData(ProblemImageDataRegisterDto problemImageDataRegisterDto, Problem problem) {
        ProblemImageData problemImageData = ProblemImageData.from(problemImageDataRegisterDto, problem);
        problemImageDataRepository.save(problemImageData);
    }

    public void updateProblemInfo(ProblemRegisterDto problemRegisterDto, Long userId) {

        Problem problem = getProblemEntity(problemRegisterDto.problemId(), userId);

        problem.updateProblem(problemRegisterDto);
    }

    public void deleteProblem(Long problemId, Long userId) {
        problemImageDataRepository.deleteAllByProblemId(problemId);
        problemRepository.deleteById(problemId);
    }

    public void deleteProblemImageData(String imageUrl) {
        problemImageDataRepository.deleteByImageUrl(imageUrl);
    }

    public void deleteProblemList(List<Long> problemIdList, Long userId) {
        problemIdList.forEach(problemId -> {
            deleteProblem(problemId, userId);
        });
    }

    public void deleteUserProblems(Long userId) {
        problemRepository.findAllByUserId(userId)
                .forEach(problem -> {
                    deleteProblem(problem.getId(), userId);
                });
    }
}