package com.aisip.OnO.backend.folder.repository;

import com.aisip.OnO.backend.folder.entity.Folder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface FolderRepository extends JpaRepository<Folder, Long>, FolderRepositoryCustom {

    Optional<Folder> findByUserIdAndParentFolderIsNull(Long userId);

    List<Folder> findAllByUserId(Long userId);

    @Modifying
    @Query("delete from Folder f where f.id in :folderIds")
    void deleteAllByIdIn(@Param("folderIds") Collection<Long> folderIds);
}
