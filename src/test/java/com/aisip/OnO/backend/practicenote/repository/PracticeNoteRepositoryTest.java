package com.aisip.OnO.backend.practicenote.repository;

import com.aisip.OnO.backend.folder.dto.FolderRegisterDto;
import com.aisip.OnO.backend.folder.entity.Folder;
import com.aisip.OnO.backend.folder.repository.FolderRepository;
import com.aisip.OnO.backend.practicenote.dto.PracticeNoteRegisterDto;
import com.aisip.OnO.backend.practicenote.entity.PracticeNote;
import com.aisip.OnO.backend.practicenote.entity.ProblemPracticeNoteMapping;
import com.aisip.OnO.backend.problem.dto.ProblemImageDataRegisterDto;
import com.aisip.OnO.backend.problem.dto.ProblemRegisterDto;
import com.aisip.OnO.backend.problem.entity.Problem;
import com.aisip.OnO.backend.problem.entity.ProblemImageData;
import com.aisip.OnO.backend.problem.entity.ProblemImageType;
import com.aisip.OnO.backend.problem.repository.ProblemImageDataRepository;
import com.aisip.OnO.backend.problem.repository.ProblemRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.*;
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
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@ExtendWith(SpringExtension.class)
class PracticeNoteRepositoryTest {

    @Autowired
    private ProblemRepository problemRepository;

    @Autowired
    private ProblemImageDataRepository problemImageDataRepository;

    @Autowired
    private PracticeNoteRepository practiceNoteRepository;

    @Autowired
    private ProblemPracticeNoteMappingRepository problemPracticeNoteMappingRepository;

    @Autowired
    private FolderRepository folderRepository;

    @Autowired
    private EntityManager em;

    private final Long userId = 1L;

    private List<Problem> problemList;

    private List<PracticeNote> practiceNoteList;

    @BeforeEach
    void setUp() {
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
        problem 0 ~ 3 -> practice 0번과 mapping
        problem 4 ~ 7 -> practice 1번과 mapping
        problem 8 ~ 11 -> practice 2번과 mapping
         */
        problemList = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            Problem problem = problemRepository.save(
                    Problem.from(
                            new ProblemRegisterDto(
                                    null,
                                    "memo" + i,
                                    "reference" + i,
                                    rootFolder.getId(),
                                    LocalDateTime.now(),
                                    null
                            ),
                            userId
                    )
            );
            problem.updateFolder(rootFolder);

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

            problemList.add(problem);
        }

        practiceNoteList = new ArrayList<>();
        for(int i = 0; i < 3; i++){
            List<Long> problemIdList = new ArrayList<>();
            for(int j = 0; j < 4; j++){
                problemIdList.add(problemList.get(i * 4 + j).getId());
            }
            PracticeNote practiceNote = practiceNoteRepository.save(
                    PracticeNote.from(
                            new PracticeNoteRegisterDto(
                                    null,
                                    "practiceNote" + i,
                                problemIdList
                            ),
                            userId
                    )
            );


            for(int j = 0; j < 4; j++){
                ProblemPracticeNoteMapping problemPracticeNoteMapping = ProblemPracticeNoteMapping.from();
                problemPracticeNoteMapping.addMappingToProblemAndPractice(problemList.get(i * 4 + j), practiceNote);
                problemPracticeNoteMappingRepository.save(problemPracticeNoteMapping);
            }

            practiceNoteList.add(practiceNote);
        }

