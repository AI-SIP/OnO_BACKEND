package com.aisip.OnO.backend.folder.service;

import com.aisip.OnO.backend.common.exception.ApplicationException;
import com.aisip.OnO.backend.folder.dto.FolderDeleteRequestDto;
import com.aisip.OnO.backend.folder.dto.FolderRegisterDto;
import com.aisip.OnO.backend.folder.dto.FolderResponseDto;
import com.aisip.OnO.backend.folder.dto.FolderThumbnailResponseDto;
import com.aisip.OnO.backend.folder.entity.Folder;
import com.aisip.OnO.backend.folder.exception.FolderErrorCase;
import com.aisip.OnO.backend.folder.repository.FolderRepository;
import com.aisip.OnO.backend.problem.dto.ProblemResponseDto;
import com.aisip.OnO.backend.problem.service.ProblemService;
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

    public FolderResponseDto findRootFolder(Long userId) {
        return folderRepository.findRootFolder(userId)
                .map(rootFolder -> {
                    log.info("userId : {} find root folder id: {}", userId, rootFolder.getId());
                    List<ProblemResponseDto> problemResponseDtoList = problemService.findFolderProblemList(rootFolder.getId());
                    return FolderResponseDto.from(rootFolder, problemResponseDtoList);
                })
                .orElseGet(() -> {
                    log.info("userId : {} create root folder", userId);
                    return createRootFolder(userId);
                });
    }

    public FolderResponseDto findFolder(Long folderId) {
        Folder folder = folderRepository.findFolderWithDetailsByFolderId(folderId)
                .orElseThrow(() -> new ApplicationException(FolderErrorCase.FOLDER_NOT_FOUND));

        List<ProblemResponseDto> problemResponseDtoList = problemService.findFolderProblemList(folderId);
        return FolderResponseDto.from(folder, problemResponseDtoList);
    }

    public Folder findFolderEntity(Long folderId) {
        return folderRepository.findById(folderId)
                .orElseThrow(() -> new ApplicationException(FolderErrorCase.FOLDER_NOT_FOUND));
    }

    public List<FolderThumbnailResponseDto> findAllUserFolderThumbnails(Long userId) {
        List<Folder> folderList = folderRepository.findAllByUserId(userId);

        return folderList.isEmpty()
                ? List.of()
                : folderList.stream().map(FolderThumbnailResponseDto::from).collect(Collectors.toList());
    }

    public List<FolderResponseDto> findAllUserFolders(Long userId) {
        List<Folder> folders = folderRepository.findAllFoldersWithDetailsByUserId(userId);

        log.info("userId : {} find All user folders", userId);
        return folders.isEmpty()
                ? List.of(findRootFolder(userId))
                : folders.stream().map(folder -> {
            List<ProblemResponseDto> problemResponseDtoList = problemService.findFolderProblemList(folder.getId());
            return FolderResponseDto.from(folder, problemResponseDtoList);
                }).toList();
    }

    public FolderResponseDto createRootFolder(Long userId) {
        FolderRegisterDto folderRegisterDto = new FolderRegisterDto(
                "메인",
                null,
                null
        );

        Folder rootFolder = Folder.from(folderRegisterDto, userId);
        folderRepository.save(rootFolder);

        log.info("userId : {} create root folder id: {}", userId, rootFolder.getId());
        return FolderResponseDto.from(rootFolder, List.of());
    }

    public Long createFolder(FolderRegisterDto folderRegisterDto, Long userId) {
        Folder folder = Folder.from(folderRegisterDto, userId);
        Folder parentFolder = findFolderEntity(folderRegisterDto.parentFolderId());

        folder.updateParentFolder(parentFolder);
        folderRepository.save(folder);

        log.info("userId : {} create folder id: {}", userId, folder.getId());
        return folder.getId();
    }

    public void updateFolder(FolderRegisterDto folderRegisterDto, Long userId) {
        Folder folder = findFolderEntity(folderRegisterDto.folderId());
        folder.updateFolderInfo(folderRegisterDto);

        if (folderRegisterDto.parentFolderId() != null && folder.getParentFolder() != null) {
            Folder newParentFolder = findFolderEntity(folderRegisterDto.parentFolderId());

            folder.updateParentFolder(newParentFolder);
        }

        log.info("userId : {} update folder id: {}", userId, folder.getId());
    }

    public void deleteFolders(FolderDeleteRequestDto folderDeleteRequestDto) {
        if (folderDeleteRequestDto.userId() != null) {
            deleteAllUserFoldersWithProblems(folderDeleteRequestDto.userId());

            log.info("userId : {} delete all user folder With Problems", folderDeleteRequestDto.userId());
            return;
        }

        if (folderDeleteRequestDto.folderIdList() != null) {
            deleteFoldersWithProblems(folderDeleteRequestDto.folderIdList());

            log.info("delete folder With Problems, folder id list: " + folderDeleteRequestDto.folderIdList());
        }
    }

    public void deleteFoldersWithProblems(List<Long> folderIds) {
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
        List<Folder> foldersToDelete = folderRepository.findAllById(folderIds);
        folderRepository.deleteAll(foldersToDelete);
    }

    public void deleteAllUserFolders(Long userId) {
        List<Folder> folderList = folderRepository.findAllByUserId(userId);

        folderRepository.deleteAll(folderList);
        log.info("userId : {} delete all user folders", userId);
    }
}
