package com.aisip.OnO.backend.folder.service;

import com.aisip.OnO.backend.common.exception.ApplicationException;
import com.aisip.OnO.backend.folder.dto.FolderRegisterDto;
import com.aisip.OnO.backend.folder.dto.FolderResponseDto;
import com.aisip.OnO.backend.folder.dto.FolderThumbnailResponseDto;
import com.aisip.OnO.backend.folder.entity.Folder;
import com.aisip.OnO.backend.folder.exception.FolderErrorCase;
import com.aisip.OnO.backend.folder.repository.FolderRepository;
import com.aisip.OnO.backend.problem.dto.ProblemImageDataRegisterDto;
import com.aisip.OnO.backend.problem.dto.ProblemRegisterDto;
import com.aisip.OnO.backend.problem.dto.ProblemResponseDto;
import com.aisip.OnO.backend.problem.entity.Problem;
import com.aisip.OnO.backend.problem.entity.ProblemImageData;
import com.aisip.OnO.backend.problem.entity.ProblemImageType;
import com.aisip.OnO.backend.problem.repository.ProblemImageDataRepository;
import com.aisip.OnO.backend.problem.repository.ProblemRepository;
import com.aisip.OnO.backend.problem.service.ProblemService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest
class FolderServiceTest {

    @Autowired
    private FolderService folderService;

    @Autowired
    private FolderRepository folderRepository;

    @Autowired
    private ProblemService problemService;

    @Autowired
    private ProblemRepository problemRepository;

