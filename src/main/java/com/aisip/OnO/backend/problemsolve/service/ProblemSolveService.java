package com.aisip.OnO.backend.problemsolve.service;

import com.aisip.OnO.backend.common.exception.ApplicationException;
import com.aisip.OnO.backend.problemsolve.dto.ProblemSolveRegisterDto;
import com.aisip.OnO.backend.problemsolve.dto.ProblemSolveResponseDto;
import com.aisip.OnO.backend.problemsolve.dto.ProblemSolveUpdateDto;
import com.aisip.OnO.backend.problemsolve.entity.ProblemSolve;
import com.aisip.OnO.backend.problemsolve.entity.ProblemSolveImageData;
import com.aisip.OnO.backend.problemsolve.exception.ProblemSolveErrorCase;
import com.aisip.OnO.backend.problemsolve.repository.ProblemSolveImageDataRepository;
import com.aisip.OnO.backend.problemsolve.repository.ProblemSolveRepository;
import com.aisip.OnO.backend.problem.entity.Problem;
import com.aisip.OnO.backend.problem.exception.ProblemErrorCase;
import com.aisip.OnO.backend.problem.repository.ProblemRepository;
import com.aisip.OnO.backend.util.fileupload.service.FileUploadService;
import com.aisip.OnO.backend.config.rabbitmq.producer.S3DeleteProducer;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProblemSolveService {

    private final ProblemSolveRepository problemSolveRepository;
    private final ProblemSolveImageDataRepository problemSolveImageDataRepository;
    private final ProblemRepository problemRepository;
    private final FileUploadService fileUploadService;
    private final S3DeleteProducer s3DeleteProducer;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public ProblemSolveResponseDto getPracticeRecord(Long practiceRecordId, Long userId) {
        ProblemSolve problemSolve = problemSolveRepository.findByIdWithImages(practiceRecordId)
                .orElseThrow(() -> new ApplicationException(ProblemSolveErrorCase.PRACTICE_RECORD_NOT_FOUND));

        if (!Objects.equals(problemSolve.getUserId(), userId)) {
            throw new ApplicationException(ProblemSolveErrorCase.PRACTICE_RECORD_USER_UNMATCHED);
        }

        return ProblemSolveResponseDto.from(problemSolve);
    }

    @Transactional(readOnly = true)
    public List<ProblemSolveResponseDto> getPracticeRecordsByProblemId(Long problemId, Long userId) {
        Problem problem = problemRepository.findById(problemId)
                .orElseThrow(() -> new ApplicationException(ProblemErrorCase.PROBLEM_NOT_FOUND));

        if (!Objects.equals(problem.getUserId(), userId)) {
            throw new ApplicationException(ProblemErrorCase.PROBLEM_USER_UNMATCHED);
        }

        List<ProblemSolve> problemSolves = problemSolveRepository.findAllByProblemIdWithImages(problemId);

        return problemSolves.stream()
                .map(ProblemSolveResponseDto::from)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<ProblemSolveResponseDto> getUserPracticeRecords(Long userId) {
        List<ProblemSolve> problemSolves = problemSolveRepository.findAllByUserId(userId);

        return problemSolves.stream()
                .map(ProblemSolveResponseDto::from)
                .collect(Collectors.toList());
    }

    @Transactional
    public Long createPracticeRecord(ProblemSolveRegisterDto dto, Long userId) {
        Problem problem = problemRepository.findById(dto.problemId())
                .orElseThrow(() -> new ApplicationException(ProblemErrorCase.PROBLEM_NOT_FOUND));

        if (!Objects.equals(problem.getUserId(), userId)) {
            throw new ApplicationException(ProblemErrorCase.PROBLEM_USER_UNMATCHED);
        }

        // improvements를 JSON 문자열로 변환
        String improvementsJson = null;
        if (dto.improvements() != null && !dto.improvements().isEmpty()) {
            try {
                improvementsJson = objectMapper.writeValueAsString(dto.improvements());
            } catch (Exception e) {
                log.error("JSON 변환 실패 for improvements", e);
                throw new RuntimeException("개선 사항 JSON 변환 실패", e);
            }
        }

        ProblemSolve problemSolve = ProblemSolve.create(
                problem,
                userId,
                dto.practicedAt(),
                dto.answerStatus(),
                dto.reflection(),
                improvementsJson,
                dto.timeSpentSeconds()
        );

        problemSolveRepository.save(problemSolve);
        log.info("userId: {} created practice record: {}", userId, problemSolve.getId());

        return problemSolve.getId();
    }

    @Transactional
    public void uploadPracticeRecordImages(Long practiceRecordId, Long userId, List<MultipartFile> images) {
        ProblemSolve problemSolve = problemSolveRepository.findById(practiceRecordId)
                .orElseThrow(() -> new ApplicationException(ProblemSolveErrorCase.PRACTICE_RECORD_NOT_FOUND));

        if (!Objects.equals(problemSolve.getUserId(), userId)) {
            throw new ApplicationException(ProblemSolveErrorCase.PRACTICE_RECORD_USER_UNMATCHED);
        }

        for (int i = 0; i < images.size(); i++) {
            MultipartFile imageFile = images.get(i);
            String imageUrl = fileUploadService.uploadFileToS3(imageFile);

            ProblemSolveImageData problemSolveImageData = ProblemSolveImageData.create(imageUrl, i);
            problemSolve.addImage(problemSolveImageData);
            problemSolveImageDataRepository.save(problemSolveImageData);

            log.info("Uploaded practice record image to S3: {} for practiceRecordId: {}", imageUrl, practiceRecordId);
        }
    }

    @Transactional
    public void updatePracticeRecord(ProblemSolveUpdateDto dto, Long userId) {
        ProblemSolve problemSolve = problemSolveRepository.findById(dto.practiceRecordId())
                .orElseThrow(() -> new ApplicationException(ProblemSolveErrorCase.PRACTICE_RECORD_NOT_FOUND));

        if (!Objects.equals(problemSolve.getUserId(), userId)) {
            throw new ApplicationException(ProblemSolveErrorCase.PRACTICE_RECORD_USER_UNMATCHED);
        }

        // improvements를 JSON 문자열로 변환
        String improvementsJson = null;
        if (dto.improvements() != null && !dto.improvements().isEmpty()) {
            try {
                improvementsJson = objectMapper.writeValueAsString(dto.improvements());
            } catch (Exception e) {
                log.error("JSON 변환 실패 for improvements", e);
                throw new RuntimeException("개선 사항 JSON 변환 실패", e);
            }
        }

        problemSolve.updateRecord(
                dto.answerStatus(),
                dto.reflection(),
                improvementsJson,
                dto.timeSpentSeconds()
        );

        log.info("userId: {} updated practice record: {}", userId, problemSolve.getId());
    }

    @Transactional
    public void deletePracticeRecord(Long practiceRecordId, Long userId) {
        ProblemSolve problemSolve = problemSolveRepository.findByIdWithImages(practiceRecordId)
                .orElseThrow(() -> new ApplicationException(ProblemSolveErrorCase.PRACTICE_RECORD_NOT_FOUND));

        if (!Objects.equals(problemSolve.getUserId(), userId)) {
            throw new ApplicationException(ProblemSolveErrorCase.PRACTICE_RECORD_USER_UNMATCHED);
        }

        List<ProblemSolveImageData> images = problemSolve.getImages();

        problemSolveRepository.delete(problemSolve);
        log.info("userId: {} deleted practice record: {}", userId, practiceRecordId);

        images.forEach(image -> {
            try {
                s3DeleteProducer.sendDeleteMessage(image.getImageUrl(), practiceRecordId);
            } catch (Exception e) {
                log.error("S3 삭제 메시지 전송 실패 - practiceRecordId: {}, imageUrl: {}, error: {}",
                        practiceRecordId, image.getImageUrl(), e.getMessage());
            }
        });

        log.info("practiceRecordId: {} S3 삭제 메시지 전송 완료 ({}개)", practiceRecordId, images.size());
    }

    @Transactional
    public void deleteAllPracticeRecordsByProblemId(Long problemId) {
        List<ProblemSolve> problemSolves = problemSolveRepository.findAllByProblemIdWithImages(problemId);

        problemSolves.forEach(record -> {
            List<ProblemSolveImageData> images = record.getImages();

            images.forEach(image -> {
                try {
                    s3DeleteProducer.sendDeleteMessage(image.getImageUrl(), record.getId());
                } catch (Exception e) {
                    log.error("S3 삭제 메시지 전송 실패 - practiceRecordId: {}, imageUrl: {}, error: {}",
                            record.getId(), image.getImageUrl(), e.getMessage());
                }
            });
        });

        problemSolveRepository.deleteAllByProblemId(problemId);
        log.info("problemId: {} 의 모든 연습 기록 삭제 완료", problemId);
    }

    @Transactional(readOnly = true)
    public Long getPracticeRecordCountByProblemId(Long problemId, Long userId) {
        Problem problem = problemRepository.findById(problemId)
                .orElseThrow(() -> new ApplicationException(ProblemErrorCase.PROBLEM_NOT_FOUND));

        if (!Objects.equals(problem.getUserId(), userId)) {
            throw new ApplicationException(ProblemErrorCase.PROBLEM_USER_UNMATCHED);
        }

        return problemSolveRepository.countByProblemId(problemId);
    }

    @Transactional(readOnly = true)
    public Long getUserPracticeRecordCount(Long userId) {
        return problemSolveRepository.countByUserId(userId);
    }
}