package com.aisip.OnO.backend.practicenote.service;

import com.aisip.OnO.backend.folder.dto.FolderRegisterDto;
import com.aisip.OnO.backend.folder.entity.Folder;
import com.aisip.OnO.backend.folder.repository.FolderRepository;
import com.aisip.OnO.backend.practicenote.dto.PracticeNoteDetailResponseDto;
import com.aisip.OnO.backend.practicenote.dto.PracticeNoteRegisterDto;
import com.aisip.OnO.backend.practicenote.dto.PracticeNoteThumbnailResponseDto;
import com.aisip.OnO.backend.practicenote.dto.PracticeNoteUpdateDto;
import com.aisip.OnO.backend.practicenote.entity.PracticeNote;
import com.aisip.OnO.backend.practicenote.entity.ProblemPracticeNoteMapping;
import com.aisip.OnO.backend.practicenote.repository.PracticeNoteRepository;
import com.aisip.OnO.backend.practicenote.repository.ProblemPracticeNoteMappingRepository;
import com.aisip.OnO.backend.problem.dto.ProblemImageDataRegisterDto;
import com.aisip.OnO.backend.problem.dto.ProblemRegisterDto;
import com.aisip.OnO.backend.problem.entity.Problem;
import com.aisip.OnO.backend.problem.entity.ProblemImageData;
import com.aisip.OnO.backend.problem.entity.ProblemImageType;
import com.aisip.OnO.backend.problem.repository.ProblemImageDataRepository;
import com.aisip.OnO.backend.problem.repository.ProblemRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class PracticeNoteServiceTest {

    @Autowired
    private PracticeNoteService practiceNoteService;

    @Autowired
    private PracticeNoteRepository practiceNoteRepository;

    @Autowired
    private ProblemPracticeNoteMappingRepository problemPracticeNoteMappingRepository;

    @Autowired
    private ProblemRepository problemRepository;

    @Autowired
    private ProblemImageDataRepository problemImageDataRepository;

    @Autowired
    private FolderRepository folderRepository;

    private final Long userId = 1L;

    private List<Problem> problemList;

    private List<PracticeNote> practiceNoteList;

    private List<ProblemPracticeNoteMapping> problemPracticeNoteMappingList;

    @BeforeEach
    void setUp() {
        practiceNoteList = new ArrayList<>();
        problemList = new ArrayList<>();
        problemPracticeNoteMappingList = new ArrayList<>();

        Folder rootFolder = folderRepository.save(Folder.from(
                new FolderRegisterDto(
                        "rootFolder",
                        null,
                        null
                ),
                null,
                1L
        ));

        /*
        practice 0 : problem 0, 1, 2, 3
        practice 1 : problem 0, 1, 4, 5, 6, 7
        practice 2 : problem 0, 8, 9, 10, 11
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
                    userId
            );
            problem.updateFolder(rootFolder);
            problemRepository.save(problem);

            List<ProblemImageData> imageDataList = new ArrayList<>();
            for (int j = 1; j <= 3; j++){
                ProblemImageDataRegisterDto problemImageDataRegisterDto = new ProblemImageDataRegisterDto(
                        (long) problem.getId(),
                        "http://example.com/problemId/" + i + "/image" + j,
                        ProblemImageType.valueOf(j)
                );

                ProblemImageData imageData = ProblemImageData.from(problemImageDataRegisterDto);
                imageData.updateProblem(problem);
                problemImageDataRepository.save(imageData);
                imageDataList.add(imageData);
            }
            problemList.add(problem);
        }

        practiceNoteList = new ArrayList<>();
        for(int i = 0; i < 3; i++){
            List<Long> problemIdList = new ArrayList<>();
            for(int j = 0; j < 4; j++){
                problemIdList.add(problemList.get(i * 4 + j).getId());
            }
            PracticeNote practiceNote = practiceNoteRepository.save(PracticeNote.from(
                    new PracticeNoteRegisterDto(
                            null,
                            "practiceNote" + i,
                            problemIdList
                    ),
                    userId
            ));

            for(int j = 0; j < 4; j++){
                ProblemPracticeNoteMapping problemPracticeNoteMapping = ProblemPracticeNoteMapping.from();
                Problem problem = problemList.get(i * 4 + j);

                problemPracticeNoteMapping.addMappingToProblemAndPractice(problem, practiceNote);

                problemPracticeNoteMappingRepository.save(problemPracticeNoteMapping);
                problemPracticeNoteMappingList.add(problemPracticeNoteMapping);
            }

            practiceNoteList.add(practiceNote);
        }

        // problem 0번에 대해서만 practiceNote 1, 2번과 추가 매핑
        for(int i = 1; i < 3; i++){
            ProblemPracticeNoteMapping problemPracticeNoteMapping = ProblemPracticeNoteMapping.from();

            problemPracticeNoteMapping.addMappingToProblemAndPractice(problemList.get(0), practiceNoteList.get(i));

            problemPracticeNoteMappingRepository.save(problemPracticeNoteMapping);
            problemPracticeNoteMappingList.add(problemPracticeNoteMapping);
        }

        // problem 1번에 대해서만 practiceNote 1번과 추가 매핑
        for(int i = 1; i < 2; i++){
            ProblemPracticeNoteMapping problemPracticeNoteMapping = ProblemPracticeNoteMapping.from();

            problemPracticeNoteMapping.addMappingToProblemAndPractice(problemList.get(1), practiceNoteList.get(i));

            problemPracticeNoteMappingRepository.save(problemPracticeNoteMapping);
            problemPracticeNoteMappingList.add(problemPracticeNoteMapping);
        }
    }

    @AfterEach
    void tearDown() {
        problemList.clear();
        practiceNoteList.clear();
        problemPracticeNoteMappingList.clear();

        folderRepository.deleteAll();
        problemImageDataRepository.deleteAll();
        problemRepository.deleteAll();
        practiceNoteRepository.deleteAll();
        problemPracticeNoteMappingRepository.deleteAll();
    }

    @Test
    @DisplayName("복습 노트 상세 정보 조회 테스트")
    void findPracticeNoteDetail() {
        //given
        PracticeNote practiceNote = practiceNoteList.get(0);
        Long practiceNoteId = practiceNote.getId();

        //when
        PracticeNoteDetailResponseDto practiceNoteDetailResponseDto = practiceNoteService.findPracticeNoteDetail(practiceNoteId);

        //then
        assertThat(practiceNote.getId()).isEqualTo(practiceNoteDetailResponseDto.practiceNoteId());
        assertThat(practiceNote.getTitle()).isEqualTo(practiceNoteDetailResponseDto.practiceTitle());
        for(int i = 0; i < 4; i++){
            System.out.println("===== imageDataSize: " + practiceNoteDetailResponseDto.problemResponseDtoList().get(i).imageUrlList().size() + "=====");
            assertThat(problemList.get(i).getId()).isEqualTo(practiceNoteDetailResponseDto.problemResponseDtoList().get(i).problemId());
        }
    }

    @Test
    @DisplayName("유저의 복습 노트 썸네일 리스트 조회 테스트")
    void findAllPracticeThumbnailsByUser() {
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
    @DisplayName("복습 노트 조회 수 증가")
    void addPracticeNoteCount() {
        //given
        Long practiceNoteId = practiceNoteList.get(0).getId();

        //when
        practiceNoteService.addPracticeNoteCount(practiceNoteId);

        //then
        PracticeNote practiceNote = practiceNoteRepository.findById(practiceNoteId).get();
        assertThat(practiceNote.getPracticeCount()).isEqualTo(1);
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

        //when
        practiceNoteService.registerPractice(practiceNoteRegisterDto, userId);

        assertThat(practiceNoteRepository.findAll().size()).isEqualTo(practiceNoteList.size() + 1);
    }

    @Test
    @DisplayName("복습 노트 수정 - 문제 리스트 수정")
    void updatePracticeInfo() {
        // given
        Long practiceNoteId = practiceNoteList.get(0).getId();
        String updateTitle = "new title";
        List<Long> addProblemIdList = List.of(problemList.get(0).getId(), problemList.get(6).getId(), problemList.get(7).getId(), problemList.get(8).getId());
        List<Long> removeProblemIdList = List.of(problemList.get(1).getId(), problemList.get(2).getId());

        PracticeNoteUpdateDto practiceNoteUpdateDto = new PracticeNoteUpdateDto(
                practiceNoteId,
                updateTitle,
                addProblemIdList,
                removeProblemIdList
        );

        // when
        assertThat(practiceNoteList.get(0).getProblemPracticeNoteMappingList().size()).isEqualTo(4);
        practiceNoteService.updatePracticeInfo(practiceNoteUpdateDto);

        // then
        Optional<PracticeNote> optionalPracticeNote = practiceNoteRepository.findPracticeNoteWithDetails(practiceNoteId);
        assertThat(optionalPracticeNote.isPresent()).isTrue();
        PracticeNote practiceNote = optionalPracticeNote.get();
        assertThat(practiceNote.getTitle()).isEqualTo(updateTitle);
        assertThat(practiceNote.getProblemPracticeNoteMappingList().size()).isEqualTo(5);
    }

    @Test
    void deletePractice() {
        // given
        Long practiceNoteId = practiceNoteList.get(0).getId();

        // when
        practiceNoteService.deletePractice(practiceNoteId);

        // then
        Optional<PracticeNote> optionalPracticeNote = practiceNoteRepository.findById(practiceNoteId);
        assertThat(optionalPracticeNote.isPresent()).isFalse();

        List<ProblemPracticeNoteMapping> practiceNoteMappingList = problemPracticeNoteMappingRepository.findAllByPracticeNoteId(practiceNoteId);
        assertThat(practiceNoteMappingList.size()).isEqualTo(0);
    }

    @Test
    void deletePractices() {
        // given
        List<Long> practiceIdList = List.of(practiceNoteList.get(0).getId(), practiceNoteList.get(1).getId());

        // when
        practiceNoteService.deletePractices(practiceIdList);

        // then
        for (int i = 0; i < 2; i++) {
            Optional<PracticeNote> optionalPracticeNote = practiceNoteRepository.findById(practiceIdList.get(i));
            assertThat(optionalPracticeNote.isPresent()).isFalse();

            List<ProblemPracticeNoteMapping> practiceNoteMappingList = problemPracticeNoteMappingRepository.findAllByPracticeNoteId(practiceIdList.get(i));
            assertThat(practiceNoteMappingList.size()).isEqualTo(0);
        }
    }

    @Test
    void deleteAllPracticesByUser() {
        // given

        // when
        practiceNoteService.deleteAllPracticesByUser(userId);

        // then
        assertThat(practiceNoteRepository.findAll()).isEmpty();
        assertThat(problemPracticeNoteMappingRepository.findAll()).isEmpty();
    }
}