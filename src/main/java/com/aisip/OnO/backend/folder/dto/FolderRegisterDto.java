package com.aisip.OnO.backend.folder.dto;

import com.aisip.OnO.backend.common.validation.ValidationGroups;
import jakarta.validation.constraints.NotNull;

public record FolderRegisterDto (

    String folderName,

    // 등록(POST)에서는 비어 있고, 수정(PATCH)에서만 필수다.
    @NotNull(groups = ValidationGroups.Update.class, message = "folderId 는 필수입니다.")
    Long folderId,

    Long parentFolderId
) {}
