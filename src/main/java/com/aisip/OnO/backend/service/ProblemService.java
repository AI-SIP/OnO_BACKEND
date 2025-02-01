package com.aisip.OnO.backend.service;

import com.aisip.OnO.backend.Dto.Problem.ProblemRegisterDto;
import com.aisip.OnO.backend.Dto.User.UserResponseDto;
import com.aisip.OnO.backend.entity.Problem.ProblemRepeat;
import com.aisip.OnO.backend.entity.Problem.TemplateType;
import com.aisip.OnO.backend.entity.User.User;
import com.aisip.OnO.backend.repository.Folder.FolderRepository;
import com.aisip.OnO.backend.repository.Repeat.ProblemRepeatRepository;
import com.aisip.OnO.backend.Dto.Problem.ProblemResponseDto;
import com.aisip.OnO.backend.converter.ProblemConverter;
import com.aisip.OnO.backend.entity.Image.ImageData;
import com.aisip.OnO.backend.entity.Image.ImageType;
import com.aisip.OnO.backend.entity.Problem.Problem;
import com.aisip.OnO.backend.exception.ProblemNotFoundException;
import com.aisip.OnO.backend.exception.UserNotAuthorizedException;
import com.aisip.OnO.backend.repository.Problem.ProblemRepository;
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

    private final ProblemRepeatRepository problemRepeatRepository;

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

        List<ImageData> images = fileUploadService.getProblemImages(problem.getId());
        List<ProblemRepeat> repeats = getProblemRepeats(problem.getId());

        return ProblemConverter.convertToResponseDto(problem, images, repeats);
    }

    public List<ProblemResponseDto> findUserProblems(Long userId) {
        User user = userService.getUserEntity(userId);
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
        User user = userService.getUserEntity(userId);

        Problem problem = Problem.builder()
                .user(user)
                .build();

        return problemRepository.save(problem);
    }

    public void createProblem(Long userId, ProblemRegisterDto problemRegisterDto) {
        User user = userService.getUserEntity(userId);

        Problem problem = Optional.ofNullable(problemRegisterDto.getProblemId())
                .flatMap(problemRepository::findById)
                .orElseGet(() -> Problem.builder().build());

        problem.setUser(user);
        problem.setMemo(problemRegisterDto.getMemo());
        problem.setReference(problemRegisterDto.getReference());
        problem.setTemplateType(TemplateType.SIMPLE_TEMPLATE);
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
                .ifPresent(image -> fileUploadService.uploadFileToS3(image, problem, ImageType.PROBLEM_IMAGE));

        Optional.ofNullable(dto.getAnswerImage())
                .ifPresent(image -> fileUploadService.uploadFileToS3(image, problem, ImageType.ANSWER_IMAGE));
    }

    private void updateProblemImages(ProblemRegisterDto dto, Problem problem) {
        Optional.ofNullable(dto.getSolveImage())
                .ifPresent(image -> fileUploadService.updateImage(image, problem, ImageType.SOLVE_IMAGE));

        Optional.ofNullable(dto.getAnswerImage())
                .ifPresent(image -> fileUploadService.updateImage(image, problem, ImageType.ANSWER_IMAGE));
    }

    public void deleteProblem(Long userId, Long problemId) {
        Problem problem = getProblemEntity(problemId);
        if (problem.getUser().getId().equals(userId)) {

            List<ImageData> images = fileUploadService.getProblemImages(problemId);
            images.forEach(fileUploadService::deleteImage);

            List<ProblemRepeat> problemRepeats = getProblemRepeats(problemId);
            problemRepeatRepository.deleteAll(problemRepeats);


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
            List<ImageData> images = fileUploadService.getProblemImages(problemId);
            images.forEach(fileUploadService::deleteImage);

            List<ProblemRepeat> problemRepeats = getProblemRepeats(problemId);
            problemRepeatRepository.deleteAll(problemRepeats);

            problemRepository.delete(problem);
        });
    }

    public Long getProblemCountByUser(Long userId) {
        return problemRepository.countByUserId(userId);
    }

    public List<Long> getAllUsersProblemCount(List<UserResponseDto> userList) {
        return userList.stream().map(user -> getProblemCountByUser(user.getUserId())).collect(Collectors.toList());
    }

    public List<ProblemRepeat> getProblemRepeats(Long problemId){
        return problemRepeatRepository.findAllByProblemId(problemId);
    }

    public void addRepeatCount(Long problemId, MultipartFile solveImage) {
        Problem problem = getProblemEntity(problemId);
        ProblemRepeat problemRepeat = ProblemRepeat.builder()
                .problem(problem)
                .build();

        if(solveImage != null){
            String solveImageUrl = fileUploadService.uploadFileToS3(solveImage, problem, ImageType.SOLVE_IMAGE);
            problemRepeat.setSolveImageUrl(solveImageUrl);
        }

        problemRepeatRepository.save(problemRepeat);
    }

    public Long getTemplateTypeCount(TemplateType templateType){
        if(templateType == null){
            return problemRepository.countAllByTemplateTypeIsNull();
        } else{
            return problemRepository.countAllByTemplateType(templateType);
        }
    }
}