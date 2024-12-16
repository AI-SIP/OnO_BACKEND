package com.aisip.OnO.backend.converter;

import com.aisip.OnO.backend.Dto.Folder.FolderResponseDto;
import com.aisip.OnO.backend.Dto.Folder.FolderThumbnailResponseDto;
import com.aisip.OnO.backend.Dto.Problem.ProblemResponseDto;
import com.aisip.OnO.backend.entity.Folder;

import java.util.ArrayList;
import java.util.List;

public class FolderConverter {

    public static FolderThumbnailResponseDto convertToThumbnailResponseDto(Folder folder) {
        Long parentFolderId = null;
        if (folder.getParentFolder() != null) {
            parentFolderId = folder.getParentFolder().getId();
        }

        List<Long> subFoldersId = new ArrayList<>();
        List<Folder> subFolders = folder.getSubFolders();

        if (subFolders != null && !subFolders.isEmpty()) {
            subFoldersId = subFolders.stream()
                    .map(Folder::getId).toList();
        }

        return FolderThumbnailResponseDto.builder()
                .folderId(folder.getId())
                .folderName(folder.getName())
                .parentFolderId(parentFolderId)
                .subFoldersId(subFoldersId)
                .build();
    }


    public static FolderResponseDto convertToResponseDto(Folder folder, List<ProblemResponseDto> problemResponseDtoList) {
        FolderThumbnailResponseDto parentFolder = null;

        // 부모 폴더 정보 설정
        if (folder.getParentFolder() != null) {
            parentFolder = FolderThumbnailResponseDto.builder()
                    .folderId(folder.getParentFolder().getId())
                    .folderName(folder.getParentFolder().getName())
                    .build();
        }

        // 하위 폴더 정보 설정
        List<FolderThumbnailResponseDto> subFolders = null;
        if (folder.getSubFolders() != null && !folder.getSubFolders().isEmpty()) {
            subFolders = folder.getSubFolders().stream()
                    .map(subFolder -> FolderThumbnailResponseDto.builder()
                            .folderId(subFolder.getId())
                            .folderName(subFolder.getName())
                            .build())
                    .toList();
        }

        // FolderResponseDto 생성 및 반환
        return FolderResponseDto.builder()
                .folderId(folder.getId())
                .folderName(folder.getName())
                .parentFolder(parentFolder)
                .subFolders(subFolders)
                .problems(problemResponseDtoList)
                .createdAt(folder.getCreatedAt())
                .updateAt(folder.getUpdatedAt())
                .build();
    }
}
