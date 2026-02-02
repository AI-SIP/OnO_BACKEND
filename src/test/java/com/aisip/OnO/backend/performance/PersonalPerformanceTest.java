package com.aisip.OnO.backend.performance;

import com.aisip.OnO.backend.folder.repository.FolderRepository;
import com.aisip.OnO.backend.practicenote.repository.PracticeNoteRepository;import com.aisip.OnO.backend.problem.repository.ProblemRepository;
import com.aisip.OnO.backend.user.entity.User;
import com.aisip.OnO.backend.user.repository.UserRepository;
import com.aisip.OnO.backend.util.BulkDataGenerator;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.DefaultTransactionDefinition;
import jakarta.persistence.EntityManager;

/**
 * BulkDataGenerator를 사용한 개인 성능 테스트
 *
 * 특정 유저에 대해 대량의 데이터를 생성하고
 * 다양한 시나리오에서 성능을 측정합니다.
 */
@Slf4j
@SpringBootTest
@ActiveProfiles("local")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class PersonalPerformanceTest {

    @Autowired
    private BulkDataGenerator bulkDataGenerator;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private FolderRepository folderRepository;

    @Autowired
    private ProblemRepository problemRepository;

    @Autowired
    private PracticeNoteRepository practiceNoteRepository;

    private static final Long testUserId = 2252L;
    private static String testUserIdentifier;

    @BeforeAll
    static void setup(@Autowired UserRepository userRepository,
                      @Autowired BulkDataGenerator bulkDataGenerator,
                      @Autowired PlatformTransactionManager transactionManager) {
        log.info("========================================");
        log.info("BulkDataGenerator를 사용한 테스트 데이터 생성");
        log.info("========================================");

        // 트랜잭션 시작
        TransactionStatus transaction = transactionManager.getTransaction(new DefaultTransactionDefinition());

        try {
            // 1. 테스트 유저 조회
            User user = userRepository.findById(2252L).get();
            testUserIdentifier = user.getIdentifier();

            // 2. BulkDataGenerator로 대량 데이터 생성
            // 원하는 데이터 규모에 따라 메서드 선택:
            // - generateSmallBulkData(): 소량 (10 폴더, 폴더당 10 문제, 10 복습노트)
            // - generateDefaultBulkData(): 기본 (100 폴더, 폴더당 50 문제, 50 복습노트)
            // - generateLargeBulkData(): 대량 (500 폴더, 폴더당 100 문제, 200 복습노트)
            // - generateBulkData(userId, folderCount, problemsPerFolder, practiceNoteCount): 커스텀

            log.info("대량 데이터 생성 시작...");
            BulkDataGenerator.BulkDataResult result = bulkDataGenerator.generateLargeBulkData(testUserId);

            log.info("========================================");
            log.info("테스트 데이터 생성 완료!");
            log.info(result.toString());
            log.info("========================================");

            // 트랜잭션 커밋
            transactionManager.commit(transaction);
        } catch (Exception e) {
            transactionManager.rollback(transaction);
            log.error("테스트 데이터 생성 실패: {}", e.getMessage(), e);
            throw e;
        }
    }

    @AfterAll
    static void cleanup(@Autowired UserRepository userRepository,
                        @Autowired PlatformTransactionManager transactionManager,
                        @Autowired EntityManager entityManager) {
        /*
        log.info("========================================");
        log.info("테스트 데이터 정리 시작");
        log.info("========================================");

        // 트랜잭션 시작
        TransactionStatus transaction = transactionManager.getTransaction(new DefaultTransactionDefinition());

        try {
            // 외래 키 제약 조건 임시 비활성화 (MySQL)
            entityManager.createNativeQuery("SET FOREIGN_KEY_CHECKS = 0").executeUpdate();
            log.info("외래 키 제약 조건 비활성화");

            if (testUserId == null) {
                log.warn("삭제할 테스트 유저 ID가 없습니다. 데이터 정리를 건너뜁니다.");
                transactionManager.commit(transaction);
                return;
            }

            String userIdStr = String.valueOf(testUserId);

            // Native Query로 물리적 삭제 (순서 중요: 자식 → 부모)
            // 1. problem_practice_note_mapping 삭제
            int mappingDeleted = entityManager.createNativeQuery(
                    "DELETE ppm FROM problem_practice_note_mapping ppm " +
                    "INNER JOIN problem p ON ppm.problem_id = p.id " +
                    "WHERE p.user_id = " + userIdStr
            ).executeUpdate();
            log.info("problem_practice_note_mapping {} 건 삭제", mappingDeleted);

            // 2. problem_analysis 삭제
            int analysisDeleted = entityManager.createNativeQuery(
                    "DELETE pa FROM problem_analysis pa " +
                    "INNER JOIN problem p ON pa.problem_id = p.id " +
                    "WHERE p.user_id = " + userIdStr
            ).executeUpdate();
            log.info("problem_analysis {} 건 삭제", analysisDeleted);

            // 3. image_data 삭제
            int imageDeleted = entityManager.createNativeQuery(
                    "DELETE id FROM image_data id " +
                    "INNER JOIN problem p ON id.problem_id = p.id " +
                    "WHERE p.user_id = " + userIdStr
            ).executeUpdate();
            log.info("image_data {} 건 삭제", imageDeleted);

            // 4. problem 삭제
            int problemDeleted = entityManager.createNativeQuery(
                    "DELETE FROM problem WHERE user_id = " + userIdStr
            ).executeUpdate();
            log.info("problem {} 건 삭제", problemDeleted);

            // 5. practice_note 삭제
            int practiceDeleted = entityManager.createNativeQuery(
                    "DELETE FROM practice_note WHERE user_id = " + userIdStr
            ).executeUpdate();
            log.info("practice_note {} 건 삭제", practiceDeleted);

            // 6. folder 삭제
            int folderDeleted = entityManager.createNativeQuery(
                    "DELETE FROM folder WHERE user_id = " + userIdStr
            ).executeUpdate();
            log.info("folder {} 건 삭제", folderDeleted);

            // 7. mission_log 삭제
            int missionDeleted = entityManager.createNativeQuery(
                    "DELETE FROM mission_log WHERE user_id = " + userIdStr
            ).executeUpdate();
            log.info("mission_log {} 건 삭제", missionDeleted);

            // 8. user 삭제
            int userDeleted = entityManager.createNativeQuery(
                    "DELETE FROM user WHERE id = " + userIdStr
            ).executeUpdate();
            log.info("user {} 건 삭제", userDeleted);

            // 외래 키 제약 조건 재활성화
            entityManager.createNativeQuery("SET FOREIGN_KEY_CHECKS = 1").executeUpdate();
            log.info("외래 키 제약 조건 재활성화");

            // 트랜잭션 커밋
            transactionManager.commit(transaction);

            log.info("테스트 데이터 물리적 삭제 완료");
        } catch (Exception e) {
            transactionManager.rollback(transaction);
            log.error("테스트 데이터 정리 실패: {}", e.getMessage(), e);
            throw e;
        }

        log.info("========================================");

         */
    }

    /**
     * 1. 폴더 조회 성능 테스트
     */
    @Test
    @Order(1)
    @DisplayName("1. 유저의 모든 폴더 조회 성능 테스트")
    void testFindAllFolders() {
        log.info("\n========================================");
        log.info("Test 1: 유저의 모든 폴더 조회");
        log.info("========================================");

        // Warm-up
        folderRepository.findAllByUserId(testUserId);

        long startTime = System.nanoTime();
        var folders = folderRepository.findAllByUserId(testUserId);
        long endTime = System.nanoTime();

        long executionTime = (endTime - startTime) / 1_000_000;

        log.info("조회 결과: {} 개의 폴더", folders.size());
        log.info("실행 시간: {}ms", executionTime);
        log.info("========================================\n");
    }

    /**
     * 2. 문제 조회 성능 테스트
     */
    @Test
    @Order(2)
    @DisplayName("2. 유저의 모든 문제 조회 성능 테스트")
    void testFindAllProblems() {
        log.info("\n========================================");
        log.info("Test 2: 유저의 모든 문제 조회");
        log.info("========================================");

        // Warm-up
        problemRepository.findAllByUserId(testUserId);

        long startTime = System.nanoTime();
        var problems = problemRepository.findAllByUserId(testUserId);
        long endTime = System.nanoTime();

        long executionTime = (endTime - startTime) / 1_000_000;

        log.info("조회 결과: {} 개의 문제", problems.size());
        log.info("실행 시간: {}ms", executionTime);
        log.info("========================================\n");
    }

    /**
     * 3. 문제 개수 카운트 성능 테스트
     */
    @Test
    @Order(3)
    @DisplayName("3. 유저의 문제 개수 카운트 성능 테스트")
    void testCountProblems() {
        log.info("\n========================================");
        log.info("Test 3: 유저의 문제 개수 카운트");
        log.info("========================================");

        // Warm-up
        problemRepository.countByUserId(testUserId);

        long startTime = System.nanoTime();
        long count = problemRepository.countByUserId(testUserId);
        long endTime = System.nanoTime();

        long executionTime = (endTime - startTime) / 1_000_000;

        log.info("문제 개수: {} 개", count);
        log.info("실행 시간: {}ms", executionTime);
        log.info("========================================\n");
    }

    /**
     * 4. 복습노트 조회 성능 테스트
     */
    @Test
    @Order(4)
    @DisplayName("4. 유저의 모든 복습노트 조회 성능 테스트")
    void testFindAllPracticeNotes() {
        log.info("\n========================================");
        log.info("Test 4: 유저의 모든 복습노트 조회");
        log.info("========================================");

        // Warm-up
        practiceNoteRepository.findAllByUserId(testUserId);

        long startTime = System.nanoTime();
        var practiceNotes = practiceNoteRepository.findAllByUserId(testUserId);
        long endTime = System.nanoTime();

        long executionTime = (endTime - startTime) / 1_000_000;

        log.info("조회 결과: {} 개의 복습노트", practiceNotes.size());
        log.info("실행 시간: {}ms", executionTime);
        log.info("========================================\n");
    }

    /**
     * 5. 유저 조회 (identifier) 성능 테스트
     */
    @Test
    @Order(5)
    @DisplayName("5. Identifier로 유저 조회 성능 테스트")
    void testFindUserByIdentifier() {
        log.info("\n========================================");
        log.info("Test 5: Identifier로 유저 조회");
        log.info("========================================");

        // Warm-up
        userRepository.findByIdentifier(testUserIdentifier);

        long startTime = System.nanoTime();
        var user = userRepository.findByIdentifier(testUserIdentifier);
        long endTime = System.nanoTime();

        long executionTime = (endTime - startTime) / 1_000_000;

        log.info("조회 결과: userId={}, identifier={}", user.map(User::getId).orElse(null), testUserIdentifier);
        log.info("실행 시간: {}ms", executionTime);
        log.info("========================================\n");
    }

    /**
     * 6. 특정 폴더의 문제 조회 성능 테스트
     */
    @Test
    @Order(6)
    @DisplayName("6. 특정 폴더의 문제 조회 성능 테스트")
    void testFindProblemsByFolder() {
        log.info("\n========================================");
        log.info("Test 6: 특정 폴더의 문제 조회");
        log.info("========================================");

        // 첫 번째 폴더 선택
        var folders = folderRepository.findAllByUserId(testUserId);
        if (folders.isEmpty()) {
            log.warn("테스트할 폴더가 없습니다.");
            return;
        }

        Long testFolderId = folders.get(0).getId();

        // Warm-up
        problemRepository.findAllByFolderId(testFolderId);

        long startTime = System.nanoTime();
        var problems = problemRepository.findAllByFolderId(testFolderId);
        long endTime = System.nanoTime();

        long executionTime = (endTime - startTime) / 1_000_000;

        log.info("조회 결과: 폴더 ID={}, 문제 {} 개", testFolderId, problems.size());
        log.info("실행 시간: {}ms", executionTime);
        log.info("========================================\n");
    }

    /**
     * 7. 종합 성능 테스트
     */
    @Test
    @Order(7)
    @DisplayName("7. 종합 성능 테스트 (모든 쿼리 연속 실행)")
    void testOverallPerformance() {
        log.info("\n========================================");
        log.info("Test 7: 종합 성능 테스트");
        log.info("========================================");

        long totalStartTime = System.currentTimeMillis();

        // 1. 폴더 조회
        long start1 = System.currentTimeMillis();
        var folders = folderRepository.findAllByUserId(testUserId);
        long time1 = System.currentTimeMillis() - start1;

        // 2. 문제 조회
        long start2 = System.currentTimeMillis();
        var problems = problemRepository.findAllByUserId(testUserId);
        long time2 = System.currentTimeMillis() - start2;

        // 3. 문제 카운트
        long start3 = System.currentTimeMillis();
        long problemCount = problemRepository.countByUserId(testUserId);
        long time3 = System.currentTimeMillis() - start3;

        // 4. 복습노트 조회
        long start4 = System.currentTimeMillis();
        var practiceNotes = practiceNoteRepository.findAllByUserId(testUserId);
        long time4 = System.currentTimeMillis() - start4;

        // 5. 유저 조회
        long start5 = System.currentTimeMillis();
        var user = userRepository.findByIdentifier(testUserIdentifier);
        long time5 = System.currentTimeMillis() - start5;

        long totalTime = System.currentTimeMillis() - totalStartTime;

        log.info("----------------------------------------");
        log.info("1. 폴더 조회: {} 건, {}ms", folders.size(), time1);
        log.info("2. 문제 조회: {} 건, {}ms", problems.size(), time2);
        log.info("3. 문제 카운트: {} 건, {}ms", problemCount, time3);
        log.info("4. 복습노트 조회: {} 건, {}ms", practiceNotes.size(), time4);
        log.info("5. 유저 조회 (identifier): {}ms", time5);
        log.info("----------------------------------------");
        log.info("전체 실행 시간: {}ms", totalTime);
        log.info("========================================\n");
    }
}