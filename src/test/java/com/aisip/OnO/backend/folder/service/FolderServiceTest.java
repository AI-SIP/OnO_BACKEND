package com.aisip.OnO.backend.folder.service;

import com.aisip.OnO.backend.common.exception.ApplicationException;
import com.aisip.OnO.backend.util.fileupload.service.FileUploadService;
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
import org.springframework.boot.test.mock.mockito.MockBean;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;

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

    @MockBean
    private FileUploadService fileUploadService;

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
                                    LocalDateTime.now()
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
        assertThat(folderResponseDto.problemIdList().get(0)).isEqualTo(problemList.get(0).problemId());
        assertThat(folderResponseDto.problemIdList().get(1)).isEqualTo(problemList.get(1).problemId());
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
        assertThat(folderResponseDto.problemIdList().get(0)).isEqualTo(problemList.get(2).problemId());
        assertThat(folderResponseDto.problemIdList().get(1)).isEqualTo(problemList.get(3).problemId());
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

        assertThat(rootFolder.folderName()).isEqualTo("책장");
        assertThat(rootFolder.parentFolder()).isNull();
        assertThat(rootFolder.subFolderList()).hasSize(1);
        assertThat(rootFolder.subFolderList().get(0).folderName()).isEqualTo("시작하기");
    }

    @Test
    @DisplayName("온보딩 폴더 보장 - 루트가 없으면 루트와 기본 하위 폴더 생성")
    void ensureOnboardingFolders_CreateRootAndDefaultSubFolder() {
        Long newUserId = 999L;

        folderService.ensureOnboardingFolders(newUserId);

        Optional<Folder> optionalRoot = folderRepository.findRootFolder(newUserId);
        assertThat(optionalRoot).isPresent();
        assertThat(optionalRoot.get().getName()).isEqualTo("책장");
        assertThat(optionalRoot.get().getSubFolderList()).hasSize(1);
        assertThat(optionalRoot.get().getSubFolderList().get(0).getName()).isEqualTo("시작하기");
    }

    @Test
    @DisplayName("온보딩 폴더 보장 - 이미 하위 폴더가 있으면 중복 생성하지 않음")
    void ensureOnboardingFolders_NoDuplicateSubFolder() {
        FolderResponseDto root = folderService.createRootFolder(userId);

        folderService.ensureOnboardingFolders(userId);
        folderService.ensureOnboardingFolders(userId);

        Optional<Folder> optionalRoot = folderRepository.findFolderWithDetailsByFolderId(root.folderId());
        assertThat(optionalRoot).isPresent();
        assertThat(optionalRoot.get().getSubFolderList()).hasSize(1);
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

        //when
        folderService.createFolder(folderRegisterDto, userId);
        Optional<Folder> optionalParentFolder = folderRepository.findFolderWithDetailsByFolderId(parentFolderId);
        assertThat(optionalParentFolder.isPresent()).isTrue();

        Folder parentFolder = optionalParentFolder.get();
        assertThat(parentFolder.getSubFolderList().size()).isEqualTo(1);
        assertThat(parentFolder.getSubFolderList().get(0).getName()).isEqualTo(folderName);
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

        //when & then
        assertThatThrownBy(() -> folderService.createFolder(folderRegisterDto, userId))
                .isInstanceOf(ApplicationException.class)
                .hasMessageContaining(FolderErrorCase.FOLDER_NOT_FOUND.getMessage());
    }

    @Test
    @DisplayName("폴더 수정 테스트 - 폴더 이름 수정")
    void updateFolder_FolderName() {
        //given
        Long folderId = folderList.get(1).getId();
        String updateFolderName = "new folder name";
        FolderRegisterDto folderRegisterDto = new FolderRegisterDto(
                updateFolderName,
                folderId,
                null
        );

        //when
        folderService.updateFolder(folderRegisterDto, userId);

        //then
        Optional<Folder> optionalFolder = folderRepository.findById(folderId);
        assertThat(optionalFolder.isPresent()).isTrue();

        Folder folder = optionalFolder.get();
        assertThat(folder.getName()).isEqualTo(updateFolderName);
    }

    @Test
    @DisplayName("폴더 수정 테스트 - 부모 폴더 수정")
    void updateFolder_ParentFolder() {
        //given
        Long oldParentFolderId = folderList.get(0).getId();
        Long folderId = folderList.get(1).getId();
        Long newParentFolderId = folderList.get(2).getId();
        FolderRegisterDto folderRegisterDto = new FolderRegisterDto(
                null,
                folderId,
                newParentFolderId
        );

        folderService.updateFolder(folderRegisterDto, userId);

        Optional<Folder> optionalOldParentFolder = folderRepository.findFolderWithDetailsByFolderId(oldParentFolderId);
        Optional<Folder> optionalFolder = folderRepository.findFolderWithDetailsByFolderId(folderId);
        Optional<Folder> optionalNewParentFolder = folderRepository.findFolderWithDetailsByFolderId(newParentFolderId);

        assertThat(optionalOldParentFolder.isPresent()).isTrue();
        assertThat(optionalFolder.isPresent()).isTrue();
        assertThat(optionalNewParentFolder.isPresent()).isTrue();

        Folder oldParentFolder = optionalOldParentFolder.get();
        Folder folder = optionalFolder.get();
        Folder newParentFolder = optionalNewParentFolder.get();

        assertThat(oldParentFolder.getSubFolderList().size()).isEqualTo(1);
        assertThat(folder.getParentFolder().getId()).isEqualTo(newParentFolderId);
        assertThat(newParentFolder.getSubFolderList().size()).isEqualTo(2);
    }

    @Test
    @DisplayName("폴더 수정 테스트 - 부모 폴더가 존재하지 않을 경우")
    void updateFolder_ParentFolderNotExist() {
        //given
        Long folderId = folderList.get(1).getId();
        Long newParentFolderId = 100L;
        FolderRegisterDto folderRegisterDto = new FolderRegisterDto(
                null,
                folderId,
                newParentFolderId
        );

        //when & then
        assertThatThrownBy(() -> folderService.updateFolder(folderRegisterDto, userId))
                .isInstanceOf(ApplicationException.class)
                .hasMessageContaining(FolderErrorCase.FOLDER_NOT_FOUND.getMessage());
    }

    @Test
    @DisplayName("폴더 삭제 테스트 - 루트 폴더 삭제 시 예외")
    void deleteFolders_rootFolder() {
        List<Long> folderIdList = List.of(folderList.get(0).getId());
        doNothing().when(fileUploadService).deleteImageFileFromS3(anyString());

        // when & then
        assertThatThrownBy(() -> folderService.deleteFoldersWithProblems(folderIdList))
                .isInstanceOf(ApplicationException.class)
                .hasMessageContaining(FolderErrorCase.ROOT_FOLDER_CANNOT_REMOVE.getMessage());

        assertThat(folderRepository.findAllByUserId(userId).size()).isEqualTo(folderList.size());
    }

    @Test
    @DisplayName("폴더 삭제 테스트 - 루트 폴더 제외 최상위 폴더 모두 삭제")
    void deleteFolders_AllParentFolder() {
        // given
        List<Long> folderIdList = List.of(folderList.get(1).getId(), folderList.get(2).getId());
        doNothing().when(fileUploadService).deleteImageFileFromS3(anyString());

        // when
        folderService.deleteFoldersWithProblems(folderIdList);

        // then
        assertThat(folderRepository.findAllByUserId(userId).size()).isEqualTo(1);
    }

    @Test
    @DisplayName("폴더 삭제 테스트 - 특정 중간 폴더 삭제")
    void deleteFolders_InternalFolder() {
        // given
        List<Long> folderIdList = List.of(folderList.get(1).getId());
        doNothing().when(fileUploadService).deleteImageFileFromS3(anyString());

        // when
        folderService.deleteFoldersWithProblems(folderIdList);

        // then
        assertThat(folderRepository.findAllByUserId(userId).size()).isEqualTo(3);

        Optional<Folder> optionalRootFolder = folderRepository.findFolderWithDetailsByFolderId(folderList.get(0).getId());
        assertThat(optionalRootFolder.isPresent()).isTrue();

        Folder rootFolder = optionalRootFolder.get();
        assertThat(rootFolder.getSubFolderList().size()).isEqualTo(1);
    }

    @Test
    @DisplayName("폴더 삭제 테스트 - 유저의 모든 폴더 삭제")
    void deleteFolders_AllUserFolders() {
        // given
        doNothing().when(fileUploadService).deleteImageFileFromS3(anyString());

        // when
        folderService.deleteAllUserFolders(userId);

        // then
        assertThat(folderRepository.findAllByUserId(userId)).isEmpty();
    }
}
