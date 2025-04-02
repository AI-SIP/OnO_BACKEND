package com.aisip.OnO.backend.folder.dto;

import java.util.List;

public record FolderDeleteRequestDto(
        Long userId,
        List<Long> folderIdList
) {
}