        // problem 0번에 대해서만 practiceNote 1, 2번과 추가 매핑
        for(int i = 1; i < 3; i++) {
            ProblemPracticeNoteMapping problemPracticeNoteMapping = ProblemPracticeNoteMapping.from();
            problemPracticeNoteMapping.addMappingToProblemAndPractice(problemList.get(0), practiceNoteList.get(i));

            problemPracticeNoteMappingRepository.save(problemPracticeNoteMapping);
        }
    }

    @AfterEach
    void tearDown() {
        problemImageDataRepository.deleteAll();
        problemRepository.deleteAll();
        problemPracticeNoteMappingRepository.deleteAll();
        practiceNoteRepository.deleteAll();

        problemList.clear();
        practiceNoteList.clear();
    }

    @Test
    @DisplayName("문제가 복습 노트에 존재하는지 체크 - 존재할 경우")
    void checkProblemAlreadyMatchingWithPracticeTest_Exist() {
        // given
        Long practiceId = practiceNoteList.get(0).getId();
        Long problemId = problemList.get(0).getId();

        // when
        boolean alreadyMatching = practiceNoteRepository.checkProblemAlreadyMatchingWithPractice(practiceId, problemId);

        // then
        Assertions.assertTrue(alreadyMatching);
    }

    @Test
    @DisplayName("문제가 복습 노트에 존재하는지 체크 - 존재하지 않을 경우")
    void checkProblemAlreadyMatchingWithPracticeTest_NotExist() {
        // given
        Long practiceId = practiceNoteList.get(0).getId();
        Long problemId = problemList.get(problemList.size() - 1).getId();

        // when
        boolean alreadyMatching = practiceNoteRepository.checkProblemAlreadyMatchingWithPractice(practiceId, problemId);

        // then
        Assertions.assertFalse(alreadyMatching);
    }

    @Test
    @DisplayName("복습 노트 상세 정보 조회")
    void findPracticeNoteWithDetailsTest(){
        // given
        PracticeNote practiceNote = practiceNoteList.get(0);
        Long practiceId = practiceNote.getId();

        // when
        Optional<PracticeNote> optionalPracticeNote = practiceNoteRepository.findPracticeNoteWithDetails(practiceId);
        assertThat(optionalPracticeNote.isPresent()).isTrue();

        PracticeNote targetPracticeNote = optionalPracticeNote.get();

        // then
        assertThat(practiceNote.getId()).isEqualTo(targetPracticeNote.getId());
        assertThat(practiceNote.getTitle()).isEqualTo(targetPracticeNote.getTitle());
        assertThat(practiceNote.getProblemPracticeNoteMappingList().size()).isEqualTo(4);
        assertThat(practiceNote.getProblemPracticeNoteMappingList().size()).isEqualTo(targetPracticeNote.getProblemPracticeNoteMappingList().size());
    }

    @Test
    @DisplayName("복습 노트 상세 정보 조회")
    void findProblemIdListByPracticeNoteIdTest(){
        // given
        PracticeNote practiceNote = practiceNoteList.get(0);
        Long practiceId = practiceNote.getId();

        // when
        List<Long> problemIdList = practiceNoteRepository.findProblemIdListByPracticeNoteId(practiceId);

        // then
        assertThat(practiceNote.getProblemPracticeNoteMappingList().size()).isEqualTo(4);
        assertThat(practiceNote.getProblemPracticeNoteMappingList().size()).isEqualTo(problemIdList.size());
        for(int i = 0; i< problemIdList.size(); i++){
            assertThat(problemIdList).contains(problemList.get(i).getId());
        }
    }

    @Test
    @DisplayName("특정 문제를 특정 복습 노트에서 제거")
    void deleteProblemFromPracticeTest(){
        // given
        PracticeNote practiceNote = practiceNoteList.get(0);
        Long practiceId = practiceNote.getId();
        Long problemId = problemList.get(0).getId();

        // when
        assertThat(problemPracticeNoteMappingRepository.findProblemPracticeNoteMappingByProblemIdAndPracticeNoteId(problemId, practiceId)).isNotEmpty();
        practiceNoteRepository.deleteProblemFromPractice(practiceId, problemId);

        // then
        assertThat(problemPracticeNoteMappingRepository.findProblemPracticeNoteMappingByProblemIdAndPracticeNoteId(problemId, practiceId)).isEmpty();
    }

    @Test
    @DisplayName("해당 문제를 모두 복습 노트에서 제거")
    void deleteProblemFromAllPracticeTest(){
        // given
        Long problemId = problemList.get(0).getId();

        // when
        for(int i = 0; i< practiceNoteList.size(); i++){
            assertThat(problemPracticeNoteMappingRepository.findProblemPracticeNoteMappingByProblemIdAndPracticeNoteId(problemId, practiceNoteList.get(i).getId())).isNotEmpty();
        }

        practiceNoteRepository.deleteProblemFromAllPractice(problemId);

        // then
        for(int i = 0; i< practiceNoteList.size(); i++){
            assertThat(problemPracticeNoteMappingRepository.findProblemPracticeNoteMappingByProblemIdAndPracticeNoteId(problemId, practiceNoteList.get(i).getId())).isEmpty();
        }

    }

    @Test
    @DisplayName("문제 리스트를 받아 해당 문제들을 모든 복습 노트에서 제거")
    void deleteProblemsFromAllPracticeTest(){
        // given
        PracticeNote practiceNote = practiceNoteList.get(0);
        Long practiceId = practiceNote.getId();
        List<Long> problemIdList = new ArrayList<>();
        for(int i = 0; i < practiceNote.getProblemPracticeNoteMappingList().size(); i++){
            problemIdList.add(problemList.get(i).getId());
        }

        // when
        practiceNoteRepository.deleteProblemsFromAllPractice(problemIdList);

        // then
        for(int i = 0; i < problemIdList.size(); i++){
            assertThat(problemPracticeNoteMappingRepository.findProblemPracticeNoteMappingByProblemIdAndPracticeNoteId(problemIdList.get(i), practiceId)).isEmpty();
        }
    }
}