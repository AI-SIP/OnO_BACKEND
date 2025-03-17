package com.aisip.OnO.backend.folder.dto;

public record FolderRegisterDto (

    String folderName,

    Long folderId,

    Long parentFolderId
) {}
