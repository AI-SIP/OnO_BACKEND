package com.aisip.OnO.backend.folder.service;

import com.aisip.OnO.backend.folder.dto.FolderRegisterDto;
import com.aisip.OnO.backend.folder.dto.FolderResponseDto;
import com.aisip.OnO.backend.folder.entity.Folder;
import com.aisip.OnO.backend.folder.repository.FolderRepository;
import com.aisip.OnO.backend.problem.dto.ProblemImageDataRegisterDto;
import com.aisip.OnO.backend.problem.dto.ProblemRegisterDto;
import com.aisip.OnO.backend.problem.dto.ProblemResponseDto;
import com.aisip.OnO.backend.problem.entity.Problem;
import com.aisip.OnO.backend.problem.entity.ProblemImageData;
import com.aisip.OnO.backend.problem.entity.ProblemImageType;
import com.aisip.OnO.backend.problem.service.ProblemService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.util.ReflectionTestUtils.setField;

@SpringBootTest
class FolderServiceTest {

    @InjectMocks
    private FolderService folderService;

    @Mock
    private FolderRepository folderRepository;

    @Mock
    private ProblemService problemService;

    private final Long userId = 1L;

    private List<ProblemResponseDto> problemList;

    private List<Folder> folderList;

    @BeforeEach
    void setUp() {
        problemList = new ArrayList<>();
        folderList = new ArrayList<>();


        /*
           0
        /    \
        1     2
        | \   |
        3, 4  5
         */
        Folder rootFolder = Folder.from(
                new FolderRegisterDto(
                        "rootFolder",
                        null,
                        null
                ),
                null,
                1L
        );
        setField(rootFolder, "id", (long) 0);
        folderList.add(rootFolder);

        for (int i = 0; i < 5; i++) {
            Folder folder = Folder.from(
                    new FolderRegisterDto(
                            "folder " + i,
                            null,
                            (long) i / 2
                    ),
                    folderList.get(i / 2),
                    userId
            );
            setField(folder, "id", (long) i + 1);
            folderList.add(folder);

            folderList.get(i / 2).addSubFolder(folder);
        }

        for (int i = 0; i < 12; i++) {
            Folder targetFolder = folderList.get(i / 2);

            Problem problem = Problem.from(
                            new ProblemRegisterDto(
                                    null,
                                    "memo" + i,
                                    "reference" + i,
                                    targetFolder.getId(),
                                    LocalDateTime.now(),
                                    null
                            ),
                            userId,
                            targetFolder
                    );
            targetFolder.addProblem(problem);

            List<ProblemImageData> imageDataList = new ArrayList<>();
            for (int j = 1; j <= 3; j++){
                ProblemImageDataRegisterDto problemImageDataRegisterDto = new ProblemImageDataRegisterDto(
                        (long) i,
                        "http://example.com/problemId/" + i + "/image" + j,
                        ProblemImageType.valueOf(j)
                );

                ProblemImageData imageData = ProblemImageData.from(problemImageDataRegisterDto, problem);
                imageDataList.add(imageData);
            }
            problem.updateImageDataList(imageDataList);

            ProblemResponseDto problemResponseDto = ProblemResponseDto.from(problem);
            problemList.add(problemResponseDto);
        }
    }

    @AfterEach
    void tearDown() {
        problemList.clear();
        folderList.clear();
    }

    @Test
    @DisplayName("folderId를 사용해 특정 폴더 조회하기 테스트")
    void findFolder() {
        //given
        Long folderId = 1L;
        when(folderRepository.findFolderWithDetailsByFolderId(folderId)).thenReturn(Optional.of(folderList.get(1)));
        when(problemService.findFolderProblemList(folderId)).thenReturn(List.of(problemList.get(2), problemList.get(3)));

        //when
        FolderResponseDto folderResponseDto = folderService.findFolder(folderId);

        //then
        assertThat(folderResponseDto.folderId()).isEqualTo(folderList.get(1).getId());
        assertThat(folderResponseDto.folderName()).isEqualTo(folderList.get(1).getName());
        assertThat(folderResponseDto.parentFolder().folderId()).isEqualTo(0L);
        assertThat(folderResponseDto.subFolderList().get(0).folderId()).isEqualTo(folderList.get(3).getId());
        assertThat(folderResponseDto.subFolderList().get(1).folderId()).isEqualTo(folderList.get(4).getId());
        assertThat(folderResponseDto.problemList().get(0)).isEqualTo(problemList.get(2));
        assertThat(folderResponseDto.problemList().get(1)).isEqualTo(problemList.get(3));
    }

    @Test
    void findFolderEntity() {
        //given
        Long folderId = 0L;
        when(folderRepository.findById(folderId)).thenReturn(Optional.of(folderList.get(0)));

        //when
        Folder folder = folderService.findFolderEntity(folderId);

        //then
        assertThat(folder.getId()).isEqualTo(folderList.get(0).getId());
        assertThat(folder.getName()).isEqualTo(folderList.get(0).getName());
        assertThat(folder.getParentFolder()).isEqualTo(folderList.get(0).getParentFolder());
        assertThat(folder.getSubFolderList().size()).isEqualTo(folderList.get(0).getSubFolderList().size());
    }

    @Test
    void findAllFolderThumbnails() {
    }

    @Test
    void findAllFolders() {
    }

    @Test
    void findRootFolder() {
    }

    @Test
    void createRootFolder() {
    }

    @Test
    void createFolder() {
    }

    @Test
    void updateFolder() {
    }

    @Test
    void deleteFoldersWithProblems() {
    }

    @Test
    void deleteAllUserFoldersWithProblems() {
    }

    @Test
    void getAllFolderIdsIncludingSubFolders() {
    }

    @Test
    void deleteAllByFolderIds() {
    }

    @Test
    void deleteAllUserFolders() {
    }
}