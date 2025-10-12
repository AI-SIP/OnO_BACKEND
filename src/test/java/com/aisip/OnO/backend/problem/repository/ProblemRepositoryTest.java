package com.aisip.OnO.backend.problem.repository;

import com.aisip.OnO.backend.folder.dto.FolderRegisterDto;
import com.aisip.OnO.backend.folder.entity.Folder;
import com.aisip.OnO.backend.folder.repository.FolderRepository;
import com.aisip.OnO.backend.practicenote.dto.PracticeNoteRegisterDto;
import com.aisip.OnO.backend.practicenote.dto.PracticeNotificationRegisterDto;
import com.aisip.OnO.backend.practicenote.entity.PracticeNote;
import com.aisip.OnO.backend.practicenote.entity.ProblemPracticeNoteMapping;
import com.aisip.OnO.backend.practicenote.repository.PracticeNoteRepository;
import com.aisip.OnO.backend.practicenote.repository.ProblemPracticeNoteMappingRepository;
import com.aisip.OnO.backend.problem.dto.ProblemImageDataRegisterDto;
import com.aisip.OnO.backend.problem.dto.ProblemRegisterDto;
import com.aisip.OnO.backend.problem.entity.Problem;
import com.aisip.OnO.backend.problem.entity.ProblemImageData;
import com.aisip.OnO.backend.problem.entity.ProblemImageType;
import com.aisip.OnO.backend.user.dto.UserRegisterDto;
import com.aisip.OnO.backend.user.entity.User;
import com.aisip.OnO.backend.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@ExtendWith(SpringExtension.class)
class ProblemRepositoryTest {

    @Autowired private ProblemRepository problemRepository;

    @Autowired private ProblemImageDataRepository problemImageDataRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private FolderRepository folderRepository;

    @Autowired private PracticeNoteRepository practiceNoteRepository;

    @Autowired private ProblemPracticeNoteMappingRepository problemPracticeNoteMappingRepository;
    @Autowired private EntityManager em;

    private User savedUser;
    private Folder savedFolder;

    private PracticeNote savedPracticeNote;

    @BeforeEach
    void setUp() {
        UserRegisterDto userRegisterDto = new UserRegisterDto(
                "test@example.com",
                "testUser",
                "identifier",
                "MEMBER",
                "password"
        );
        savedUser = userRepository.save(User.from(
                userRegisterDto
        ));

        FolderRegisterDto folderRegisterDto = new FolderRegisterDto(
                "folder",
                1L,
                null
        );
        savedFolder = folderRepository.save(Folder.from(
                folderRegisterDto,
                null,
                savedUser.getId()
        ));

        PracticeNoteRegisterDto practiceNoteRegisterDto = new PracticeNoteRegisterDto(
                null,
                "practiceTitle",
                List.of(1L, 2L, 3L, 4L, 5L),
                new PracticeNotificationRegisterDto(1, 9, 0, "NONE", null)
        );
        savedPracticeNote = practiceNoteRepository.save(PracticeNote.from(
                practiceNoteRegisterDto,
                savedUser.getId()
        ));

        for (int i = 1; i <= 5; i++) {
            ProblemRegisterDto problemRegisterDto = new ProblemRegisterDto(
                    (long) i,
                    "memo" + i,
                    "reference" + i,
                    savedFolder.getId(),
                    LocalDateTime.now(),
                    null
            );
            Problem problem = problemRepository.save(Problem.from(
                    problemRegisterDto,
                    savedUser.getId()
            ));
            problem.updateFolder(savedFolder);

            ProblemPracticeNoteMapping problemPracticeNoteMapping = ProblemPracticeNoteMapping.from();
            problemPracticeNoteMapping.addMappingToProblemAndPractice(problem, savedPracticeNote);
            problemPracticeNoteMappingRepository.save(problemPracticeNoteMapping);

            // 연관된 이미지 추가
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
        }

        // flush & clear 로 영속성 컨텍스트 초기화 (fetch join 테스트에서 중요)
        em.flush();
        em.clear();
    }

    @AfterEach
    void tearDown() {
    }

    @Test
    @DisplayName("userId로 문제 조회 - 성공")
    void findAllByUserId_success() {
        List<Problem> problems = problemRepository.findAllByUserId(savedUser.getId());

        assertThat(problems).hasSize(5);
        assertThat(problems.get(0).getProblemImageDataList()).hasSize(3); // fetch join 확인
    }

    @Test
    @DisplayName("folderId로 문제 조회 - 실패")
    void findAllByFolderId_success() {
        List<Problem> problems = problemRepository.findAllByFolderId(savedFolder.getId());

        assertThat(problems).hasSize(5);
        assertThat(problems.get(0).getProblemImageDataList()).hasSize(3); // fetch join 확인
    }

    @Test
    @DisplayName("practiceId로 문제 조회 - 실패")
    void findAllByPracticeId_success() {
        List<Problem> problems = problemRepository.findAllProblemsByPracticeId(savedPracticeNote.getId());

        assertThat(problems).hasSize(5);
        assertThat(problems.get(0).getProblemImageDataList()).hasSize(3); // fetch join 확인
    }
}