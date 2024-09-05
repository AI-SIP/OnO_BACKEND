package com.aisip.OnO.backend.service;

import com.aisip.OnO.backend.Dto.Folder.FolderResponseDto;
import com.aisip.OnO.backend.Dto.Problem.ProblemResponseDto;
import com.aisip.OnO.backend.entity.Folder;
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

    private UserRepository userRepository;

    private ProblemService problemService;
    private ProblemRepository problemRepository;

    private FolderRepository folderRepository;

    @Override
    public FolderResponseDto createRootFolder(Long userId, String folderName) {
        Optional<User> optionalUser = userRepository.findById(userId);
        if (optionalUser.isPresent()) {
            Folder folder = Folder.builder()
                    .name(folderName)
                    .user(optionalUser.get())
                    .parentFolder(null)
                    .build();

            folderRepository.save(folder);

            return findFolder(folder.getId());
        }

        return null;
    }

    @Override
    public FolderResponseDto createFolder(Long userId, String folderName, Long parentFolderId) {
        Optional<User> optionalUser = userRepository.findById(userId);
        if (optionalUser.isPresent()) {
            Folder folder = Folder.builder()
                    .name(folderName)
                    .user(optionalUser.get())
                    .build();

            if (parentFolderId != null) {
                Optional<Folder> parentFolder = folderRepository.findById(parentFolderId);
                parentFolder.ifPresent(folder::setParentFolder);
            }

            folderRepository.save(folder);

            return findFolder(folder.getId());
        }

        return null;
    }

    @Override
    public FolderResponseDto findFolder(Long folderId) {

        Optional<Folder> optionalFolder = folderRepository.findById(folderId);

        if (optionalFolder.isPresent()) {

            Folder folder = optionalFolder.get();
            List<Long> subFoldersId = folder.getSubFolders().stream()
                    .map(Folder::getId)
                    .toList();
            List<ProblemResponseDto> problems = problemService.findAllProblemsByFolderId(folderId);

            return FolderResponseDto.builder()
                    .folderId(folder.getId())
                    .folderName(folder.getName())
                    .parentFolderId(folder.getParentFolder().getId())
                    .subFoldersId(subFoldersId)
                    .problems(problems)
                    .updateAt(folder.getUpdatedAt())
                    .createdAt(folder.getCreatedAt())
                    .build();
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

                return findFolder(folderId);
            }
        }

        return null;
    }

    @Override
    public void deleteFolder(Long folderId) {
        folderRepository.deleteById(folderId);
    }

    @Override
    public List<Folder> getAllFolders() {
        return folderRepository.findAll();
    }
}
