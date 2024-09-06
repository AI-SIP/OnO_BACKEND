package com.aisip.OnO.backend.Dto.Folder;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FolderThumbnailResponseDto {

    private Long folderId;

    private String folderName;
}
