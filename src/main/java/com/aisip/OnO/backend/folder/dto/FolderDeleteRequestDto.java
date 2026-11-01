package com.aisip.OnO.backend.folder.dto;

import jakarta.validation.constraints.NotNull;

import java.util.List;

public record FolderDeleteRequestDto(
        @NotNull(message = "deleteFolderIdList 는 필수입니다.")
        List<@NotNull(message = "deleteFolderIdList 에 빈 id 가 있습니다.") Long> deleteFolderIdList
) {
}
