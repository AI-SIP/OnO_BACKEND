package com.aisip.OnO.backend.performance;

import com.aisip.OnO.backend.folder.entity.Folder;
import com.aisip.OnO.backend.folder.repository.FolderRepository;
import com.aisip.OnO.backend.practicenote.entity.PracticeNote;
import com.aisip.OnO.backend.practicenote.repository.PracticeNoteRepository;
import com.aisip.OnO.backend.problem.entity.Problem;
import com.aisip.OnO.backend.problem.entity.ProblemImageData;
import com.aisip.OnO.backend.problem.repository.ProblemImageDataRepository;
import com.aisip.OnO.backend.problem.repository.ProblemRepository;
import com.aisip.OnO.backend.user.entity.User;
import com.aisip.OnO.backend.user.repository.UserRepository;
import com.aisip.OnO.backend.util.RandomFolderGenerator;
import com.aisip.OnO.backend.util.RandomPracticeNoteGenerator;
import com.aisip.OnO.backend.util.RandomProblemGenerator;
import com.aisip.OnO.backend.util.RandomUserGenerator;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.DefaultTransactionDefinition;
import jakarta.persistence.EntityManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 인덱스 도입 전후 성능 비교 테스트
 *
 * 테스트 대상 인덱스:
 * 1. Problem.folder_id
 * 2. Folder.userId
 * 3. Problem.userId
 * 4. PracticeNote.userId
 * 5. User.identifier (UNIQUE)
 */
