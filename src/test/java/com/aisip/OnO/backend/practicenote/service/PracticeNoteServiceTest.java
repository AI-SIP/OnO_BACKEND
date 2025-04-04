package com.aisip.OnO.backend.practicenote.service;

import com.aisip.OnO.backend.folder.dto.FolderRegisterDto;
import com.aisip.OnO.backend.folder.entity.Folder;
import com.aisip.OnO.backend.practicenote.dto.PracticeNoteRegisterDto;
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
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
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
    void registerPractice() {
    }

    @Test
    void findPracticeNoteDetail() {
    }

    @Test
    void findAllPracticeThumbnailsByUser() {
    }

    @Test
    void updatePracticeNoteCount() {
    }

    @Test
    void updatePracticeInfo() {
    }

    @Test
    void deletePractice() {
    }

    @Test
    void deletePractices() {
    }

    @Test
    void deleteAllPracticesByUser() {
    }

    @Test
    void deleteProblemsFromAllPractice() {
    }
}