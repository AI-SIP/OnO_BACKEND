package com.aisip.OnO.backend.problem.service;

import com.aisip.OnO.backend.common.exception.ApplicationException;
import com.aisip.OnO.backend.fileupload.service.FileUploadService;
import com.aisip.OnO.backend.folder.dto.FolderRegisterDto;
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
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@SpringBootTest
class ProblemServiceTest {

    @InjectMocks
    private ProblemService problemService;

    @Mock
    private ProblemRepository problemRepository;

    @Mock
    private ProblemImageDataRepository problemImageDataRepository;

    @Mock
    private FolderRepository folderRepository;

    @Mock
    private FileUploadService fileUploadService;

    private Long userId = 1L;
    private Folder existFolder;
    private Problem existProblem;

    private List<ProblemImageData> existImageDataList;

    @BeforeEach
    void setUp() {

        FolderRegisterDto folderRegisterDto = new FolderRegisterDto(
            "folder",
            null,
            null
        );
        existFolder = Folder.from(folderRegisterDto, null, userId);

        ProblemRegisterDto problemRegisterDto = new ProblemRegisterDto(
                200L,
                "memo",
                "reference",
                null,
                LocalDateTime.now(),
                new ArrayList<>()
        );
        existProblem = Problem.from(problemRegisterDto, userId, existFolder);

        List<ProblemImageDataRegisterDto> imageDataRegisterDtoList = List.of(
                new ProblemImageDataRegisterDto(null, "imageUrl1", ProblemImageType.valueOf(1)),
                new ProblemImageDataRegisterDto(null, "imageUrl2", ProblemImageType.valueOf(2))
        );

        existImageDataList = imageDataRegisterDtoList.stream()
                .map(imageData -> ProblemImageData.from(imageData, existProblem)).toList();

        existProblem.updateImageDataList(existImageDataList);
    }

    @AfterEach
    void tearDown() {
    }

    @Test
    void findProblem() {
        // given
        Long problemId = 200L;
        when(problemRepository.findProblemWithImageData(problemId)).thenReturn(Optional.of(existProblem));

        // when
        ProblemResponseDto problemResponseDto = problemService.findProblem(problemId, userId);

        // then
        assertThat(problemResponseDto.memo()).isEqualTo("memo");
        assertThat(problemResponseDto.reference()).isEqualTo("reference");
        assertThat(problemResponseDto.imageUrlList().size()).isEqualTo(2);
    }

    @Test
    void findUserProblems() {
    }

    @Test
    void findFolderProblemList() {
    }

    @Test
    void findAllProblems() {
    }

    @Test
    void findProblemCountByUser() {
    }

    @Test
    @DisplayName("registerProblem - 정상 케이스")
    void registerProblem_success() {
        // Given
        ProblemRegisterDto dto = new ProblemRegisterDto(
                null,
                "memo",
                "reference",
                existFolder.getId(),
                LocalDateTime.now(),
                List.of(
                        new ProblemImageDataRegisterDto(null, "imageUrl1", ProblemImageType.valueOf(1)),
                        new ProblemImageDataRegisterDto(null, "imageUrl2", ProblemImageType.valueOf(2))
                )
        );

        when(folderRepository.findById(existFolder.getId())).thenReturn(Optional.of(existFolder));
        when(problemRepository.save(any(Problem.class))).thenReturn(existProblem);

        // When
        problemService.registerProblem(dto, userId);

        // Then
        verify(problemRepository).save(any(Problem.class));
        verify(problemImageDataRepository, times(2)).save(any(ProblemImageData.class));
    }

    @Test
    @DisplayName("registerProblem - 존재하지 않는 폴더 예외")
    void registerProblem_folderNotFound() {
        // Given
        ProblemRegisterDto dto = new ProblemRegisterDto(
                null, "memo", "reference", 999L, LocalDateTime.now(), null
        );

        // when
        when(folderRepository.findById(dto.folderId())).thenReturn(Optional.empty());

        // Then
        assertThatThrownBy(() -> problemService.registerProblem(dto, userId))
                .isInstanceOf(ApplicationException.class)
                .hasMessageContaining(FolderErrorCase.FOLDER_NOT_FOUND.getMessage());
    }

    @Test
    void registerProblemImageData() {
    }

    @Test
    void updateProblemInfo() {
    }

    @Test
    void updateProblemFolder() {
    }

    @Test
    void deleteProblems() {
    }

    @Test
    @DisplayName("deleteProblem - 정상 케이스")
    void deleteProblem_success() {
        // Given
        Long problemId = 123L;

        List<ProblemImageDataRegisterDto> imageDataRegisterDtoList = List.of(
                new ProblemImageDataRegisterDto(null, "imageUrl1", ProblemImageType.valueOf(1)),
                new ProblemImageDataRegisterDto(null, "imageUrl2", ProblemImageType.valueOf(2))
        );

        List<ProblemImageData> imageDataList = imageDataRegisterDtoList.stream()
                .map(imageData -> {
                    return ProblemImageData.from(imageData, existProblem);
                }).collect(Collectors.toList());

        when(problemImageDataRepository.findAllByProblemId(problemId)).thenReturn(imageDataList);

        // When
        problemService.deleteProblem(problemId);

        // Then
        verify(fileUploadService, times(2)).deleteImageFileFromS3(anyString());
        verify(problemImageDataRepository, times(2)).delete(any(ProblemImageData.class));
        verify(problemRepository).deleteById(problemId);
    }

    @Test
    void deleteProblemImageData() {
    }

    @Test
    void deleteProblemList() {
    }

    @Test
    void deleteFolderProblems() {
    }

    @Test
    void deleteAllByFolderIds() {
    }

    @Test
    void deleteAllUserProblems() {
    }
}