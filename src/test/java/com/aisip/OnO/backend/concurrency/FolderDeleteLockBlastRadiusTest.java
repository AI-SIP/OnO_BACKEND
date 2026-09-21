package com.aisip.OnO.backend.concurrency;

import com.aisip.OnO.backend.folder.entity.Folder;
import com.aisip.OnO.backend.folder.service.FolderService;
import com.aisip.OnO.backend.problem.dto.ProblemRegisterV2Dto;
import com.aisip.OnO.backend.problem.service.ProblemService;
import com.aisip.OnO.backend.problem.support.ProblemTestSupport;
import com.aisip.OnO.backend.support.TestContainers;
import com.aisip.OnO.backend.user.entity.User;
import com.github.gavlyukovskiy.boot.jdbc.decorator.DecoratedDataSource;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

/**
 * 폴더 삭제 잠금이 삭제 대상 밖으로 번지지 않는지 본다. (#319)
 *
 * <p>폴더 하나를 지우는 동안 두 군데서 잠금이 새어 나갔다.
 * <ul>
 *   <li>#233 을 막으려고 넣은 폴더 잠금이 삭제 대상 서브트리가 아니라 <b>사용자 폴더 전체</b>를 잡았다.
 *       삭제와 아무 상관 없는 폴더로 들어오는 등록까지 삭제가 끝날 때까지 기다렸다.</li>
 *   <li>문제마다 도는 복습 알림 취소가 {@code problem_id} 범위 UPDATE 라
 *       {@code uq_problem_review_reminder_seq} 인덱스 끝의 갭까지 next-key lock 으로 잡았다.
 *       {@code problem_id} 는 계속 커지므로 <b>그 뒤에 등록되는 모든 문제</b>의 예약 INSERT 가
 *       사용자와 무관하게 막혔다.</li>
 * </ul>
 *
 * <p>기다리는 요청은 하나씩 커넥션을 물고 있다. 그래서 대기가 길어지면 Hikari 풀(기본 10개)이 바닥나고,
 * 폴더와도 그 사용자와도 무관한 요청까지 {@code connection-timeout}(기본 30초) 뒤에 실패한다.
 * dev 에서 잰 30.2초가 이 값이다.
 *
 * <p>그래서 여기서는 고아 데이터가 아니라 <b>영향 범위</b>를 본다.
 * <ol>
 *   <li>삭제 트랜잭션이 잠그는 {@code folder} 행이 삭제 대상 서브트리뿐인지 ({@code performance_schema.data_locks})</li>
 *   <li>삭제와 무관한 폴더로 들어오는 등록이 막히지 않는지</li>
 *   <li>다른 계정의 등록이 막히지 않는지</li>
 *   <li>등록이 몰려 들어와도 전부 막히지 않는지</li>
 * </ol>
 *
 * <p>고아 데이터 쪽 계약은 {@link FolderDeleteProblemRegisterRaceTest} 가 그대로 들고 있다.
 * 잠금 범위를 줄이는 변경은 두 파일이 같이 통과해야 한다.
 */
@DisplayName("동시성 - 폴더 삭제 잠금의 영향 범위")
class FolderDeleteLockBlastRadiusTest extends ProblemTestSupport {

    private static final long STEP_TIMEOUT_SECONDS = 20;

    /**
     * 잠금이 번지지 않으면 이 안에 끝난다.
     *
     * <p>번지면 잠금 대기({@code innodb_lock_wait_timeout} 기본 50초)나
     * 커넥션 대기(Hikari {@code connection-timeout} 기본 30초)로 넘어가므로, 몇 초짜리 여유로 갈린다.
     */
    private static final long NO_BLOCKING_TIMEOUT_SECONDS = 8;

    @Autowired
    private ProblemService problemService;

    @Autowired
    private FolderService folderService;

    @Autowired
    private DataSource dataSource;

    private ExecutorService executor;
    private final CountDownLatch release = new CountDownLatch(1);

    private User owner;
    private Folder ownerRoot;
    private Folder target;
    private Folder targetChild;
    private Folder untouched;

