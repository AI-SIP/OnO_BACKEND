package com.aisip.OnO.backend.problem.repository.folder;

import com.aisip.OnO.backend.problem.entity.Folder;

import java.util.List;
import java.util.Optional;

public interface FolderRepositoryCustom {

    Optional<Folder> findRootFolder(Long userId);

    Optional<Folder> findFolderWithDetailsByFolderId(Long folderId);

    List<Folder> findAllFoldersWithDetailsByUserId(Long userId);
}
