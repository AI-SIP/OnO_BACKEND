package com.aisip.OnO.backend.folder.service;

import com.aisip.OnO.backend.common.exception.ApplicationException;
import com.aisip.OnO.backend.common.response.CursorPageResponse;
import com.aisip.OnO.backend.folder.dto.FolderRegisterDto;
import com.aisip.OnO.backend.folder.dto.FolderResponseDto;
import com.aisip.OnO.backend.folder.dto.FolderThumbnailResponseDto;
import com.aisip.OnO.backend.folder.entity.Folder;
import com.aisip.OnO.backend.folder.exception.FolderErrorCase;
import com.aisip.OnO.backend.folder.repository.FolderRepository;
import com.aisip.OnO.backend.problem.dto.ProblemResponseDto;
import com.aisip.OnO.backend.problem.service.ProblemService;
import org.springframework.transaction.annotation.Transactional;
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

    private static final String ROOT_FOLDER_NAME = "책장";
    private static final String DEFAULT_SUB_FOLDER_NAME = "공책";

    private final FolderRepository folderRepository;

    private final ProblemService problemService;

    public void initializeDefaultFoldersIfAbsent(Long userId) {
        Folder rootFolder = folderRepository.findByUserIdAndParentFolderIsNull(userId)
                .orElseGet(() -> {
                    Folder createdRoot = Folder.from(new FolderRegisterDto(ROOT_FOLDER_NAME, null, null), userId);
                    folderRepository.save(createdRoot);
                    log.info("userId : {} root folder created", userId);
                    return createdRoot;
                });

        boolean hasDefaultSubFolder = rootFolder.getSubFolderList().stream()
                .anyMatch(subFolder -> DEFAULT_SUB_FOLDER_NAME.equals(subFolder.getName()));

        if (!hasDefaultSubFolder) {
            createDefaultSubFolder(rootFolder, userId);
        }
    }

    public FolderResponseDto findRootFolder(Long userId) {
        Folder rootFolder = folderRepository.findRootFolder(userId)
                .orElseThrow(() -> new ApplicationException(FolderErrorCase.FOLDER_NOT_FOUND));

        log.info("userId : {} find root folder id: {}", userId, rootFolder.getId());

        List<Long> problemIdList = folderRepository.findProblemIdsByFolder(rootFolder.getId());
        return FolderResponseDto.from(rootFolder, problemIdList);
    }

    @Transactional(readOnly = true)
    public FolderResponseDto findFolder(Long folderId) {
        Folder folder = folderRepository.findFolderWithDetailsByFolderId(folderId)
                .orElseThrow(() -> new ApplicationException(FolderErrorCase.FOLDER_NOT_FOUND));

        List<Long> problemIdList = folderRepository.findProblemIdsByFolder(folder.getId());
        return FolderResponseDto.from(folder, problemIdList);
    }

    @Transactional(readOnly = true)
    public Folder findFolderEntity(Long folderId) {
        return folderRepository.findById(folderId)
                .orElseThrow(() -> new ApplicationException(FolderErrorCase.FOLDER_NOT_FOUND));
    }

    @Transactional(readOnly = true)
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
                ? List.of()
                : folders.stream().map(folder -> {
                    List<Long> problemIdList = folderRepository.findProblemIdsByFolder(folder.getId());
                    return FolderResponseDto.from(folder, problemIdList);
                }).toList();
    }

    private void createDefaultSubFolder(Folder rootFolder, Long userId) {
        Folder defaultSubFolder = Folder.from(new FolderRegisterDto(DEFAULT_SUB_FOLDER_NAME, null, rootFolder.getId()), userId);
        defaultSubFolder.updateParentFolder(rootFolder);
        folderRepository.save(defaultSubFolder);

        log.info("userId : {} default sub folder created", userId);
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

    /**
     * V2 API: 커서 기반 하위 폴더 조회
     * @param folderId 부모 폴더 ID
     * @param cursor 마지막으로 조회한 폴더 ID (null이면 처음부터)
     * @param size 조회할 개수
     * @return 커서 기반 페이징 응답
     */
    @Transactional(readOnly = true)
    public CursorPageResponse<FolderThumbnailResponseDto> findSubFoldersWithCursor(Long folderId, Long cursor, int size) {
        List<Folder> folders = folderRepository.findSubFoldersWithCursor(folderId, cursor, size);

        boolean hasNext = folders.size() > size;
        List<Folder> content = hasNext ? folders.subList(0, size) : folders;
        Long nextCursor = hasNext ? content.get(content.size() - 1).getId() : null;

        List<FolderThumbnailResponseDto> dtoList = content.stream()
                .map(FolderThumbnailResponseDto::from)
                .collect(Collectors.toList());

        log.info("folderId: {} find subfolders with cursor: {}, size: {}, hasNext: {}", folderId, cursor, size, hasNext);
        return CursorPageResponse.of(dtoList, nextCursor, hasNext, size);
    }

    /**
     * V2 API: 커서 기반 유저의 모든 폴더 썸네일 조회
     * @param userId 유저 ID
     * @param cursor 마지막으로 조회한 폴더 ID (null이면 처음부터)
     * @param size 조회할 개수
     * @return 커서 기반 페이징 응답
     */
    @Transactional(readOnly = true)
    public CursorPageResponse<FolderThumbnailResponseDto> findAllUserFolderThumbnailsWithCursor(Long userId, Long cursor, int size) {
        List<Folder> folders = folderRepository.findAllUserFolderThumbnailsWithCursor(userId, cursor, size);

        boolean hasNext = folders.size() > size;
        List<Folder> content = hasNext ? folders.subList(0, size) : folders;
        Long nextCursor = hasNext ? content.get(content.size() - 1).getId() : null;

        List<FolderThumbnailResponseDto> dtoList = content.stream()
                .map(FolderThumbnailResponseDto::from)
                .collect(Collectors.toList());

        log.info("userId: {} find all folder thumbnails with cursor: {}, size: {}, hasNext: {}", userId, cursor, size, hasNext);
        return CursorPageResponse.of(dtoList, nextCursor, hasNext, size);
    }
}
