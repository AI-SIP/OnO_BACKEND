package com.aisip.OnO.backend.problem.service;

import com.aisip.OnO.backend.problem.dto.FolderResponseDto;
import com.aisip.OnO.backend.problem.dto.ProblemResponseDto;
import com.aisip.OnO.backend.problem.FolderConverter;
import com.aisip.OnO.backend.problem.entity.Folder;
import com.aisip.OnO.backend.problem.entity.Problem;
import com.aisip.OnO.backend.practicenote.service.PracticeNoteService;
import com.aisip.OnO.backend.user.entity.User;
import com.aisip.OnO.backend.problem.exception.FolderNotFoundException;
import com.aisip.OnO.backend.problem.repository.FolderRepository;
import com.aisip.OnO.backend.user.service.UserService;
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
public class FolderService {

    private final UserService userService;

    private final ProblemService problemService;

    private final PracticeNoteService practiceNoteService;

    private final FolderRepository folderRepository;

    private Folder getFolderEntity(Long folderId) {
        return folderRepository.findById(folderId)
                .orElseThrow(() -> new FolderNotFoundException(folderId));
    }

    public List<FolderResponseDto> findAllFolders(Long userId) {
        List<Folder> folders = folderRepository.findAllByUserId(userId);
        return folders.isEmpty()
                ? List.of(findRootFolder(userId))
                : folders.stream().map(this::getFolderResponseDto).toList();
    }

    public FolderResponseDto findFolder(Long userId, Long folderId) {
        return getFolderResponseDto(getFolderEntity(folderId));
    }

    public FolderResponseDto findRootFolder(Long userId) {

        //folderRepository.findByUserIdAndParentFolderIsNull(userId);
        Optional<Folder> optionalRootFolder = folderRepository.findRootFolder(userId);

        if (optionalRootFolder.isPresent()) {

            Folder rootFolder = optionalRootFolder.get();
            log.info("find root folder id: " + optionalRootFolder.get().getId());

            return getFolderResponseDto(rootFolder);
        } else {
            log.info("create root folder for userId: " + userId);
            return createRootFolder(userId, "책장");
        }
    }

    public FolderResponseDto createRootFolder(Long userId, String folderName) {
        User user = userService.getUserEntity(userId);
        Folder rootFolder = Folder.builder().name(folderName).user(user).build();

        folderRepository.save(rootFolder);

        return getFolderResponseDto(rootFolder);
    }

    public FolderResponseDto createFolder(Long userId, String folderName, Long parentFolderId) {
        log.info("create folder for folderName: " + folderName + " parentFolderId : " + parentFolderId);

        User user = userService.getUserEntity(userId);
        Folder folder = Folder.builder()
                .name(folderName)
                .user(user)
                .build();

        if (parentFolderId != null) {
            Optional<Folder> parentFolder = folderRepository.findById(parentFolderId);
            parentFolder.ifPresent(folder::setParentFolder);
        }

        return getFolderResponseDto(folderRepository.save(folder));
    }

    public FolderResponseDto getFolderResponseDto(Folder folder) {

        List<ProblemResponseDto> problemResponseDtoList = problemService.findAllProblemsByFolderId(folder.getId());

        return FolderConverter.convertToResponseDto(folder, problemResponseDtoList);
    }

    public FolderResponseDto updateFolder(Long userId, Long folderId, String folderName, Long parentFolderId) {
        User user = userService.getUserEntity(userId);
        Folder folder = getFolderEntity(folderId);
        Optional<Folder> optionalFolder = folderRepository.findById(folderId);

        log.info("update folderId: " + folderId + " , parentFolderId : " + parentFolderId);

        if (folderName != null) {
            folder.setName(folderName);
        }

        if (parentFolderId != null && !(parentFolderId.equals(folderId))) {
            Folder parentFolder = getFolderEntity(parentFolderId);
            folder.setParentFolder(parentFolder);
        }

        return getFolderResponseDto(folderRepository.save(folder));
    }

    public FolderResponseDto updateProblemPath(Long userId, Long problemId, Long folderId) {

        User user = userService.getUserEntity(userId);
        Problem problem = problemService.getProblemEntity(problemId);
        Folder folder = getFolderEntity(folderId);

        problem.setFolder(folder);
        problemService.saveProblemEntity(problem);

        return findFolder(userId, folderId);
    }

    public void deleteFolder(Long userId, Long folderId) {
        Folder folder = getFolderEntity(folderId);
        Long parentFolderId = folder.getParentFolder().getId();

        // 재귀적으로 하위 폴더를 삭제
        deleteSubFolders(userId, folder);
        folderRepository.flush();

        if (parentFolderId != null) {
            log.info("Folder and its subfolders deleted successfully for folderId: " + folderId);
            folderRepository.deleteById(folderId);

            log.info("root folder id: " + parentFolderId);
        } else {
            throw new FolderNotFoundException("메인 폴더는 삭제할 수 없습니다.");
        }
    }

    public void deleteFolderList(Long userId, List<Long> folderIdList) {
        folderIdList.forEach(folderId -> {
            deleteFolder(userId, folderId);
        });
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
                practiceNoteService.deleteProblemsFromAllPractice(List.of(problem.getId()));
                problemService.deleteProblem(userId, problem.getId());
            }
        }

        folder.getSubFolders().clear();
        folderRepository.delete(folder);
    }

    public void deleteAllUserFolder(Long userId) {
        List<Folder> folders = folderRepository.findAllByUserId(userId);

        folderRepository.deleteAll(folders);
    }
}
