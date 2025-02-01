package com.aisip.OnO.backend.repository.Folder;

import com.aisip.OnO.backend.entity.Folder;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FolderRepository extends JpaRepository<Folder, Long>, FolderRepositoryCustom {

    Optional<Folder> findByUserIdAndParentFolderIsNull(Long userId);

    List<Folder> findAllByUserId(Long userId);
}
