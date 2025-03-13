package com.aisip.OnO.backend.folder.repository;

import com.aisip.OnO.backend.folder.entity.Folder;

import java.util.List;
import java.util.Optional;

public interface FolderRepositoryCustom {

    Optional<Folder> findRootFolder(Long userId);

    Optional<Folder> findFolderWithDetailsByFolderId(Long folderId);

    List<Folder> findAllFoldersWithDetailsByUserId(Long userId);
}
