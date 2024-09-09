package com.aisip.OnO.backend.Dto.Folder;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FolderThumbnailResponseDto {

    private Long folderId;

    private String folderName;

    private Long parentFolderId;

    private List<Long> subFoldersId;
}
