package com.aisip.OnO.backend.service;

import com.aisip.OnO.backend.Dto.Folder.FolderResponseDto;
import com.aisip.OnO.backend.Dto.Folder.FolderThumbnailResponseDto;
import com.aisip.OnO.backend.Dto.Problem.ProblemResponseDto;
import com.aisip.OnO.backend.entity.Folder;
import com.aisip.OnO.backend.entity.Problem;
import com.aisip.OnO.backend.entity.User.User;
import com.aisip.OnO.backend.repository.FolderRepository;
import com.aisip.OnO.backend.repository.ProblemRepository;
import com.aisip.OnO.backend.repository.UserRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional
public class FolderServiceImpl implements FolderService {

    private final UserRepository userRepository;

    private final ProblemService problemService;
    private final ProblemRepository problemRepository;

    private final FolderRepository folderRepository;

    @Override
    public FolderResponseDto findRootFolder(Long userId) {

        Optional<Folder> optionalRootFolder = folderRepository.findByUserIdAndParentFolderIsNull(userId);

        if (optionalRootFolder.isPresent()) {
            return findFolder(userId, optionalRootFolder.get().getId());
        } else {
            return createRootFolder(userId, "메인");
        }
    }

    @Override
    public FolderResponseDto createRootFolder(Long userId, String folderName) {
        Optional<User> optionalUser = userRepository.findById(userId);
        if (optionalUser.isPresent()) {
            Folder rootFolder = Folder.builder().name(folderName).user(optionalUser.get()).parentFolder(null).build();

            folderRepository.save(rootFolder);

            List<Problem> noFolderProblems = problemRepository.findAllByUserIdAndFolderIsNull(userId);

            for (Problem problem : noFolderProblems) {
                problem.setFolder(rootFolder);
                problemRepository.save(problem);
            }

            return findFolder(userId, rootFolder.getId());
        }

        return null;
    }

    @Override
    public FolderResponseDto createFolder(Long userId, String folderName, Long parentFolderId) {
        Optional<User> optionalUser = userRepository.findById(userId);
        if (optionalUser.isPresent()) {
            Folder folder = Folder.builder().name(folderName).user(optionalUser.get()).build();

            if (parentFolderId != null) {
                Optional<Folder> parentFolder = folderRepository.findById(parentFolderId);
                parentFolder.ifPresent(folder::setParentFolder);
            }

            folderRepository.save(folder);

            return findFolder(userId, folder.getId());
        }

        return null;
    }

    @Override
    public FolderResponseDto findFolder(Long userId, Long folderId) {

        Optional<Folder> optionalFolder = folderRepository.findById(folderId);

        if (optionalFolder.isPresent() && optionalFolder.get().getUser().getId().equals(userId)) {

            Folder folder = optionalFolder.get();

            FolderThumbnailResponseDto parentFolder = FolderThumbnailResponseDto.builder().folderId(folder.getParentFolder().getId()).folderName(folder.getParentFolder().getName()).build();

            List<FolderThumbnailResponseDto> subFolders = folder.getSubFolders().stream().map(subFolder -> {
                return FolderThumbnailResponseDto.builder().folderId(subFolder.getId()).folderName(subFolder.getName()).build();
            }).toList();

            List<ProblemResponseDto> problems = problemService.findAllProblemsByFolderId(folderId);

            return FolderResponseDto.builder().folderId(folder.getId()).folderName(folder.getName()).parentFolder(parentFolder).subFolders(subFolders).problems(problems).updateAt(folder.getUpdatedAt()).createdAt(folder.getCreatedAt()).build();
        }

        return null;
    }

    @Override
    public FolderResponseDto updateFolder(Long userId, Long folderId, String folderName, Long parentFolderId) {
        Optional<User> optionalUser = userRepository.findById(userId);
        if (optionalUser.isPresent()) {
            Optional<Folder> optionalFolder = folderRepository.findById(folderId);

            if (optionalFolder.isPresent()) {
                Folder folder = optionalFolder.get();
                folder.setName(folderName);

                Optional<Folder> optionalParentFolder = folderRepository.findById(parentFolderId);
                Folder parentFolder = optionalParentFolder.orElse(null);

                folder.setParentFolder(parentFolder);

                folderRepository.save(folder);

                return findFolder(userId, folderId);
            }
        }

        return null;
    }

    @Override
    public void deleteFolder(Long folderId) {
        folderRepository.deleteById(folderId);
    }

    @Override
    public List<FolderThumbnailResponseDto> findAllFolderThumbnailsByUserId(Long userId) {

        List<Folder> folders = folderRepository.findAllByUserId(userId);

        return folders.stream().map(folder -> {
            return FolderThumbnailResponseDto.builder().folderId(folder.getId()).folderName(folder.getName()).build();
        }).toList();
    }
}
