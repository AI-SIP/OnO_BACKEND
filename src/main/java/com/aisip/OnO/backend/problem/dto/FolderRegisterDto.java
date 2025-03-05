package com.aisip.OnO.backend.problem.dto;

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
