package com.aisip.OnO.backend.service;

import com.aisip.OnO.backend.Dto.Folder.FolderResponseDto;
import com.aisip.OnO.backend.entity.Folder;

import java.util.List;

public interface FolderService {

    Folder getFolderEntity(Long folderId);

    FolderResponseDto createRootFolder(Long userId, String folderName);

    FolderResponseDto createFolder(Long userId, String folderName, Long parentFolderId);

    List<FolderResponseDto> findAllFolders(Long userId);

    FolderResponseDto findFolder(Long userId, Long folderId);

    FolderResponseDto findRootFolder(Long userId);

    FolderResponseDto getFolderResponseDto(Folder folder);

    FolderResponseDto updateFolder(Long userId, Long folderId, String folderName, Long parentFolderId);

    FolderResponseDto updateProblemPath(Long userId, Long problemId, Long folderId);

    FolderResponseDto deleteFolder(Long userId, Long folderId);

    void deleteAllUserFolder(Long userId);
}
