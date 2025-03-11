package com.aisip.OnO.backend.problem.service;

import com.aisip.OnO.backend.common.exception.ApplicationException;
import com.aisip.OnO.backend.problem.dto.FolderRegisterDto;
import com.aisip.OnO.backend.problem.dto.FolderResponseDto;
import com.aisip.OnO.backend.problem.dto.FolderThumbnailResponseDto;
import com.aisip.OnO.backend.problem.entity.Folder;
import com.aisip.OnO.backend.problem.exception.FolderErrorCase;
import com.aisip.OnO.backend.problem.repository.folder.FolderRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class FolderService {

    private final FolderRepository folderRepository;

    private final ProblemService problemService;

    public FolderResponseDto findFolder(Long folderId) {
        Folder folder = folderRepository.findFolderWithDetailsByFolderId(folderId)
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
        List<Folder> folders = folderRepository.findAllFoldersWithDetailsByUserId(userId);
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

    public void updateFolder(FolderRegisterDto folderRegisterDto, Long userId) {
        Folder folder = findFolderEntity(folderRegisterDto.folderId());

        folder.updateFolderInfo(folderRegisterDto);

        if (folderRegisterDto.parentFolderId() != null) {
            Folder parentFolder = findFolderEntity(folderRegisterDto.parentFolderId());
            folder.updateParentFolder(parentFolder);
        }
    }

    public void deleteFoldersWithProblems(List<Long> folderIds, Long userId) {

        // 삭제할 모든 폴더의 ID 조회 (하위 폴더 포함)
        Set<Long> allFolderIds = getAllFolderIdsIncludingSubFolders(folderIds);

        problemService.deleteAllByFolderIds(allFolderIds);

        deleteAllByFolderIds(allFolderIds);
    }

    public void deleteAllUserFoldersWithProblems(Long userId) {

        problemService.deleteAllUserProblems(userId);

        deleteAllUserFolders(userId);
    }

    public Set<Long> getAllFolderIdsIncludingSubFolders(List<Long> folderIds) {
        Set<Long> allFolderIds = new HashSet<>();

        for (Long folderId : folderIds) {
            Folder folder = folderRepository.findById(folderId)
                    .orElseThrow(() -> new ApplicationException(FolderErrorCase.FOLDER_NOT_FOUND));

            if (folder.getParentFolder() == null) {
                throw new ApplicationException(FolderErrorCase.ROOT_FOLDER_CANNOT_REMOVE);
            }
            allFolderIds.add(folder.getId());
            allFolderIds.addAll(getSubFolderIdsRecursive(folder));
        }

        return allFolderIds;
    }

    private Set<Long> getSubFolderIdsRecursive(Folder folder) {
        Set<Long> subFolderIds = new HashSet<>();

        for (Folder subFolder : folder.getSubFolderList()) {
            subFolderIds.add(subFolder.getId());
            subFolderIds.addAll(getSubFolderIdsRecursive(subFolder));
        }

        return subFolderIds;
    }

    public void deleteAllByFolderIds(Collection<Long> folderIds) {
        folderRepository.deleteAllByIdIn(folderIds);
    }

    public void deleteAllUserFolders(Long userId) {
        List<Folder> folderList = folderRepository.findAllByUserId(userId);

        folderRepository.deleteAll(folderList);
    }
}
