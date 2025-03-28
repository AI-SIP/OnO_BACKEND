package com.aisip.OnO.backend.folder.service;

import com.aisip.OnO.backend.folder.dto.FolderRegisterDto;
import com.aisip.OnO.backend.folder.dto.FolderResponseDto;
import com.aisip.OnO.backend.folder.dto.FolderThumbnailResponseDto;
import com.aisip.OnO.backend.folder.entity.Folder;
import com.aisip.OnO.backend.folder.repository.FolderRepository;
import com.aisip.OnO.backend.problem.dto.ProblemImageDataRegisterDto;
import com.aisip.OnO.backend.problem.dto.ProblemRegisterDto;
import com.aisip.OnO.backend.problem.dto.ProblemResponseDto;
import com.aisip.OnO.backend.problem.entity.Problem;
import com.aisip.OnO.backend.problem.entity.ProblemImageData;
import com.aisip.OnO.backend.problem.entity.ProblemImageType;
import com.aisip.OnO.backend.problem.service.ProblemService;
import org.junit.jupiter.api.*;
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
    void findRootFolder() {
        //given
        Long folderId = 0L;
        when(folderRepository.findRootFolder(userId)).thenReturn(Optional.of(folderList.get(0)));
        when(problemService.findFolderProblemList(folderId)).thenReturn(List.of(problemList.get(0), problemList.get(1)));

        //when
        FolderResponseDto folderResponseDto = folderService.findRootFolder(userId);

        //then
        assertThat(folderResponseDto.folderId()).isEqualTo(folderList.get(0).getId());
        assertThat(folderResponseDto.folderName()).isEqualTo(folderList.get(0).getName());
        assertThat(folderResponseDto.parentFolder()).isNull();
        assertThat(folderResponseDto.subFolderList().get(0).folderId()).isEqualTo(folderList.get(1).getId());
        assertThat(folderResponseDto.subFolderList().get(1).folderId()).isEqualTo(folderList.get(2).getId());
        assertThat(folderResponseDto.problemList().get(0)).isEqualTo(problemList.get(0));
        assertThat(folderResponseDto.problemList().get(1)).isEqualTo(problemList.get(1));
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
    void findAllUserFolderThumbnails() {
        //given
        when(folderRepository.findAllByUserId(userId)).thenReturn(folderList);

        //when
        List<FolderThumbnailResponseDto> folderThumbnailResponseDtoList = folderService.findAllUserFolderThumbnails(userId);

        //then
        for (int i = 0; i < folderThumbnailResponseDtoList.size(); i++) {
            assertThat(folderThumbnailResponseDtoList.get(i).folderId()).isEqualTo(folderList.get(i).getId());
            assertThat(folderThumbnailResponseDtoList.get(i).folderName()).isEqualTo(folderList.get(i).getName());
        }
    }

    @Test
    void findAllUserFolders() {
        //given
        when(folderRepository.findAllFoldersWithDetailsByUserId(userId)).thenReturn(folderList);
        for (int i = 0; i < folderList.size(); i++) {
            when(problemService.findFolderProblemList((long) i)).thenReturn(List.of(problemList.get(i), problemList.get(i + 1)));
        }

        //when
        List<FolderResponseDto> userFolderList = folderService.findAllUserFolders(userId);

        //then
        assertThat(userFolderList.size()).isEqualTo(folderList.size());
        for (int i = 0; i < userFolderList.size(); i++) {
            assertThat(userFolderList.get(i).folderId()).isEqualTo(folderList.get(i).getId());
            assertThat(userFolderList.get(i).folderName()).isEqualTo(folderList.get(i).getName());
            if (i == 0) {
                assertThat(userFolderList.get(i).parentFolder()).isNull();
            } else{
                assertThat(userFolderList.get(i).parentFolder().folderId()).isEqualTo(folderList.get(i).getParentFolder().getId());
            }

            if (i < 3) {
                assertThat(userFolderList.get(i).subFolderList().get(0).folderId()).isEqualTo(folderList.get(i).getSubFolderList().get(0).getId());
            } else{
                assertThat(userFolderList.get(i).subFolderList()).isEmpty();
            }
        }
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