package com.aisip.OnO.backend.practicenote.service;

import com.aisip.OnO.backend.practicenote.entity.PracticeNote;
import com.aisip.OnO.backend.practicenote.dto.PracticeNoteRegisterDto;
import com.aisip.OnO.backend.practicenote.dto.PracticeNoteResponseDto;
import com.aisip.OnO.backend.problem.dto.ProblemResponseDto;
import com.aisip.OnO.backend.practicenote.converter.PracticeConverter;
import com.aisip.OnO.backend.problem.entity.Problem;
import com.aisip.OnO.backend.practicenote.entity.ProblemPracticeNoteMapping;
import com.aisip.OnO.backend.problem.service.ProblemService;
import com.aisip.OnO.backend.user.entity.User;
import com.aisip.OnO.backend.practicenote.exception.PracticeNotFoundException;
import com.aisip.OnO.backend.practicenote.repository.PracticeNoteRepository;
import com.aisip.OnO.backend.user.service.UserService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class PracticeNoteService {

    private final UserService userService;

    private final ProblemService problemService;

    private final PracticeNoteRepository practiceNoteRepository;

    private PracticeNote getPracticeEntity(Long practiceId){

        return practiceNoteRepository.findById(practiceId)
                .orElseThrow(() -> new PracticeNotFoundException(practiceId));
    }

    public PracticeNoteResponseDto createPractice(Long userId, PracticeNoteRegisterDto practiceNoteRegisterDto) {

        User user = userService.findUserEntity(userId);
        PracticeNote practiceNote = PracticeNote.builder()
                .practiceCount(0L)
                .user(user)
                .title(practiceNoteRegisterDto.getPracticeTitle())
                .build();

        PracticeNote savedPracticeNote = practiceNoteRepository.save(practiceNote);

        if (practiceNoteRegisterDto.getRegisterProblemIds() != null) {
            practiceNoteRegisterDto.getRegisterProblemIds().forEach(problemId ->
                    addProblemToPractice(savedPracticeNote, problemId));
        }

        return findPractice(savedPracticeNote.getId());
    }

    public void addProblemToPractice(PracticeNote practiceNote, Long problemId) {
        Problem problem = problemService.getProblemEntity(problemId);

        boolean alreadyExists = practiceNote.getProblemPracticeNoteMappings().stream()
                .anyMatch(mapping -> mapping.getProblem().getId().equals(problemId));

        if (!alreadyExists) {
            ProblemPracticeNoteMapping mapping = ProblemPracticeNoteMapping.builder()
                    .practiceNote(practiceNote)
                    .problem(problem)
                    .build();

            practiceNote.getProblemPracticeNoteMappings().add(mapping);
        }

        practiceNoteRepository.save(practiceNote);
    }

    public PracticeNoteResponseDto findPractice(Long practiceId) {
        return getPracticeResponseDto(getPracticeEntity(practiceId));
    }

    public List<PracticeNoteResponseDto> findAllPracticesByUser(Long userId){
        List<PracticeNote> practiceNoteList = practiceNoteRepository.findAllByUserId(userId);

        return practiceNoteList.stream().map(
                this::getPracticeResponseDto
        ).collect(Collectors.toList());
    }

    public boolean addPracticeCount(Long practiceId) {
        PracticeNote practiceNote = practiceNoteRepository.findById(practiceId)
                .orElseThrow(() -> new PracticeNotFoundException(practiceId));

        practiceNote.setPracticeCount(practiceNote.getPracticeCount() + 1);
        practiceNote.setLastSolvedAt(LocalDateTime.now());
        practiceNoteRepository.save(practiceNote);

        return true;
    }

    public boolean updatePractice(PracticeNoteRegisterDto practiceNoteRegisterDto) {
        Long practiceId = practiceNoteRegisterDto.getPracticeId();
        PracticeNote practiceNote = getPracticeEntity(practiceId);

        if (practiceNoteRegisterDto.getPracticeTitle() != null) {
            practiceNote.setTitle(practiceNoteRegisterDto.getPracticeTitle());
        }

        // ✅ 기존 문제 리스트 가져오기 (매핑을 통해 문제 가져오기)
        List<Long> existingProblemIds = practiceNote.getProblemPracticeNoteMappings().stream()
                .map(mapping -> mapping.getProblem().getId())
                .toList();

        List<Long> newProblemIds = practiceNoteRegisterDto.getRegisterProblemIds();

        // ✅ 추가해야 할 문제 찾기
        List<Long> problemsToAdd = newProblemIds.stream()
                .filter(problemId -> !existingProblemIds.contains(problemId))
                .toList();

        // ✅ 삭제해야 할 문제 찾기
        List<Long> problemsToRemove = existingProblemIds.stream()
                .filter(problemId -> !newProblemIds.contains(problemId))
                .toList();

        // ✅ 문제 추가
        problemsToAdd.forEach(problemId -> addProblemToPractice(practiceNote, problemId));

        // ✅ 문제 삭제
        problemsToRemove.forEach(problemId -> removeProblemFromPractice(practiceId, problemId));

        practiceNoteRepository.save(practiceNote);
        return true;
    }

    public void deletePractice(Long practiceId) {
        PracticeNote practiceNote = getPracticeEntity(practiceId);
        practiceNote.getProblemPracticeNoteMappings().clear();  // 매핑 삭제
        practiceNoteRepository.delete(practiceNote);
    }

    public void deletePractices(List<Long> practiceIds) {
        practiceIds.forEach(this::deletePractice);
    }

    public void deleteAllPracticesByUser(Long userId) {
        List<PracticeNote> practiceNoteList = practiceNoteRepository.findAllByUserId(userId);

        practiceNoteRepository.deleteAll(practiceNoteList);
    }

    public void removeProblemFromPractice(Long practiceId, Long problemId) {
        PracticeNote practiceNote = getPracticeEntity(practiceId);
        Problem problem = problemService.getProblemEntity(problemId);

        // 연관된 매핑 엔티티 찾기
        practiceNote.getProblemPracticeNoteMappings().removeIf(mapping ->
                mapping.getProblem().getId().equals(problemId)
        );

        practiceNoteRepository.save(practiceNote);
    }

    public void deleteProblemsFromAllPractice(List<Long> deleteProblemIdList) {
        deleteProblemIdList.forEach(deleteProblemId -> {
            Problem problemToRemove = problemService.getProblemEntity(deleteProblemId);

            // ✅ 해당 문제를 포함하고 있는 모든 Practice 가져오기
            List<PracticeNote> practicesContainingProblem = practiceNoteRepository.findAllByProblemsContaining(problemToRemove);

            for (PracticeNote practiceNote : practicesContainingProblem) {
                // ✅ 기존 코드: practice.getProblems().remove(problemToRemove);
                practiceNote.getProblemPracticeNoteMappings().removeIf(mapping -> mapping.getProblem().equals(problemToRemove));
                practiceNoteRepository.save(practiceNote);
            }
        });
    }

    private PracticeNoteResponseDto getPracticeResponseDto(PracticeNote practiceNote) {
        PracticeNoteResponseDto practiceNoteResponseDto = PracticeConverter.convertToResponseDto(practiceNote);
        practiceNoteResponseDto.setPracticeSize((long) practiceNoteRepository.countProblemsByPracticeId(practiceNote.getId()));

        // ✅ 문제 리스트 가져오기 (매핑을 통해 문제 추출)
        List<Problem> problemList = practiceNote.getProblemPracticeNoteMappings().stream()
                .map(ProblemPracticeNoteMapping::getProblem)
                .toList();

        List<ProblemResponseDto> problemResponseDtoList = problemList.stream()
                .map(problemService::convertToProblemResponse)
                .toList();

        practiceNoteResponseDto.setProblems(problemResponseDtoList);
        return practiceNoteResponseDto;
    }
}