    private User other;
    private Folder otherRoot;

    private int poolSize;

    @BeforeEach
    void setUpFoldersAndPool() {
        owner = fixtures.createUser();
        ownerRoot = fixtures.createRootFolder(owner.getId());
        target = fixtures.createFolder(owner.getId(), "삭제 대상", ownerRoot);
        targetChild = fixtures.createFolder(owner.getId(), "삭제 대상 하위", target);
        untouched = fixtures.createFolder(owner.getId(), "삭제와 무관한 폴더", ownerRoot);
        saveProblem(owner.getId(), target);

        other = fixtures.createUser();
        otherRoot = fixtures.createRootFolder(other.getId());

        poolSize = maximumPoolSize();
        executor = Executors.newFixedThreadPool(poolSize + 4);
    }

    /**
     * 한 번에 몰아 넣을 등록 수. 커넥션 풀의 절반까지만 쓴다.
     *
     * <p>문제 등록은 커밋 직후 복습 알림 예약을 {@code AFTER_COMMIT} + {@code REQUIRES_NEW} 로 넣는다.
     * 그동안 바깥 트랜잭션의 커넥션이 아직 반납되기 전이라, 요청 하나가 <b>커넥션 두 개를 동시에</b> 쥔다.
     * 그래서 동시 등록이 풀 크기에 닿으면 폴더 삭제가 없어도 풀이 서로를 기다리며 멈추고,
     * 전부 {@code connection-timeout} 을 채운다. 실제로 이 테스트에서 8건을 몰아 넣으면
     * "사용중 10, 유휴 0, 커넥션 대기중인 스레드 8" 로 멈춘다.
     *
     * <p>그건 폴더 삭제 잠금과는 다른 원인이라 여기서 고정하지 않는다. 이 테스트가 보는 것은
     * "폴더 삭제가 무관한 등록을 붙잡고 있는가" 뿐이므로, 풀이 문제가 되지 않는 선까지만 몰아 넣는다.
     */
    private int concurrentRegistrationCount() {
        return Math.max(2, poolSize / 2);
    }

