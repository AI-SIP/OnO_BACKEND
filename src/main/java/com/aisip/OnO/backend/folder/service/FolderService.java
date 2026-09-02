package com.aisip.OnO.backend.folder.service;

import com.aisip.OnO.backend.common.exception.ApplicationException;
import com.aisip.OnO.backend.common.response.CursorPageResponse;
import com.aisip.OnO.backend.folder.dto.FolderRegisterDto;
import com.aisip.OnO.backend.folder.dto.FolderResponseDto;
import com.aisip.OnO.backend.folder.dto.FolderThumbnailResponseDto;
import com.aisip.OnO.backend.folder.entity.Folder;
import com.aisip.OnO.backend.folder.exception.FolderErrorCase;
import com.aisip.OnO.backend.folder.repository.FolderRepository;
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
        return toFolderResponseDto(rootFolder, problemIdList);
    }

    @Transactional(readOnly = true)
    public FolderResponseDto findFolderForAdmin(Long folderId) {
        Folder folder = folderRepository.findFolderWithDetailsByFolderId(folderId)
                .orElseThrow(() -> new ApplicationException(FolderErrorCase.FOLDER_NOT_FOUND));

        List<Long> problemIdList = folderRepository.findProblemIdsByFolder(folder.getId());
        return toFolderResponseDto(folder, problemIdList);
    }

    @Transactional(readOnly = true)
    public FolderResponseDto findFolder(Long folderId, Long userId) {
        Folder folder = findFolderWithDetailsOwnedByUser(folderId, userId);

        List<Long> problemIdList = folderRepository.findProblemIdsByFolder(folder.getId());
        return toFolderResponseDto(folder, problemIdList);
    }

    @Transactional(readOnly = true)
    private Folder findFolderEntity(Long folderId) {
        return folderRepository.findById(folderId)
                .orElseThrow(() -> new ApplicationException(FolderErrorCase.FOLDER_NOT_FOUND));
    }

    @Transactional(readOnly = true)
    public Folder findFolderEntity(Long folderId, Long userId) {
        Folder folder = findFolderEntity(folderId);
        validateFolderOwner(folder, userId);
        return folder;
    }

    @Transactional(readOnly = true)
    public List<FolderThumbnailResponseDto> findAllUserFolderThumbnails(Long userId) {
        List<Folder> folderList = folderRepository.findAllByUserId(userId);

        return folderList.isEmpty()
                ? List.of()
                : toFolderThumbnailDtos(folderList);
    }

    public List<FolderResponseDto> findAllUserFolders(Long userId) {
        List<Folder> folders = folderRepository.findAllFoldersWithDetailsByUserId(userId);
        if (folders.isEmpty()) {
            return List.of();
        }

        List<Long> folderIds = folders.stream().map(Folder::getId).toList();
        Map<Long, List<Long>> problemIdsByFolder = folderRepository.findProblemIdsByFolderIds(folderIds);

        Set<Long> thumbnailFolderIds = new HashSet<>();
        for (Folder f : folders) {
            if (f.getParentFolder() != null) thumbnailFolderIds.add(f.getParentFolder().getId());
            if (f.getSubFolderList() != null) f.getSubFolderList().forEach(sf -> thumbnailFolderIds.add(sf.getId()));
        }
        Map<Long, Long> problemCounts = findProblemCountsByFolderIds(thumbnailFolderIds);

        log.info("userId : {} find All user folders", userId);
        return folders.stream()
                .map(folder -> FolderResponseDto.from(
                        folder,
                        problemIdsByFolder.getOrDefault(folder.getId(), List.of()),
                        problemCounts))
                .toList();
    }

    private void createDefaultSubFolder(Folder rootFolder, Long userId) {
        Folder defaultSubFolder = Folder.from(new FolderRegisterDto(DEFAULT_SUB_FOLDER_NAME, null, rootFolder.getId()), userId);
        defaultSubFolder.updateParentFolder(rootFolder);
        folderRepository.save(defaultSubFolder);

        log.info("userId : {} default sub folder created", userId);
    }

    public Long createFolder(FolderRegisterDto folderRegisterDto, Long userId) {
        // parentFolderId 가 없으면 findById(null) 이 IllegalArgumentException 을 던져 500 으로 나갔다.
        // 루트 폴더는 initializeDefaultFoldersIfAbsent 만 만들 수 있으므로, 부모 없는 생성 요청은
        // 잘못된 요청으로 보고 다른 "부모 폴더를 찾을 수 없음"과 같은 응답을 준다.
        if (folderRegisterDto.parentFolderId() == null) {
            throw new ApplicationException(FolderErrorCase.FOLDER_NOT_FOUND);
        }

        Folder folder = Folder.from(folderRegisterDto, userId);
        Folder parentFolder = findFolderEntity(folderRegisterDto.parentFolderId(), userId);

        folder.updateParentFolder(parentFolder);
        folderRepository.save(folder);

        log.info("userId : {} create folder id: {}", userId, folder.getId());
        return folder.getId();
    }

    public void updateFolder(FolderRegisterDto folderRegisterDto, Long userId) {
        Folder folder = findFolderEntity(folderRegisterDto.folderId(), userId);

        if (folder.getParentFolder() == null) {
            throw new ApplicationException(FolderErrorCase.ROOT_FOLDER_CANNOT_UPDATE);
        }

        folder.updateFolderInfo(folderRegisterDto);

        if (folderRegisterDto.parentFolderId() != null && folder.getParentFolder() != null) {
            Folder newParentFolder = findFolderEntity(folderRegisterDto.parentFolderId(), userId);
            validateNotCyclic(folder, newParentFolder);

            folder.updateParentFolder(newParentFolder);
        }

        log.info("userId : {} update folder id: {}", userId, folder.getId());
    }

    public void deleteFoldersWithProblems(Long userId, List<Long> folderIds) {
        // 삭제할 모든 폴더의 ID 조회 (하위 폴더 포함)
        Set<Long> allFolderIds = getAllFolderIdsIncludingSubFolders(userId, folderIds);

        problemService.deleteAllByFolderIds(userId, allFolderIds);

        deleteAllByFolderIds(allFolderIds);
    }

    public void deleteAllUserFoldersWithProblems(Long userId) {

        problemService.deleteAllUserProblems(userId);

        deleteAllUserFolders(userId);
    }

    public Set<Long> getAllFolderIdsIncludingSubFolders(Long userId, List<Long> folderIds) {
        Set<Long> allFolderIds = new HashSet<>();

        for (Long folderId : folderIds) {
            Folder folder = findFolderEntity(folderId, userId);

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
        collectSubFolderIds(folder, subFolderIds);
        return subFolderIds;
    }

    /**
     * 이미 방문한 폴더는 다시 타고 들어가지 않는다.
     *
     * <p>부모-자식 관계에 순환이 남아 있으면(과거 데이터 등) 단순 재귀는 StackOverflowError 로
     * 삭제 요청 전체를 500 으로 떨어뜨린다. 방문 집합으로 한 번만 훑는다.
     */
    private void collectSubFolderIds(Folder folder, Set<Long> collectedIds) {
        for (Folder subFolder : folder.getSubFolderList()) {
            if (collectedIds.add(subFolder.getId())) {
                collectSubFolderIds(subFolder, collectedIds);
            }
        }
    }

    /**
     * 폴더를 자기 자신이나 자기 하위 폴더 아래로 옮기려는 요청을 막는다.
     *
     * <p>막지 않으면 트리에 순환이 생겨 폴더 삭제(하위 폴더 재귀 수집)와 앱의 폴더 탐색이
     * 무한 루프에 빠진다. 새 부모에서 루트 방향으로 거슬러 올라가며 자기 자신이 나오는지 본다.
     */
    private void validateNotCyclic(Folder folder, Folder newParentFolder) {
        Set<Long> visitedFolderIds = new HashSet<>();
        Folder ancestor = newParentFolder;

        while (ancestor != null && visitedFolderIds.add(ancestor.getId())) {
            if (Objects.equals(ancestor.getId(), folder.getId())) {
                throw new ApplicationException(FolderErrorCase.INVALID_PARENT_FOLDER);
            }
            ancestor = ancestor.getParentFolder();
        }
    }

    private void deleteAllByFolderIds(Collection<Long> folderIds) {
        if (folderIds.isEmpty()) {
            return;
        }

        folderRepository.softDeleteAllByIdIn(folderIds);
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
    public CursorPageResponse<FolderThumbnailResponseDto> findSubFoldersWithCursor(Long folderId, Long userId, Long cursor, int size) {
        findFolderEntity(folderId, userId);
        List<Folder> folders = folderRepository.findSubFoldersWithCursor(folderId, cursor, size);

        boolean hasNext = folders.size() > size;
        List<Folder> content = hasNext ? folders.subList(0, size) : folders;
        Long nextCursor = hasNext ? content.get(content.size() - 1).getId() : null;

        List<FolderThumbnailResponseDto> dtoList = toFolderThumbnailDtos(content);

        log.info("folderId: {} find subfolders with cursor: {}, size: {}, hasNext: {}", folderId, cursor, size, hasNext);
        return CursorPageResponse.of(dtoList, nextCursor, hasNext, size);
    }

    private Folder findFolderWithDetailsOwnedByUser(Long folderId, Long userId) {
        Folder folder = folderRepository.findFolderWithDetailsByFolderId(folderId)
                .orElseThrow(() -> new ApplicationException(FolderErrorCase.FOLDER_NOT_FOUND));
        validateFolderOwner(folder, userId);
        return folder;
    }

    private void validateFolderOwner(Folder folder, Long userId) {
        if (!Objects.equals(folder.getUserId(), userId)) {
            throw new ApplicationException(FolderErrorCase.FOLDER_USER_UNMATCHED);
        }
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

        List<FolderThumbnailResponseDto> dtoList = toFolderThumbnailDtos(content);

        log.info("userId: {} find all folder thumbnails with cursor: {}, size: {}, hasNext: {}", userId, cursor, size, hasNext);
        return CursorPageResponse.of(dtoList, nextCursor, hasNext, size);
    }

    private FolderResponseDto toFolderResponseDto(Folder folder, List<Long> problemIdList) {
        Set<Long> thumbnailFolderIds = new HashSet<>();
        if (folder.getParentFolder() != null) {
            thumbnailFolderIds.add(folder.getParentFolder().getId());
        }
        if (folder.getSubFolderList() != null) {
            folder.getSubFolderList().stream()
                    .map(Folder::getId)
                    .forEach(thumbnailFolderIds::add);
        }

        Map<Long, Long> problemCountsByFolderId = findProblemCountsByFolderIds(thumbnailFolderIds);
        return FolderResponseDto.from(folder, problemIdList, problemCountsByFolderId);
    }

    private List<FolderThumbnailResponseDto> toFolderThumbnailDtos(List<Folder> folders) {
        List<Long> folderIds = folders.stream()
                .map(Folder::getId)
                .toList();
        Map<Long, Long> problemCountsByFolderId = findProblemCountsByFolderIds(folderIds);

        return folders.stream()
                .map(folder -> FolderThumbnailResponseDto.from(
                        folder,
                        problemCountsByFolderId.getOrDefault(folder.getId(), 0L)
                ))
                .collect(Collectors.toList());
    }

    private Map<Long, Long> findProblemCountsByFolderIds(Collection<Long> folderIds) {
        if (folderIds == null || folderIds.isEmpty()) {
            return Map.of();
        }

        return folderRepository.countProblemsByFolderIds(folderIds).stream()
                .collect(Collectors.toMap(
                        row -> (Long) row[0],
                        row -> (Long) row[1]
                ));
    }
}
