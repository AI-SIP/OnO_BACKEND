package com.aisip.OnO.backend.service;

import com.aisip.OnO.backend.Dto.Problem.ProblemPractice.ProblemPracticeRegisterDto;
import com.aisip.OnO.backend.Dto.Problem.ProblemPractice.ProblemPracticeResponseDto;
import com.aisip.OnO.backend.Dto.Problem.ProblemResponseDto;
import com.aisip.OnO.backend.converter.PracticeConverter;
import com.aisip.OnO.backend.entity.Problem.Practice;
import com.aisip.OnO.backend.entity.Problem.Problem;
import com.aisip.OnO.backend.entity.Problem.ProblemPracticeMapping;
import com.aisip.OnO.backend.entity.User.User;
import com.aisip.OnO.backend.exception.PracticeNotFoundException;
import com.aisip.OnO.backend.repository.Practice.PracticeRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class PracticeService {

    private final UserService userService;

    private final ProblemService problemService;

    private final PracticeRepository practiceRepository;

    private Practice getPracticeEntity(Long practiceId){

        return practiceRepository.findById(practiceId)
                .orElseThrow(() -> new PracticeNotFoundException(practiceId));
    }

    public ProblemPracticeResponseDto createPractice(Long userId, ProblemPracticeRegisterDto problemPracticeRegisterDto) {

        User user = userService.getUserEntity(userId);
        Practice practice = Practice.builder()
                .practiceCount(0L)
                .user(user)
                .title(problemPracticeRegisterDto.getPracticeTitle())
                .build();

        Practice savedPractice = practiceRepository.save(practice);

        if (problemPracticeRegisterDto.getRegisterProblemIds() != null) {
            problemPracticeRegisterDto.getRegisterProblemIds().forEach(problemId ->
                    addProblemToPractice(savedPractice, problemId));
        }

        return findPractice(savedPractice.getId());
    }

    public void addProblemToPractice(Practice practice, Long problemId) {
        Problem problem = problemService.getProblemEntity(problemId);

        boolean alreadyExists = practice.getProblemPracticeMappings().stream()
                .anyMatch(mapping -> mapping.getProblem().getId().equals(problemId));

        if (!alreadyExists) {
            ProblemPracticeMapping mapping = ProblemPracticeMapping.builder()
                    .practice(practice)
                    .problem(problem)
                    .build();

            practice.getProblemPracticeMappings().add(mapping);
        }

        practiceRepository.save(practice);
    }

    public ProblemPracticeResponseDto findPractice(Long practiceId) {
        return getPracticeResponseDto(getPracticeEntity(practiceId));
    }

    public List<ProblemPracticeResponseDto> findAllPracticesByUser(Long userId){
        List<Practice> practiceList = practiceRepository.findAllByUserId(userId);

        return practiceList.stream().map(
                this::getPracticeResponseDto
        ).collect(Collectors.toList());
    }

    public boolean addPracticeCount(Long practiceId) {
        Practice practice = practiceRepository.findById(practiceId)
                .orElseThrow(() -> new PracticeNotFoundException(practiceId));

        practice.setPracticeCount(practice.getPracticeCount() + 1);
        practice.setLastSolvedAt(LocalDateTime.now());
        practiceRepository.save(practice);

        return true;
    }

    public boolean updatePractice(ProblemPracticeRegisterDto problemPracticeRegisterDto) {
        Long practiceId = problemPracticeRegisterDto.getPracticeId();
        Practice practice = getPracticeEntity(practiceId);

        if (problemPracticeRegisterDto.getPracticeTitle() != null) {
            practice.setTitle(problemPracticeRegisterDto.getPracticeTitle());
        }

        // ✅ 기존 문제 리스트 가져오기 (매핑을 통해 문제 가져오기)
        List<Long> existingProblemIds = practice.getProblemPracticeMappings().stream()
                .map(mapping -> mapping.getProblem().getId())
                .toList();

        List<Long> newProblemIds = problemPracticeRegisterDto.getRegisterProblemIds();

        // ✅ 추가해야 할 문제 찾기
        List<Long> problemsToAdd = newProblemIds.stream()
                .filter(problemId -> !existingProblemIds.contains(problemId))
                .toList();

        // ✅ 삭제해야 할 문제 찾기
        List<Long> problemsToRemove = existingProblemIds.stream()
                .filter(problemId -> !newProblemIds.contains(problemId))
                .toList();

        // ✅ 문제 추가
        problemsToAdd.forEach(problemId -> addProblemToPractice(practice, problemId));

        // ✅ 문제 삭제
        problemsToRemove.forEach(problemId -> removeProblemFromPractice(practiceId, problemId));

        practiceRepository.save(practice);
        return true;
    }

    public void deletePractice(Long practiceId) {
        Practice practice = getPracticeEntity(practiceId);
        practice.getProblemPracticeMappings().clear();  // 매핑 삭제
        practiceRepository.delete(practice);
    }

    public void deletePractices(List<Long> practiceIds) {
        practiceIds.forEach(this::deletePractice);
    }

    public void deleteAllPracticesByUser(Long userId) {
        List<Practice> practiceList = practiceRepository.findAllByUserId(userId);

        practiceRepository.deleteAll(practiceList);
    }

    public void removeProblemFromPractice(Long practiceId, Long problemId) {
        Practice practice = getPracticeEntity(practiceId);
        Problem problem = problemService.getProblemEntity(problemId);

        // 연관된 매핑 엔티티 찾기
        practice.getProblemPracticeMappings().removeIf(mapping ->
                mapping.getProblem().getId().equals(problemId)
        );

        practiceRepository.save(practice);
    }

    public void deleteProblemsFromAllPractice(List<Long> deleteProblemIdList) {
        deleteProblemIdList.forEach(deleteProblemId -> {
            Problem problemToRemove = problemService.getProblemEntity(deleteProblemId);

            // ✅ 해당 문제를 포함하고 있는 모든 Practice 가져오기
            List<Practice> practicesContainingProblem = practiceRepository.findAllByProblemsContaining(problemToRemove);

            for (Practice practice : practicesContainingProblem) {
                // ✅ 기존 코드: practice.getProblems().remove(problemToRemove);
                practice.getProblemPracticeMappings().removeIf(mapping -> mapping.getProblem().equals(problemToRemove));
                practiceRepository.save(practice);
            }
        });
    }

    private ProblemPracticeResponseDto getPracticeResponseDto(Practice practice) {
        ProblemPracticeResponseDto problemPracticeResponseDto = PracticeConverter.convertToResponseDto(practice);
        problemPracticeResponseDto.setPracticeSize((long) practiceRepository.countProblemsByPracticeId(practice.getId()));

        // ✅ 문제 리스트 가져오기 (매핑을 통해 문제 추출)
        List<Problem> problemList = practice.getProblemPracticeMappings().stream()
                .map(ProblemPracticeMapping::getProblem)
                .toList();

        List<ProblemResponseDto> problemResponseDtoList = problemList.stream()
                .map(problemService::convertToProblemResponse)
                .toList();

        problemPracticeResponseDto.setProblems(problemResponseDtoList);
        return problemPracticeResponseDto;
    }
}
