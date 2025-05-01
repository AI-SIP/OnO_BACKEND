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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

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

    @Autowired
    private ProblemService problemService;

    @Autowired
    private ProblemRepository problemRepository;

    @Autowired
    private ProblemImageDataRepository problemImageDataRepository;

    @Autowired
    private FolderRepository folderRepository;

    @MockBean
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
            Folder folder = folderRepository.save(Folder.from(
                    new FolderRegisterDto("folder" + f, null, null),
                    null,
                    userId
            ));
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
                    userId
            );
            problem.updateFolder(folder);
            problemRepository.save(problem);

            // 이미지 2개씩 추가
            ProblemImageData imageData1 = ProblemImageData.from(new ProblemImageDataRegisterDto(problem.getId(), "url" + i + "_1", ProblemImageType.PROBLEM_IMAGE));
            ProblemImageData imageData2 = ProblemImageData.from(new ProblemImageDataRegisterDto(problem.getId(), "url" + i + "_2", ProblemImageType.ANSWER_IMAGE));

            imageData1.updateProblem(problem);
            imageData2.updateProblem(problem);

            problemImageDataRepository.save(imageData1);
            problemImageDataRepository.save(imageData2);

            problemList.add(problem);
        }
    }

    @AfterEach
    void tearDown() {
        problemList.clear();
        folderList.clear();

        problemImageDataRepository.deleteAll();
        problemRepository.deleteAll();
        folderRepository.deleteAll();
    }

    @Test
    @DisplayName("problemId를 사용해 특정 문제 조회하기")
    void findProblem() {
        // given
        Problem problem = problemList.get(0);
        Long problemId = problem.getId();

        // when
        ProblemResponseDto problemResponseDto = problemService.findProblem(problemId, userId);

        // then
        assertThat(problemResponseDto.memo()).isEqualTo(problem.getMemo());
        assertThat(problemResponseDto.reference()).isEqualTo(problem.getReference());
        assertThat(problemResponseDto.imageUrlList().size()).isEqualTo(problemImageDataRepository.findAllByProblemId(problemId).size());
        assertThat(problemResponseDto.imageUrlList().size()).isEqualTo(2);
    }

    @Test
    @DisplayName("특정 유저의 모든 문제 목록 조회하기")
    void findUserProblems() {
        //given

        //when
        List<ProblemResponseDto> problemResponseDtoList = problemService.findUserProblems(userId);

        //then
        for(int i = 0; i<problemResponseDtoList.size(); i++){
            Problem problem = problemList.get(i);
            assertThat(problemResponseDtoList.get(i)).isNotNull();
            assertThat(problemResponseDtoList.size()).isEqualTo(problemList.size());
            assertThat(problemResponseDtoList.get(i).imageUrlList().size()).isEqualTo(2);
            assertThat(problemResponseDtoList.get(i).problemId()).isEqualTo(problem.getId());
            assertThat(problemResponseDtoList.get(i).memo()).isEqualTo(problem.getMemo());
            assertThat(problemResponseDtoList.get(i).reference()).isEqualTo(problem.getReference());
        }
    }

    @Test
    @DisplayName("특정 폴더의 모든 문제 목록 조회하기")
    void findFolderProblemList() {
        //given
        Folder folder = folderList.get(0);
        Long folderId = folder.getId();

        //when
        List<ProblemResponseDto> problemResponseDtoList = problemService.findFolderProblemList(folderId);

        //then
        assertThat(problemResponseDtoList.size()).isEqualTo(problemRepository.findAllByFolderId(folderId).size());
        for(int i = 0; i<problemResponseDtoList.size(); i++){
            Problem problem = problemList.get(i);
            assertThat(problemResponseDtoList.get(i)).isNotNull();
            assertThat(problemResponseDtoList.get(i).imageUrlList().size()).isEqualTo(problemImageDataRepository.findAllByProblemId(problem.getId()).size());
            assertThat(problemResponseDtoList.get(i).problemId()).isEqualTo(problem.getId());
            assertThat(problemResponseDtoList.get(i).memo()).isEqualTo(problem.getMemo());
            assertThat(problemResponseDtoList.get(i).reference()).isEqualTo(problem.getReference());
        }

        assertThat(problemResponseDtoList.get(0)).isNotNull();
        assertThat(problemResponseDtoList.size()).isEqualTo(3);
        assertThat(problemResponseDtoList.get(0).imageUrlList().size()).isEqualTo(2);
    }

    @Test
    @DisplayName("모든 문제 목록 조회하기")
    void findAllProblems() {
        //given

        //when
        List<ProblemResponseDto> problemResponseDtoList = problemService.findAllProblems();

        //then
        assertThat(problemResponseDtoList.size()).isEqualTo(problemList.size());
        for(int i = 0; i<problemResponseDtoList.size(); i++){
            Problem problem = problemList.get(i);
            assertThat(problemResponseDtoList.get(i)).isNotNull();
            assertThat(problemResponseDtoList.get(i).imageUrlList().size()).isEqualTo(problemImageDataRepository.findAllByProblemId(problem.getId()).size());
            assertThat(problemResponseDtoList.get(i).problemId()).isEqualTo(problem.getId());
            assertThat(problemResponseDtoList.get(i).memo()).isEqualTo(problem.getMemo());
            assertThat(problemResponseDtoList.get(i).reference()).isEqualTo(problem.getReference());
        }
    }

    @Test
    @DisplayName("유저의 문제 수 조회하기")
    void findProblemCountByUser() {
        //given

        //when
        Long problemCount = problemService.findProblemCountByUser(userId);

        //then
        assertThat(problemCount).isEqualTo(problemRepository.countByUserId(userId));
        assertThat(problemCount).isEqualTo(problemList.size());
    }

    @Test
    @DisplayName("문제 등록하기 - 정상 케이스")
    void registerProblem_success() {
        // Given
        Long folderId = folderList.get(0).getId();
        ProblemRegisterDto dto = new ProblemRegisterDto(
                null,
                "memo",
                "reference",
                folderId,
                LocalDateTime.now(),
                List.of(
                        new ProblemImageDataRegisterDto(null, "imageUrl1", ProblemImageType.valueOf(1)),
                        new ProblemImageDataRegisterDto(null, "imageUrl2", ProblemImageType.valueOf(2))
                )
        );

        // When
        problemService.registerProblem(dto, userId);

        // Then
        assertThat(problemRepository.findAllByUserId(userId).size()).isEqualTo(problemList.size() + 1);
    }

    @Test
    @DisplayName("문제 등록하기 - 존재하지 않는 폴더 예외")
    void registerProblem_folderNotFound() {
        // Given
        ProblemRegisterDto dto = new ProblemRegisterDto(
                null, "memo", "reference", 999L, LocalDateTime.now(), null
        );

        // Then
        assertThatThrownBy(() -> problemService.registerProblem(dto, userId))
                .isInstanceOf(ApplicationException.class)
                .hasMessageContaining(FolderErrorCase.FOLDER_NOT_FOUND.getMessage());
    }

    @Test
    @DisplayName("문제 정보(memo, reference) 수정")
    void updateProblemInfo() {
        // Given
        Long problemId = problemList.get(0).getId();
        String updateMemo = "update memo";
        String updateReference = "update reference";
        ProblemRegisterDto updateDto = new ProblemRegisterDto(
                problemId,
                updateMemo,
                updateReference,
                1L,
                LocalDateTime.now(),
                List.of(
                        new ProblemImageDataRegisterDto(null, "imageUrl1", ProblemImageType.valueOf(1)),
                        new ProblemImageDataRegisterDto(null, "imageUrl2", ProblemImageType.valueOf(2))
                )
        );
        //when
        problemService.updateProblemInfo(updateDto, userId);

        //then
        Optional<Problem> optionalProblem = problemRepository.findById(problemId);
        if (optionalProblem.isEmpty()) {
            assertThat(0).isEqualTo(1);
        } else{
            Problem problem = optionalProblem.get();
            assertThat(problem.getMemo()).isEqualTo(updateMemo);
            assertThat(problem.getReference()).isEqualTo(updateReference);
        }
    }

    @Test
    @DisplayName("문제 폴더 수정")
    void updateProblemFolder() {
        // Given
        Long problemId = problemList.get(0).getId();
        Long updatedFolderId = folderList.get(1).getId();
        String updateMemo = "update memo";
        String updateReference = "update reference";

        ProblemRegisterDto updateDto = new ProblemRegisterDto(
                problemId,
                updateMemo,
                updateReference,
                updatedFolderId,
                LocalDateTime.now(),
                List.of(
                        new ProblemImageDataRegisterDto(null, "imageUrl1", ProblemImageType.valueOf(1)),
                        new ProblemImageDataRegisterDto(null, "imageUrl2", ProblemImageType.valueOf(2))
                )
        );

        //when
        problemService.updateProblemFolder(updateDto, userId);

        // Then
        Optional<Problem> optionalProblem = problemRepository.findById(problemId);
        if (optionalProblem.isEmpty()) {
            assertThat(0).isEqualTo(1);
        } else {
            Problem problem = optionalProblem.get();
            assertThat(problem.getFolder().getId()).isEqualTo(updatedFolderId);
        }
    }

    @Test
    @DisplayName("문제 이미지 데이터 수정")
    void updateProblemImageData() {
        // Given
        Long problemId = problemList.get(0).getId();

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

        //when
        problemService.updateProblemImageData(updateDto, userId);

        //then
        Optional<Problem> optionalProblem = problemRepository.findProblemWithImageData(problemId);
        if (optionalProblem.isEmpty()) {
            assertThat(0).isEqualTo(1);
        } else {
            Problem problem = optionalProblem.get();
            assertThat(problem.getProblemImageDataList().size()).isEqualTo(2);
            assertThat(problem.getProblemImageDataList().get(0).getImageUrl()).isEqualTo("imageUrl1 update");
            assertThat(problem.getProblemImageDataList().get(1).getImageUrl()).isEqualTo("imageUrl2 update");
        }
    }

    @Test
    @DisplayName("특정 문제 삭제 - 정상 케이스")
    void deleteProblem_success() {
        // Given
        Long problemId = problemList.get(0).getId();

        // When
        doNothing().when(fileUploadService).deleteImageFileFromS3(anyString());
        problemService.deleteProblem(problemId);

        // Then
        verify(fileUploadService, times(2)).deleteImageFileFromS3(anyString());
        assertThat(problemRepository.findAll().size()).isEqualTo(problemList.size() - 1);
    }

    @Test
    @DisplayName("특정 유저의 모든 문제 삭제하기")
    void deleteProblems_userId() {
        // given
        ProblemDeleteRequestDto problemDeleteRequestDto = new ProblemDeleteRequestDto(
                userId,
                null,
                null
        );

        // when
        doNothing().when(fileUploadService).deleteImageFileFromS3(anyString());

        // when
        problemService.deleteProblems(problemDeleteRequestDto);

        // then
        verify(fileUploadService, times(2  * problemList.size())).deleteImageFileFromS3(anyString());
        assertThat(problemRepository.findAll().size()).isEqualTo(0);
    }

    @Test
    @DisplayName("삭제할 문제 목록을 전달받아 삭제하기")
    void deleteProblems_problemIdList() {
        // given
        int deleteCount = 3;
        List<Long> problemIdList = new ArrayList<>();
        for (int i = 0; i < deleteCount; i++) {
            Long problemId = problemList.get(i).getId();
            problemIdList.add(problemId);
        }

        ProblemDeleteRequestDto problemDeleteRequestDto = new ProblemDeleteRequestDto(
                null,
                problemIdList,
                null
        );

        // when
        doNothing().when(fileUploadService).deleteImageFileFromS3(anyString());
        problemService.deleteProblems(problemDeleteRequestDto);

        // then
        verify(fileUploadService, times(2 * problemIdList.size())).deleteImageFileFromS3(anyString());
        assertThat(problemRepository.findAll().size()).isEqualTo(problemList.size() - (long) deleteCount);
    }

    @Test
    @DisplayName("삭제할 폴더 목록을 전달받아 삭제하기")
    void deleteProblems_folderIdList() {
        // given
        List<Long> folderIdList = List.of(folderList.get(0).getId());

        ProblemDeleteRequestDto problemDeleteRequestDto = new ProblemDeleteRequestDto(
                null,
                null,
                folderIdList
        );

        // when
        int problemCount = problemRepository.findAllByFolderId(folderIdList.get(0)).size();
        problemService.deleteProblems(problemDeleteRequestDto);

        // then
        verify(fileUploadService, times(2 * problemCount)).deleteImageFileFromS3(anyString());
        assertThat(problemRepository.findAll().size()).isEqualTo(problemList.size() - (long) problemCount);
    }

    @Test
    @DisplayName("특정 이미지 URL로 이미지 삭제")
    void deleteProblemImageData() {
        // given
        Long problemId = problemList.get(0).getId();
        Optional<Problem> optionalProblem = problemRepository.findProblemWithImageData(problemId);
        if(optionalProblem.isPresent()) {
            Problem problem = optionalProblem.get();
            String imageUrl = problem.getProblemImageDataList().get(0).getImageUrl();

            // when
            problemService.deleteProblemImageData(imageUrl);

            // then
            verify(fileUploadService, times(1)).deleteImageFileFromS3(anyString());

            optionalProblem = problemRepository.findProblemWithImageData(problemId);
            if(optionalProblem.isPresent()) {
                problem = optionalProblem.get();
                imageUrl = problem.getProblemImageDataList().get(0).getImageUrl();
                assertThat(problem.getProblemImageDataList().size()).isEqualTo(1);
            }
        }
    }
}