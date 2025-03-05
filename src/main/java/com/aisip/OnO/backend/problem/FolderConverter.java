package com.aisip.OnO.backend.problem;

import com.aisip.OnO.backend.problem.dto.FolderResponseDto;
import com.aisip.OnO.backend.problem.dto.ProblemResponseDto;
import com.aisip.OnO.backend.problem.entity.Folder;

import java.util.ArrayList;
import java.util.List;

public class FolderConverter {
    public static FolderResponseDto convertToResponseDto(Folder folder, List<ProblemResponseDto> problemResponseDtoList) {
        // FolderResponseDto 생성 및 반환
        FolderResponseDto folderResponseDto = FolderResponseDto.builder()
                .folderId(folder.getId())
                .folderName(folder.getName())
                .problems(problemResponseDtoList)
                .createdAt(folder.getCreatedAt())
                .updateAt(folder.getUpdatedAt())
                .build();


        if (folder.getParentFolder() != null) {
            Long parentFolderId = folder.getParentFolder().getId();
            folderResponseDto.setParentFolderId(parentFolderId);
        }

        List<Long> subFolderIds = new ArrayList<>();
        List<Folder> subFolders = folder.getSubFolders();

        if (subFolders != null && !subFolders.isEmpty()) {
            subFolderIds = subFolders.stream()
                    .map(Folder::getId).toList();
        }

        folderResponseDto.setSubFolderIds(subFolderIds);

        return folderResponseDto;
    }
}
