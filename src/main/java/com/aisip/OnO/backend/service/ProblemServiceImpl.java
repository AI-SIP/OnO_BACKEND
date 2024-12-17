package com.aisip.OnO.backend.service;

import com.aisip.OnO.backend.Dto.Problem.ProblemRegisterDto;
import com.aisip.OnO.backend.entity.Folder;
import com.aisip.OnO.backend.entity.Problem.ProblemRepeat;
import com.aisip.OnO.backend.entity.Problem.TemplateType;
import com.aisip.OnO.backend.entity.User.User;
import com.aisip.OnO.backend.repository.FolderRepository;
import com.aisip.OnO.backend.repository.ProblemRepeatRepository;
import com.aisip.OnO.backend.Dto.Problem.ProblemResponseDto;
import com.aisip.OnO.backend.converter.ProblemConverter;
import com.aisip.OnO.backend.entity.Image.ImageData;
import com.aisip.OnO.backend.entity.Image.ImageType;
import com.aisip.OnO.backend.entity.Problem.Problem;
import com.aisip.OnO.backend.exception.ProblemNotFoundException;
import com.aisip.OnO.backend.exception.UserNotAuthorizedException;
import com.aisip.OnO.backend.repository.ProblemRepository;
import io.sentry.Sentry;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class ProblemServiceImpl implements ProblemService {

    private final UserService userService;
    private final ProblemRepository problemRepository;

    private final ProblemRepeatRepository problemRepeatRepository;

    private final FolderRepository folderRepository;
    private final FileUploadService fileUploadService;


    @Override
    public ProblemResponseDto findProblem(Long userId, Long problemId) {
        Problem problem = getProblemEntity(problemId);

        if (problem.getUser().getId().equals(userId)) {
            return convertToProblemResponse(problem);
        } else {
            throw new UserNotAuthorizedException("해당 문제의 작성자가 아닙니다.");
        }
    }

    @Override
    public Problem getProblemEntity(Long problemId) {
        Optional<Problem> optionalProblem = problemRepository.findById(problemId);
        if (optionalProblem.isPresent()) {
            return optionalProblem.get();
        } else {
            throw new ProblemNotFoundException("문제를 찾을 수 없습니다! problemId: " + problemId);
        }
    }

    @Override
    public void saveProblemEntity(Problem problem) {
        problemRepository.save(problem);
    }

    @Override
    public ProblemResponseDto convertToProblemResponse(Problem problem){

        List<ImageData> images = fileUploadService.getProblemImages(problem.getId());
        List<ProblemRepeat> repeats = getProblemRepeats(problem.getId());

        return ProblemConverter.convertToResponseDto(problem, images, repeats);
    }

    @Override
    public List<ProblemResponseDto> findUserProblems(Long userId) {
        User user = userService.getUserEntity(userId);
        return problemRepository.findAllByUserId(user.getId())
                .stream()
                .map(this::convertToProblemResponse)
                .collect(Collectors.toList());
    }

    @Override
    public List<ProblemResponseDto> findAllProblems() {
        List<Problem> problems = problemRepository.findAll();
        return problems.stream().map(this::convertToProblemResponse).collect(Collectors.toList());
    }

    @Override
    public List<ProblemResponseDto> findAllProblemsByFolderId(Long folderId) {

        return problemRepository.findAllByFolderId(folderId)
                .stream().map(this::convertToProblemResponse).collect(Collectors.toList());

    }

    @Override
    public Problem createProblem(Long userId) {
        User user = userService.getUserEntity(userId);

        Problem problem = Problem.builder()
                .user(user)
                .build();

        return problemRepository.save(problem);
    }

    @Override
    public boolean createProblem(Long userId, ProblemRegisterDto problemRegisterDto) {
        try {
            User user = userService.getUserEntity(userId);
            Problem problem = Problem.builder().build();

            if(problemRegisterDto.getProblemId() != null){
                Optional<Problem> optionalProblem = problemRepository.findById(problemRegisterDto.getProblemId());

                if(optionalProblem.isPresent()){
                    problem = optionalProblem.get();
                }
            }

            problem.setUser(user);
            problem.setMemo(problemRegisterDto.getMemo());
            problem.setReference(problemRegisterDto.getReference());
            problem.setTemplateType(TemplateType.SIMPLE_TEMPLATE);
            problem.setSolvedAt(problemRegisterDto.getSolvedAt() != null ? problemRegisterDto.getSolvedAt() : LocalDateTime.now());

            if (problemRegisterDto.getFolderId() != null) {
                Optional<Folder> optionalFolder = folderRepository.findById(problemRegisterDto.getFolderId());
                optionalFolder.ifPresent(problem::setFolder);
            }

            if(problemRegisterDto.getProblemImage() != null){
                fileUploadService.uploadFileToS3(problemRegisterDto.getProblemImage(), problem, ImageType.PROBLEM_IMAGE);
            }

            if (problemRegisterDto.getAnswerImage() != null) {
                fileUploadService.uploadFileToS3(problemRegisterDto.getAnswerImage(), problem, ImageType.ANSWER_IMAGE);
            }

            problemRepository.save(problem);

            return true;
        } catch (Exception e) {
            e.printStackTrace();
            Sentry.captureException(e);
            return false;
        }
    }

    @Override
    public boolean updateProblem(Long userId, ProblemRegisterDto problemRegisterDto) {
        Problem problem = getProblemEntity(problemRegisterDto.getProblemId());
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

            return true;
        } else {
            return false;
        }
    }

    @Override
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
    public void addRepeatCount(Long problemId, MultipartFile solveImage) throws IOException {
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

    @Override
    public Long getTemplateTypeCount(TemplateType templateType){
        if(templateType == null){
            return problemRepository.countAllByTemplateTypeIsNull();
        } else{
            return problemRepository.countAllByTemplateType(templateType);
        }
    }
}