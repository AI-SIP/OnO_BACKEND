package com.aisip.OnO.backend.concurrency;

import com.aisip.OnO.backend.common.exception.ApplicationException;
import com.aisip.OnO.backend.folder.dto.FolderRegisterDto;
import com.aisip.OnO.backend.folder.entity.Folder;
import com.aisip.OnO.backend.folder.exception.FolderErrorCase;
import com.aisip.OnO.backend.folder.service.FolderService;
import com.aisip.OnO.backend.problem.dto.ProblemRegisterDto;
import com.aisip.OnO.backend.problem.dto.ProblemRegisterV2BatchDto;
import com.aisip.OnO.backend.problem.dto.ProblemRegisterV2Dto;
import com.aisip.OnO.backend.problem.entity.Problem;
import com.aisip.OnO.backend.problem.service.ProblemService;
import com.aisip.OnO.backend.problem.support.ProblemTestSupport;
import com.aisip.OnO.backend.support.TestContainers;
import com.aisip.OnO.backend.user.entity.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.assertj.core.api.Assertions.fail;

/**
 * 폴더 삭제와 문제 등록이 같은 폴더에서 겹칠 때 고아 문제가 남지 않는지 본다. (#233)
 *
 * <p>예전에는 폴더는 소프트 삭제되는데 그 사이 등록된 문제가 삭제된 폴더를 가리킨 채 살아남았다.
 * 어느 폴더에서도 보이지 않지만 문제 개수 집계에는 잡히는 상태다.
 *
 * <p>스레드를 동시에 출발시키는 방식({@link ConcurrentRunner})은 경합이 매번 같은 순서로 일어난다는
 * 보장이 없다. 여기서는 순서를 직접 만든다. 한쪽 트랜잭션을 커밋 직전에 세워 두고, 다른 쪽이
 * {@code folder} 테이블 잠금에서 실제로 기다리기 시작한 것을 {@code performance_schema} 로 확인한 뒤에
 * 앞 트랜잭션을 커밋시킨다. 시간(sleep)으로 순서를 맞추지 않으므로 결과가 흔들리지 않는다.
 */
@DisplayName("동시성 - 폴더 삭제와 문제 등록 경합")
class FolderDeleteProblemRegisterRaceTest extends ProblemTestSupport {

    private static final long STEP_TIMEOUT_SECONDS = 20;

    @Autowired
    private ProblemService problemService;

    @Autowired
    private FolderService folderService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final ExecutorService executor = Executors.newFixedThreadPool(2);
    private final CountDownLatch release = new CountDownLatch(1);

    private User user;
    private Folder rootFolder;
    private Folder target;

    @BeforeEach
    void setUpFolders() {
        user = fixtures.createUser();
        rootFolder = fixtures.createRootFolder(user.getId());
        target = fixtures.createFolder(user.getId(), "삭제 대상", rootFolder);
        saveProblem(user.getId(), target);
    }

