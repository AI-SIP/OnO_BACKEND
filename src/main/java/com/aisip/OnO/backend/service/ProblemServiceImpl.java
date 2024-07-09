package com.aisip.OnO.backend.service;

import com.aisip.OnO.backend.Dto.Problem.ProblemRegisterDto;
import com.aisip.OnO.backend.Dto.Problem.ProblemResponseDto;
import com.aisip.OnO.backend.converter.ProblemConverter;
import com.aisip.OnO.backend.entity.Problem;
import com.aisip.OnO.backend.entity.User;
import com.aisip.OnO.backend.exception.ProblemNotFoundException;
import com.aisip.OnO.backend.exception.UserNotAuthorizedException;
import com.aisip.OnO.backend.exception.UserNotFoundException;
import com.aisip.OnO.backend.repository.ProblemRepository;
import com.aisip.OnO.backend.repository.UserRepository;
import com.amazonaws.services.s3.model.ObjectMetadata;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
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

    @Override
    public ProblemResponseDto findProblemByUserId(Long userId, Long problemId) {
        Optional<Problem> optionalProblem = problemRepository.findById(problemId);
        if (optionalProblem.isPresent()) {
            Problem problem = optionalProblem.get();

            if (problem.getUser().getId().equals(userId)) {
                return ProblemConverter.convertToResponseDto(problem);
            } else {
                throw new UserNotAuthorizedException("User ID does not match with problem's user ID");
            }
        } else {
            throw new ProblemNotFoundException("Problem not found with ID: " + problemId);
        }
    }

    @Override
    public List<ProblemResponseDto> findAllProblemsByUserId(Long userId) {
        Optional<User> user = userRepository.findById(userId);
        return user.map(u -> problemRepository.findByUser(u)
                        .stream()
                        .map(ProblemConverter::convertToResponseDto)
                        .collect(Collectors.toList()))
                .orElse(Collections.emptyList());
    }

    @Override
    public ProblemResponseDto saveProblem(Long userId, ProblemRegisterDto problemRegisterDto) {
        Optional<User> optionalUser = userRepository.findById(userId);

        try{
            if (optionalUser.isPresent()) {
                User user = optionalUser.get();

                Problem problem = Problem.builder()
                        .user(user)
                        .processImageUrl("")
                        .reference(problemRegisterDto.getReference())
                        .memo(problemRegisterDto.getMemo())
                        .solvedAt(problemRegisterDto.getSolvedAt())
                        .createdAt(LocalDateTime.now())
                        .updateAt(LocalDateTime.now())
                        .build();

                String problemImageUrl = fileUploadService.uploadFileToS3(problemRegisterDto.getProblemImage());
                problem.setImageUrl(problemImageUrl);

                String answerImageUrl = fileUploadService.uploadFileToS3(problemRegisterDto.getAnswerImage());
                problem.setAnswerImageUrl(answerImageUrl);

                String solveImageUrl = fileUploadService.uploadFileToS3(problemRegisterDto.getSolveImage());
                problem.setSolveImageUrl(solveImageUrl);

                Problem savedProblem = problemRepository.save(problem);
                return ProblemConverter.convertToResponseDto(savedProblem);
            } else {
                throw new UserNotFoundException("User not found with ID: " + userId);
            }
        } catch (Exception e){
            e.printStackTrace();
            return null;
        }

    }

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

    @Override
    public void deleteProblem(Long userId, Long problemId) {
        Optional<Problem> optionalProblem = problemRepository.findById(problemId);
        if (optionalProblem.isPresent()) {
            Problem problem = optionalProblem.get();
            if (problem.getUser().getId().equals(userId)) {
                problemRepository.delete(problem);
            } else {
                throw new UserNotAuthorizedException("User ID does not match with problem's user ID");
            }
        } else {
            throw new ProblemNotFoundException("Problem not found with ID: " + problemId);
        }
    }
}