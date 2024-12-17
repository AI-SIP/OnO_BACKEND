package com.aisip.OnO.backend.service;

import com.aisip.OnO.backend.Dto.Folder.FolderResponseDto;
import com.aisip.OnO.backend.Dto.Folder.FolderThumbnailResponseDto;
import com.aisip.OnO.backend.Dto.Problem.ProblemResponseDto;
import com.aisip.OnO.backend.converter.FolderConverter;
import com.aisip.OnO.backend.entity.Folder;
import com.aisip.OnO.backend.entity.Problem.Problem;
import com.aisip.OnO.backend.entity.User.User;
import com.aisip.OnO.backend.exception.FolderNotFoundException;
import com.aisip.OnO.backend.repository.FolderRepository;
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

    private final UserService userService;

    private final ProblemService problemService;

    private final FolderRepository folderRepository;

    @Override
    public Folder getFolderEntity(Long folderId) {
        Optional<Folder> optionalFolder = folderRepository.findById(folderId);

        if(optionalFolder.isPresent()){
            return optionalFolder.get();
        } else{
            throw new FolderNotFoundException("폴더를 찾을 수 없습니다!");
        }
    }

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
        return getFolderResponseDto(getFolderEntity(folderId));
    }

    @Override
    public FolderResponseDto findRootFolder(Long userId) {

        Optional<Folder> optionalRootFolder = folderRepository.findByUserIdAndParentFolderIsNull(userId);

        if (optionalRootFolder.isPresent()) {

            Folder rootFolder = optionalRootFolder.get();
            log.info("find root folder id: " + optionalRootFolder.get().getId());

            return getFolderResponseDto(rootFolder);
        } else {
            log.info("create root folder for userId: " + userId);
            return createRootFolder(userId, "책장");
        }
    }

    @Override
    public FolderResponseDto createRootFolder(Long userId, String folderName) {
        User user = userService.getUserEntity(userId);
        Folder rootFolder = Folder.builder().name(folderName).user(user).build();

        folderRepository.save(rootFolder);

        return getFolderResponseDto(rootFolder);
    }

    @Override
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

    @Override
    public FolderResponseDto getFolderResponseDto(Folder folder) {

        List<ProblemResponseDto> problemResponseDtoList = problemService.findAllProblemsByFolderId(folder.getId());

        return FolderConverter.convertToResponseDto(folder, problemResponseDtoList);
    }

    @Override
    public List<FolderThumbnailResponseDto> findAllFolderThumbnailsByUser(Long userId) {

        List<Folder> folders = folderRepository.findAllByUserId(userId);

        if (folders.isEmpty()) {
            return new ArrayList<>();
        }

        return folders.stream().map(FolderConverter::convertToThumbnailResponseDto).toList();
    }

    @Override
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

    @Override
    public FolderResponseDto updateProblemPath(Long userId, Long problemId, Long folderId) {

        User user = userService.getUserEntity(userId);
        Problem problem = problemService.getProblemEntity(problemId);
        Folder folder = getFolderEntity(folderId);

        problem.setFolder(folder);
        problemService.saveProblemEntity(problem);

        return findFolder(userId, folderId);
    }

    @Override
    public FolderResponseDto deleteFolder(Long userId, Long folderId) {
        Folder folder = getFolderEntity(folderId);
        Long parentFolderId = folder.getParentFolder().getId();

        // 재귀적으로 하위 폴더를 삭제
        deleteSubFolders(userId, folder);
        folderRepository.flush();

        if (parentFolderId != null) {
            log.info("Folder and its subfolders deleted successfully for folderId: " + folderId);
            folderRepository.deleteById(folderId);

            log.info("root folder id: " + parentFolderId);

            return findFolder(userId, parentFolderId);
        } else {
            throw new FolderNotFoundException("메인 폴더는 삭제할 수 없습니다.");
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
