package com.aisip.OnO.backend.service;

import com.aisip.OnO.backend.Dto.Process.ImageProcessRegisterDto;
import com.aisip.OnO.backend.entity.Folder;
import com.aisip.OnO.backend.entity.User.User;
import com.aisip.OnO.backend.repository.FolderRepository;
import com.aisip.OnO.backend.repository.UserRepository;
import com.aisip.OnO.backend.Dto.Problem.ProblemRegisterDto;
import com.aisip.OnO.backend.Dto.Problem.ProblemResponseDto;
import com.aisip.OnO.backend.converter.ProblemConverter;
import com.aisip.OnO.backend.entity.Image.ImageData;
import com.aisip.OnO.backend.entity.Image.ImageType;
import com.aisip.OnO.backend.entity.Problem;
import com.aisip.OnO.backend.exception.ProblemNotFoundException;
import com.aisip.OnO.backend.exception.UserNotAuthorizedException;
import com.aisip.OnO.backend.exception.UserNotFoundException;
import com.aisip.OnO.backend.repository.ProblemRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

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

    private final FolderRepository folderRepository;
    private final FileUploadService fileUploadService;

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

    @Override
    public List<ProblemResponseDto> findAllProblemsByUserId(Long userId) {
        Optional<User> user = userRepository.findById(userId);
        return user.map(u -> problemRepository.findAllByUserId(u.getId())
                        .stream()
                        .map(problem -> {
                            List<ImageData> images = fileUploadService.getProblemImages(problem.getId());
                            return ProblemConverter.convertToResponseDto(problem, images); // 문제 데이터와 이미지 데이터를 DTO로 변환
                        })
                        .collect(Collectors.toList()))
                .orElse(Collections.emptyList());
    }

    @Override
    public List<ProblemResponseDto> findAllProblemsByFolderId(Long folderId) {

        return problemRepository.findAllByFolderId(folderId)
                .stream().map(problem -> {
                    List<ImageData> images = fileUploadService.getProblemImages(problem.getId());
                    return ProblemConverter.convertToResponseDto(problem, images); // 문제 데이터와 이미지 데이터를 DTO로 변환
                }).collect(Collectors.toList());

    }

    @Override
    public boolean saveProblem(Long userId, ProblemRegisterDto problemRegisterDto) {
        Optional<User> optionalUserEntity = userRepository.findById(userId);

        try {
            if (optionalUserEntity.isPresent()) {
                User user = optionalUserEntity.get();

                Problem problem = Problem.builder()
                        .user(user)
                        .reference(problemRegisterDto.getReference())
                        .memo(problemRegisterDto.getMemo())
                        .solvedAt(problemRegisterDto.getSolvedAt())
                        .build();

                Optional<Folder> optionalFolder = folderRepository.findById(problemRegisterDto.getFolderId());
                optionalFolder.ifPresent(problem::setFolder);

                Problem savedProblem = problemRepository.save(problem);

                if (problemRegisterDto.getProblemImage() != null) {
                    String problemImageUrl = fileUploadService.uploadFileToS3(problemRegisterDto.getProblemImage(), savedProblem, ImageType.PROBLEM_IMAGE);
                    if (problemImageUrl != null) {

                        problemRegisterDto.initColorsList();
                        ImageProcessRegisterDto imageProcessRegisterDto = ImageProcessRegisterDto.builder()
                                .fullUrl(problemImageUrl)
                                .colorsList(problemRegisterDto.getColorsList())
                                .build();

                        String processImageUrl = fileUploadService.saveProcessImageUrl(imageProcessRegisterDto, savedProblem, ImageType.PROCESS_IMAGE);
                    }
                }

                if (problemRegisterDto.getAnswerImage() != null) {
                    String answerImageUrl = fileUploadService.uploadFileToS3(problemRegisterDto.getAnswerImage(), savedProblem, ImageType.ANSWER_IMAGE);
                }

                if (problemRegisterDto.getSolveImage() != null) {
                    String solveImageUrl = fileUploadService.uploadFileToS3(problemRegisterDto.getSolveImage(), savedProblem, ImageType.SOLVE_IMAGE);
                }

                return true;
            } else {
                throw new UserNotFoundException("User not found with ID: " + userId);
            }
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    @Override
    public boolean updateProblem(Long userId, ProblemRegisterDto problemRegisterDto) {

        Long problemId = problemRegisterDto.getProblemId();
        Optional<Problem> optionalProblem = problemRepository.findById(problemId);
        if (optionalProblem.isPresent()) {
            Problem problem = optionalProblem.get();
            if (problem.getUser().getId().equals(userId)) {

                try {
                    if (problemRegisterDto.getSolvedAt() != null) {
                        problem.setSolvedAt(problemRegisterDto.getSolvedAt());
                    }

                    if (problemRegisterDto.getReference() != null) {
                        problem.setReference(problemRegisterDto.getReference());
                    }

                    if (problemRegisterDto.getMemo() != null) {
                        problem.setMemo(problemRegisterDto.getMemo());
                    }

                    if (problemRegisterDto.getProblemImage() != null) {
                        String problemImageUrl = fileUploadService.updateImage(problemRegisterDto.getProblemImage(), problem, ImageType.PROBLEM_IMAGE);

                        if (problemImageUrl != null) {

                            problemRegisterDto.initColorsList();
                            ImageProcessRegisterDto imageProcessRegisterDto = ImageProcessRegisterDto.builder()
                                    .fullUrl(problemImageUrl)
                                    .colorsList(problemRegisterDto.getColorsList())
                                    .build();

                            String processImageUrl = fileUploadService.saveProcessImageUrl(imageProcessRegisterDto, problem, ImageType.PROCESS_IMAGE);
                        }
                    }

                    if (problemRegisterDto.getSolveImage() != null) {
                        fileUploadService.updateImage(problemRegisterDto.getSolveImage(), problem, ImageType.SOLVE_IMAGE);
                    }

                    if (problemRegisterDto.getAnswerImage() != null) {
                        fileUploadService.updateImage(problemRegisterDto.getAnswerImage(), problem, ImageType.ANSWER_IMAGE);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    return false;
                }
            } else {
                return false;
            }
            return true;
        } else {
            return false;
        }
    }

    @Override
    public void deleteProblem(Long userId, Long problemId) {
        Optional<Problem> optionalProblem = problemRepository.findById(problemId);
        if (optionalProblem.isPresent()) {
            Problem problem = optionalProblem.get();
            if (problem.getUser().getId().equals(userId)) {

                List<ImageData> images = fileUploadService.getProblemImages(problemId);
                images.forEach(fileUploadService::deleteImage);

                problemRepository.delete(problem);
            } else {
                throw new UserNotAuthorizedException("User ID does not match with problem's user ID");
            }
        } else {
            throw new ProblemNotFoundException("Problem not found with ID: " + problemId);
        }
    }

    @Override
    public void deleteUserProblems(Long userId) {
        List<Problem> problemList = problemRepository.findAllByUserId(userId);
        problemList.forEach(problem -> {
            Long problemId = problem.getId();
            List<ImageData> images = fileUploadService.getProblemImages(problemId);
            images.forEach(fileUploadService::deleteImage);
            problemRepository.delete(problem);
        });
    }
}