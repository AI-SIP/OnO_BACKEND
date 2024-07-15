package com.aisip.OnO.backend.service;

import com.aisip.OnO.backend.Dto.Problem.ProblemRegisterDto;
import com.aisip.OnO.backend.Dto.Problem.ProblemResponseDto;
import com.aisip.OnO.backend.converter.ProblemConverter;
import com.aisip.OnO.backend.entity.Image.ImageData;
import com.aisip.OnO.backend.entity.Image.ImageType;
import com.aisip.OnO.backend.entity.Problem;
import com.aisip.OnO.backend.entity.User;
import com.aisip.OnO.backend.exception.ProblemNotFoundException;
import com.aisip.OnO.backend.exception.UserNotAuthorizedException;
import com.aisip.OnO.backend.exception.UserNotFoundException;
import com.aisip.OnO.backend.repository.ProblemRepository;
import com.aisip.OnO.backend.repository.UserRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class ProblemServiceImpl implements ProblemService {

    private final UserRepository userRepository;
    private final ProblemRepository problemRepository;

    private final FileUploadService fileUploadService;

    /**특정 문제 조회*/
    @Override
    public ProblemResponseDto findProblemByUserId(Long userId, Long problemId) {
        Optional<Problem> optionalProblem = problemRepository.findById(problemId);
        if (optionalProblem.isPresent()) {
            Problem problem = optionalProblem.get();

            if (problem.getUser().getId().equals(userId)) {
                List<ImageData> images = fileUploadService.getProblemImages(problemId);
                return ProblemConverter.convertToResponseDto(problem, images);
            } else {
                throw new UserNotAuthorizedException("User ID does not match with problem's user ID");
            }
        } else {
            throw new ProblemNotFoundException("Problem not found with ID: " + problemId);
        }
    }

    /**특정 유저의 전체 문제 조회*/
    @Override
    public List<ProblemResponseDto> findAllProblemsByUserId(Long userId) {
        Optional<User> user = userRepository.findById(userId);
        return user.map(u -> problemRepository.findByUser(u)
                        .stream()
                        .map(problem -> {
                            List<ImageData> images = fileUploadService.getProblemImages(problem.getId());
                            return ProblemConverter.convertToResponseDto(problem, images); // 문제 데이터와 이미지 데이터를 DTO로 변환
                        })
                        .collect(Collectors.toList()))
                .orElse(Collections.emptyList());
    }

    /**문제 저장*/
    @Override
    public boolean saveProblem(Long userId, ProblemRegisterDto problemRegisterDto) {
        Optional<User> optionalUser = userRepository.findById(userId);

        try{
            if (optionalUser.isPresent()) {
                User user = optionalUser.get();

                Problem problem = Problem.builder()
                        .user(user)
                        .reference(problemRegisterDto.getReference())
                        .memo(problemRegisterDto.getMemo())
                        .solvedAt(problemRegisterDto.getSolvedAt())
                        .createdAt(LocalDateTime.now())
                        .updateAt(LocalDateTime.now())
                        .build();

                Problem savedProblem = problemRepository.save(problem);

                String problemImageUrl = fileUploadService.uploadFileToS3(problemRegisterDto.getProblemImage());
                fileUploadService.saveImageData(problemImageUrl, savedProblem, ImageType.PROBLEM_IMAGE);

                String answerImageUrl = fileUploadService.uploadFileToS3(problemRegisterDto.getAnswerImage());
                fileUploadService.saveImageData(answerImageUrl, savedProblem, ImageType.ANSWER_IMAGE);

                String solveImageUrl = fileUploadService.uploadFileToS3(problemRegisterDto.getSolveImage());
                fileUploadService.saveImageData(solveImageUrl, savedProblem, ImageType.SOLVE_IMAGE);

                String processImageUrl = "";

                return true;
            } else {
                throw new UserNotFoundException("User not found with ID: " + userId);
            }
        } catch (Exception e){
            e.printStackTrace();
            return false;
        }

    }

    /**문제 업데이트*/
    /*
    @Override
    public ProblemResponseDto updateProblem(Long userId, Long problemId, ProblemRegisterDto problemRegisterDto) {
        Optional<User> optionalUser = userRepository.findById(userId);
        Optional<Problem> optionalProblem = problemRepository.findById(problemId);

        if (optionalUser.isPresent() && optionalProblem.isPresent()) {
            User user = optionalUser.get();
            Problem problem = optionalProblem.get();

            // Update fields from ProblemRegisterDto
            problem.setImageUrl(problemRegisterDto.getImageUrl());
            problem.setAnswerImageUrl(problemRegisterDto.getAnswerImageUrl());
            problem.setSolveImageUrl(problemRegisterDto.getSolveImageUrl());
            problem.setMemo(problemRegisterDto.getMemo());
            problem.setReference(problemRegisterDto.getReference());
            problem.setSolvedAt(problemRegisterDto.getSolvedAt());
            problem.setUpdateAt(LocalDate.now());

            Problem savedProblem = problemRepository.save(problem);
            return ProblemConverter.convertToResponseDto(savedProblem);
        } else {
            throw new ProblemNotFoundException("Problem not found with ID: " + problemId);
        }
    }
     */

    /**문제 삭제*/
    @Override
    public void deleteProblem(Long userId, Long problemId) {
        Optional<Problem> optionalProblem = problemRepository.findById(problemId);
        if (optionalProblem.isPresent()) {
            Problem problem = optionalProblem.get();
            if (problem.getUser().getId().equals(userId)) {
                // 문제와 관련된 이미지 데이터를 조회
                List<ImageData> images = fileUploadService.getProblemImages(problemId);
                // 각 이미지에 대해 S3에서 삭제
                images.forEach(image -> fileUploadService.deleteImage(image.getImageUrl()));
                // 문제 데이터 삭제
                problemRepository.delete(problem);
            } else {
                throw new UserNotAuthorizedException("User ID does not match with problem's user ID");
            }
        } else {
            throw new ProblemNotFoundException("Problem not found with ID: " + problemId);
        }
    }
}