@Slf4j
@SpringBootTest
@ActiveProfiles("local")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class LargePerformanceTest {

    @Autowired
    private ProblemRepository problemRepository;

    @Autowired
    private FolderRepository folderRepository;

    @Autowired
    private PracticeNoteRepository practiceNoteRepository;

    @Autowired
    private UserRepository userRepository;

    private static final int USER_COUNT = 500;
    private static final int FOLDER_PER_USER = 10;
    private static final int PROBLEM_PER_FOLDER = 25;
    private static final int PRACTICE_NOTE_PER_USER = 20;
    private static final int QUERY_REPEAT_COUNT = 10;

    private static List<Long> testUserIds = new ArrayList<>();
    private static List<Long> testFolderIds = new ArrayList<>();
    private static List<String> testIdentifiers = new ArrayList<>();

    @BeforeAll
    static void setup(@Autowired UserRepository userRepository,
                      @Autowired FolderRepository folderRepository,
                      @Autowired ProblemRepository problemRepository,
                      @Autowired PracticeNoteRepository practiceNoteRepository,
                      @Autowired ProblemImageDataRepository problemImageDataRepository,
                      @Autowired PlatformTransactionManager transactionManager,
                      @Autowired EntityManager entityManager) {
        log.info("========================================");
        log.info("테스트 데이터 생성 시작");
        log.info("========================================");

        // 트랜잭션 시작
        TransactionStatus transaction = transactionManager.getTransaction(new DefaultTransactionDefinition());

        long startTime = System.currentTimeMillis();

        // 1. User 생성
        for (int i = 0; i < USER_COUNT; i++) {
            User user = RandomUserGenerator.createRandomUser();
            user = userRepository.save(user);
            testUserIds.add(user.getId());
            testIdentifiers.add(user.getIdentifier());
        }
        log.info("User {} 개 생성 완료", USER_COUNT);

        // 2. Folder 생성
        for (Long userId : testUserIds) {
            for (int i = 0; i < FOLDER_PER_USER; i++) {
                Folder folder = RandomFolderGenerator.createRandomFolder(userId);

                folder = folderRepository.save(folder);
                testFolderIds.add(folder.getId());
            }
        }
        log.info("Folder {} 개 생성 완료 (총 {}개)", FOLDER_PER_USER, testFolderIds.size());

        // 3. Problem 생성 (배치 처리)
        int problemCount = 0;
        int batchSize = 100;
        for (Long folderId : testFolderIds) {
            Folder folder = folderRepository.findById(folderId).orElseThrow();
            for (int i = 0; i < PROBLEM_PER_FOLDER; i++) {
                Problem problem = RandomProblemGenerator.createRandomProblemWithFolder(folder, folder.getUserId());
                problem.updateFolder(folder);
                problemRepository.save(problem);
                problemCount++;

                List<ProblemImageData> imageDataList = RandomProblemGenerator.createDefaultProblemImageDataList(problem.getId());

                imageDataList.forEach(imageData -> {
                    imageData.updateProblem(problem);
                    ProblemImageData saveImageData = problemImageDataRepository.save(imageData);
                });

                // 배치 단위로 flush & clear
                if (problemCount % batchSize == 0) {
                    entityManager.flush();
                    entityManager.clear();
                    if (problemCount % 10000 == 0) {
                        log.info("Problem {} / {} 생성 중...", problemCount, testFolderIds.size() * PROBLEM_PER_FOLDER);
                    }
                }
            }
        }
        entityManager.flush();
        entityManager.clear();
        log.info("Problem {} 개 생성 완료", problemCount);

        // 4. PracticeNote 생성
        int practiceNoteCount = 0;
        for (Long userId : testUserIds) {
            for (int i = 0; i < PRACTICE_NOTE_PER_USER; i++) {
                PracticeNote practiceNote = RandomPracticeNoteGenerator.createRandomPracticeNote(userId);
                practiceNoteRepository.save(practiceNote);
                practiceNoteCount++;
            }
        }
        log.info("PracticeNote {} 개 생성 완료", practiceNoteCount);

        long endTime = System.currentTimeMillis();
        log.info("========================================");
        log.info("테스트 데이터 생성 완료: {}ms", (endTime - startTime));
        log.info("User: {}, Folder: {}, Problem: {}, PracticeNote: {}",
                USER_COUNT, testFolderIds.size(), problemCount, practiceNoteCount);
        log.info("========================================");

        // 트랜잭션 커밋
        transactionManager.commit(transaction);
    }

    @AfterAll
    static void cleanup(@Autowired UserRepository userRepository,
                        @Autowired FolderRepository folderRepository,
                        @Autowired ProblemRepository problemRepository,
                        @Autowired PracticeNoteRepository practiceNoteRepository,
                        @Autowired PlatformTransactionManager transactionManager,
                        @Autowired EntityManager entityManager) {
        log.info("========================================");
        log.info("테스트 데이터 정리 시작");
        log.info("========================================");

        // 트랜잭션 시작
        TransactionStatus transaction = transactionManager.getTransaction(new DefaultTransactionDefinition());

        try {
            // 외래 키 제약 조건 임시 비활성화 (MySQL)
            entityManager.createNativeQuery("SET FOREIGN_KEY_CHECKS = 0").executeUpdate();
            log.info("외래 키 제약 조건 비활성화");

            // testUserIds를 기반으로 테스트 데이터만 삭제
            String userIdsStr = testUserIds.stream()
                    .map(String::valueOf)
                    .reduce((a, b) -> a + "," + b)
                    .orElse("");

            if (userIdsStr.isEmpty()) {
                log.warn("삭제할 테스트 유저 ID가 없습니다. 데이터 정리를 건너뜁니다.");
                return;
            }

            // Native Query로 물리적 삭제 (순서 중요: 자식 → 부모)
            // 1. problem_practice_note_mapping 삭제 (problem과 practice_note를 통해 간접 참조)
            int mappingDeleted = entityManager.createNativeQuery(
                    "DELETE ppm FROM problem_practice_note_mapping ppm " +
                    "INNER JOIN problem p ON ppm.problem_id = p.id " +
                    "WHERE p.user_id IN (" + userIdsStr + ")"
            ).executeUpdate();
            log.info("problem_practice_note_mapping {} 건 삭제", mappingDeleted);

            // 2. problem_analysis 삭제 (problem을 참조)
            int analysisDeleted = entityManager.createNativeQuery(
                    "DELETE pa FROM problem_analysis pa " +
                    "INNER JOIN problem p ON pa.problem_id = p.id " +
                    "WHERE p.user_id IN (" + userIdsStr + ")"
            ).executeUpdate();
            log.info("problem_analysis {} 건 삭제", analysisDeleted);

            // 3. image_data 삭제 (problem을 참조)
            int imageDeleted = entityManager.createNativeQuery(
                    "DELETE id FROM image_data id " +
                    "INNER JOIN problem p ON id.problem_id = p.id " +
                    "WHERE p.user_id IN (" + userIdsStr + ")"
            ).executeUpdate();
            log.info("image_data {} 건 삭제", imageDeleted);

            // 4. problem 삭제 (테스트 유저의 문제만)
            int problemDeleted = entityManager.createNativeQuery(
                    "DELETE FROM problem WHERE user_id IN (" + userIdsStr + ")"
            ).executeUpdate();
            log.info("problem {} 건 삭제", problemDeleted);

            // 5. practice_note 삭제 (테스트 유저의 복습노트만)
            int practiceDeleted = entityManager.createNativeQuery(
                    "DELETE FROM practice_note WHERE user_id IN (" + userIdsStr + ")"
            ).executeUpdate();
            log.info("practice_note {} 건 삭제", practiceDeleted);

            // 6. folder 삭제 (테스트 유저의 폴더만, 자기 자신을 참조하므로 FK 체크 비활성화 필요)
            int folderDeleted = entityManager.createNativeQuery(
                    "DELETE FROM folder WHERE user_id IN (" + userIdsStr + ")"
            ).executeUpdate();
            log.info("folder {} 건 삭제", folderDeleted);

            // 7. mission_log 삭제 (테스트 유저의 미션 로그만)
            int missionDeleted = entityManager.createNativeQuery(
                    "DELETE FROM mission_log WHERE user_id IN (" + userIdsStr + ")"
            ).executeUpdate();
            log.info("mission_log {} 건 삭제", missionDeleted);

            // 8. user 삭제 (테스트 유저만)
            int userDeleted = entityManager.createNativeQuery(
                    "DELETE FROM user WHERE id IN (" + userIdsStr + ")"
            ).executeUpdate();
            log.info("user {} 건 삭제", userDeleted);

            // 외래 키 제약 조건 재활성화
            entityManager.createNativeQuery("SET FOREIGN_KEY_CHECKS = 1").executeUpdate();
            log.info("외래 키 제약 조건 재활성화");

            // 트랜잭션 커밋
            transactionManager.commit(transaction);

            log.info("테스트 데이터 물리적 삭제 완료");
        } catch (Exception e) {
            // 에러 발생 시 롤백
            transactionManager.rollback(transaction);
            log.error("테스트 데이터 정리 실패: {}", e.getMessage(), e);
            throw e;  // 에러를 다시 던져서 문제를 확인할 수 있도록
        }

        log.info("========================================");
    }

    /**
     * 1. Problem.folder_id 인덱스 성능 테스트
     * 쿼리: findAllByFolderId(), findProblemIdsByFolder()
     */
    @Test
    @Order(1)
    @DisplayName("1. Problem.folder_id 인덱스 성능 테스트")
    void testProblemFolderIdIndex() {
        log.info("\n========================================");
        log.info("Test 1: Problem.folder_id 인덱스");
        log.info("========================================");

        Random random = new Random();

        // Warm-up
        problemRepository.findAllByFolderId(testFolderIds.get(0));

        List<Long> executionTimes = new ArrayList<>();

        for (int i = 0; i < QUERY_REPEAT_COUNT; i++) {
            Long folderId = testFolderIds.get(random.nextInt(testFolderIds.size()));

            long startTime = System.nanoTime();
            List<Problem> problems = problemRepository.findAllByFolderId(folderId);
            long endTime = System.nanoTime();

            long executionTime = (endTime - startTime) / 1_000_000; // ms로 변환
            executionTimes.add(executionTime);

            log.info("Query {}: folderId={}, 결과={} 건, 실행시간={}ms",
                    i + 1, folderId, problems.size(), executionTime);
        }

        double avgTime = executionTimes.stream().mapToLong(Long::longValue).average().orElse(0);
        long maxTime = executionTimes.stream().mapToLong(Long::longValue).max().orElse(0);
        long minTime = executionTimes.stream().mapToLong(Long::longValue).min().orElse(0);

        log.info("----------------------------------------");
        log.info("평균 실행시간: {}ms", String.format("%.2f", avgTime));
        log.info("최대 실행시간: {}ms", maxTime);
        log.info("최소 실행시간: {}ms", minTime);
        log.info("========================================\n");
    }

    /**
     * 2. Folder.userId 인덱스 성능 테스트
     * 쿼리: findAllByUserId(), findRootFolder(), findAllFoldersWithDetailsByUserId()
     */
    @Test
    @Order(2)
    @DisplayName("2. Folder.userId 인덱스 성능 테스트")
    void testFolderUserIdIndex() {
        log.info("\n========================================");
        log.info("Test 2: Folder.userId 인덱스");
        log.info("========================================");

        Random random = new Random();

        // Warm-up
        folderRepository.findAllByUserId(testUserIds.get(0));

        List<Long> executionTimes = new ArrayList<>();

        for (int i = 0; i < QUERY_REPEAT_COUNT; i++) {
            Long userId = testUserIds.get(random.nextInt(testUserIds.size()));

            long startTime = System.nanoTime();
            List<Folder> folders = folderRepository.findAllByUserId(userId);
            long endTime = System.nanoTime();

            long executionTime = (endTime - startTime) / 1_000_000;
            executionTimes.add(executionTime);

            log.info("Query {}: userId={}, 결과={} 건, 실행시간={}ms",
                    i + 1, userId, folders.size(), executionTime);
        }

        double avgTime = executionTimes.stream().mapToLong(Long::longValue).average().orElse(0);
        long maxTime = executionTimes.stream().mapToLong(Long::longValue).max().orElse(0);
        long minTime = executionTimes.stream().mapToLong(Long::longValue).min().orElse(0);

        log.info("----------------------------------------");
        log.info("평균 실행시간: {}ms", String.format("%.2f", avgTime));
        log.info("최대 실행시간: {}ms", maxTime);
        log.info("최소 실행시간: {}ms", minTime);
        log.info("========================================\n");
    }

    /**
     * 3. Problem.userId 인덱스 성능 테스트
     * 쿼리: findAllByUserId(), countByUserId()
     */
    @Test
    @Order(3)
    @DisplayName("3. Problem.userId 인덱스 성능 테스트")
    void testProblemUserIdIndex() {
        log.info("\n========================================");
        log.info("Test 3: Problem.userId 인덱스");
        log.info("========================================");

        Random random = new Random();

        // Warm-up
        problemRepository.findAllByUserId(testUserIds.get(0));

        List<Long> executionTimes = new ArrayList<>();

        for (int i = 0; i < QUERY_REPEAT_COUNT; i++) {
            Long userId = testUserIds.get(random.nextInt(testUserIds.size()));

            long startTime = System.nanoTime();
            List<Problem> problems = problemRepository.findAllByUserId(userId);
            long endTime = System.nanoTime();

            long executionTime = (endTime - startTime) / 1_000_000;
            executionTimes.add(executionTime);

            log.info("Query {}: userId={}, 결과={} 건, 실행시간={}ms",
                    i + 1, userId, problems.size(), executionTime);
        }

        double avgTime = executionTimes.stream().mapToLong(Long::longValue).average().orElse(0);
        long maxTime = executionTimes.stream().mapToLong(Long::longValue).max().orElse(0);
        long minTime = executionTimes.stream().mapToLong(Long::longValue).min().orElse(0);

        log.info("----------------------------------------");
        log.info("평균 실행시간: {}ms", String.format("%.2f", avgTime));
        log.info("최대 실행시간: {}ms", maxTime);
        log.info("최소 실행시간: {}ms", minTime);
        log.info("========================================\n");
    }

    /**
     * 4. PracticeNote.userId 인덱스 성능 테스트
     * 쿼리: findAllByUserId(), findAllUserPracticeNotesWithDetails()
     */
    @Test
    @Order(4)
    @DisplayName("4. PracticeNote.userId 인덱스 성능 테스트")
    void testPracticeNoteUserIdIndex() {
        log.info("\n========================================");
        log.info("Test 4: PracticeNote.userId 인덱스");
        log.info("========================================");

        Random random = new Random();

        // Warm-up
        practiceNoteRepository.findAllByUserId(testUserIds.get(0));

        List<Long> executionTimes = new ArrayList<>();

        for (int i = 0; i < QUERY_REPEAT_COUNT; i++) {
            Long userId = testUserIds.get(random.nextInt(testUserIds.size()));

            long startTime = System.nanoTime();
            List<PracticeNote> practiceNotes = practiceNoteRepository.findAllByUserId(userId);
            long endTime = System.nanoTime();

            long executionTime = (endTime - startTime) / 1_000_000;
            executionTimes.add(executionTime);

            log.info("Query {}: userId={}, 결과={} 건, 실행시간={}ms",
                    i + 1, userId, practiceNotes.size(), executionTime);
        }

        double avgTime = executionTimes.stream().mapToLong(Long::longValue).average().orElse(0);
        long maxTime = executionTimes.stream().mapToLong(Long::longValue).max().orElse(0);
        long minTime = executionTimes.stream().mapToLong(Long::longValue).min().orElse(0);

        log.info("----------------------------------------");
        log.info("평균 실행시간: {}ms", String.format("%.2f", avgTime));
        log.info("최대 실행시간: {}ms", maxTime);
        log.info("최소 실행시간: {}ms", minTime);
        log.info("========================================\n");
    }

    /**
     * 5. User.identifier 인덱스 성능 테스트
     * 쿼리: findByIdentifier()
     */
    @Test
    @Order(5)
    @DisplayName("5. User.identifier 인덱스 성능 테스트")
    void testUserIdentifierIndex() {
        log.info("\n========================================");
        log.info("Test 5: User.identifier 인덱스");
        log.info("========================================");

        Random random = new Random();

        // Warm-up
        userRepository.findByIdentifier(testIdentifiers.get(0));

        List<Long> executionTimes = new ArrayList<>();

        for (int i = 0; i < QUERY_REPEAT_COUNT; i++) {
            String identifier = testIdentifiers.get(random.nextInt(testIdentifiers.size()));

            long startTime = System.nanoTime();
            userRepository.findByIdentifier(identifier);
            long endTime = System.nanoTime();

            long executionTime = (endTime - startTime) / 1_000_000;
            executionTimes.add(executionTime);

            log.info("Query {}: identifier={}, 실행시간={}ms",
                    i + 1, identifier, executionTime);
        }

        double avgTime = executionTimes.stream().mapToLong(Long::longValue).average().orElse(0);
        long maxTime = executionTimes.stream().mapToLong(Long::longValue).max().orElse(0);
        long minTime = executionTimes.stream().mapToLong(Long::longValue).min().orElse(0);

        log.info("----------------------------------------");
        log.info("평균 실행시간: {}ms", String.format("%.2f", avgTime));
        log.info("최대 실행시간: {}ms", maxTime);
        log.info("최소 실행시간: {}ms", minTime);
        log.info("========================================\n");
    }

    /**
     * 종합 성능 테스트 - 모든 쿼리를 순차적으로 실행
     */
    @Test
    @Order(6)
    @DisplayName("6. 종합 성능 테스트")
    void testOverallPerformance() {
        log.info("\n========================================");
        log.info("Test 6: 종합 성능 테스트");
        log.info("========================================");

        Random random = new Random();
        long totalStartTime = System.currentTimeMillis();

        // 1. Problem by folderId
        long start1 = System.currentTimeMillis();
        for (int i = 0; i < QUERY_REPEAT_COUNT; i++) {
            problemRepository.findAllByFolderId(testFolderIds.get(random.nextInt(testFolderIds.size())));
        }
        long time1 = System.currentTimeMillis() - start1;

        // 2. Folder by userId
        long start2 = System.currentTimeMillis();
        for (int i = 0; i < QUERY_REPEAT_COUNT; i++) {
            folderRepository.findAllByUserId(testUserIds.get(random.nextInt(testUserIds.size())));
        }
        long time2 = System.currentTimeMillis() - start2;

        // 3. Problem by userId
        long start3 = System.currentTimeMillis();
        for (int i = 0; i < QUERY_REPEAT_COUNT; i++) {
            problemRepository.findAllByUserId(testUserIds.get(random.nextInt(testUserIds.size())));
        }
        long time3 = System.currentTimeMillis() - start3;

        // 4. PracticeNote by userId
        long start4 = System.currentTimeMillis();
        for (int i = 0; i < QUERY_REPEAT_COUNT; i++) {
            practiceNoteRepository.findAllByUserId(testUserIds.get(random.nextInt(testUserIds.size())));
        }
        long time4 = System.currentTimeMillis() - start4;

        // 5. User by identifier
        long start5 = System.currentTimeMillis();
        for (int i = 0; i < QUERY_REPEAT_COUNT; i++) {
            userRepository.findByIdentifier(testIdentifiers.get(random.nextInt(testIdentifiers.size())));
        }
        long time5 = System.currentTimeMillis() - start5;

        long totalTime = System.currentTimeMillis() - totalStartTime;

        log.info("----------------------------------------");
        log.info("1. Problem.folder_id 조회 ({}회): {}ms (평균 {}ms)",
                QUERY_REPEAT_COUNT, time1, String.format("%.2f", (double) time1 / QUERY_REPEAT_COUNT));
        log.info("2. Folder.userId 조회 ({}회): {}ms (평균 {}ms)",
                QUERY_REPEAT_COUNT, time2, String.format("%.2f", (double) time2 / QUERY_REPEAT_COUNT));
        log.info("3. Problem.userId 조회 ({}회): {}ms (평균 {}ms)",
                QUERY_REPEAT_COUNT, time3, String.format("%.2f", (double) time3 / QUERY_REPEAT_COUNT));
        log.info("4. PracticeNote.userId 조회 ({}회): {}ms (평균 {}ms)",
                QUERY_REPEAT_COUNT, time4, String.format("%.2f", (double) time4 / QUERY_REPEAT_COUNT));
        log.info("5. User.identifier 조회 ({}회): {}ms (평균 {}ms)",
                QUERY_REPEAT_COUNT, time5, String.format("%.2f", (double) time5 / QUERY_REPEAT_COUNT));
        log.info("----------------------------------------");
        log.info("전체 실행 시간: {}ms", totalTime);
        log.info("========================================\n");
    }
}
