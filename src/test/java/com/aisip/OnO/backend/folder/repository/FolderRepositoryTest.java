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
import org.junit.jupiter.api.DisplayName;
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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

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

    private List<Folder> folderList;

    @BeforeEach
    void setUp() {
        folderList = new ArrayList<>();
        Folder rootFolder = folderRepository.save(Folder.from(
                new FolderRegisterDto(
                        "rootFolder",
                        null,
                        null
                ),
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
        folderList.addAll(List.of(rootFolder, firstLevelFolder1, firstLevelFolder2, secondLevelFolder1, secondLevelFolder2, secondLevelFolder3));

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
                            userId
                    )
            );
            problem.updateFolder(targetFolder);

            List<ProblemImageData> imageDataList = new ArrayList<>();
            for (int j = 1; j <= 3; j++){
                ProblemImageDataRegisterDto problemImageDataRegisterDto = new ProblemImageDataRegisterDto(
                        (long) i,
                        "http://example.com/problemId/" + i + "/image" + j,
                        ProblemImageType.valueOf(j)
                );

                ProblemImageData imageData = ProblemImageData.from(problemImageDataRegisterDto);
                imageData.updateProblem(problem);
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
        folderList.clear();
    }

    @Test
    @DisplayName("root folder 찾기 테스트")
    void findRootFolderTest() {
        //given

        //when
        Optional<Folder> optionalFolder = folderRepository.findRootFolder(userId);

        //then
        if (optionalFolder.isPresent()) {
            Folder rootFolder = optionalFolder.get();
            assertThat(rootFolder.getId()).isNotNull();
            assertThat(rootFolder.getName()).isEqualTo("rootFolder");
        } else{
            assertThat(0L).isEqualTo(1L);
        }
    }

    @Test
    @DisplayName("루트 폴더의 문제, 하위 폴더를 포함한 정보 찾기 테스트")
    void findFolderWithDetailsByFolderIdTest_Root () {
        //given
        Long folderId = folderList.get(0).getId();

        //when
        Optional<Folder> optionalFolder = folderRepository.findFolderWithDetailsByFolderId(folderId);

        //then
        if (optionalFolder.isPresent()) {
            Folder folder = optionalFolder.get();
            assertThat(folder.getId()).isNotNull();
            assertThat(folder.getName()).isEqualTo("rootFolder");
            assertThat(folder.getParentFolder()).isNull();
            assertThat(folder.getSubFolderList().size()).isEqualTo(2);
        } else{
            assertThat(0L).isEqualTo(1L);
        }
    }

    @Test
    @DisplayName("중간 폴더의 문제, 하위 폴더를 포함한 정보 찾기 테스트")
    void findFolderWithDetailsByFolderIdTest_Internal () {
        //given
        Long folderId = folderList.get(1).getId();

        //when
        Optional<Folder> optionalFolder = folderRepository.findFolderWithDetailsByFolderId(folderId);

        //then
        if (optionalFolder.isPresent()) {
            Folder folder = optionalFolder.get();
            assertThat(folder.getId()).isNotNull();
            assertThat(folder.getName()).isEqualTo("firstLevelFolder1");
            assertThat(folder.getParentFolder().getId()).isEqualTo(folderList.get(0).getId());
            assertThat(folder.getSubFolderList().size()).isEqualTo(2);
        } else{
            assertThat(0L).isEqualTo(1L);
        }
    }

    @Test
    @DisplayName("최하위 폴더의 문제, 하위 폴더를 포함한 정보 찾기 테스트")
    void findFolderWithDetailsByFolderIdTest_Leaf () {
        //given
        Long folderId = folderList.get(folderList.size() - 1).getId();

        //when
        Optional<Folder> optionalFolder = folderRepository.findFolderWithDetailsByFolderId(folderId);

        //then
        if (optionalFolder.isPresent()) {
            Folder folder = optionalFolder.get();
            assertThat(folder.getId()).isNotNull();
            assertThat(folder.getName()).isEqualTo("secondLevelFolder3");
            assertThat(folder.getParentFolder().getId()).isEqualTo(folderList.get(2).getId());
            assertThat(folder.getSubFolderList().size()).isEqualTo(0);
        } else{
            assertThat(0L).isEqualTo(1L);
        }
    }

    @Test
    @DisplayName("유저의 모든 폴더 조회 테스트")
    void findAllFoldersWithDetailsByUserIdTest () {
        //given

        //when
        List<Folder> userFolderList = folderRepository.findAllFoldersWithDetailsByUserId(userId);

        //then
        if (!userFolderList.isEmpty()) {
            for (int i = 0; i < userFolderList.size(); i++) {
                assertThat(userFolderList.get(i).getId()).isEqualTo(folderList.get(i).getId());
            }
        } else{
            assertThat(0L).isEqualTo(1L);
        }
    }
}