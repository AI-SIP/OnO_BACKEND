package com.aisip.OnO.backend.practicenote.service;

import com.aisip.OnO.backend.common.exception.ApplicationException;
import com.aisip.OnO.backend.common.response.CursorPageResponse;
import com.aisip.OnO.backend.mission.service.MissionLogService;
import com.aisip.OnO.backend.practicenote.dto.*;
import com.aisip.OnO.backend.practicenote.entity.PracticeNote;
import com.aisip.OnO.backend.practicenote.entity.PracticeNotification;
import com.aisip.OnO.backend.practicenote.exception.PracticeNoteErrorCase;
import com.aisip.OnO.backend.practicenote.repository.ProblemPracticeNoteMappingRepository;
import com.aisip.OnO.backend.problem.entity.Problem;
import com.aisip.OnO.backend.practicenote.entity.ProblemPracticeNoteMapping;
import com.aisip.OnO.backend.problem.exception.ProblemErrorCase;
import com.aisip.OnO.backend.problem.repository.ProblemRepository;
import com.aisip.OnO.backend.practicenote.repository.PracticeNoteRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class PracticeNoteService {
    private static final String DEFAULT_PRACTICE_NOTE_TITLE = "복습노트";

    private final ProblemRepository problemRepository;

    private final PracticeNoteRepository practiceNoteRepository;

    private final ProblemPracticeNoteMappingRepository problemPracticeNoteMappingRepository;

    private final PracticeNotificationScheduler practiceNotificationScheduler;

    private final MissionLogService missionLogService;

    private PracticeNote getPracticeEntity(Long practiceId){

        return practiceNoteRepository.findById(practiceId)
                .orElseThrow(() -> new ApplicationException(PracticeNoteErrorCase.PRACTICE_NOTE_NOT_FOUND));
    }

    public Long registerPractice(PracticeNoteRegisterDto practiceNoteRegisterDto, Long userId) {

        PracticeNote practiceNote = PracticeNote.from(practiceNoteRegisterDto, userId);
        practiceNoteRepository.save(practiceNote);

        if (practiceNoteRegisterDto.problemIdList() != null) {
            practiceNoteRegisterDto.problemIdList().forEach(problemId ->
                    addProblemToPractice(practiceNote, problemId));
        }

        if(practiceNoteRegisterDto.practiceNotification() != null) {
            registerPracticeNotification(userId, practiceNote.getId(), practiceNote.getTitle(), practiceNoteRegisterDto.practiceNotification());
        }

        log.info("userId: {} register practiceId: {}", userId, practiceNote.getId());
        return practiceNote.getId();
    }

    public Long registerDefaultPractice(Long userId) {
        return registerPractice(new PracticeNoteRegisterDto(
                null,
                DEFAULT_PRACTICE_NOTE_TITLE,
                List.of(),
                null
        ), userId);
    }

    public void registerPracticeNotification(Long userId, Long practiceId, String practiceTitle, PracticeNotificationRegisterDto notificationRegisterDto) {
        practiceNotificationScheduler.schedulePracticeNotification(userId, practiceId, practiceTitle, notificationRegisterDto);
    }

    @Transactional(readOnly = true)
    public PracticeNoteDetailResponseDto findPracticeNoteDetail(Long practiceId){
        log.info("find practiceId: {}", practiceId);
        PracticeNote practiceNote = practiceNoteRepository.findPracticeNoteWithDetails(practiceId)
                .orElseThrow(() -> new ApplicationException(PracticeNoteErrorCase.PRACTICE_NOTE_NOT_FOUND));

        List<Long> problemIdList = practiceNoteRepository.findProblemIdListByPracticeNoteId(practiceId);

        log.info("find detail for practiceId: {}", practiceNote.getId());
        return PracticeNoteDetailResponseDto.from(practiceNote, problemIdList);
    }

    @Transactional(readOnly = true)
    public List<PracticeNoteThumbnailResponseDto> findAllPracticeThumbnailsByUser(Long userId){
        List<PracticeNote> practiceNoteList = practiceNoteRepository.findAllByUserId(userId);

        log.info("userId: {} find all practice thumbnails", userId);

        return practiceNoteList.stream().map(
                PracticeNoteThumbnailResponseDto::from
        ).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<PracticeNoteDetailResponseDto> findAllPracticesByUser(Long userId){
        List<PracticeNote> practiceNoteList = practiceNoteRepository.findAllUserPracticeNotesWithDetails(userId);

        log.info("userId: {} find all practice details", userId);

        return practiceNoteList.stream().map(
                practiceNote -> {
                    List<Long> problemIdList = practiceNoteRepository.findProblemIdListByPracticeNoteId(practiceNote.getId());
                    return PracticeNoteDetailResponseDto.from(practiceNote, problemIdList);
                }

        ).collect(Collectors.toList());
    }

    public void addPracticeNoteCount(Long userId, Long practiceId) {
        PracticeNote practiceNote = getPracticeEntity(practiceId);
        practiceNote.updatePracticeNoteCount();

        // 복습노트 사용 미션 등록
        missionLogService.registerNotePracticeMission(userId, practiceId);

        log.info("practiceId: {} count has updated", practiceId);
    }

    public void updatePracticeInfo(Long userId, PracticeNoteUpdateDto practiceNoteUpdateDto) {
        Long practiceId = practiceNoteUpdateDto.practiceNoteId();
        PracticeNote practiceNote = getPracticeEntity(practiceId);

        practiceNote.updateTitle(practiceNoteUpdateDto.practiceTitle());

        practiceNote.updateNotification(PracticeNotification.from(practiceNoteUpdateDto.practiceNotification()));

        if (!practiceNoteUpdateDto.addProblemIdList().isEmpty()) {
            practiceNoteUpdateDto.addProblemIdList().forEach(problemId -> {
                addProblemToPractice(practiceNote, problemId);
            });
        }

        if (!practiceNoteUpdateDto.removeProblemIdList().isEmpty()) {
            practiceNoteUpdateDto.removeProblemIdList().forEach(problemId -> {
                deletePracticeNoteMapping(practiceNote, problemId);
            });
        }

        if (practiceNoteUpdateDto.practiceNotification() != null) {
            practiceNotificationScheduler.updateNotification(userId, practiceId, practiceNote.getTitle(), practiceNoteUpdateDto.practiceNotification());
        }

        if (practiceNoteUpdateDto.practiceNotification() == null) {
            practiceNotificationScheduler.deleteNotification(practiceId);
        }

        log.info("practiceId: {} has updated", practiceId);
    }

    public void updatePracticeNotification(Long userId, Long practiceId, String practiceTitle, PracticeNotificationRegisterDto notificationRegisterDto) {

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

    /**
     * V2 API: 커서 기반 복습노트 썸네일 조회
     * @param userId 유저 ID
     * @param cursor 마지막으로 조회한 복습노트 ID (null이면 처음부터)
     * @param size 조회할 개수
     * @return 커서 기반 페이징 응답
     */
    @Transactional(readOnly = true)
    public CursorPageResponse<PracticeNoteThumbnailResponseDto> findPracticeThumbnailsByUserWithCursor(Long userId, Long cursor, int size) {
        List<PracticeNote> practiceNotes = practiceNoteRepository.findPracticeNotesByUserWithCursor(userId, cursor, size);

        boolean hasNext = practiceNotes.size() > size;
        List<PracticeNote> content = hasNext ? practiceNotes.subList(0, size) : practiceNotes;
        Long nextCursor = hasNext ? content.get(content.size() - 1).getId() : null;

        List<PracticeNoteThumbnailResponseDto> dtoList = content.stream()
                .map(PracticeNoteThumbnailResponseDto::from)
                .collect(Collectors.toList());

        log.info("userId: {} find practice thumbnails with cursor: {}, size: {}, hasNext: {}", userId, cursor, size, hasNext);
        return CursorPageResponse.of(dtoList, nextCursor, hasNext, size);
    }
}
