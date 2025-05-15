package com.aisip.OnO.backend.practicenote.service;

import com.aisip.OnO.backend.common.exception.ApplicationException;
import com.aisip.OnO.backend.practicenote.dto.PracticeNoteDetailResponseDto;
import com.aisip.OnO.backend.practicenote.dto.PracticeNoteThumbnailResponseDto;
import com.aisip.OnO.backend.practicenote.dto.PracticeNoteUpdateDto;
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

import java.util.List;
import java.util.Optional;
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
        log.info("find practiceId: {}", practiceId);
        PracticeNote practiceNote = practiceNoteRepository.findPracticeNoteWithDetails(practiceId)
                .orElseThrow(() -> new ApplicationException(PracticeNoteErrorCase.PRACTICE_NOTE_NOT_FOUND));

        List<Long> problemIdList = practiceNoteRepository.findProblemIdListByPracticeNoteId(practiceId);

        log.info("find detail for practiceId: {}", practiceNote.getId());
        return PracticeNoteDetailResponseDto.from(practiceNote, problemIdList);
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

    public void updatePracticeInfo(PracticeNoteUpdateDto practiceNoteUpdateDto) {
        Long practiceId = practiceNoteUpdateDto.practiceNoteId();
        PracticeNote practiceNote = getPracticeEntity(practiceId);

        practiceNote.updateTitle(practiceNoteUpdateDto.practiceTitle());

        if (!practiceNoteUpdateDto.addProblemIdList().isEmpty()) {
            practiceNoteUpdateDto.addProblemIdList().forEach(problemId -> {
                addProblemToPractice(practiceNote, problemId);
            });
        }

        // ✅ 문제 삭제
        if (!practiceNoteUpdateDto.removeProblemIdList().isEmpty()) {
            practiceNoteUpdateDto.removeProblemIdList().forEach(problemId -> {
                deletePracticeNoteMapping(practiceNote, problemId);
            });
        }

        log.info("practiceId: {} has updated", practiceId);
    }

    public void deletePractice(Long practiceId) {
        List<ProblemPracticeNoteMapping> problemPracticeNoteMappingList = problemPracticeNoteMappingRepository.findAllByPracticeNoteId(practiceId);
        problemPracticeNoteMappingList.forEach(ProblemPracticeNoteMapping::removeMappingFromProblemAndPractice);

        practiceNoteRepository.deleteById(practiceId);
        log.info("practiceId: {} has deleted", practiceId);
    }

    public void deletePractices(List<Long> practiceIdList) {
        practiceIdList.forEach(this::deletePractice);
    }

    public void deleteAllPracticesByUser(Long userId) {
        List<Long> practiceIdList = practiceNoteRepository.findAllPracticeIdsByUserId(userId);

        deletePractices(practiceIdList);
        log.info("userId: {} has delete all practices", userId);
    }

    private void addProblemToPractice(PracticeNote practiceNote, Long problemId) {

        boolean exists = practiceNoteRepository.checkProblemAlreadyMatchingWithPractice(practiceNote.getId(), problemId);

        if (!exists) {
            Problem problem = problemRepository.findById(problemId)
                    .orElseThrow(() -> new ApplicationException(ProblemErrorCase.PROBLEM_NOT_FOUND));

            ProblemPracticeNoteMapping problemPracticeNoteMapping = ProblemPracticeNoteMapping.from();
            problemPracticeNoteMapping.addMappingToProblemAndPractice(problem, practiceNote);

            problemPracticeNoteMappingRepository.save(problemPracticeNoteMapping);
        }
    }

   private void deletePracticeNoteMapping(PracticeNote practiceNote, Long problemId) {
        Optional<ProblemPracticeNoteMapping> optionalProblemPracticeNoteMapping = problemPracticeNoteMappingRepository.findProblemPracticeNoteMappingByProblemIdAndPracticeNoteId(problemId, practiceNote.getId());
       optionalProblemPracticeNoteMapping.ifPresent(ProblemPracticeNoteMapping::removeMappingFromProblemAndPractice);

        log.info("problemId: {} has deleted from practiceId: {}", problemId, practiceNote.getId());
    }

    public void deleteProblemsFromAllPractice(List<Long> deleteProblemIdList) {
        practiceNoteRepository.deleteProblemsFromAllPractice(deleteProblemIdList);
    }
}