    @Autowired
    private ProblemImageDataRepository problemImageDataRepository;

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
        folderRepository.save(rootFolder);
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
            folder.updateParentFolder(folderList.get(i / 2));
            folderRepository.save(folder);
            folderList.add(folder);
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
                            userId
                    );
            problem.updateFolder(targetFolder);
            problemRepository.save(problem);

            for (int j = 1; j <= 3; j++){
                ProblemImageDataRegisterDto problemImageDataRegisterDto = new ProblemImageDataRegisterDto(
                        (long) i,
                        "http://example.com/problemId/" + i + "/image" + j,
                        ProblemImageType.valueOf(j)
                );

                ProblemImageData imageData = ProblemImageData.from(problemImageDataRegisterDto);
                imageData.updateProblem(problem);
                problemImageDataRepository.save(imageData);
            }

            ProblemResponseDto problemResponseDto = ProblemResponseDto.from(problem);
            problemList.add(problemResponseDto);
        }
    }

    @AfterEach
    void tearDown() {
        problemRepository.deleteAll();
        problemRepository.deleteAll();
        folderRepository.deleteAll();

        problemList.clear();
        folderList.clear();
    }

    @Test
    void findRootFolder() {
        //given
        Long folderId = folderList.get(0).getId();

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
    @DisplayName("folderId를 사용해 특정 폴더 response dto 조회하기 테스트")
    void findFolder() {
        //given
        Long parentFolderId = folderList.get(0).getId();
        Long folderId = folderList.get(1).getId();

        //when
        FolderResponseDto folderResponseDto = folderService.findFolder(folderId);

        //then
        assertThat(folderResponseDto.folderId()).isEqualTo(folderList.get(1).getId());
        assertThat(folderResponseDto.folderName()).isEqualTo(folderList.get(1).getName());
        assertThat(folderResponseDto.parentFolder().folderId()).isEqualTo(parentFolderId);
        assertThat(folderResponseDto.subFolderList().get(0).folderId()).isEqualTo(folderList.get(3).getId());
        assertThat(folderResponseDto.subFolderList().get(1).folderId()).isEqualTo(folderList.get(4).getId());
        assertThat(folderResponseDto.problemList().get(0)).isEqualTo(problemList.get(2));
        assertThat(folderResponseDto.problemList().get(1)).isEqualTo(problemList.get(3));
    }

    @Test
    @DisplayName("folderId를 사용해 특정 폴더 엔티티 조회하기 테스트")
    void findFolderEntity() {
        //given
        Long folderId = folderList.get(0).getId();

        //when
        Folder folder = folderService.findFolderEntity(folderId);

        //then
        assertThat(folder.getId()).isEqualTo(folderList.get(0).getId());
        assertThat(folder.getName()).isEqualTo(folderList.get(0).getName());
        assertThat(folder.getParentFolder()).isEqualTo(folderList.get(0).getParentFolder());
    }

    @Test
    @DisplayName("특정 유저의 모든 폴더 썸네일 조회하기 테스트")
    void findAllUserFolderThumbnails() {
        //given

        //when
        List<FolderThumbnailResponseDto> folderThumbnailResponseDtoList = folderService.findAllUserFolderThumbnails(userId);

        //then
        for (int i = 0; i < folderThumbnailResponseDtoList.size(); i++) {
            assertThat(folderThumbnailResponseDtoList.get(i).folderId()).isEqualTo(folderList.get(i).getId());
            assertThat(folderThumbnailResponseDtoList.get(i).folderName()).isEqualTo(folderList.get(i).getName());
        }
    }

    @Test
    @DisplayName("특정 유저의 모든 폴더 조회하기 테스트")
    void findAllUserFolders() {
        //given

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
    @DisplayName("루트 폴더 생성 로직 테스트")
    void createRootFolder() {
        //when
        FolderResponseDto rootFolder = folderService.createRootFolder(userId);

        assertThat(rootFolder.folderName()).isEqualTo("메인");
        assertThat(rootFolder.parentFolder()).isNull();
        assertThat(rootFolder.subFolderList()).isEmpty();
    }

    @Test
    @DisplayName("폴더 생성 테스트 - 정상 등록")
    void createFolder_FolderExists() {
        //given
        Long parentFolderId = folderList.get(folderList.size() - 1).getId();
        String folderName = "new folder";
        FolderRegisterDto folderRegisterDto = new FolderRegisterDto(
                folderName,
                null,
                parentFolderId
        );
        when(folderRepository.findById(parentFolderId)).thenReturn(Optional.of(folderList.get(folderList.size() - 1)));

        //when
        folderService.createFolder(folderRegisterDto, userId);

        verify(folderRepository).save(any(Folder.class));
        assertThat(folderList.get(folderList.size() - 1).getSubFolderList().size()).isEqualTo(1);
        assertThat(folderList.get(folderList.size() - 1).getSubFolderList().get(0).getName()).isEqualTo(folderName);
    }

    @Test
    @DisplayName("폴더 생성 테스트 - 부모 폴더가 존재하지 않는 경우")
    void createFolder_FolderNotExists() {
        //given
        Long parentFolderId = 200L;
        FolderRegisterDto folderRegisterDto = new FolderRegisterDto(
                "new folder",
                null,
                parentFolderId
        );
        when(folderRepository.findById(parentFolderId)).thenReturn(Optional.empty());

        //when & then
        assertThatThrownBy(() -> folderService.createFolder(folderRegisterDto, userId))
                .isInstanceOf(ApplicationException.class)
                .hasMessageContaining(FolderErrorCase.FOLDER_NOT_FOUND.getMessage());
    }

    @Test
    @DisplayName("폴더 수정 테스트 - 폴더 이름 수정")
    void updateFolder_FolderName() {
        //given
        Long folderId = 1L;
        String updateFolderName = "new folder name";
        FolderRegisterDto folderRegisterDto = new FolderRegisterDto(
                updateFolderName,
                folderId,
                null
        );
        when(folderRepository.findById(folderId)).thenReturn(Optional.of(folderList.get(folderId.intValue())));

        //when
        folderService.updateFolder(folderRegisterDto, userId);

        //then
        assertThat(folderList.get(folderId.intValue()).getName()).isEqualTo(updateFolderName);
    }

    @Test
    @DisplayName("폴더 수정 테스트 - 부모 폴더 수정")
    void updateFolder_ParentFolder() {
        //given
        Long folderId = 1L;
        Long newParentFolderId = 2L;
        FolderRegisterDto folderRegisterDto = new FolderRegisterDto(
                null,
                folderId,
                newParentFolderId
        );
        when(folderRepository.findById(folderId)).thenReturn(Optional.of(folderList.get(folderId.intValue())));
        when(folderRepository.findById(newParentFolderId)).thenReturn(Optional.of(folderList.get(newParentFolderId.intValue())));

        folderService.updateFolder(folderRegisterDto, userId);
        assertThat(folderList.get(0).getSubFolderList().size()).isEqualTo(1);
        assertThat(folderList.get(folderId.intValue()).getParentFolder().getId()).isEqualTo(folderList.get(newParentFolderId.intValue()).getId());
        assertThat(folderList.get(newParentFolderId.intValue()).getSubFolderList().size()).isEqualTo(2);
    }

    @Test
    @DisplayName("폴더 수정 테스트 - 부모 폴더가 존재하지 않을 경우")
    void updateFolder_ParentFolderNotExist() {
        //given
        Long folderId = 1L;
        Long newParentFolderId = 100L;
        FolderRegisterDto folderRegisterDto = new FolderRegisterDto(
                null,
                folderId,
                newParentFolderId
        );
        when(folderRepository.findById(folderId)).thenReturn(Optional.of(folderList.get(folderId.intValue())));
        when(folderRepository.findById(newParentFolderId)).thenReturn(Optional.empty());

        //when & then
        assertThatThrownBy(() -> folderService.updateFolder(folderRegisterDto, userId))
                .isInstanceOf(ApplicationException.class)
                .hasMessageContaining(FolderErrorCase.FOLDER_NOT_FOUND.getMessage());
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