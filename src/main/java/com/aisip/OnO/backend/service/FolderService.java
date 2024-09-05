package com.aisip.OnO.backend.service;

import com.aisip.OnO.backend.Dto.Folder.FolderResponseDto;
import com.aisip.OnO.backend.entity.Folder;

import java.util.List;
import java.util.Optional;

public interface FolderService {

    public FolderResponseDto createRootFolder(Long userId, String folderName);

    public FolderResponseDto createFolder(Long userId, String folderName, Long parentFolderId);

    public FolderResponseDto findFolder(Long folderId);

    public FolderResponseDto updateFolder(Long userId, Long folderId, String folderName, Long parentFolderId);

    public void deleteFolder(Long folderId);

    public List<Folder> getAllFolders();
}
