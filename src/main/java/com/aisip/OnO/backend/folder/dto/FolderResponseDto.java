package com.aisip.OnO.backend.folder.dto;

import com.aisip.OnO.backend.folder.entity.Folder;
import com.aisip.OnO.backend.problem.dto.ProblemResponseDto;
import lombok.AccessLevel;
import lombok.Builder;

import java.time.LocalDateTime;
import java.util.List;

@Builder(access = AccessLevel.PRIVATE)
public record FolderResponseDto (

    Long folderId,

    String folderName,

    FolderThumbnailResponseDto parentFolder,

    List<FolderThumbnailResponseDto> subFolderList,

    List<ProblemResponseDto> problemList,

    LocalDateTime createdAt,

    LocalDateTime updateAt
) {
    public static FolderResponseDto from(Folder folder, List<ProblemResponseDto> problemResponseDtoList) {

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
                .problemList(problemResponseDtoList)
                .createdAt(folder.getCreatedAt())
                .updateAt(folder.getUpdatedAt())
                .build();
    }
}