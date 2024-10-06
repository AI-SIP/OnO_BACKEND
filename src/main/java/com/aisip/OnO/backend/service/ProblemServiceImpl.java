package com.aisip.OnO.backend.service;

import com.aisip.OnO.backend.Dto.Problem.ProblemRegisterDtoV2;
import com.aisip.OnO.backend.Dto.Process.ImageProcessRegisterDto;
import com.aisip.OnO.backend.entity.Folder;
import com.aisip.OnO.backend.entity.Problem.ProblemRepeat;
import com.aisip.OnO.backend.entity.Problem.TemplateType;
import com.aisip.OnO.backend.entity.User.User;
import com.aisip.OnO.backend.repository.FolderRepository;
import com.aisip.OnO.backend.repository.ProblemRepeatRepository;
import com.aisip.OnO.backend.repository.UserRepository;
import com.aisip.OnO.backend.Dto.Problem.ProblemRegisterDto;
import com.aisip.OnO.backend.Dto.Problem.ProblemResponseDto;
import com.aisip.OnO.backend.converter.ProblemConverter;
import com.aisip.OnO.backend.entity.Image.ImageData;
import com.aisip.OnO.backend.entity.Image.ImageType;
import com.aisip.OnO.backend.entity.Problem.Problem;
import com.aisip.OnO.backend.exception.ProblemNotFoundException;
import com.aisip.OnO.backend.exception.UserNotAuthorizedException;
import com.aisip.OnO.backend.exception.UserNotFoundException;
import com.aisip.OnO.backend.repository.ProblemRepository;
import io.sentry.Sentry;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class ProblemServiceImpl implements ProblemService {

    private final UserRepository userRepository;
    private final ProblemRepository problemRepository;

    private final ProblemRepeatRepository problemRepeatRepository;

    private final FolderRepository folderRepository;
    private final FileUploadService fileUploadService;


    @Override
    public ProblemResponseDto findProblemByUserId(Long userId, Long problemId) {
        Optional<Problem> optionalProblem = problemRepository.findById(problemId);
        if (optionalProblem.isPresent()) {
            Problem problem = optionalProblem.get();

            if (problem.getUser().getId().equals(userId)) {
                List<ImageData> images = fileUploadService.getProblemImages(problemId);
                List<ProblemRepeat> repeats = getProblemRepeats(problemId);
                return ProblemConverter.convertToResponseDto(problem, images, repeats);
            } else {
                throw new UserNotAuthorizedException("해당 문제의 작성자가 아닙니다.");
            }
        } else {
            throw new ProblemNotFoundException("문제를 찾을 수 없습니다! problemId: " + problemId);
        }
    }

    @Override
    public List<ProblemResponseDto> findAllProblemsByUserId(Long userId) {
        Optional<User> user = userRepository.findById(userId);
        return user.map(u -> problemRepository.findAllByUserId(u.getId())
                        .stream()
                        .map(problem -> {
                            List<ImageData> images = fileUploadService.getProblemImages(problem.getId());
                            List<ProblemRepeat> repeats = getProblemRepeats(problem.getId());
                            return ProblemConverter.convertToResponseDto(problem, images, repeats); // 문제 데이터와 이미지 데이터를 DTO로 변환
                        })
                        .collect(Collectors.toList()))
                .orElse(Collections.emptyList());
    }

    @Override
    public List<ProblemResponseDto> findAllProblemsByFolderId(Long folderId) {

        return problemRepository.findAllByFolderId(folderId)
                .stream().map(problem -> {
                    List<ImageData> images = fileUploadService.getProblemImages(problem.getId());
                    List<ProblemRepeat> repeats = getProblemRepeats(problem.getId());
                    return ProblemConverter.convertToResponseDto(problem, images, repeats); // 문제 데이터와 이미지 데이터를 DTO로 변환
                }).collect(Collectors.toList());

    }

    @Override
    public Problem createProblem(Long userId) {

        Optional<User> optionalUserEntity = userRepository.findById(userId);
        if (optionalUserEntity.isPresent()) {
            User user = optionalUserEntity.get();

            Problem problem = Problem.builder()
                    .user(user)
                    .build();

            return problemRepository.save(problem);
        } else{
            throw new UserNotFoundException("유저를 찾을 수 없습니다!, userId : " + userId);
        }
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

                if (problemRegisterDto.getFolderId() != null) {
                    Optional<Folder> optionalFolder = folderRepository.findById(problemRegisterDto.getFolderId());
                    optionalFolder.ifPresent(problem::setFolder);
                }

                Problem savedProblem = problemRepository.save(problem);

                if (problemRegisterDto.getProblemImage() != null) {
                    String problemImageUrl = fileUploadService.uploadFileToS3(problemRegisterDto.getProblemImage(), savedProblem, ImageType.PROBLEM_IMAGE);
                    if (problemImageUrl != null) {

                        log.info("isProcess: " + problemRegisterDto.isProcess());
                        if(problemRegisterDto.isProcess()){
                            problemRegisterDto.initColorsList();
                            ImageProcessRegisterDto imageProcessRegisterDto = ImageProcessRegisterDto.builder()
                                    .fullUrl(problemImageUrl)
                                    .colorsList(problemRegisterDto.getColorsList())
                                    .build();

                            String processImageUrl = fileUploadService.saveAndGetProcessImageUrl(imageProcessRegisterDto, savedProblem, ImageType.PROCESS_IMAGE);
                        } else{
                            String processImageUrl = fileUploadService.uploadFileToS3(problemRegisterDto.getProblemImage(), savedProblem, ImageType.PROCESS_IMAGE);
                        }
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
                throw new UserNotFoundException("유저를 찾을 수 없습니다!, userId : " + userId);
            }
        } catch (Exception e) {
            e.printStackTrace();
            Sentry.captureException(e);
            return false;
        }
    }

    @Override
    public boolean saveProblemV2(Long userId, ProblemRegisterDtoV2 problemRegisterDto) {
        Optional<User> optionalUserEntity = userRepository.findById(userId);

        try {
            if (optionalUserEntity.isPresent()) {
                User user = optionalUserEntity.get();
                Problem problem;

                Optional<Problem> optionalProblem = problemRepository.findById(problemRegisterDto.getProblemId());
                problem = optionalProblem.orElseGet(() -> Problem.builder()
                        .build());

                problem.setUser(user);
                problem.setMemo(problemRegisterDto.getMemo());
                problem.setReference(problemRegisterDto.getReference());
                problem.setAnalysis(problemRegisterDto.getAnalysis());
                problem.setTemplateType(TemplateType.valueOf(problemRegisterDto.getTemplateType()));
                problem.setSolvedAt(problemRegisterDto.getSolvedAt());

                if (problemRegisterDto.getFolderId() != null) {
                    Optional<Folder> optionalFolder = folderRepository.findById(problemRegisterDto.getFolderId());
                    optionalFolder.ifPresent(problem::setFolder);
                }

                if(problemRegisterDto.getProblemImageUrl() != null){
                    fileUploadService.saveImageData(problemRegisterDto.getProblemImageUrl(), problem, ImageType.PROBLEM_IMAGE);
                }

                if (problemRegisterDto.getAnswerImage() != null) {
                    String answerImageUrl = fileUploadService.uploadFileToS3(problemRegisterDto.getAnswerImage(), problem, ImageType.ANSWER_IMAGE);
                }

                if (problemRegisterDto.getSolveImage() != null) {
                    String solveImageUrl = fileUploadService.uploadFileToS3(problemRegisterDto.getSolveImage(), problem, ImageType.SOLVE_IMAGE);
                }

                if(problemRegisterDto.getProcessImageUrl() != null){
                    fileUploadService.saveImageData(problemRegisterDto.getProcessImageUrl(), problem, ImageType.PROCESS_IMAGE);
                }

                return true;
            } else {
                throw new UserNotFoundException("유저를 찾을 수 없습니다!, userId : " + userId);
            }
        } catch (Exception e) {
            e.printStackTrace();
            Sentry.captureException(e);
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

                    if (problemRegisterDto.getFolderId() != null) {
                        Optional<Folder> optionalFolder = folderRepository.findById(problemRegisterDto.getFolderId());
                        optionalFolder.ifPresent(problem::setFolder);
                    }

                    if (problemRegisterDto.getProblemImage() != null) {
                        String problemImageUrl = fileUploadService.updateImage(problemRegisterDto.getProblemImage(), problem, ImageType.PROBLEM_IMAGE);

                        log.info("isProcess: " + problemRegisterDto.isProcess());
                        if(problemRegisterDto.isProcess()){
                            problemRegisterDto.initColorsList();
                            ImageProcessRegisterDto imageProcessRegisterDto = ImageProcessRegisterDto.builder()
                                    .fullUrl(problemImageUrl)
                                    .colorsList(problemRegisterDto.getColorsList())
                                    .build();

                            String processImageUrl = fileUploadService.saveAndGetProcessImageUrl(imageProcessRegisterDto, problem, ImageType.PROCESS_IMAGE);
                        } else{
                            String processImageUrl = fileUploadService.uploadFileToS3(problemRegisterDto.getProblemImage(), problem, ImageType.PROCESS_IMAGE);
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
                    Sentry.captureException(e);
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

                List<ProblemRepeat> problemRepeats = getProblemRepeats(problemId);
                problemRepeatRepository.deleteAll(problemRepeats);

                problemRepository.delete(problem);
            } else {
                throw new UserNotAuthorizedException("문제 작성자와 유저가 불일치합니다!");
            }
        } else {
            throw new ProblemNotFoundException("문제를 찾을 수 없습니다! problemId: " + problemId);
        }
    }

    @Override
    public void deleteUserProblems(Long userId) {
        List<Problem> problemList = problemRepository.findAllByUserId(userId);
        problemList.forEach(problem -> {
            Long problemId = problem.getId();
            List<ImageData> images = fileUploadService.getProblemImages(problemId);
            images.forEach(fileUploadService::deleteImage);

            List<ProblemRepeat> problemRepeats = getProblemRepeats(problemId);
            problemRepeatRepository.deleteAll(problemRepeats);
            problemRepository.delete(problem);
        });
    }

    @Override
    public List<ProblemRepeat> getProblemRepeats(Long problemId){
        return problemRepeatRepository.findAllByProblemId(problemId);
    }

    @Override
    public void addRepeatCount(Long problemId) {
        Optional<Problem> optionalProblem = problemRepository.findById(problemId);

        if (optionalProblem.isPresent()) {
            Problem problem = optionalProblem.get();
            ProblemRepeat problemRepeat = ProblemRepeat.builder()
                    .problem(problem)
                    .build();

            problemRepeatRepository.save(problemRepeat);
        } else {
            throw new ProblemNotFoundException("문제를 찾을 수 없습니다! problemId: " + problemId);
        }
    }
}