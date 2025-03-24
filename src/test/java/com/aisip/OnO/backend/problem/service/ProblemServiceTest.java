package com.aisip.OnO.backend.problem.service;

import com.aisip.OnO.backend.common.exception.ApplicationException;
import com.aisip.OnO.backend.fileupload.service.FileUploadService;
import com.aisip.OnO.backend.folder.dto.FolderRegisterDto;
import com.aisip.OnO.backend.folder.entity.Folder;
import com.aisip.OnO.backend.folder.exception.FolderErrorCase;
import com.aisip.OnO.backend.folder.repository.FolderRepository;
import com.aisip.OnO.backend.problem.dto.ProblemDeleteRequestDto;
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
import static org.springframework.test.util.ReflectionTestUtils.setField;

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
            setField(folder, "id", (long) f);
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
            setField(problem, "id", (long) i);

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
    void updateProblemInfo() {
        Long problemId = 1L;

        // Given
        when(problemRepository.findById(problemId)).thenReturn(Optional.of(problemList.get(0)));

        ProblemRegisterDto updateDto = new ProblemRegisterDto(
                problemId,
                "update memo",
                "update reference",
                1L,
                LocalDateTime.now(),
                List.of(
                        new ProblemImageDataRegisterDto(null, "imageUrl1", ProblemImageType.valueOf(1)),
                        new ProblemImageDataRegisterDto(null, "imageUrl2", ProblemImageType.valueOf(2))
                )
        );

        Problem target = problemList.get(0);

        //when
        problemService.updateProblemInfo(updateDto, userId);

        //then
        assertThat(target.getMemo()).isEqualTo("update memo");
        assertThat(target.getReference()).isEqualTo("update reference");
    }

    @Test
    void updateProblemFolder() {
        Long problemId = 1L;
        Long folderId = 2L;

        // Given
        when(problemRepository.findById(problemId)).thenReturn(Optional.of(problemList.get(0)));
        when(folderRepository.findById(folderId)).thenReturn(Optional.of(folderList.get(1)));

        ProblemRegisterDto updateDto = new ProblemRegisterDto(
                problemId,
                "update memo",
                "update reference",
                2L,
                LocalDateTime.now(),
                List.of(
                        new ProblemImageDataRegisterDto(null, "imageUrl1", ProblemImageType.valueOf(1)),
                        new ProblemImageDataRegisterDto(null, "imageUrl2", ProblemImageType.valueOf(2))
                )
        );

        Problem target = problemList.get(0);

        //when
        problemService.updateProblemFolder(updateDto, userId);

        // Then
        assertThat(target.getFolder()).isEqualTo(folderList.get(1));
    }

    @Test
    void updateProblemImageData() {
        // Given
        Long problemId = 1L;

        when(problemRepository.findById(problemId)).thenReturn(Optional.of(problemList.get(0)));

        ProblemRegisterDto updateDto = new ProblemRegisterDto(
                problemId,
                "update memo",
                "update reference",
                2L,
                LocalDateTime.now(),
                List.of(
                        new ProblemImageDataRegisterDto(problemId, "imageUrl1 update", ProblemImageType.valueOf(1)),
                        new ProblemImageDataRegisterDto(problemId, "imageUrl2 update", ProblemImageType.valueOf(2))
                )
        );

        Problem target = problemList.get(0);

        //when
        problemService.updateProblemImageData(updateDto, userId);

        //then
        assertThat(target.getProblemImageDataList().size()).isEqualTo(2);
        assertThat(target.getProblemImageDataList().get(0).getImageUrl()).isEqualTo("imageUrl1 update");
        assertThat(target.getProblemImageDataList().get(1).getImageUrl()).isEqualTo("imageUrl2 update");
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
                .map(imageData -> ProblemImageData.from(imageData, problemList.get(0))).collect(Collectors.toList());

        when(problemImageDataRepository.findAllByProblemId(problemId)).thenReturn(imageDataList);

        // When
        problemService.deleteProblem(problemId);

        // Then
        verify(fileUploadService, times(2)).deleteImageFileFromS3(anyString());
        verify(problemImageDataRepository, times(2)).delete(any(ProblemImageData.class));
        verify(problemRepository).deleteById(problemId);
    }

    @Test
    @DisplayName("deleteProblems - 특정 유저의 모든 문제 삭제하기")
    void deleteProblems_userId() {
        // given
        ProblemDeleteRequestDto problemDeleteRequestDto = new ProblemDeleteRequestDto(
                userId,
                null,
                null
        );

        when(problemRepository.findAllByUserId(userId)).thenReturn(problemList);
        for (int i = 0; i < problemList.size(); i++) {
            Long problemId = (long) i + 1;
            when(problemImageDataRepository.findAllByProblemId(problemId)).thenReturn(problemList.get(i).getProblemImageDataList());
        }

        // when
        problemService.deleteProblems(problemDeleteRequestDto);

        // then
        verify(problemRepository).findAllByUserId(userId);
        verify(fileUploadService, times(2 * problemList.size())).deleteImageFileFromS3(anyString());
        verify(problemImageDataRepository, times(2 * problemList.size())).delete(any(ProblemImageData.class));
        verify(problemRepository, times(problemList.size())).deleteById(anyLong());
    }

    @Test
    @DisplayName("deleteProblems - 삭제할 문제 목록을 전달받아 삭제하기")
    void deleteProblems_problemIdList() {
        // given
        List<Long> problemIdList = List.of(1L, 2L, 3L);

        ProblemDeleteRequestDto problemDeleteRequestDto = new ProblemDeleteRequestDto(
                null,
                problemIdList,
                null
        );

        for (int i = 0; i < problemList.size(); i++) {
            Long problemId = (long) i + 1;
            when(problemImageDataRepository.findAllByProblemId(problemId)).thenReturn(problemList.get(i).getProblemImageDataList());
        }

        // when
        problemService.deleteProblems(problemDeleteRequestDto);

        // then
        verify(fileUploadService, times(2 * problemIdList.size())).deleteImageFileFromS3(anyString());
        verify(problemImageDataRepository, times(2 * problemIdList.size())).delete(any(ProblemImageData.class));
        verify(problemRepository, times(problemIdList.size())).deleteById(anyLong());
    }

    @Test
    @DisplayName("deleteProblems - 삭제할 폴더 목록을 전달받아 삭제하기")
    void deleteProblems_folderIdList() {
        // given
        List<Long> folderIdList = List.of(1L, 2L);

        ProblemDeleteRequestDto problemDeleteRequestDto = new ProblemDeleteRequestDto(
                null,
                null,
                folderIdList
        );

        when(problemRepository.findAllByFolderId(1L)).thenReturn(List.of(problemList.get(0), problemList.get(1), problemList.get(2)));
        when(problemRepository.findAllByFolderId(2L)).thenReturn(List.of(problemList.get(3), problemList.get(4)));
        for (int i = 0; i < problemList.size(); i++) {
            Long problemId = (long) i + 1;
            when(problemImageDataRepository.findAllByProblemId(problemId)).thenReturn(problemList.get(i).getProblemImageDataList());
        }

        // when
        problemService.deleteProblems(problemDeleteRequestDto);

        // then
        verify(problemRepository).findAllByFolderId(1L);
        verify(problemRepository).findAllByFolderId(2L);
        verify(fileUploadService, times(2 * 5)).deleteImageFileFromS3(anyString());
        verify(problemImageDataRepository, times(2 * 5)).delete(any(ProblemImageData.class));
        verify(problemRepository, times(5)).deleteById(anyLong());
    }

    @Test
    @DisplayName("deleteProblemImageData - 특정 이미지 URL로 이미지 삭제")
    void deleteProblemImageData() {
        // given
        String imageUrl = "https://s3.amazonaws.com/bucket/problem_image_123.jpg";

        // when
        problemService.deleteProblemImageData(imageUrl);

        // then
        verify(problemImageDataRepository, times(1)).deleteByImageUrl(imageUrl);
    }
}