    @AfterEach
    void releaseHeldTransaction() throws InterruptedException {
        // 단언이 중간에 실패해도 세워 둔 트랜잭션이 커밋되고 스레드가 끝나야 다음 테스트의 DB 정리가 막히지 않는다.
        release.countDown();
        executor.shutdown();
        assertThat(executor.awaitTermination(STEP_TIMEOUT_SECONDS * 4, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    @DisplayName("삭제 트랜잭션은 삭제 대상 서브트리 밖의 폴더 행을 잠그지 않는다")
    void deleteLocksOnlyTargetSubtree() throws Exception {
        holdUntilReleased(() -> folderService.deleteFoldersWithProblems(owner.getId(), List.of(target.getId())));

        Set<Long> lockedFolderIds = lockedFolderRowIds();
        Set<String> lockedIndexNames = lockedFolderIndexNames();

        assertThat(lockedFolderIds)
                .as("사용자 폴더 전체를 잡으면 루트와 무관한 폴더까지 잠겨서, 그 폴더로 들어오는 등록이 다 줄을 선다")
                .containsExactlyInAnyOrder(target.getId(), targetChild.getId());
        assertThat(lockedIndexNames)
                .as("idx_folder_user_id 등치 스캔은 next-key lock 이라 갭이 인접 사용자 구간까지 덮는다 (#291 댓글 실측)")
                .doesNotContain("idx_folder_user_id");
    }

    @Test
    @DisplayName("삭제와 무관한 폴더로 들어오는 등록은 삭제를 기다리지 않는다")
    void registrationIntoUnrelatedFolderIsNotBlocked() throws Exception {
        holdUntilReleased(() -> folderService.deleteFoldersWithProblems(owner.getId(), List.of(target.getId())));

        Future<Long> registration = executor.submit(() -> problemService.registerProblemV2(
                new ProblemRegisterV2Dto(null, "무관한 폴더 등록", null, untouched.getId(), null, null, null),
                owner.getId()));

        assertCompletesWithoutBlocking(registration, "삭제 대상이 아닌 폴더로 들어오는 등록");
    }

    @Test
    @DisplayName("다른 계정의 문제 등록은 남의 폴더 삭제를 기다리지 않는다")
    void registrationByAnotherUserIsNotBlocked() throws Exception {
        holdUntilReleased(() -> folderService.deleteFoldersWithProblems(owner.getId(), List.of(target.getId())));

        Future<Long> registration = executor.submit(() -> problemService.registerProblemV2(
                new ProblemRegisterV2Dto(null, "다른 계정 등록", null, otherRoot.getId(), null, null, null),
                other.getId()));

        assertCompletesWithoutBlocking(registration, "폴더를 지운 사용자와 아무 관계도 없는 다른 계정의 문제 등록");
    }

    @Test
    @DisplayName("삭제와 무관한 폴더로 등록이 몰려도 전부 삭제를 기다리지 않는다")
    void concurrentRegistrationsIntoUnrelatedFolderAreNotBlocked() throws Exception {
        holdUntilReleased(() -> folderService.deleteFoldersWithProblems(owner.getId(), List.of(target.getId())));

        List<Future<Long>> registrations = new ArrayList<>();
        for (int i = 0; i < concurrentRegistrationCount(); i++) {
            String memo = "동시 등록 " + i;
            registrations.add(executor.submit(() -> problemService.registerProblemV2(
                    new ProblemRegisterV2Dto(null, memo, null, untouched.getId(), null, null, null),
                    owner.getId())));
        }

        for (int i = 0; i < registrations.size(); i++) {
            assertCompletesWithoutBlocking(registrations.get(i), (i + 1) + "번째 동시 등록");
        }
    }

    /** 작업을 트랜잭션 안에서 실행하고, 커밋하기 직전에 {@link #release} 가 풀릴 때까지 붙잡아 둔다. */
    private void holdUntilReleased(Runnable work) throws Exception {
        CountDownLatch workDone = new CountDownLatch(1);
        Future<?> future = executor.submit(() -> transactionTemplate.executeWithoutResult(status -> {
            work.run();
            workDone.countDown();
            awaitRelease();
        }));

        if (!workDone.await(STEP_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            release.countDown();
            future.get(STEP_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            fail("먼저 잡아야 할 삭제 트랜잭션이 %d초 안에 잠금을 잡지 못했다", STEP_TIMEOUT_SECONDS);
        }
    }

    private void awaitRelease() {
        try {
            if (!release.await(STEP_TIMEOUT_SECONDS * 4, TimeUnit.SECONDS)) {
                throw new IllegalStateException("release 신호를 받지 못했다");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private void assertCompletesWithoutBlocking(Future<?> future, String what) {
        try {
            future.get(NO_BLOCKING_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (java.util.concurrent.TimeoutException e) {
            fail("%s 가 폴더 삭제 때문에 %d초 안에 끝나지 않았다.%n커넥션 풀: %s%n대기 사슬:%n%s",
                    what, NO_BLOCKING_TIMEOUT_SECONDS, poolStats(), lockWaitChain());
        } catch (Exception e) {
            throw new AssertionError(what + " 가 실패했다", e);
        }
    }

    /** 커넥션을 못 받아 막힌 것인지 바로 보이게 찍는다. */
    private String poolStats() {
        var pool = hikariDataSource().getHikariPoolMXBean();
        return "최대 %d, 사용중 %d, 유휴 %d, 커넥션 대기중인 스레드 %d".formatted(
                poolSize, pool.getActiveConnections(), pool.getIdleConnections(),
                pool.getThreadsAwaitingConnection());
    }

    /** 왜 막혔는지 바로 보이도록 {@code performance_schema} 의 대기 사슬을 그대로 찍는다. */
    private String lockWaitChain() {
        StringBuilder chain = new StringBuilder();
        try (Connection root = rootConnection();
             PreparedStatement statement = root.prepareStatement("""
                     SELECT r.OBJECT_NAME, r.INDEX_NAME, r.LOCK_TYPE, r.LOCK_MODE, r.LOCK_DATA,
                            b.LOCK_MODE, b.LOCK_DATA
                     FROM performance_schema.data_lock_waits w
                     JOIN performance_schema.data_locks r ON r.ENGINE_LOCK_ID = w.REQUESTING_ENGINE_LOCK_ID
                     JOIN performance_schema.data_locks b ON b.ENGINE_LOCK_ID = w.BLOCKING_ENGINE_LOCK_ID
                     """);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                chain.append("  요청 %s.%s %s %s [%s] <- 보유 %s [%s]%n".formatted(
                        resultSet.getString(1), resultSet.getString(2), resultSet.getString(3),
                        resultSet.getString(4), resultSet.getString(5),
                        resultSet.getString(6), resultSet.getString(7)));
            }
        } catch (SQLException e) {
            chain.append("  (대기 사슬을 읽지 못했다: ").append(e.getMessage()).append(')');
        }
        return chain.isEmpty() ? "  (잠금 대기 없음 - 커넥션 풀 대기일 수 있다)" : chain.toString();
    }

    /**
     * 지금 잠겨 있는 {@code folder} 행의 기본 키 집합.
     *
     * <p>{@code X,GAP} 처럼 갭만 잡은 잠금은 뺀다. 갭 잠금은 그 행을 잡은 것이 아니라 앞의 빈 구간을 잡은 것이다.
     * {@code X,REC_NOT_GAP} 도 'GAP' 으로 끝나므로 {@code LIKE '%GAP'} 로 거르면 전부 빠진다.
     */
    private Set<Long> lockedFolderRowIds() throws Exception {
        Set<Long> lockedIds = new LinkedHashSet<>();
        try (Connection root = rootConnection();
             PreparedStatement statement = root.prepareStatement("""
                     SELECT DISTINCT LOCK_DATA
                     FROM performance_schema.data_locks
                     WHERE OBJECT_SCHEMA = DATABASE()
                       AND OBJECT_NAME = 'folder'
                       AND INDEX_NAME = 'PRIMARY'
                       AND LOCK_TYPE = 'RECORD'
                       AND LOCK_MODE NOT LIKE '%,GAP'
                     """);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                String lockData = resultSet.getString(1);
                if (lockData != null && lockData.chars().allMatch(Character::isDigit)) {
                    lockedIds.add(Long.parseLong(lockData));
                }
            }
        }
        return lockedIds;
    }

    /** 지금 {@code folder} 테이블에서 잠금이 잡힌 인덱스 이름들. */
    private Set<String> lockedFolderIndexNames() throws Exception {
        Set<String> indexNames = new LinkedHashSet<>();
        try (Connection root = rootConnection();
             PreparedStatement statement = root.prepareStatement("""
                     SELECT DISTINCT INDEX_NAME
                     FROM performance_schema.data_locks
                     WHERE OBJECT_SCHEMA = DATABASE()
                       AND OBJECT_NAME = 'folder'
                       AND LOCK_TYPE = 'RECORD'
                     """);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                indexNames.add(resultSet.getString(1));
            }
        }
        return indexNames;
    }

    /** performance_schema 는 테스트 계정 권한으로 볼 수 없어 root 로 붙는다. */
    private Connection rootConnection() throws SQLException {
        return DriverManager.getConnection(
                TestContainers.mysql().getJdbcUrl(), "root", TestContainers.mysql().getPassword());
    }

    private int maximumPoolSize() {
        return hikariDataSource().getMaximumPoolSize();
    }

    /** p6spy 가 DataSource 를 감싸고 있어서 원본을 한 겹 벗겨야 Hikari 가 나온다. */
    private HikariDataSource hikariDataSource() {
        DataSource candidate = dataSource instanceof DecoratedDataSource decorated
                ? decorated.getRealDataSource()
                : dataSource;
        if (candidate instanceof HikariDataSource hikari) {
            return hikari;
        }
        throw new IllegalStateException("HikariDataSource 를 꺼내지 못했다: " + dataSource.getClass());
    }
}
