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

    // 호출자의 트랜잭션 안에서 실행된다. private 에 붙인 @Transactional 은 프록시가 가로채지 못해 무효다.
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

    /**
     * 부모로 삼을 폴더를 공유 잠금(FOR SHARE)으로 읽는다. (#233)
     *
     * <p>문제 등록이 폴더를 잠그고 읽는 것과 같은 이유다. 잠금 없이 읽으면 아직 커밋되지 않은 폴더 삭제를
     * 못 보고 그 폴더 아래에 새 폴더를 만들거나 기존 폴더를 옮긴다. 삭제가 커밋된 뒤에는 삭제된 폴더를
     * 부모로 가리키는 살아 있는 폴더가 남아, 앱에서 어느 폴더에도 보이지 않는다.
     *
     * <p>잠금 조회는 스냅숏이 아니라 최신 행을 읽으므로, 삭제가 먼저 잡았으면 커밋까지 기다렸다가
     * {@code deleted_at} 이 찍힌 행을 보고 {@code @SQLRestriction} 에 걸려 기존 {@code FOLDER_NOT_FOUND} 가 된다.
     *
     * <p>호출자의 트랜잭션 안에서 실행된다.
     */
    private Folder findParentFolderForShare(Long folderId, Long userId) {
        Folder parentFolder = folderRepository.findByIdForShare(folderId)
                .orElseThrow(() -> new ApplicationException(FolderErrorCase.FOLDER_NOT_FOUND));
        validateFolderOwner(parentFolder, userId);
        return parentFolder;
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
        Folder parentFolder = findParentFolderForShare(folderRegisterDto.parentFolderId(), userId);

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
            Folder newParentFolder = findParentFolderForShare(folderRegisterDto.parentFolderId(), userId);
            validateNotCyclic(folder, newParentFolder);

            folder.updateParentFolder(newParentFolder);
        }

        log.info("userId : {} update folder id: {}", userId, folder.getId());
    }

    public void deleteFoldersWithProblems(Long userId, List<Long> folderIds) {
        // 삭제 대상 서브트리를 잠그면서 하위 폴더까지 모은다. 이 줄보다 앞에 조회를 두면 안 된다. (#233, #319)
        Set<Long> allFolderIds = lockSubtreeAndCollectFolderIds(userId, folderIds);

        problemService.deleteAllByFolderIds(userId, allFolderIds);

        deleteAllByFolderIds(allFolderIds);
    }

    public void deleteAllUserFoldersWithProblems(Long userId) {
        // 폴더 하나씩 지우는 경로와 같은 이유로 사용자 폴더 전체를 먼저 잡는다. 이 줄보다 앞에 조회를 두면 안 된다. (#233)
        folderRepository.lockAllByUserId(userId);

        problemService.deleteAllUserProblems(userId);

        deleteAllUserFolders(userId);
    }

    /**
     * 삭제 대상 폴더와 그 하위 폴더 전부를 배타 잠금으로 잡으면서 ID 를 모은다. (#233, #319)
     *
     * <p><b>호출자의 트랜잭션에서 가장 먼저 실행돼야 한다.</b> 여기서 쓰는 조회는 전부 잠금 조회라
     * REPEATABLE READ 스냅숏을 고정하지 않는다. 그래서 잠금을 다 잡은 뒤에 일어나는 일반 조회가
     * "잠금을 잡은 시점 이후" 를 보게 되고, 잠금을 기다리다 커밋된 등록도 삭제 대상에 들어온다.
     * 이 앞에 일반 조회를 한 줄이라도 두면 그 순간 스냅숏이 박혀 #233 의 고아 문제가 되살아난다.
     *
     * <p>한 단계씩 내려가도 빠지는 폴더는 없다. 어떤 폴더 아래에 새 폴더를 만들거나 옮기려면
     * {@link #findParentFolderForShare} 로 그 부모를 공유 잠금해야 하는데, 우리가 배타 잠금을 쥔 뒤에는
     * 그쪽이 기다렸다가 삭제된 부모를 보고 거절된다. 아직 안 잠근 단계에서 먼저 들어온 생성은
     * 우리가 그 부모를 잠그려고 기다리는 동안 커밋되고, 그다음 잠금 조회가 최신 행을 읽어 잡아낸다.
     *
     * <p>사용자 폴더 전체를 잡던 예전 방식({@code lockAllByUserId})은 삭제와 무관한 폴더로 들어오는
     * 등록까지 줄 세웠고, 기다리는 요청이 커넥션을 문 채로 풀을 바닥내 다른 사용자까지 죽였다. (#319)
     */
    private Set<Long> lockSubtreeAndCollectFolderIds(Long userId, List<Long> folderIds) {
        if (folderIds == null || folderIds.isEmpty()) {
            return Set.of();
        }

        Map<Long, Folder> lockedTargets = folderRepository.lockAllByIdIn(new LinkedHashSet<>(folderIds)).stream()
                .collect(Collectors.toMap(Folder::getId, folder -> folder));

        // 검증 순서는 예전과 같게 요청받은 순서대로 본다. 없음 → 소유자 불일치 → 루트 순이다.
        for (Long folderId : folderIds) {
            Folder folder = lockedTargets.get(folderId);
            if (folder == null) {
                throw new ApplicationException(FolderErrorCase.FOLDER_NOT_FOUND);
            }
            validateFolderOwner(folder, userId);
            if (folder.getParentFolder() == null) {
                throw new ApplicationException(FolderErrorCase.ROOT_FOLDER_CANNOT_REMOVE);
            }
        }

        // 이미 잠근 폴더는 다시 타고 들어가지 않는다. 부모-자식에 순환이 남아 있어도(과거 데이터) 한 번만 훑는다.
        Set<Long> allFolderIds = new LinkedHashSet<>(lockedTargets.keySet());
        Collection<Long> currentLevel = new ArrayList<>(allFolderIds);

        while (!currentLevel.isEmpty()) {
            List<Long> nextLevel = new ArrayList<>();
            for (Folder subFolder : folderRepository.lockAllByParentFolderIdIn(currentLevel)) {
                if (allFolderIds.add(subFolder.getId())) {
                    nextLevel.add(subFolder.getId());
                }
            }
            currentLevel = nextLevel;
        }

        return allFolderIds;
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
