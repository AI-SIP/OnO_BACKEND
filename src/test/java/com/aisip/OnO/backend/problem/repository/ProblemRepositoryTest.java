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
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
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
                    LocalDateTime.now()
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

    /**
     * 컬렉션 fetch join + limit 을 한 쿼리로 쓰면 Hibernate 가 SQL 에 LIMIT 을 걸지 못하고
     * 조건에 맞는 행을 전부 읽은 뒤 메모리에서 잘라냈다 (HHH90003004).
     * 쿼리 "횟수"는 그때도 1번이라 지표가 못 된다. 실제로 몇 건을 읽었는지로 고정한다.
     */
    @Test
    @DisplayName("폴더 커서 조회 - 폴더에 5건이 있어도 요청한 size 만큼만 읽는다")
    void findProblemsByFolderWithCursor_readsOnlyRequestedRows() {
        Statistics statistics = em.getEntityManagerFactory().unwrap(SessionFactory.class).getStatistics();
        statistics.setStatisticsEnabled(true);
        statistics.clear();

        List<Problem> problems = problemRepository.findProblemsByFolderWithCursor(savedFolder.getId(), null, 2);

        // hasNext 판단용 +1개
        assertThat(problems).hasSize(3);
        assertThat(problems.get(0).getProblemImageDataList()).hasSize(3);

        // 폴더에는 5건이 있다. 전부 읽어 메모리에서 자르던 시절에는 5건이 적재됐다
        long loadedProblems = statistics.getEntityStatistics(Problem.class.getName()).getLoadCount();
        assertThat(loadedProblems).isEqualTo(3);
    }

    @Test
    @DisplayName("폴더 커서 조회 - 커서 이후 문제만 반환한다")
    void findProblemsByFolderWithCursor_respectsCursor() {
        List<Problem> firstPage = problemRepository.findProblemsByFolderWithCursor(savedFolder.getId(), null, 2);
        Long cursor = firstPage.get(1).getId();

        List<Problem> secondPage = problemRepository.findProblemsByFolderWithCursor(savedFolder.getId(), cursor, 2);

        assertThat(secondPage).isNotEmpty();
        assertThat(secondPage).allSatisfy(problem ->
                assertThat(problem.getId()).isGreaterThan(cursor));
        assertThat(secondPage.get(0).getProblemImageDataList()).hasSize(3);
    }

    @Test
    @DisplayName("제목 커서 조회 - size 만큼만 반환하고 이미지가 붙는다")
    void findProblemsByTitleWithCursor_limits() {
        List<Problem> problems =
                problemRepository.findProblemsByTitleWithCursor("reference", savedUser.getId(), null, 2);

        assertThat(problems).hasSize(3);
        assertThat(problems.get(0).getProblemImageDataList()).hasSize(3);
    }
}