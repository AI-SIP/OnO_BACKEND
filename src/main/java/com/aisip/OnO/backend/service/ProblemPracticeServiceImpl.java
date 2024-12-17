package com.aisip.OnO.backend.service;

import com.aisip.OnO.backend.Dto.Problem.ProblemPractice.ProblemPracticeRegisterDto;
import com.aisip.OnO.backend.Dto.Problem.ProblemPractice.ProblemPracticeResponseDto;
import com.aisip.OnO.backend.Dto.Problem.ProblemResponseDto;
import com.aisip.OnO.backend.converter.ProblemPracticeConverter;
import com.aisip.OnO.backend.entity.Problem.Problem;
import com.aisip.OnO.backend.entity.Problem.ProblemPractice;
import com.aisip.OnO.backend.entity.User.User;
import com.aisip.OnO.backend.exception.ProblemPracticeNotFoundException;
import com.aisip.OnO.backend.repository.ProblemPracticeRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class ProblemPracticeServiceImpl implements ProblemPracticeService{

    private final UserService userService;

    private final ProblemService problemService;

    private final ProblemPracticeRepository problemPracticeRepository;

    @Override
    public ProblemPractice getPracticeEntity(Long practiceId){
        Optional<ProblemPractice> optionalProblemPractice = problemPracticeRepository.findById(practiceId);

        if(optionalProblemPractice.isPresent()) {
            return optionalProblemPractice.get();
        }

        throw new ProblemPracticeNotFoundException("복습 리스트를 찾을 수 없습니다!");
    }

    @Override
    public boolean createPractice(Long userId, ProblemPracticeRegisterDto problemPracticeRegisterDto) {

        User user = userService.getUserEntity(userId);
        ProblemPractice practice = ProblemPractice.builder()
                .practiceCount(0L)
                .user(user)
                .build();

        ProblemPractice resultPractice = problemPracticeRepository.save(practice);
        log.info(resultPractice.getId().toString());

        if(problemPracticeRegisterDto.getPracticeTitle() != null){
            resultPractice.setTitle(problemPracticeRegisterDto.getPracticeTitle());
        }

        if(problemPracticeRegisterDto.getRegisterProblemIds() != null && !problemPracticeRegisterDto.getRegisterProblemIds().isEmpty()){
            List<Long> problemIds = problemPracticeRegisterDto.getRegisterProblemIds();

            List<Problem> problems = problemIds.stream()
                    .map(problemService::getProblemEntity).collect(Collectors.toList());

            resultPractice.setProblems(problems);
        }

        return true;
    }

    @Override
    public void addProblemToPractice(Long practiceId, Long problemId) {
        ProblemPractice practice = problemPracticeRepository.findById(practiceId)
                .orElseThrow(() -> new ProblemPracticeNotFoundException("Invalid practice practiceId: " + practiceId));

        Problem problem = problemService.getProblemEntity(problemId);

        Set<Long> existingProblemIds = practice.getProblems().stream()
                .map(Problem::getId)
                .collect(Collectors.toSet());

        if(!existingProblemIds.contains(problem.getId())){
            practice.getProblems().add(problem);
        }

        problemPracticeRepository.save(practice);
    }

    public ProblemPracticeResponseDto findPractice(Long practiceId) {
        return getPracticeResponseDto(getPracticeEntity(practiceId));
    }

    @Override
    public List<ProblemPracticeResponseDto> findAllPracticesByUser(Long userId){
        List<ProblemPractice> problemPracticeList = problemPracticeRepository.findAllByUserId(userId);

        return problemPracticeList.stream().map(
                this::getPracticeResponseDto
        ).collect(Collectors.toList());
    }

    @Override
    public boolean addPracticeCount(Long practiceId) {
        ProblemPractice practice = problemPracticeRepository.findById(practiceId)
                .orElseThrow(() -> new ProblemPracticeNotFoundException("Invalid practice practiceId: " + practiceId));

        practice.setPracticeCount(practice.getPracticeCount() + 1);
        practice.setLastSolvedAt(LocalDateTime.now());
        problemPracticeRepository.save(practice);

        return true;
    }

    @Override
    public boolean updatePractice(ProblemPracticeRegisterDto problemPracticeRegisterDto){
        Long practiceId = problemPracticeRegisterDto.getPracticeId();
        ProblemPractice practice = getPracticeEntity(practiceId);

        // 제목 업데이트
        if (problemPracticeRegisterDto.getPracticeTitle() != null) {
            practice.setTitle(problemPracticeRegisterDto.getPracticeTitle());
        }

        // 문제 ID 목록 업데이트
        List<Long> newProblemIds = problemPracticeRegisterDto.getRegisterProblemIds();
        List<Long> existingProblemIds = practice.getProblems().stream()
                .map(Problem::getId).toList();

        // 새로 추가할 문제: newProblemIds에만 포함된 문제 ID
        List<Long> problemsToAdd = newProblemIds.stream()
                .filter(problemId -> !existingProblemIds.contains(problemId)).toList();

        // 삭제할 문제: 기존 문제 목록에만 포함된 문제 ID
        List<Long> problemsToRemove = existingProblemIds.stream()
                .filter(problemId -> !newProblemIds.contains(problemId)).toList();

        // 문제 추가
        problemsToAdd.forEach(problemId -> addProblemToPractice(practiceId, problemId));

        // 문제 삭제
        problemsToRemove.forEach(problemId -> removeProblemFromPractice(practiceId, problemId));

        // 연습 문제 저장
        problemPracticeRepository.save(practice);
        return true;
    }

    @Override
    public void deletePractice(Long practiceId) {
        ProblemPractice practice = getPracticeEntity(practiceId);
        // 연관 관계 제거
        practice.getProblems().clear();
        problemPracticeRepository.save(practice);

        // 이제 ProblemPractice 엔티티 삭제
        problemPracticeRepository.deleteById(practiceId);
    }

    @Override
    public void deletePractices(List<Long> practiceIds) {
        practiceIds.forEach(this::deletePractice);
    }

    @Override
    public void deleteAllPracticesByUser(Long userId) {
        List<ProblemPractice> practiceList = problemPracticeRepository.findAllByUserId(userId);

        problemPracticeRepository.deleteAll(practiceList);
    }

    @Override
    public void removeProblemFromPractice(Long practiceId, Long problemId) {
        ProblemPractice practice = problemPracticeRepository.findById(practiceId)
                .orElseThrow(() -> new IllegalArgumentException("Invalid practice ID"));

        Problem problem = problemService.getProblemEntity(problemId);

        Set<Long> existingProblemIds = practice.getProblems().stream()
                .map(Problem::getId)
                .collect(Collectors.toSet());

        if(existingProblemIds.contains(problem.getId())){
            practice.getProblems().remove(problem);
        }

        problemPracticeRepository.save(practice);
    }

    @Override
    public void deleteProblemFromAllPractice(Long problemId) {
        Problem problemToRemove = problemService.getProblemEntity(problemId);

        // 해당 문제를 포함하고 있는 모든 ProblemPractice 가져오기
        List<ProblemPractice> practicesContainingProblem = problemPracticeRepository.findAllByProblemsContaining(problemToRemove);

        for (ProblemPractice practice : practicesContainingProblem) {
            practice.getProblems().remove(problemToRemove);

            problemPracticeRepository.save(practice);
        }
    }

    private ProblemPracticeResponseDto getPracticeResponseDto(ProblemPractice problemPractice){
        ProblemPracticeResponseDto problemPracticeResponseDto = ProblemPracticeConverter.convertToResponseDto(problemPractice);
        problemPracticeResponseDto.setPracticeSize((long) problemPracticeRepository.countProblemsByPracticeId(problemPractice.getId()));

        List<Problem> problemList = problemPractice.getProblems();
        List<ProblemResponseDto> problemResponseDtoList = problemList.stream().map(
                problemService::convertToProblemResponse
        ).toList();

        problemPracticeResponseDto.setProblems(problemResponseDtoList);

        return problemPracticeResponseDto;
    }
}
