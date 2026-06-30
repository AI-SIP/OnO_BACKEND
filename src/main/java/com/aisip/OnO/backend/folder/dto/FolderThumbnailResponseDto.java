package com.aisip.OnO.backend.folder.dto;

import com.aisip.OnO.backend.folder.entity.Folder;
import lombok.AccessLevel;
import lombok.Builder;
import org.jetbrains.annotations.NotNull;

@Builder(access = AccessLevel.PRIVATE)
public record FolderThumbnailResponseDto(
        Long folderId,
        String folderName,
        Long problemCount
) {
    public static FolderThumbnailResponseDto from(@NotNull Folder folder, Long problemCount) {
        return FolderThumbnailResponseDto.builder()
                .folderId(folder.getId())
                .folderName(folder.getName())
                .problemCount(problemCount)
                .build();
    }

    public static FolderThumbnailResponseDto from(@NotNull Folder folder) {
        return from(folder, 0L);
    }
}
