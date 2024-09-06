package com.aisip.OnO.backend.Dto.Folder;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FolderRegisterDto {

    private String folderName;

    private Long parentFolderId;
}
