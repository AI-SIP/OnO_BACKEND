package com.aisip.OnO.backend.practicenote.service;

import com.aisip.OnO.backend.common.exception.ApplicationException;
import com.aisip.OnO.backend.practicenote.dto.PracticeNoteDetailResponseDto;
import com.aisip.OnO.backend.practicenote.dto.PracticeNoteThumbnailResponseDto;
import com.aisip.OnO.backend.practicenote.entity.PracticeNote;
import com.aisip.OnO.backend.practicenote.dto.PracticeNoteRegisterDto;
import com.aisip.OnO.backend.practicenote.exception.PracticeNoteErrorCase;
import com.aisip.OnO.backend.practicenote.repository.ProblemPracticeNoteMappingRepository;
import com.aisip.OnO.backend.problem.dto.ProblemResponseDto;
import com.aisip.OnO.backend.problem.entity.Problem;
import com.aisip.OnO.backend.practicenote.entity.ProblemPracticeNoteMapping;
import com.aisip.OnO.backend.problem.exception.ProblemErrorCase;
import com.aisip.OnO.backend.problem.repository.ProblemRepository;
import com.aisip.OnO.backend.practicenote.repository.PracticeNoteRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class PracticeNoteService {

    private final ProblemRepository problemRepository;

    private final PracticeNoteRepository practiceNoteRepository;

    private final ProblemPracticeNoteMappingRepository problemPracticeNoteMappingRepository;

    private PracticeNote getPracticeEntity(Long practiceId){

        return practiceNoteRepository.findById(practiceId)
                .orElseThrow(() -> new ApplicationException(PracticeNoteErrorCase.PRACTICE_NOTE_NOT_FOUND));
    }

    public void registerPractice(PracticeNoteRegisterDto practiceNoteRegisterDto, Long userId) {

        PracticeNote practiceNote = PracticeNote.from(practiceNoteRegisterDto, userId);
        practiceNoteRepository.save(practiceNote);

        if (practiceNoteRegisterDto.problemIdList() != null) {
            practiceNoteRegisterDto.problemIdList().forEach(problemId ->
                    addProblemToPractice(practiceNote, problemId));
        }

        log.info("userId: {} register practiceId: {}", userId, practiceNote.getId());
    }

    public PracticeNoteDetailResponseDto findPracticeNoteDetail(Long practiceId){
        PracticeNote practiceNote = practiceNoteRepository.findPracticeNoteWithDetails(practiceId);
        List<Problem> problemList = problemRepository.findAllProblemsByPracticeId(practiceId);
        List<ProblemResponseDto> problemResponseDtoList = problemList.stream().map(
                ProblemResponseDto::from
        ).toList();

        log.info("find detail for practiceId: {}", practiceNote.getId());
        return PracticeNoteDetailResponseDto.from(practiceNote, problemResponseDtoList);
    }

    public List<PracticeNoteThumbnailResponseDto> findAllPracticeThumbnailsByUser(Long userId){
        List<PracticeNote> practiceNoteList = practiceNoteRepository.findAllByUserId(userId);

        log.info("userId: {} find all practice thumbnails", userId);

        return practiceNoteList.stream().map(
                PracticeNoteThumbnailResponseDto::from
        ).collect(Collectors.toList());
    }

    public void addPracticeNoteCount(Long practiceId) {
        PracticeNote practiceNote = getPracticeEntity(practiceId);
        practiceNote.updatePracticeNoteCount();

        log.info("practiceId: {} count has updated", practiceId);
    }

    public void updatePracticeInfo(PracticeNoteRegisterDto practiceNoteRegisterDto) {
        Long practiceId = practiceNoteRegisterDto.practiceNoteId();
        PracticeNote practiceNote = getPracticeEntity(practiceId);

        practiceNote.updateTitle(practiceNoteRegisterDto.practiceTitle());

        // ✅ 기존 문제 ID 목록 가져오기 (DB에서 한 번에 조회)
        Set<Long> existingProblemIds = practiceNoteRepository.findProblemIdListByPracticeNoteId(practiceId);

        // ✅ 새로운 문제 ID 목록
        Set<Long> newProblemIds = new HashSet<>(practiceNoteRegisterDto.problemIdList());

        // ✅ 추가해야 할 문제 찾기
        Set<Long> problemsToAdd = new HashSet<>(newProblemIds);
        problemsToAdd.removeAll(existingProblemIds);

        // ✅ 삭제해야 할 문제 찾기
        Set<Long> problemsToRemove = new HashSet<>(existingProblemIds);
        problemsToRemove.removeAll(newProblemIds);

        // ✅ 문제 추가
        if (!problemsToAdd.isEmpty()) {
            problemsToAdd.forEach(problemId -> {
                addProblemToPractice(practiceNote, problemId);
            });
        }

        // ✅ 문제 삭제
        if (!problemsToRemove.isEmpty()) {
            problemsToRemove.forEach(problemId -> {
                deleteProblemFromPractice(practiceId, problemId);
            });
        }

        log.info("practiceId: {} has updated", practiceId);
    }

    public void deletePractice(Long practiceId) {
        PracticeNote practiceNote = getPracticeEntity(practiceId);
        practiceNote.getProblemPracticeNoteMappingList().clear();  // 매핑 삭제

        practiceNoteRepository.delete(practiceNote);
        log.info("practiceId: {} has deleted", practiceId);
    }

    public void deletePractices(List<Long> practiceIds) {
        practiceIds.forEach(this::deletePractice);
    }

    public void deleteAllPracticesByUser(Long userId) {
        List<PracticeNote> practiceNoteList = practiceNoteRepository.findAllByUserId(userId);

        practiceNoteRepository.deleteAll(practiceNoteList);
        log.info("userId: {} has delete all practices", userId);
    }

    private void addProblemToPractice(PracticeNote practiceNote, Long problemId) {

        boolean exists = practiceNoteRepository.checkProblemAlreadyMatchingWithPractice(practiceNote.getId(), problemId);

        if (!exists) {
            Problem problem = problemRepository.findById(problemId)
                    .orElseThrow(() -> new ApplicationException(ProblemErrorCase.PROBLEM_NOT_FOUND));

            ProblemPracticeNoteMapping problemPracticeNoteMapping = ProblemPracticeNoteMapping.from(practiceNote, problem);
            problemPracticeNoteMappingRepository.save(problemPracticeNoteMapping);

            problem.addProblemToPractice(problemPracticeNoteMapping);
            practiceNote.addProblemToPracticeNote(problemPracticeNoteMapping);
        }
    }

    private void deleteProblemFromPractice(Long practiceId, Long problemId) {
        practiceNoteRepository.deleteProblemFromPractice(practiceId, problemId);
        log.info("problemId: {} has deleted from practiceId: {}", problemId, practiceId);
    }

    public void deleteProblemsFromAllPractice(List<Long> deleteProblemIdList) {
        practiceNoteRepository.deleteProblemsFromAllPractice(deleteProblemIdList);
    }
}
