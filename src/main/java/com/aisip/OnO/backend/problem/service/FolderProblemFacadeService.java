package com.aisip.OnO.backend.problem.service;

import com.aisip.OnO.backend.problem.dto.ProblemRegisterDto;
import com.aisip.OnO.backend.problem.entity.Folder;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class FolderProblemFacadeService {

    private final FolderService folderService;

    private final ProblemService problemService;

    public void registerProblemToFolder(ProblemRegisterDto problemRegisterDto, Long userId) {
        Folder folder = folderService.findFolderEntity(problemRegisterDto.folderId());
        problemService.registerProblem(problemRegisterDto, folder, userId);
    }

    public void updateProblemPath(ProblemRegisterDto problemRegisterDto, Long userId) {
        if (problemRegisterDto.folderId() != null) {
            Folder folder = folderService.findFolderEntity(problemRegisterDto.folderId());
            problemService.updateProblemFolder(problemRegisterDto.problemId(), folder, userId);
        }
    }

    public void deleteFoldersWithProblems(List<Long> folderIds, Long userId) {

        // 삭제할 모든 폴더의 ID 조회 (하위 폴더 포함)
        Set<Long> allFolderIds = folderService.getAllFolderIdsIncludingSubFolders(folderIds);

        problemService.deleteAllByFolderIds(allFolderIds);

        folderService.deleteAllByFolderIds(allFolderIds);
    }

    public void deleteAllUserFoldersWithProblems(Long userId) {

        problemService.deleteAllUserProblems(userId);

        folderService.deleteAllUserFolders(userId);
    }
}
