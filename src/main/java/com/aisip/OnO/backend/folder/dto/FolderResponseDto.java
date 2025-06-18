package com.aisip.OnO.backend.folder.dto;

import com.aisip.OnO.backend.folder.entity.Folder;
import com.aisip.OnO.backend.problem.dto.ProblemResponseDto;
import lombok.AccessLevel;
import lombok.Builder;
import org.jetbrains.annotations.NotNull;

import java.time.LocalDateTime;
import java.util.List;

@Builder(access = AccessLevel.PRIVATE)
public record FolderResponseDto (

    Long folderId,

    String folderName,

    FolderThumbnailResponseDto parentFolder,

    List<FolderThumbnailResponseDto> subFolderList,

    List<Long> problemIdList,

    LocalDateTime createdAt,

    LocalDateTime updateAt
) {
    public static FolderResponseDto from(@NotNull Folder folder, List<Long> problemIdList) {

        FolderThumbnailResponseDto parentFolder = folder.getParentFolder() != null
                ? FolderThumbnailResponseDto.from(folder.getParentFolder())
                : null;

        List<FolderThumbnailResponseDto> subFolderList = folder.getSubFolderList() != null
                ? folder.getSubFolderList()
                .stream()
                .map(FolderThumbnailResponseDto::from)
                .toList()
                : List.of();

        return FolderResponseDto.builder()
                .folderId(folder.getId())
                .folderName(folder.getName())
                .parentFolder(parentFolder)
                .subFolderList(subFolderList)
                .problemIdList(problemIdList)
                .createdAt(folder.getCreatedAt())
                .updateAt(folder.getUpdatedAt())
                .build();
    }
}