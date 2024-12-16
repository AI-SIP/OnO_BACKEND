package com.aisip.OnO.backend.service;

import com.aisip.OnO.backend.Dto.Folder.FolderResponseDto;
import com.aisip.OnO.backend.Dto.Folder.FolderThumbnailResponseDto;
import com.aisip.OnO.backend.Dto.Problem.ProblemResponseDto;
import com.aisip.OnO.backend.converter.FolderConverter;
import com.aisip.OnO.backend.entity.Folder;
import com.aisip.OnO.backend.entity.Problem.Problem;
import com.aisip.OnO.backend.entity.User.User;
import com.aisip.OnO.backend.exception.FolderNotFoundException;
import com.aisip.OnO.backend.exception.ProblemNotFoundException;
import com.aisip.OnO.backend.exception.UserNotFoundException;
import com.aisip.OnO.backend.repository.FolderRepository;
import com.aisip.OnO.backend.repository.ProblemRepository;
import com.aisip.OnO.backend.repository.UserRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class FolderServiceImpl implements FolderService {

    private final UserRepository userRepository;

    private final ProblemService problemService;
    private final ProblemRepository problemRepository;

    private final FolderRepository folderRepository;

    @Override
    public List<FolderResponseDto> findAllFolders(Long userId) {

        List<Folder> folders = folderRepository.findAllByUserId(userId);
        if(folders.isEmpty()){
            return List.of(findRootFolder(userId));
        }

        return folders.stream()
                .map(this::getFolderResponseDto).toList();
    }

    @Override
    public FolderResponseDto findFolder(Long userId, Long folderId) {
        Optional<Folder> optionalFolder = folderRepository.findById(folderId);

        return optionalFolder.map(this::getFolderResponseDto).orElse(null);
    }

    @Override
    public FolderResponseDto findRootFolder(Long userId) {

        Optional<Folder> optionalRootFolder = folderRepository.findByUserIdAndParentFolderIsNull(userId);

        if (optionalRootFolder.isPresent()) {

            Folder rootFolder = optionalRootFolder.get();
            List<Problem> noFolderProblems = problemRepository.findAllByUserIdAndFolderIsNull(userId);

            for (Problem problem : noFolderProblems) {
                problem.setFolder(optionalRootFolder.get());
                problemRepository.save(problem);
            }

            log.info("find root folder id: " + optionalRootFolder.get().getId());
            return getFolderResponseDto(rootFolder);
        } else {
            log.info("create root folder for userId: " + userId);
            return createRootFolder(userId, "책장");
        }
    }

    @Override
    public FolderResponseDto createRootFolder(Long userId, String folderName) {
        Optional<User> optionalUser = userRepository.findById(userId);
        if (optionalUser.isPresent()) {
            Folder rootFolder = Folder.builder().name(folderName).user(optionalUser.get()).build();

            folderRepository.save(rootFolder);

            List<Problem> noFolderProblems = problemRepository.findAllByUserIdAndFolderIsNull(userId);

            for (Problem problem : noFolderProblems) {
                problem.setFolder(rootFolder);
                problemRepository.save(problem);
            }

            return getFolderResponseDto(rootFolder);
        }

        throw new UserNotFoundException("유저를 찾을 수 없습니다!, userId : " + userId);
    }

    @Override
    public FolderResponseDto createFolder(Long userId, String folderName, Long parentFolderId) {
        Optional<User> optionalUser = userRepository.findById(userId);

        log.info("folderName: " + folderName + " parentFolderId : " + parentFolderId);

        if (optionalUser.isPresent()) {
            Folder folder = Folder.builder()
                    .name(folderName)
                    .user(optionalUser.get())
                    .build();

            if (parentFolderId != null) {
                Optional<Folder> parentFolder = folderRepository.findById(parentFolderId);
                parentFolder.ifPresent(folder::setParentFolder);
            }

            folderRepository.save(folder);

            return getFolderResponseDto(folder);
        }

        throw new UserNotFoundException("유저를 찾을 수 없습니다!, userId : " + userId);
    }

    @Override
    public FolderResponseDto getFolderResponseDto(Folder folder) {

        List<ProblemResponseDto> problemResponseDtoList = problemService.findAllProblemsByFolderId(folder.getId());

        return FolderConverter.convertToResponseDto(folder, problemResponseDtoList);
    }

    @Override
    public List<FolderThumbnailResponseDto> findFolderThumbnailsTreeByUserId(Long userId) {

        List<Folder> folders = folderRepository.findAllByUserId(userId);

        if (folders.isEmpty()) {
            return new ArrayList<>();
        }

        return folders.stream().map(FolderConverter::convertToThumbnailResponseDto).toList();
    }

    @Override
    public FolderResponseDto updateFolder(Long userId, Long folderId, String folderName, Long parentFolderId) {
        Optional<User> optionalUser = userRepository.findById(userId);
        if (optionalUser.isPresent()) {
            Optional<Folder> optionalFolder = folderRepository.findById(folderId);

            log.info("folderName: " + folderName + " , parentFolderId : " + parentFolderId);

            if (optionalFolder.isPresent()) {
                Folder folder = optionalFolder.get();

                if (folderName != null) {
                    folder.setName(folderName);
                }

                if (parentFolderId != null && !(parentFolderId.equals(folderId))) {
                    Optional<Folder> optionalParentFolder = folderRepository.findById(parentFolderId);
                    optionalParentFolder.ifPresent(folder::setParentFolder);
                }

                folderRepository.save(folder);

                return getFolderResponseDto(folder);
            }
        }

        throw new UserNotFoundException("유저를 찾을 수 없습니다!, userId : " + userId);
    }

    @Override
    public FolderResponseDto updateProblemPath(Long userId, Long problemId, Long folderId) {

        Optional<User> optionalUser = userRepository.findById(userId);
        Optional<Problem> optionalProblem = problemRepository.findById(problemId);
        Optional<Folder> optionalFolder = folderRepository.findById(folderId);

        if (optionalUser.isPresent()) {
            if (optionalProblem.isPresent() && optionalFolder.isPresent()) {
                Problem problem = optionalProblem.get();
                Folder folder = optionalFolder.get();

                problem.setFolder(folder);
                problemRepository.save(problem);

                return getFolderResponseDto(folder);
            }

            throw new ProblemNotFoundException("문제를 찾을 수 없습니다!, problemId: " + problemId);
        }

        throw new UserNotFoundException("유저를 찾을 수 없습니다!, userId : " + userId);
    }

    @Override
    public FolderResponseDto deleteFolder(Long userId, Long folderId) {
        Optional<Folder> optionalFolder = folderRepository.findById(folderId);

        if (optionalFolder.isPresent() && optionalFolder.get().getUser().getId().equals(userId)) {
            Folder folder = optionalFolder.get();
            Long parentFolderId = folder.getParentFolder().getId();

            // 재귀적으로 하위 폴더를 삭제
            deleteSubFolders(userId, folder);
            folderRepository.flush();

            if (parentFolderId != null) {
                log.info("Folder and its subfolders deleted successfully for folderId: " + folderId);
                folderRepository.deleteById(folderId);

                log.info("root folder id: " + parentFolderId);
                return getFolderResponseDto(folder);
            } else {
                throw new FolderNotFoundException("메인 폴더는 삭제할 수 없습니다.");
            }

        } else {
            throw new FolderNotFoundException("폴더를 찾을 수 없습니다!, folderId: " + folderId);
        }
    }

    private void deleteSubFolders(Long userId, Folder folder) {
        // 하위 폴더들을 먼저 삭제
        if (folder.getSubFolders() != null && !folder.getSubFolders().isEmpty()) {
            for (Folder subFolder : folder.getSubFolders()) {
                deleteSubFolders(userId, subFolder); // 재귀적으로 하위 폴더 삭제
            }
        }

        if (folder.getProblems() != null && !folder.getProblems().isEmpty()) {
            for (Problem problem : folder.getProblems()) {
                problemService.deleteProblem(userId, problem.getId());
            }
        }

        folder.getSubFolders().clear();
        folderRepository.delete(folder);
    }

    @Override
    public void deleteAllUserFolder(Long userId) {

        List<Folder> folders = folderRepository.findAllByUserId(userId);

        folderRepository.deleteAll(folders);
    }
}
