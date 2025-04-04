package com.aisip.OnO.backend.practicenote.service;

import com.aisip.OnO.backend.folder.dto.FolderRegisterDto;
import com.aisip.OnO.backend.folder.entity.Folder;
import com.aisip.OnO.backend.practicenote.dto.PracticeNoteDetailResponseDto;
import com.aisip.OnO.backend.practicenote.dto.PracticeNoteRegisterDto;
import com.aisip.OnO.backend.practicenote.dto.PracticeNoteThumbnailResponseDto;
import com.aisip.OnO.backend.practicenote.entity.PracticeNote;
import com.aisip.OnO.backend.practicenote.entity.ProblemPracticeNoteMapping;
import com.aisip.OnO.backend.practicenote.repository.PracticeNoteRepository;
import com.aisip.OnO.backend.practicenote.repository.ProblemPracticeNoteMappingRepository;
import com.aisip.OnO.backend.problem.dto.ProblemImageDataRegisterDto;
import com.aisip.OnO.backend.problem.dto.ProblemRegisterDto;
import com.aisip.OnO.backend.problem.entity.Problem;
import com.aisip.OnO.backend.problem.entity.ProblemImageData;
import com.aisip.OnO.backend.problem.entity.ProblemImageType;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;
import static org.springframework.test.util.ReflectionTestUtils.setField;

@SpringBootTest
class PracticeNoteServiceTest {

    @InjectMocks
    private PracticeNoteService practiceNoteService;

    @Mock
    private PracticeNoteRepository practiceNoteRepository;

    @Mock
    private ProblemPracticeNoteMappingRepository problemPracticeNoteMappingRepository;

    @Mock
    private ProblemRepository problemRepository;

    private final Long userId = 1L;

    private List<Problem> problemList;

    private List<PracticeNote> practiceNoteList;

    private List<ProblemPracticeNoteMapping> problemPracticeNoteMappingList;

    @BeforeEach
    void setUp() {
        practiceNoteList = new ArrayList<>();
        problemList = new ArrayList<>();
        problemPracticeNoteMappingList = new ArrayList<>();

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

        /*
        problem 0 ~ 3 -> practice 0번과 mapping
        problem 4 ~ 7 -> practice 1번과 mapping
        problem 8 ~ 11 -> practice 2번과 mapping
         */
        problemList = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            Problem problem = Problem.from(
                    new ProblemRegisterDto(
                            null,
                            "memo" + i,
                            "reference" + i,
                            rootFolder.getId(),
                            LocalDateTime.now(),
                            null
                    ),
                    userId,
                    rootFolder
            );
            setField(problem, "id", (long) i);

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

        practiceNoteList = new ArrayList<>();
        for(int i = 0; i < 3; i++){
            List<Long> problemIdList = new ArrayList<>();
            for(int j = 0; j < 4; j++){
                problemIdList.add(problemList.get(i * 4 + j).getId());
            }
            PracticeNote practiceNote = PracticeNote.from(
                    new PracticeNoteRegisterDto(
                            null,
                            "practiceNote" + i,
                            problemIdList
                    ),
                    userId
            );
            setField(practiceNote, "id", (long) i);

            for(int j = 0; j < 4; j++){
                ProblemPracticeNoteMapping problemPracticeNoteMapping = ProblemPracticeNoteMapping.from(practiceNote, problemList.get(i * 4 + j));

                problemList.get(i * 4 + j).addProblemToPractice(problemPracticeNoteMapping);
                practiceNote.addProblemToPracticeNote(problemPracticeNoteMapping);
                setField(problemPracticeNoteMapping, "id", (long) i * 4 + j);

                problemPracticeNoteMappingList.add(problemPracticeNoteMapping);
            }

            practiceNoteList.add(practiceNote);
        }

        // problem 0번에 대해서만 practiceNote 1, 2번과 추가 매핑
        for(int i = 1; i < 3; i++){
            ProblemPracticeNoteMapping problemPracticeNoteMapping = ProblemPracticeNoteMapping.from(practiceNoteList.get(i), problemList.get(0));

            problemList.get(0).addProblemToPractice(problemPracticeNoteMapping);
            practiceNoteList.get(i).addProblemToPracticeNote(problemPracticeNoteMapping);

            setField(problemPracticeNoteMapping, "id", (long) problemPracticeNoteMappingList.size());
            problemPracticeNoteMappingList.add(problemPracticeNoteMapping);
        }

        // problem 1번에 대해서만 practiceNote 0번과 추가 매핑
        for(int i = 0; i < 1; i++){
            ProblemPracticeNoteMapping problemPracticeNoteMapping = ProblemPracticeNoteMapping.from(practiceNoteList.get(i), problemList.get(1));

            problemList.get(1).addProblemToPractice(problemPracticeNoteMapping);
            practiceNoteList.get(i).addProblemToPracticeNote(problemPracticeNoteMapping);

            setField(problemPracticeNoteMapping, "id", (long) problemPracticeNoteMappingList.size());
            problemPracticeNoteMappingList.add(problemPracticeNoteMapping);
        }
    }

    @AfterEach
    void tearDown() {
        problemList.clear();
        practiceNoteList.clear();
        problemPracticeNoteMappingList.clear();
    }

