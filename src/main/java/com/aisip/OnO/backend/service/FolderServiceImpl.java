package com.aisip.OnO.backend.service;

import com.aisip.OnO.backend.Dto.Folder.FolderResponseDto;
import com.aisip.OnO.backend.Dto.Folder.FolderThumbnailResponseDto;
import com.aisip.OnO.backend.Dto.Problem.ProblemResponseDto;
import com.aisip.OnO.backend.entity.Folder;
import com.aisip.OnO.backend.entity.Problem;
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
    public FolderResponseDto findRootFolder(Long userId) {

        Optional<Folder> optionalRootFolder = folderRepository.findByUserIdAndParentFolderIsNull(userId);

        if (optionalRootFolder.isPresent()) {
            log.info("find root folder id: " + optionalRootFolder.get().getId());
            return findFolder(userId, optionalRootFolder.get().getId());
        } else {
            log.info("create root folder for userId: " + userId);
            return createRootFolder(userId, "메인");
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

            return findFolder(userId, rootFolder.getId());
        }

        throw new UserNotFoundException("유저를 찾을 수 없습니다!");
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

            return findFolder(userId, folder.getId());
        }

        throw new UserNotFoundException("유저를 찾을 수 없습니다!");
    }

    @Override
    public FolderResponseDto findFolder(Long userId, Long folderId) {

        Optional<Folder> optionalFolder = folderRepository.findById(folderId);

        if (optionalFolder.isPresent() && optionalFolder.get().getUser().getId().equals(userId)) {

            Folder folder = optionalFolder.get();

            FolderThumbnailResponseDto parentFolder = null;
            if (folder.getParentFolder() != null) {
                parentFolder = FolderThumbnailResponseDto.builder()
                        .folderId(folder.getParentFolder().getId())
                        .folderName(folder.getParentFolder().getName())
                        .build();
            }


            List<FolderThumbnailResponseDto> subFolders = null;

            if (folder.getSubFolders() != null) {
                subFolders = folder.getSubFolders().stream().map(subFolder -> {
                    return FolderThumbnailResponseDto.builder()
                            .folderId(subFolder.getId())
                            .folderName(subFolder.getName())
                            .build();

                }).toList();
            }

            List<ProblemResponseDto> problems = problemService.findAllProblemsByFolderId(folderId);

            return FolderResponseDto.builder()
                    .folderId(folder.getId())
                    .folderName(folder.getName())
                    .parentFolder(parentFolder)
                    .subFolders(subFolders)
                    .problems(problems)
                    .updateAt(folder.getUpdatedAt())
                    .createdAt(folder.getCreatedAt())
                    .build();
        }

        throw new FolderNotFoundException("폴더를 찾을 수 없습니다!");
    }

    @Override
    public List<FolderThumbnailResponseDto> findAllFolderThumbnailsByUserId(Long userId) {

        List<Folder> folders = folderRepository.findAllByUserId(userId);

        if (folders != null) {
            return folders.stream().map(folder -> {
                return FolderThumbnailResponseDto.builder()
                        .folderId(folder.getId())
                        .folderName(folder.getName())
                        .build();
            }).toList();
        }

        return null;
    }

    @Override
    public FolderResponseDto updateFolder(Long userId, Long folderId, String folderName, Long parentFolderId) {
        Optional<User> optionalUser = userRepository.findById(userId);
        if (optionalUser.isPresent()) {
            Optional<Folder> optionalFolder = folderRepository.findById(folderId);

            if (optionalFolder.isPresent()) {
                Folder folder = optionalFolder.get();
                folder.setName(folderName);

                Optional<Folder> optionalParentFolder = folderRepository.findById(parentFolderId);
                Folder parentFolder = optionalParentFolder.orElse(null);

                folder.setParentFolder(parentFolder);

                folderRepository.save(folder);

                return findFolder(userId, folderId);
            }
        }

        throw new UserNotFoundException("유저를 찾을 수 없습니다!");
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

                return findFolder(userId, folderId);
            }

            throw new ProblemNotFoundException("문제를 찾을 수 없습니다!");
        }

        throw new UserNotFoundException("유저를 찾을 수 없습니다!");
    }

    @Override
    public FolderResponseDto deleteFolder(Long userId, Long folderId) {
        Optional<Folder> optionalFolder = folderRepository.findById(folderId);

        if (optionalFolder.isPresent() && optionalFolder.get().getUser().getId().equals(userId)) {
            Folder folder = optionalFolder.get();
            Long parentFolderId = folder.getParentFolder().getId();

            // 재귀적으로 하위 폴더를 삭제
            deleteSubFolders(folder);

            if (parentFolderId != null) {
                folderRepository.deleteById(folderId);
                log.info("Folder and its subfolders deleted successfully for folderId: " + folderId);

                return findFolder(userId, parentFolderId);
            } else {
                return findFolder(userId, folderId);
            }

        } else {
            throw new FolderNotFoundException("폴더를 찾을 수 없습니다!");
        }
    }

    private void deleteSubFolders(Folder folder) {
        // 하위 폴더들을 먼저 삭제
        if (folder.getSubFolders() != null && !folder.getSubFolders().isEmpty()) {
            for (Folder subFolder : folder.getSubFolders()) {
                deleteSubFolders(subFolder); // 재귀적으로 하위 폴더 삭제
                log.info("folderId: " + subFolder.getId() + " deleted");
                folderRepository.deleteById(subFolder.getId()); // 하위 폴더 삭제
            }
        }
    }

    @Override
    public void deleteAllUserFolder(Long userId) {

        List<Folder> folders = folderRepository.findAllByUserId(userId);

        folderRepository.deleteAll(folders);
    }
}
