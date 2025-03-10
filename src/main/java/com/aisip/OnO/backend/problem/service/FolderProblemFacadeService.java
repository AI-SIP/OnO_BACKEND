package com.aisip.OnO.backend.problem.service;

import com.aisip.OnO.backend.common.exception.ApplicationException;
import com.aisip.OnO.backend.problem.dto.ProblemRegisterDto;
import com.aisip.OnO.backend.problem.entity.Folder;
import com.aisip.OnO.backend.problem.entity.Problem;
import com.aisip.OnO.backend.problem.exception.FolderErrorCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class FolderProblemFacadeService {

    private final FolderService folderService;

    private final ProblemService problemService;

    public void registerProblem(ProblemRegisterDto problemRegisterDto, Long userId) {
        Folder folder = folderService.findFolderEntity(problemRegisterDto.folderId());
        problemService.registerProblem(problemRegisterDto, folder, userId);
    }

    public void updateProblem(ProblemRegisterDto problemRegisterDto, Long userId) {
        problemService.updateProblemInfo(problemRegisterDto, userId);

        if (problemRegisterDto.folderId() != null) {
            Folder folder = folderService.findFolderEntity(problemRegisterDto.folderId());
            problemService.updateProblemFolder(problemRegisterDto.problemId(), folder, userId);
        }
    }

    public void deleteFolderAndProblem(Long folderId) {
        problemService.deleteFolderProblems(folderId);
        folderService.deleteFolder(folderId);
    }

    public void deleteFolderWithProblem(Long folderId) {
        Folder folder = folderService.findFolderEntity(folderId);
        Long parentFolderId = folder.getParentFolder().getId();

        if (parentFolderId == null) {
            throw new ApplicationException(FolderErrorCase.ROOT_FOLDER_CANNOT_REMOVE);
        }

        // 재귀적으로 하위 폴더를 삭제
        deleteSubFolders(folder);

        folderService.deleteFolder(folderId);
    }

    public void deleteFolderList(List<Long> folderIdList) {
        folderIdList.forEach(this::deleteFolderAndProblem);
    }

    private void deleteSubFolders(Folder folder) {
        // 하위 폴더들을 먼저 삭제
        if (folder.getSubFolderList() != null && !folder.getSubFolderList().isEmpty()) {
            for (Folder subFolder : folder.getSubFolderList()) {
                deleteSubFolders(subFolder); // 재귀적으로 하위 폴더 삭제
            }
        }

        if (folder.getProblemList() != null && !folder.getProblemList().isEmpty()) {
            for (Problem problem : folder.getProblemList()) {
                problemService.deleteProblem(problem.getId());
            }
        }

        folder.getSubFolderList().clear();

        folderService.deleteFolder(folder.getId());
    }
}