    @Test
    @DisplayName("복습 노트 등록 테스트")
    void registerPractice() {
        //given
        PracticeNoteRegisterDto practiceNoteRegisterDto = new PracticeNoteRegisterDto(
                null,
                "new practice",
                List.of(problemList.get(0).getId(), problemList.get(1).getId(), problemList.get(2).getId())
        );

        when(practiceNoteRepository.checkProblemAlreadyMatchingWithPractice(anyLong(), anyLong()))
                .thenReturn(false);
        for(int i = 0; i < problemList.size(); i++){
            when(problemRepository.findById((long) i)).thenReturn(Optional.of(problemList.get(i)));
        }

        //when
        practiceNoteService.registerPractice(practiceNoteRegisterDto, userId);

        //then
        verify(practiceNoteRepository).save(any(PracticeNote.class));
        verify(problemPracticeNoteMappingRepository, times(3))
                .save(any(ProblemPracticeNoteMapping.class));
        verify(problemRepository).findById(problemList.get(0).getId());
        verify(problemRepository).findById(problemList.get(1).getId());
        verify(problemRepository).findById(problemList.get(2).getId());
    }


    @Test
    @DisplayName("복습 노트 상세 정보 조회 테스트")
    void findPracticeNoteDetail() {
        //given
        PracticeNote practiceNote = practiceNoteList.get(0);
        Long practiceNoteId = practiceNote.getId();
        List<Problem> resultProblemList = problemList.subList(0, 4);

        when(practiceNoteRepository.findPracticeNoteWithDetails(practiceNoteId)).thenReturn(practiceNote);
        when(problemRepository.findAllProblemsByPracticeId(practiceNoteId)).thenReturn(resultProblemList);

        //when
        PracticeNoteDetailResponseDto practiceNoteDetailResponseDto = practiceNoteService.findPracticeNoteDetail(practiceNoteId);

        //then
        assertThat(practiceNote.getId()).isEqualTo(practiceNoteDetailResponseDto.practiceNoteId());
        assertThat(practiceNote.getTitle()).isEqualTo(practiceNoteDetailResponseDto.practiceTitle());
        for(int i = 0; i < 4; i++){
            assertThat(problemList.get(i).getId()).isEqualTo(practiceNoteDetailResponseDto.problemResponseDtoList().get(i).problemId());
            assertThat(problemList.get(i).getProblemImageDataList().size()).isEqualTo(practiceNoteDetailResponseDto.problemResponseDtoList().get(i).imageUrlList().size());
        }
    }

    @Test
    @DisplayName("유저의 복습 노트 썸네일 리스트 조회 테스트")
    void findAllPracticeThumbnailsByUser() {
        //given
        when(practiceNoteRepository.findAllByUserId(userId)).thenReturn(practiceNoteList);

        //when
        List<PracticeNoteThumbnailResponseDto> practiceNoteThumbnailResponseDtoList = practiceNoteService.findAllPracticeThumbnailsByUser(userId);

        //then
        assertThat(practiceNoteThumbnailResponseDtoList.size()).isEqualTo(practiceNoteList.size());
        for(int i = 0; i < practiceNoteThumbnailResponseDtoList.size(); i++){
            assertThat(practiceNoteList.get(i).getId()).isEqualTo(practiceNoteThumbnailResponseDtoList.get(i).practiceNoteId());
            assertThat(practiceNoteList.get(i).getTitle()).isEqualTo(practiceNoteThumbnailResponseDtoList.get(i).practiceTitle());
        }
    }

    @Test
    void addPracticeNoteCount() {
        //given
        PracticeNote practiceNote = practiceNoteList.get(0);
        Long practiceNoteId = practiceNote.getId();
        when(practiceNoteRepository.findById(practiceNoteId)).thenReturn(Optional.of(practiceNote));

        //when
        practiceNoteService.addPracticeNoteCount(practiceNoteId);

        //then
        assertThat(practiceNote.getPracticeCount()).isEqualTo(1);
    }

    @Test
    void updatePracticeInfo() {
    }

    @Test
    void deletePractice() {
        //given
        PracticeNote practiceNote = practiceNoteList.get(0);
        Long practiceNoteId = practiceNote.getId();
        when(practiceNoteRepository.findById(practiceNoteId)).thenReturn(Optional.of(practiceNote));
    }

    @Test
    void deletePractices() {
        // given
        List<Long> ids = List.of(1L, 2L, 3L);
        PracticeNote mockNote = mock(PracticeNote.class);
        when(practiceNoteRepository.findById(anyLong())).thenReturn(Optional.of(mockNote));
        when(mockNote.getProblemPracticeNoteMappingList()).thenReturn(new ArrayList<>());

        // when
        practiceNoteService.deletePractices(ids);

        // then
        verify(practiceNoteRepository, times(3)).delete(any());
    }

    @Test
    void deleteAllPracticesByUser() {
        // given
        List<PracticeNote> list = List.of(mock(PracticeNote.class), mock(PracticeNote.class));
        when(practiceNoteRepository.findAllByUserId(userId)).thenReturn(list);

        // when
        practiceNoteService.deleteAllPracticesByUser(userId);

        // then
        verify(practiceNoteRepository).deleteAll(list);
    }

    @Test
    void deleteProblemFromPractice() {
        // when
        practiceNoteService.deleteProblemFromPractice(1L, 2L);

        // then
        verify(practiceNoteRepository).deleteProblemFromPractice(1L, 2L);
    }
}