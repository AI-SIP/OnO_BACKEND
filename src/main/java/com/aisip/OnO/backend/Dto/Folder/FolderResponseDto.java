package com.aisip.OnO.backend.Dto.Folder;

import com.aisip.OnO.backend.Dto.Problem.ProblemResponseDto;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FolderResponseDto {

    private Long folderId;

    private String folderName;

    private FolderThumbnailResponseDto parentFolder;

    private List<FolderThumbnailResponseDto> subFolders;

    private List<ProblemResponseDto> problems;

    private LocalDateTime createdAt;

    private LocalDateTime updateAt;
}
