package com.aisip.OnO.backend.problem.service;

import com.aisip.OnO.backend.problem.entity.ProblemSolve;
import com.aisip.OnO.backend.problem.dto.ProblemRegisterDto;
import com.aisip.OnO.backend.problem.entity.ProblemImageData;
import com.aisip.OnO.backend.problem.entity.ProblemTemplateType;
import com.aisip.OnO.backend.fileupload.service.FileUploadService;
import com.aisip.OnO.backend.user.dto.UserResponseDto;
import com.aisip.OnO.backend.user.entity.User;
import com.aisip.OnO.backend.problem.repository.FolderRepository;
import com.aisip.OnO.backend.problem.repository.ProblemSolveRepository;
import com.aisip.OnO.backend.problem.dto.ProblemResponseDto;
import com.aisip.OnO.backend.problem.ProblemConverter;
import com.aisip.OnO.backend.problem.entity.ProblemImageType;
import com.aisip.OnO.backend.problem.entity.Problem;
import com.aisip.OnO.backend.problem.exception.ProblemNotFoundException;
import com.aisip.OnO.backend.problem.repository.ProblemRepository;
import com.aisip.OnO.backend.user.service.UserService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class ProblemService {

    private final UserService userService;
    private final ProblemRepository problemRepository;

    private final ProblemSolveRepository problemSolveRepository;

    private final FolderRepository folderRepository;
    private final FileUploadService fileUploadService;


    public ProblemResponseDto findProblem(Long userId, Long problemId) {
        Problem problem = getProblemEntity(problemId);

        return convertToProblemResponse(problem);
    }

    public Problem getProblemEntity(Long problemId) {
        return problemRepository.findById(problemId)
                .orElseThrow(() -> new ProblemNotFoundException(problemId));
    }

    public void saveProblemEntity(Problem problem) {
        problemRepository.save(problem);
    }

    public ProblemResponseDto convertToProblemResponse(Problem problem){

        List<ProblemImageData> images = fileUploadService.getProblemImages(problem.getId());
        List<ProblemSolve> repeats = getProblemRepeats(problem.getId());

        return ProblemConverter.convertToResponseDto(problem, images, repeats);
    }

    public List<ProblemResponseDto> findUserProblems(Long userId) {
        User user = userService.findUserEntity(userId);
        return problemRepository.findAllByUserId(user.getId())
                .stream()
                .map(this::convertToProblemResponse)
                .collect(Collectors.toList());
    }

    public List<ProblemResponseDto> findAllProblems() {
        List<Problem> problems = problemRepository.findAll();
        return problems.stream().map(this::convertToProblemResponse).collect(Collectors.toList());
    }

    public List<ProblemResponseDto> findAllProblemsByFolderId(Long folderId) {

        return problemRepository.findAllByFolderId(folderId)
                .stream().map(this::convertToProblemResponse).collect(Collectors.toList());

    }

    public Problem createProblem(Long userId) {
        User user = userService.findUserEntity(userId);

        Problem problem = Problem.builder()
                .user(user)
                .build();

        return problemRepository.save(problem);
    }

    public void createProblem(Long userId, ProblemRegisterDto problemRegisterDto) {
        User user = userService.findUserEntity(userId);

        Problem problem = Optional.ofNullable(problemRegisterDto.getProblemId())
                .flatMap(problemRepository::findById)
                .orElseGet(() -> Problem.builder().build());

        problem.setUser(user);
        problem.setMemo(problemRegisterDto.getMemo());
        problem.setReference(problemRegisterDto.getReference());
        problem.setProblemTemplateType(ProblemTemplateType.SIMPLE_TEMPLATE);
        problem.setSolvedAt(Optional.ofNullable(problemRegisterDto.getSolvedAt()).orElse(LocalDateTime.now()));

        Optional.ofNullable(problemRegisterDto.getFolderId())
                .flatMap(folderRepository::findById)
                .ifPresent(problem::setFolder);

        uploadProblemImages(problemRegisterDto, problem);

        problemRepository.save(problem);
    }

    public void updateProblem(Long userId, ProblemRegisterDto problemRegisterDto) {
        Problem problem = getProblemEntity(problemRegisterDto.getProblemId());

        if (!problem.getUser().getId().equals(userId)) {
            throw new UserNotAuthorizedException("해당 문제를 수정할 권한이 없습니다.");
        }

        Optional.ofNullable(problemRegisterDto.getSolvedAt()).ifPresent(problem::setSolvedAt);
        Optional.ofNullable(problemRegisterDto.getReference()).ifPresent(problem::setReference);
        Optional.ofNullable(problemRegisterDto.getMemo()).ifPresent(problem::setMemo);

        Optional.ofNullable(problemRegisterDto.getFolderId())
                .flatMap(folderRepository::findById)
                .ifPresent(problem::setFolder);

        updateProblemImages(problemRegisterDto, problem);
    }

    private void uploadProblemImages(ProblemRegisterDto dto, Problem problem) {
        Optional.ofNullable(dto.getProblemImage())
                .ifPresent(image -> fileUploadService.uploadFileToS3(image, problem, ProblemImageType.PROBLEM_IMAGE));

        Optional.ofNullable(dto.getAnswerImage())
                .ifPresent(image -> fileUploadService.uploadFileToS3(image, problem, ProblemImageType.ANSWER_IMAGE));
    }

    private void updateProblemImages(ProblemRegisterDto dto, Problem problem) {
        Optional.ofNullable(dto.getSolveImage())
                .ifPresent(image -> fileUploadService.updateImage(image, problem, ProblemImageType.SOLVE_IMAGE));

        Optional.ofNullable(dto.getAnswerImage())
                .ifPresent(image -> fileUploadService.updateImage(image, problem, ProblemImageType.ANSWER_IMAGE));
    }

    public void deleteProblem(Long userId, Long problemId) {
        Problem problem = getProblemEntity(problemId);
        if (problem.getUser().getId().equals(userId)) {

            List<ProblemImageData> images = fileUploadService.getProblemImages(problemId);
            images.forEach(fileUploadService::deleteImage);

            List<ProblemSolve> problemSolves = getProblemRepeats(problemId);
            problemSolveRepository.deleteAll(problemSolves);


            problemRepository.delete(problem);
        } else {
            throw new UserNotAuthorizedException("문제 작성자와 유저가 불일치합니다!");
        }
    }

    public void deleteProblemList(Long userId, List<Long> problemIdList) {
        problemIdList.forEach(problemId -> {
            deleteProblem(userId, problemId);
        });
    }

    public void deleteUserProblems(Long userId) {
        List<Problem> problemList = problemRepository.findAllByUserId(userId);
        problemList.forEach(problem -> {
            Long problemId = problem.getId();
            List<ProblemImageData> images = fileUploadService.getProblemImages(problemId);
            images.forEach(fileUploadService::deleteImage);

            List<ProblemSolve> problemSolves = getProblemRepeats(problemId);
            problemSolveRepository.deleteAll(problemSolves);

            problemRepository.delete(problem);
        });
    }

    public Long getProblemCountByUser(Long userId) {
        return problemRepository.countByUserId(userId);
    }

    public List<Long> getAllUsersProblemCount(List<UserResponseDto> userList) {
        return userList.stream().map(user -> getProblemCountByUser(user.userId())).collect(Collectors.toList());
    }

    public List<ProblemSolve> getProblemRepeats(Long problemId){
        return problemSolveRepository.findAllByProblemId(problemId);
    }

    public void addRepeatCount(Long problemId, MultipartFile solveImage) {
        Problem problem = getProblemEntity(problemId);
        ProblemSolve problemSolve = ProblemSolve.builder()
                .problem(problem)
                .build();

        if(solveImage != null){
            String solveImageUrl = fileUploadService.uploadFileToS3(solveImage, problem, ProblemImageType.SOLVE_IMAGE);
            problemSolve.setSolveImageUrl(solveImageUrl);
        }

        problemSolveRepository.save(problemSolve);
    }

    public Long getTemplateTypeCount(ProblemTemplateType problemTemplateType){
        if(problemTemplateType == null){
            return problemRepository.countAllByTemplateTypeIsNull();
        } else{
            return problemRepository.countAllByTemplateType(problemTemplateType);
        }
    }
}