    @AfterEach
    void releaseHeldTransaction() throws InterruptedException {
        // 단언이 중간에 실패해도 세워 둔 트랜잭션이 커밋되고 스레드가 끝나야 다음 테스트의 DB 정리가 막히지 않는다.
        release.countDown();
        executor.shutdown();
        assertThat(executor.awaitTermination(STEP_TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();
    }

    @Nested
    @DisplayName("등록이 폴더를 먼저 잡은 경우")
    class RegistrationFirst {

        @Test
        @DisplayName("삭제는 등록이 끝날 때까지 기다렸다가 새로 들어온 문제까지 함께 지운다")
        void deleteWaitsAndRemovesProblemRegisteredMeanwhile() throws Exception {
            Future<?> registration = holdUntilReleased(() -> problemService.registerProblem(
                    new ProblemRegisterDto(null, "경합 메모", null, target.getId(), null), user.getId()));

            Future<?> deletion = executor.submit(
                    () -> folderService.deleteFoldersWithProblems(user.getId(), List.of(target.getId())));
            awaitFolderLockWaitOrDone(deletion);
            release.countDown();

            registration.get(STEP_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            deletion.get(STEP_TIMEOUT_SECONDS, TimeUnit.SECONDS);

            assertThat(aliveProblemCountInFolder(target.getId()))
                    .as("등록된 문제가 삭제된 폴더를 가리킨 채 남으면 어느 폴더에서도 안 보이는 고아가 된다")
                    .isZero();
            assertNoOrphanProblems();
        }

        @Test
        @DisplayName("하위 폴더에 v2 일괄 등록 중 상위 폴더를 지워도 하위 폴더 문제까지 함께 지워진다")
        void deletingParentRemovesBatchRegisteredIntoSubFolder() throws Exception {
            Folder child = fixtures.createFolder(user.getId(), "하위", target);

            Future<?> registration = holdUntilReleased(() -> problemService.registerProblemsV2(
                    new ProblemRegisterV2BatchDto(List.of(
                            new ProblemRegisterV2Dto(null, "일괄1", null, child.getId(), null, null, null),
                            new ProblemRegisterV2Dto(null, "일괄2", null, child.getId(), null, null, null))),
                    user.getId()));

            Future<?> deletion = executor.submit(
                    () -> folderService.deleteFoldersWithProblems(user.getId(), List.of(target.getId())));
            awaitFolderLockWaitOrDone(deletion);
            release.countDown();

            registration.get(STEP_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            deletion.get(STEP_TIMEOUT_SECONDS, TimeUnit.SECONDS);

            assertThat(aliveProblemCountInFolder(child.getId())).isZero();
            assertNoOrphanProblems();
        }

        @Test
        @DisplayName("전체 폴더 삭제도 등록이 끝날 때까지 기다렸다가 새로 들어온 문제까지 함께 지운다")
        void deleteAllWaitsAndRemovesProblemRegisteredMeanwhile() throws Exception {
            Future<?> registration = holdUntilReleased(() -> problemService.registerProblem(
                    new ProblemRegisterDto(null, "경합 메모", null, target.getId(), null), user.getId()));

            Future<?> deletion = executor.submit(() -> folderService.deleteAllUserFoldersWithProblems(user.getId()));
            awaitFolderLockWaitOrDone(deletion);
            release.countDown();

            registration.get(STEP_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            deletion.get(STEP_TIMEOUT_SECONDS, TimeUnit.SECONDS);

            assertThat(aliveProblemCountInFolder(target.getId()))
                    .as("전체 삭제 경로에는 폴더 잠금이 없어서, 삭제 스냅숏 이후 커밋된 문제가 그대로 남았다")
                    .isZero();
            assertNoOrphanProblems();
        }

        @Test
        @DisplayName("폴더 생성이 부모를 먼저 잡으면 삭제가 기다렸다가 새 폴더까지 함께 지운다")
        void deleteWaitsAndRemovesFolderCreatedMeanwhile() throws Exception {
            Future<?> creation = holdUntilReleased(() -> folderService.createFolder(
                    new FolderRegisterDto("경합 폴더", null, target.getId()), user.getId()));

            Future<?> deletion = executor.submit(
                    () -> folderService.deleteFoldersWithProblems(user.getId(), List.of(target.getId())));
            awaitFolderLockWaitOrDone(deletion);
            release.countDown();

            creation.get(STEP_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            deletion.get(STEP_TIMEOUT_SECONDS, TimeUnit.SECONDS);

            assertNoOrphanFolders();
        }
    }

    @Nested
    @DisplayName("삭제가 폴더를 먼저 잡은 경우")
    class DeletionFirst {

        @Test
        @DisplayName("등록은 삭제가 끝날 때까지 기다렸다가 FOLDER_NOT_FOUND 로 거절된다")
        void registrationWaitsAndIsRejected() throws Exception {
            Future<?> deletion = holdUntilReleased(
                    () -> folderService.deleteFoldersWithProblems(user.getId(), List.of(target.getId())));

            Future<Long> registration = executor.submit(() -> problemService.registerProblem(
                    new ProblemRegisterDto(null, "경합 메모", null, target.getId(), null), user.getId()));
            awaitFolderLockWaitOrDone(registration);
            release.countDown();

            deletion.get(STEP_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            assertRejectedWithFolderNotFound(registration);
            assertThat(aliveProblemCountInFolder(target.getId())).isZero();
            assertNoOrphanProblems();
        }

        @Test
        @DisplayName("v2 단건 등록도 삭제된 하위 폴더로는 들어가지 않는다")
        void v2RegistrationIntoDeletedSubFolderIsRejected() throws Exception {
            Folder child = fixtures.createFolder(user.getId(), "하위", target);

            Future<?> deletion = holdUntilReleased(
                    () -> folderService.deleteFoldersWithProblems(user.getId(), List.of(target.getId())));

            Future<Long> registration = executor.submit(() -> problemService.registerProblemV2(
                    new ProblemRegisterV2Dto(null, "경합 메모", null, child.getId(), null, null, null), user.getId()));
            awaitFolderLockWaitOrDone(registration);
            release.countDown();

            deletion.get(STEP_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            assertRejectedWithFolderNotFound(registration);
            assertNoOrphanProblems();
        }

        @Test
        @DisplayName("삭제 중인 폴더로 문제를 옮기는 요청도 FOLDER_NOT_FOUND 로 거절되고 문제는 원래 폴더에 남는다")
        void moveIntoDeletedFolderIsRejected() throws Exception {
            Folder keep = fixtures.createFolder(user.getId(), "남는 폴더", rootFolder);
            Problem moving = saveProblem(user.getId(), keep);

            Future<?> deletion = holdUntilReleased(
                    () -> folderService.deleteFoldersWithProblems(user.getId(), List.of(target.getId())));

            Future<?> move = executor.submit(() -> problemService.updateProblemFolder(
                    new ProblemRegisterDto(moving.getId(), null, null, target.getId(), null), user.getId()));
            awaitFolderLockWaitOrDone(move);
            release.countDown();

            deletion.get(STEP_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            assertRejectedWithFolderNotFound(move);
            assertThat(aliveProblemCountInFolder(keep.getId())).isEqualTo(1);
            assertNoOrphanProblems();
        }

        @Test
        @DisplayName("삭제 중인 폴더 아래에 새 폴더를 만드는 요청은 FOLDER_NOT_FOUND 로 거절된다")
        void folderCreationUnderDeletedParentIsRejected() throws Exception {
            Future<?> deletion = holdUntilReleased(
                    () -> folderService.deleteFoldersWithProblems(user.getId(), List.of(target.getId())));

            Future<Long> creation = executor.submit(() -> folderService.createFolder(
                    new FolderRegisterDto("경합 폴더", null, target.getId()), user.getId()));
            awaitFolderLockWaitOrDone(creation);
            release.countDown();

            deletion.get(STEP_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            assertRejectedWithFolderNotFound(creation);
            assertNoOrphanFolders();
        }

        @Test
        @DisplayName("삭제 중인 폴더 아래로 폴더를 옮기는 요청도 FOLDER_NOT_FOUND 로 거절된다")
        void folderMoveUnderDeletedParentIsRejected() throws Exception {
            Folder moving = fixtures.createFolder(user.getId(), "옮길 폴더", rootFolder);

            Future<?> deletion = holdUntilReleased(
                    () -> folderService.deleteFoldersWithProblems(user.getId(), List.of(target.getId())));

            Future<?> move = executor.submit(() -> folderService.updateFolder(
                    new FolderRegisterDto("옮길 폴더", moving.getId(), target.getId()), user.getId()));
            awaitFolderLockWaitOrDone(move);
            release.countDown();

            deletion.get(STEP_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            assertRejectedWithFolderNotFound(move);
            assertNoOrphanFolders();
        }
    }

    /** 작업을 트랜잭션 안에서 실행하고, 커밋하기 직전에 {@link #release} 가 풀릴 때까지 붙잡아 둔다. */
    private Future<?> holdUntilReleased(Runnable work) throws InterruptedException {
        CountDownLatch workDone = new CountDownLatch(1);
        Future<?> future = executor.submit(() -> transactionTemplate.executeWithoutResult(status -> {
            work.run();
            workDone.countDown();
            awaitRelease();
        }));

        boolean reached = workDone.await(STEP_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        if (!reached) {
            release.countDown();
            rethrowIfFailed(future);
            fail("먼저 잡아야 할 트랜잭션이 %d초 안에 작업을 끝내지 못했다", STEP_TIMEOUT_SECONDS);
        }
        return future;
    }

    private void awaitRelease() {
        try {
            if (!release.await(STEP_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                throw new IllegalStateException("release 신호를 받지 못했다");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    /**
     * 뒤에 들어온 요청이 {@code folder} 행 잠금에서 실제로 기다리기 시작할 때까지 기다린다.
     *
     * <p>잠금 없이 그냥 끝나 버리는 경우(수정 전 코드의 일부 경로)도 있으므로 요청이 끝나면 바로 돌아간다.
     * 이때는 뒤이은 상태 단언이 고아 데이터를 잡아낸다.
     */
    private void awaitFolderLockWaitOrDone(Future<?> waiter) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(STEP_TIMEOUT_SECONDS);
        try (Connection root = rootConnection();
             PreparedStatement statement = root.prepareStatement("""
                     SELECT COUNT(*)
                     FROM performance_schema.data_lock_waits w
                     JOIN performance_schema.data_locks l
                       ON l.ENGINE_LOCK_ID = w.REQUESTING_ENGINE_LOCK_ID
                     WHERE l.OBJECT_SCHEMA = DATABASE() AND l.OBJECT_NAME = 'folder'
                     """)) {
            while (System.nanoTime() < deadline) {
                if (waiter.isDone()) {
                    return;
                }
                try (ResultSet resultSet = statement.executeQuery()) {
                    resultSet.next();
                    if (resultSet.getLong(1) > 0) {
                        return;
                    }
                }
                Thread.sleep(10);
            }
        }
        fail("뒤에 들어온 요청이 %d초 안에 folder 잠금 대기에 들어가지도, 끝나지도 않았다", STEP_TIMEOUT_SECONDS);
    }

    /** performance_schema 는 테스트 계정 권한으로 볼 수 없어 root 로 붙는다. */
    private Connection rootConnection() throws SQLException {
        return DriverManager.getConnection(
                TestContainers.mysql().getJdbcUrl(), "root", TestContainers.mysql().getPassword());
    }

    private void assertRejectedWithFolderNotFound(Future<?> future) {
        Throwable thrown = catchThrowable(() -> future.get(STEP_TIMEOUT_SECONDS, TimeUnit.SECONDS));

        assertThat(thrown)
                .as("삭제가 커밋된 뒤의 등록은 성공하면 안 되고, 500 이 아니라 기존 404 계약으로 거절돼야 한다")
                .isInstanceOf(ExecutionException.class)
                .cause()
                .isInstanceOf(ApplicationException.class);
        assertThat(((ApplicationException) thrown.getCause()).getErrorCase())
                .isEqualTo(FolderErrorCase.FOLDER_NOT_FOUND);
    }

    private void rethrowIfFailed(Future<?> future) {
        try {
            future.get(STEP_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new AssertionError("먼저 잡아야 할 트랜잭션이 실패했다", e);
        }
    }

    private long aliveProblemCountInFolder(Long folderId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM problem WHERE folder_id = ? AND deleted_at IS NULL", Long.class, folderId);
    }

    private void assertNoOrphanProblems() {
        Long orphanCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM problem p
                JOIN folder f ON f.id = p.folder_id
                WHERE p.user_id = ? AND p.deleted_at IS NULL AND f.deleted_at IS NOT NULL
                """, Long.class, user.getId());
        assertThat(orphanCount)
                .as("삭제된 폴더를 가리키는 살아 있는 문제")
                .isZero();
    }

    private void assertNoOrphanFolders() {
        Long orphanCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM folder f
                JOIN folder p ON p.id = f.parent_folder_id
                WHERE f.user_id = ? AND f.deleted_at IS NULL AND p.deleted_at IS NOT NULL
                """, Long.class, user.getId());
        assertThat(orphanCount)
                .as("삭제된 폴더를 부모로 가리키는 살아 있는 폴더")
                .isZero();
    }
}
