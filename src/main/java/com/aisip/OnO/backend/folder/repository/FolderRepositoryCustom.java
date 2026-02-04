package com.aisip.OnO.backend.folder.repository;

import com.aisip.OnO.backend.folder.entity.Folder;

import java.util.List;
import java.util.Optional;

public interface FolderRepositoryCustom {

    Optional<Long> findRootFolderId(Long userId);

    Optional<Folder> findRootFolder(Long userId);

    Optional<Folder> findFolderWithDetailsByFolderId(Long folderId);

    List<Folder> findAllFoldersWithDetailsByUserId(Long userId);

    List<Long> findProblemIdsByFolder(Long folderId);

    /**
     * 커서 기반 하위 폴더 조회
     * @param folderId 부모 폴더 ID
     * @param cursor 마지막으로 조회한 폴더 ID (null이면 처음부터)
     * @param size 조회할 개수
     * @return 하위 폴더 리스트 (size+1개 조회하여 hasNext 판단)
     */
    List<Folder> findSubFoldersWithCursor(Long folderId, Long cursor, int size);
}
