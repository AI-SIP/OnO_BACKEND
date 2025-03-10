package com.aisip.OnO.backend.problem.service;

import com.aisip.OnO.backend.common.exception.ApplicationException;
import com.aisip.OnO.backend.problem.dto.FolderRegisterDto;
import com.aisip.OnO.backend.problem.dto.FolderResponseDto;
import com.aisip.OnO.backend.problem.dto.FolderThumbnailResponseDto;
import com.aisip.OnO.backend.problem.dto.ProblemRegisterDto;
import com.aisip.OnO.backend.problem.entity.Folder;
import com.aisip.OnO.backend.problem.exception.FolderErrorCase;
import com.aisip.OnO.backend.problem.repository.folder.FolderRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class FolderService {

    private final FolderRepository folderRepository;

    private final ProblemService problemService;

    public FolderResponseDto findFolder(Long folderId) {
        Folder folder = folderRepository.findWithAllData(folderId)
                .orElseThrow(() -> new ApplicationException(FolderErrorCase.FOLDER_NOT_FOUND));

        return FolderResponseDto.from(folder);
    }

    public Folder findFolderEntity(Long folderId) {
        return folderRepository.findById(folderId)
                .orElseThrow(() -> new ApplicationException(FolderErrorCase.FOLDER_NOT_FOUND));
    }

    public List<FolderThumbnailResponseDto> findAllFolderThumbnails(Long userId) {
        List<Folder> folderList = folderRepository.findAllByUserId(userId);

        return folderList.isEmpty()
                ? List.of()
                : folderList.stream().map(FolderThumbnailResponseDto::from).collect(Collectors.toList());
    }

    public List<FolderResponseDto> findAllFolders(Long userId) {
        List<Folder> folders = folderRepository.findAllByUserId(userId);
        return folders.isEmpty()
                ? List.of(findRootFolder(userId))
                : folders.stream().map(FolderResponseDto::from).toList();
    }

    public FolderResponseDto findRootFolder(Long userId) {
        return folderRepository.findRootFolder(userId)
                .map(rootFolder -> {
                    log.info("find root folder id: {}", rootFolder.getId());
                    return FolderResponseDto.from(rootFolder);
                })
                .orElseGet(() -> {
                    log.info("create root folder for userId: {}", userId);
                    return createRootFolder(userId);
                });
    }

    public FolderResponseDto createRootFolder(Long userId) {
        FolderRegisterDto folderRegisterDto = new FolderRegisterDto(
                "메인",
                null,
                null
        );

        Folder rootFolder = Folder.from(folderRegisterDto, null, userId);
        folderRepository.save(rootFolder);

        return FolderResponseDto.from(rootFolder);
    }

    public void createFolder(FolderRegisterDto folderRegisterDto, Long userId) {
        Folder parentFolder = findFolderEntity(folderRegisterDto.parentFolderId());

        Folder folder = Folder.from(folderRegisterDto, parentFolder, userId);
        folderRepository.save(folder);
    }

    public void registerProblemToFolder(ProblemRegisterDto problemRegisterDto, Long userId) {
        Folder folder = findFolderEntity(problemRegisterDto.folderId());
        problemService.registerProblem(problemRegisterDto, folder, userId);
    }

    public void updateFolder(FolderRegisterDto folderRegisterDto, Long userId) {
        Folder folder = findFolderEntity(folderRegisterDto.folderId());

        folder.updateFolderInfo(folderRegisterDto);

        if (folderRegisterDto.parentFolderId() != null) {
            Folder parentFolder = findFolderEntity(folderRegisterDto.parentFolderId());
            folder.updateParentFolder(parentFolder);
        }
    }

    public void updateFolderProblem(ProblemRegisterDto problemRegisterDto, Long userId) {
        problemService.updateProblemInfo(problemRegisterDto, userId);

        if (problemRegisterDto.folderId() != null) {
            Folder folder = findFolderEntity(problemRegisterDto.folderId());
            problemService.updateProblemFolder(problemRegisterDto.problemId(), folder, userId);
        }
    }

    public void deleteFolderWithProblem(Long folderId) {
        Folder folder = findFolderEntity(folderId);
        Long parentFolderId = folder.getParentFolder().getId();

        if (parentFolderId == null) {
            throw new ApplicationException(FolderErrorCase.ROOT_FOLDER_CANNOT_REMOVE);
        }

        // 재귀적으로 하위 폴더를 삭제
        deleteSubFolders(folder);
    }

    public void deleteFolderList(List<Long> folderIdList) {
        folderIdList.forEach(this::deleteFolderWithProblem);
    }

    private void deleteSubFolders(Folder folder) {
        // 하위 폴더들을 먼저 삭제
        if (folder.getSubFolderList() != null && !folder.getSubFolderList().isEmpty()) {
            for (Folder subFolder : folder.getSubFolderList()) {
                deleteSubFolders(subFolder); // 재귀적으로 하위 폴더 삭제
            }
        }

        if (folder.getProblemList() != null && !folder.getProblemList().isEmpty()) {
            problemService.deleteFolderProblems(folder.getId());
        }

        folder.getSubFolderList().clear();
        folder.getProblemList().clear();

        folderRepository.deleteById(folder.getId());
    }

    public void deleteAllUserFolder(Long userId) {
        List<Folder> folderList = folderRepository.findAllByUserId(userId);

        folderRepository.deleteAll(folderList);
    }
}
