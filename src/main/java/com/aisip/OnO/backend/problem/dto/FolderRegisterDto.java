package com.aisip.OnO.backend.problem.dto;

public record FolderRegisterDto (

    String folderName,

    Long folderId,

    Long parentFolderId
) {}
