package com.aisip.OnO.backend.folder.repository;

import com.aisip.OnO.backend.folder.dto.FolderRegisterDto;
import com.aisip.OnO.backend.folder.entity.Folder;
import com.aisip.OnO.backend.problem.dto.ProblemImageDataRegisterDto;
import com.aisip.OnO.backend.problem.dto.ProblemRegisterDto;
import com.aisip.OnO.backend.problem.entity.Problem;
import com.aisip.OnO.backend.problem.entity.ProblemImageData;
import com.aisip.OnO.backend.problem.entity.ProblemImageType;
import com.aisip.OnO.backend.problem.repository.ProblemImageDataRepository;
import com.aisip.OnO.backend.problem.repository.ProblemRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@ExtendWith(SpringExtension.class)
class FolderRepositoryTest {

    @Autowired
    private FolderRepository folderRepository;

    @Autowired
    private ProblemRepository problemRepository;

    @Autowired
    private ProblemImageDataRepository problemImageDataRepository;

    @Autowired
    private EntityManager em;

    private final Long userId = 1L;

    @BeforeEach
    void setUp() {
        FolderRegisterDto rootFolderRegisterDto = new FolderRegisterDto(
                "rootFolder",
                null,
                null
        );
        Folder rootFolder = folderRepository.save(Folder.from(
                rootFolderRegisterDto,
                null,
                1L
        ));
        Folder firstLevelFolder1 = folderRepository.save(Folder.from(
                new FolderRegisterDto(
                        "firstLevelFolder1",
                        null,
                        rootFolder.getId()
                ),
                rootFolder,
                1L
        ));
        Folder firstLevelFolder2 = folderRepository.save(Folder.from(
                new FolderRegisterDto(
                        "firstLevelFolder2",
                        null,
                        rootFolder.getId()
                ),
                rootFolder,
                1L
        ));
        Folder secondLevelFolder1 = folderRepository.save(Folder.from(
                new FolderRegisterDto(
                        "secondLevelFolder1",
                        null,
                        firstLevelFolder1.getId()
                ),
                firstLevelFolder1,
                1L
        ));
        Folder secondLevelFolder2 = folderRepository.save(Folder.from(
                new FolderRegisterDto(
                        "secondLevelFolder2",
                        null,
                        firstLevelFolder1.getId()
                ),
                firstLevelFolder1,
                1L
        ));
        Folder secondLevelFolder3 = folderRepository.save(Folder.from(
                new FolderRegisterDto(
                        "secondLevelFolder3",
                        null,
                        firstLevelFolder2.getId()
                ),
                firstLevelFolder2,
                1L
        ));

        for (int i = 1; i <= 14; i++) {
            Folder targetFolder;
            if (i <= 2) {
                targetFolder = rootFolder;
            } else if (i <= 4) {
                targetFolder = firstLevelFolder1;
            } else if (i <= 6){
                targetFolder = firstLevelFolder2;
            } else if (i <= 8) {
                targetFolder = secondLevelFolder1;
            } else if (i <= 10) {
                targetFolder = secondLevelFolder2;
            } else {
                targetFolder = secondLevelFolder3;
            }

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
            problemImageDataRepository.saveAll(imageDataList);
            problem.updateImageDataList(imageDataList);
        }

        em.flush();
        em.clear();
    }

    @AfterEach
    void tearDown() {
        folderRepository.deleteAll();
    }

    @Test
    void test() {

    }
}