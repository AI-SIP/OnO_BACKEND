package com.aisip.OnO.backend.problemsolve.service;

import com.aisip.OnO.backend.common.exception.ApplicationException;
import com.aisip.OnO.backend.mission.service.MissionLogService;
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
    private final MissionLogService missionLogService;
    private final FileUploadService fileUploadService;
    private final S3DeleteProducer s3DeleteProducer;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public ProblemSolveResponseDto getProblemSolve(Long problemSolveId, Long userId) {
        ProblemSolve problemSolve = problemSolveRepository.findByIdWithImages(problemSolveId)
                .orElseThrow(() -> new ApplicationException(ProblemSolveErrorCase.PROBLEM_SOLVE_NOT_FOUND));

        if (!Objects.equals(problemSolve.getUserId(), userId)) {
            throw new ApplicationException(ProblemSolveErrorCase.PROBLEM_SOLVE_USER_UNMATCHED);
        }

        return ProblemSolveResponseDto.from(problemSolve);
    }

    @Transactional(readOnly = true)
    public List<ProblemSolveResponseDto> getProblemSolvesByProblemId(Long problemId, Long userId) {
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
    public List<ProblemSolveResponseDto> getUserProblemSolves(Long userId) {
        List<ProblemSolve> problemSolves = problemSolveRepository.findAllByUserId(userId);

        return problemSolves.stream()
                .map(ProblemSolveResponseDto::from)
                .collect(Collectors.toList());
    }

    @Transactional
    public Long createProblemSolve(ProblemSolveRegisterDto dto, Long userId) {
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
        missionLogService.registerProblemPracticeMission(userId, problem.getId());
        log.info("userId: {} created problem solve: {}", userId, problemSolve.getId());

        return problemSolve.getId();
    }

    @Transactional
    public void uploadProblemSolveImages(Long problemSolveId, Long userId, List<MultipartFile> images) {
        ProblemSolve problemSolve = problemSolveRepository.findById(problemSolveId)
                .orElseThrow(() -> new ApplicationException(ProblemSolveErrorCase.PROBLEM_SOLVE_NOT_FOUND));

        if (!Objects.equals(problemSolve.getUserId(), userId)) {
            throw new ApplicationException(ProblemSolveErrorCase.PROBLEM_SOLVE_USER_UNMATCHED);
        }

        for (int i = 0; i < images.size(); i++) {
            MultipartFile imageFile = images.get(i);
            String imageUrl = fileUploadService.uploadFileToS3(imageFile);

            ProblemSolveImageData problemSolveImageData = ProblemSolveImageData.create(imageUrl, i);
            problemSolve.addImage(problemSolveImageData);
            problemSolveImageDataRepository.save(problemSolveImageData);

            log.info("Uploaded problem solve image to S3: {} for problemSolveId: {}", imageUrl, problemSolveId);
        }
    }

    @Transactional
    public void updateProblemSolve(ProblemSolveUpdateDto dto, Long userId) {
        ProblemSolve problemSolve = problemSolveRepository.findById(dto.problemSolveId())
                .orElseThrow(() -> new ApplicationException(ProblemSolveErrorCase.PROBLEM_SOLVE_NOT_FOUND));

        if (!Objects.equals(problemSolve.getUserId(), userId)) {
            throw new ApplicationException(ProblemSolveErrorCase.PROBLEM_SOLVE_USER_UNMATCHED);
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

        problemSolve.updateSolve(
                dto.answerStatus(),
                dto.reflection(),
                improvementsJson,
                dto.timeSpentSeconds()
        );

        log.info("userId: {} updated problem solve: {}", userId, problemSolve.getId());
    }

    @Transactional
    public void deleteProblemSolve(Long problemSolveId, Long userId) {
        ProblemSolve problemSolve = problemSolveRepository.findByIdWithImages(problemSolveId)
                .orElseThrow(() -> new ApplicationException(ProblemSolveErrorCase.PROBLEM_SOLVE_NOT_FOUND));

        if (!Objects.equals(problemSolve.getUserId(), userId)) {
            throw new ApplicationException(ProblemSolveErrorCase.PROBLEM_SOLVE_USER_UNMATCHED);
        }

        List<ProblemSolveImageData> images = problemSolve.getImages();

        problemSolveRepository.delete(problemSolve);
        log.info("userId: {} deleted problem solve: {}", userId, problemSolveId);

        images.forEach(image -> {
            try {
                s3DeleteProducer.sendDeleteMessage(image.getImageUrl(), problemSolveId);
            } catch (Exception e) {
                log.error("S3 삭제 메시지 전송 실패 - problemSolveId: {}, imageUrl: {}, error: {}",
                        problemSolveId, image.getImageUrl(), e.getMessage());
            }
        });

        log.info("problemSolveId: {} S3 삭제 메시지 전송 완료 ({}개)", problemSolveId, images.size());
    }

    @Transactional
    public void deleteAllProblemSolvesByProblemId(Long problemId) {
        List<ProblemSolve> problemSolves = problemSolveRepository.findAllByProblemIdWithImages(problemId);

        problemSolves.forEach(record -> {
            List<ProblemSolveImageData> images = record.getImages();

            images.forEach(image -> {
                try {
                    s3DeleteProducer.sendDeleteMessage(image.getImageUrl(), record.getId());
                } catch (Exception e) {
                    log.error("S3 삭제 메시지 전송 실패 - problemSolveId: {}, imageUrl: {}, error: {}",
                            record.getId(), image.getImageUrl(), e.getMessage());
                }
            });
        });

        problemSolveRepository.deleteAllByProblemId(problemId);
        log.info("problemId: {} 의 모든 복습 기록 삭제 완료", problemId);
    }

    @Transactional(readOnly = true)
    public Long getProblemSolveCountByProblemId(Long problemId, Long userId) {
        Problem problem = problemRepository.findById(problemId)
                .orElseThrow(() -> new ApplicationException(ProblemErrorCase.PROBLEM_NOT_FOUND));

        if (!Objects.equals(problem.getUserId(), userId)) {
            throw new ApplicationException(ProblemErrorCase.PROBLEM_USER_UNMATCHED);
        }

        return problemSolveRepository.countByProblemId(problemId);
    }

    @Transactional(readOnly = true)
    public Long getUserProblemSolveCount(Long userId) {
        return problemSolveRepository.countByUserId(userId);
    }
}