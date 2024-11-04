package com.aisip.OnO.backend.service;

import com.aisip.OnO.backend.Dto.Problem.ProblemPractice.ProblemPracticeRegisterDto;
import com.aisip.OnO.backend.Dto.Problem.ProblemPractice.ProblemPracticeResponseDto;
import com.aisip.OnO.backend.converter.ProblemPracticeConverter;
import com.aisip.OnO.backend.entity.Problem.Problem;
import com.aisip.OnO.backend.entity.Problem.ProblemPractice;
import com.aisip.OnO.backend.entity.User.User;
import com.aisip.OnO.backend.exception.ProblemNotFoundException;
import com.aisip.OnO.backend.exception.ProblemPracticeNotFoundException;
import com.aisip.OnO.backend.exception.UserNotFoundException;
import com.aisip.OnO.backend.repository.ProblemPracticeRepository;
import com.aisip.OnO.backend.repository.ProblemRepository;
import com.aisip.OnO.backend.repository.UserRepository;
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

    private final UserRepository userRepository;

    private final ProblemRepository problemRepository;

    private final ProblemPracticeRepository problemPracticeRepository;

    @Override
    public boolean createPractice(Long userId, ProblemPracticeRegisterDto problemPracticeRegisterDto) {

        Optional<User> optionalUserEntity = userRepository.findById(userId);
        if(optionalUserEntity.isPresent()){
            ProblemPractice practice = ProblemPractice.builder()
                    .practiceCount(0L)
                    .user(optionalUserEntity.get())
                    .build();

            ProblemPractice resultPractice = problemPracticeRepository.save(practice);
            log.info(resultPractice.getId().toString());

            if(problemPracticeRegisterDto.getPracticeTitle() != null){
                resultPractice.setTitle(problemPracticeRegisterDto.getPracticeTitle());
            }

            if(problemPracticeRegisterDto.getRegisterProblemIds() != null && !problemPracticeRegisterDto.getRegisterProblemIds().isEmpty()){
                List<Long> problemIds = problemPracticeRegisterDto.getRegisterProblemIds();

                List<Problem> problems = problemIds.stream()
                        .map(problemRepository::findById)  // Optional<Problem> 반환
                        .filter(optionalProblem -> optionalProblem.isPresent() &&
                                optionalProblem.get().getUser().equals(optionalUserEntity.get()))  // 조건을 만족하는 Optional<Problem>만 남김
                        .map(Optional::get)  // Optional<Problem>을 Problem으로 변환
                        .toList();

                resultPractice.setProblems(problems);
            }

            return true;
        } else{
            throw new UserNotFoundException("유저를 찾을 수 없습니다!");
        }
    }

    @Override
    public void addProblemToPractice(Long practiceId, Long problemId) {
        ProblemPractice practice = problemPracticeRepository.findById(practiceId)
                .orElseThrow(() -> new ProblemPracticeNotFoundException("Invalid practice practiceId: " + practiceId));

        Problem problem = problemRepository.findById(problemId)
                        .orElseThrow(() -> new ProblemNotFoundException("문제를 찾을 수 없습니다! problemId: " + problemId));

        Set<Long> existingProblemIds = practice.getProblems().stream()
                .map(Problem::getId)
                .collect(Collectors.toSet());

        if(!existingProblemIds.contains(problem.getId())){
            practice.getProblems().add(problem);
        }

        problemPracticeRepository.save(practice);
    }

    public ProblemPractice findPracticeEntity(Long practiceId) {

        return problemPracticeRepository.findById(practiceId)
                .orElseThrow(() -> new ProblemPracticeNotFoundException("Invalid practice practiceId: " + practiceId));
    }

    @Override
    public List<ProblemPracticeResponseDto> findAllPracticeThumbnailsByUser(Long userId){
        List<ProblemPractice> problemPracticeList = problemPracticeRepository.findAllByUserId(userId);

        return problemPracticeList.stream().map(
                problemPractice -> {
                    ProblemPracticeResponseDto problemPracticeResponseDto = ProblemPracticeConverter.convertToResponseDto(problemPractice);
                    problemPracticeResponseDto.setPracticeSize((long) problemPracticeRepository.countProblemsByPracticeId(problemPractice.getId()));

                    List<Problem> problems = problemPractice.getProblems();
                    List<Long> problemIds = (problems != null)
                            ? problems.stream().map(Problem::getId).toList()
                            : null;

                    problemPracticeResponseDto.setProblemIds(problemIds);

                    return problemPracticeResponseDto;
                }
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
    public boolean updatePractice(Long practiceId, ProblemPracticeRegisterDto problemPracticeRegisterDto) {
        ProblemPractice practice = problemPracticeRepository.findById(practiceId)
                .orElseThrow(() -> new ProblemPracticeNotFoundException("Invalid practice practiceId: " + practiceId));

        if(problemPracticeRegisterDto.getPracticeTitle() != null){
            practice.setTitle(problemPracticeRegisterDto.getPracticeTitle());
        }

        if(problemPracticeRegisterDto.getPracticeCount() != null && problemPracticeRegisterDto.getPracticeCount() > practice.getPracticeCount()){
            practice.setPracticeCount(problemPracticeRegisterDto.getPracticeCount());
        }

        if(problemPracticeRegisterDto.getRegisterProblemIds() != null){
            List<Long> registerProblemIds = problemPracticeRegisterDto.getRegisterProblemIds();

            registerProblemIds.forEach(problemId -> {
                addProblemToPractice(practiceId, problemId);
            });
        }

        if(problemPracticeRegisterDto.getRemoveProblemIds() != null){
            List<Long> removeProblemIds = problemPracticeRegisterDto.getRemoveProblemIds();

            removeProblemIds.forEach(problemId -> {
                removeProblemFromPractice(practiceId, problemId);
            });
        }

        problemPracticeRepository.save(practice);
        return true;
    }

    @Override
    public void deletePractice(Long practiceId) {
        ProblemPractice practice = problemPracticeRepository.findById(practiceId)
                .orElseThrow(() -> new IllegalArgumentException("Invalid practice ID: " + practiceId));

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
    public void removeProblemFromPractice(Long practiceId, Long problemId) {
        ProblemPractice practice = problemPracticeRepository.findById(practiceId)
                .orElseThrow(() -> new IllegalArgumentException("Invalid practice ID"));

        Problem problem = problemRepository.findById(problemId)
                .orElseThrow(() -> new ProblemNotFoundException("문제를 찾을 수 없습니다! problemId: " + problemId));

        Set<Long> existingProblemIds = practice.getProblems().stream()
                .map(Problem::getId)
                .collect(Collectors.toSet());

        if(!existingProblemIds.contains(problem.getId())){
            practice.getProblems().remove(problem);
        }

        problemPracticeRepository.save(practice);
    }

    @Override
    public void deleteProblemFromAllPractice(Long problemId) {
        Optional<Problem> optionalProblem = problemRepository.findById(problemId);

        if (optionalProblem.isPresent()) {
            Problem problemToRemove = optionalProblem.get();

            // 해당 문제를 포함하고 있는 모든 ProblemPractice 가져오기
            List<ProblemPractice> practicesContainingProblem = problemPracticeRepository.findAllByProblemsContaining(problemToRemove);

            for (ProblemPractice practice : practicesContainingProblem) {
                practice.getProblems().remove(problemToRemove);

                problemPracticeRepository.save(practice);
            }

            problemRepository.delete(problemToRemove);
        }
    }
}
