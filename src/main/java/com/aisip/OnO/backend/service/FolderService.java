package com.aisip.OnO.backend.service;

import com.aisip.OnO.backend.Dto.Folder.FolderResponseDto;

import java.util.List;

public interface FolderService {

    FolderResponseDto createRootFolder(Long userId, String folderName);

    FolderResponseDto createFolder(Long userId, String folderName, Long parentFolderId);

    FolderResponseDto findRootFolder(Long userId);

    FolderResponseDto findFolder(Long userId, Long folderId);

    List<FolderResponseDto> findAllFolderNamesByUserId(Long userId);

    FolderResponseDto updateFolder(Long userId, Long folderId, String folderName, Long parentFolderId);

    void deleteFolder(Long folderId);
}