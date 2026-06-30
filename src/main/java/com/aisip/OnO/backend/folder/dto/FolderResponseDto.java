package com.aisip.OnO.backend.folder.dto;

import com.aisip.OnO.backend.folder.entity.Folder;
import lombok.AccessLevel;
import lombok.Builder;
import org.jetbrains.annotations.NotNull;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Builder(access = AccessLevel.PRIVATE)
public record FolderResponseDto (

    Long folderId,

    Long userId,

    String folderName,

    FolderThumbnailResponseDto parentFolder,

    List<FolderThumbnailResponseDto> subFolderList,

    List<Long> problemIdList,

    LocalDateTime createdAt,

    LocalDateTime updateAt
) {
    public static FolderResponseDto from(@NotNull Folder folder, List<Long> problemIdList) {
        return from(folder, problemIdList, Map.of());
    }

    public static FolderResponseDto from(
            @NotNull Folder folder,
            List<Long> problemIdList,
            Map<Long, Long> problemCountsByFolderId
    ) {

        FolderThumbnailResponseDto parentFolder = folder.getParentFolder() != null
                ? FolderThumbnailResponseDto.from(
                        folder.getParentFolder(),
                        problemCountsByFolderId.getOrDefault(folder.getParentFolder().getId(), 0L)
                )
                : null;

        List<FolderThumbnailResponseDto> subFolderList = folder.getSubFolderList() != null
                ? folder.getSubFolderList()
                .stream()
                .map(subFolder -> FolderThumbnailResponseDto.from(
                        subFolder,
                        problemCountsByFolderId.getOrDefault(subFolder.getId(), 0L)
                ))
                .toList()
                : List.of();

        return FolderResponseDto.builder()
                .folderId(folder.getId())
                .userId(folder.getUserId())
                .folderName(folder.getName())
                .parentFolder(parentFolder)
                .subFolderList(subFolderList)
                .problemIdList(problemIdList)
                .createdAt(folder.getCreatedAt())
                .updateAt(folder.getUpdatedAt())
                .build();
    }
}
