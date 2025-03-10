package com.aisip.OnO.backend.problem.dto;

import com.aisip.OnO.backend.problem.entity.Folder;
import lombok.AccessLevel;
import lombok.Builder;

@Builder(access = AccessLevel.PRIVATE)
public record FolderThumbnailResponseDto(
        Long folderId,
        String folderName
) {
    public static FolderThumbnailResponseDto from(Folder folder) {
        return FolderThumbnailResponseDto.builder()
                .folderId(folder.getId())
                .folderName(folder.getName())
                .build();
    }
}
