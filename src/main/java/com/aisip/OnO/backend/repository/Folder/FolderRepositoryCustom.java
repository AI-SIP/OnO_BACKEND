package com.aisip.OnO.backend.repository.Folder;

import com.aisip.OnO.backend.entity.Folder;

import java.util.Optional;

public interface FolderRepositoryCustom {

    Optional<Folder> findRootFolder(Long userId);
}
