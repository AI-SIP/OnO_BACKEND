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

    private final Long userId = 1L;
    private List<Problem> problemList;

    private List<Folder> folderList;

    @BeforeEach
    void setUp() {

        problemList = new ArrayList<>();
        folderList = new ArrayList<>();

        // 폴더 2개 생성
        for (int f = 1; f <= 2; f++) {
            Folder folder = Folder.from(
                    new FolderRegisterDto("folder" + f, null, null),
                    null,
                    userId
            );
            folderList.add(folder);
        }

        // 문제 5개 생성 (폴더 1번에 3개, 폴더 2번에 2개)
        for (int i = 1; i <= 5; i++) {
            Folder folder = (i <= 3) ? folderList.get(0) : folderList.get(1);
            Problem problem = Problem.from(
                    new ProblemRegisterDto(
                            (long) i,
                            "memo" + i,
                            "reference" + i,
                            null,
                            LocalDateTime.now(),
                            new ArrayList<>()
                    ),
                    userId,
                    folder
            );

            // 이미지 2개씩 추가
            List<ProblemImageData> imageDataList = List.of(
                    ProblemImageData.from(new ProblemImageDataRegisterDto(null, "url" + i + "_1", ProblemImageType.PROBLEM_IMAGE), problem),
                    ProblemImageData.from(new ProblemImageDataRegisterDto(null, "url" + i + "_2", ProblemImageType.ANSWER_IMAGE), problem)
            );
            problem.updateImageDataList(imageDataList);

            problemList.add(problem);
        }
    }

    @AfterEach
    void tearDown() {
    }

    @Test
    void findProblem() {
        // given
        Long problemId = 200L;
        when(problemRepository.findProblemWithImageData(problemId)).thenReturn(Optional.of(problemList.get(0)));

        // when
        ProblemResponseDto problemResponseDto = problemService.findProblem(problemId, userId);

        // then
        assertThat(problemResponseDto.memo()).isEqualTo("memo1");
        assertThat(problemResponseDto.reference()).isEqualTo("reference1");
        assertThat(problemResponseDto.imageUrlList().size()).isEqualTo(2);
    }

    @Test
    void findUserProblems() {
        //given
        when(problemRepository.findAllByUserId(userId)).thenReturn(problemList);

        //when
        List<ProblemResponseDto> problemResponseDtoList = problemService.findUserProblems(userId);

        //then
        assertThat(problemResponseDtoList.get(0)).isNotNull();
        assertThat(problemResponseDtoList.size()).isEqualTo(5);
        assertThat(problemResponseDtoList.get(0).imageUrlList().size()).isEqualTo(2);
    }

    @Test
    void findFolderProblemList() {
        //given
        Long folderId = 1L;
        when(problemRepository.findAllByFolderId(folderId)).thenReturn(List.of(problemList.get(0), problemList.get(1), problemList.get(2)));

        //when
        List<ProblemResponseDto> problemResponseDtoList = problemService.findFolderProblemList(folderId);

        //then
        assertThat(problemResponseDtoList.get(0)).isNotNull();
        assertThat(problemResponseDtoList.size()).isEqualTo(3);
        assertThat(problemResponseDtoList.get(0).imageUrlList().size()).isEqualTo(2);
    }

    @Test
    void findAllProblems() {
        //given
        when(problemRepository.findAll()).thenReturn(problemList);

        //when
        List<ProblemResponseDto> problemResponseDtoList = problemService.findAllProblems();

        //then
        assertThat(problemResponseDtoList.get(0)).isNotNull();
        assertThat(problemResponseDtoList.size()).isEqualTo(5);
        assertThat(problemResponseDtoList.get(0).imageUrlList().size()).isEqualTo(2);
    }

    @Test
    void findProblemCountByUser() {
        //given
        when(problemRepository.countByUserId(userId)).thenReturn((long) problemList.size());

        //when
        Long problemCount = problemService.findProblemCountByUser(userId);

        //then
        assertThat(problemCount).isEqualTo(5L);
    }

    @Test
    @DisplayName("registerProblem - 정상 케이스")
    void registerProblem_success() {
        // Given
        ProblemRegisterDto dto = new ProblemRegisterDto(
                null,
                "memo",
                "reference",
                1L,
                LocalDateTime.now(),
                List.of(
                        new ProblemImageDataRegisterDto(null, "imageUrl1", ProblemImageType.valueOf(1)),
                        new ProblemImageDataRegisterDto(null, "imageUrl2", ProblemImageType.valueOf(2))
                )
        );

        when(folderRepository.findById(1L)).thenReturn(Optional.of(folderList.get(0)));
        when(problemRepository.save(any(Problem.class))).thenReturn(problemList.get(0));

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
                    return ProblemImageData.from(imageData, problemList.get(0));
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