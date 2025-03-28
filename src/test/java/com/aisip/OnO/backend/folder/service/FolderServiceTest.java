package com.aisip.OnO.backend.folder.service;

import com.aisip.OnO.backend.folder.dto.FolderRegisterDto;
import com.aisip.OnO.backend.folder.entity.Folder;
import com.aisip.OnO.backend.folder.repository.FolderRepository;
import com.aisip.OnO.backend.problem.dto.ProblemImageDataRegisterDto;
import com.aisip.OnO.backend.problem.dto.ProblemRegisterDto;
import com.aisip.OnO.backend.problem.entity.Problem;
import com.aisip.OnO.backend.problem.entity.ProblemImageData;
import com.aisip.OnO.backend.problem.entity.ProblemImageType;
import com.aisip.OnO.backend.problem.repository.ProblemImageDataRepository;
import com.aisip.OnO.backend.problem.repository.ProblemRepository;
import com.aisip.OnO.backend.problem.service.ProblemService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.springframework.test.util.ReflectionTestUtils.setField;

@SpringBootTest
class FolderServiceTest {

    @InjectMocks
    private FolderService folderService;

    @Mock
    private FolderRepository folderRepository;

    @Mock
    private ProblemRepository problemRepository;

    @Mock
    private ProblemImageDataRepository problemImageDataRepository;

    @Mock
    private ProblemService problemService;

    private final Long userId = 1L;

    private List<Problem> problemList;

    private List<Folder> folderList;

    @BeforeEach
    void setUp() {
        problemList = new ArrayList<>();
        folderList = new ArrayList<>();


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
            folderList.add(folder);
        }

        for (int i = 0; i < 12; i++) {
            Folder targetFolder = folderList.get(i / 2);

            Problem problem = problemRepository.save(
                    Problem.from(
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
                    )
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
            problemList.add(problem);
        }
    }

    @AfterEach
    void tearDown() {
        problemList.clear();
        folderList.clear();
    }

    @Test
    void findFolder() {
    }

    @Test
    void findFolderEntity() {
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