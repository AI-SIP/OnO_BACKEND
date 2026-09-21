package com.aisip.OnO.backend.concurrency;

import com.aisip.OnO.backend.folder.entity.Folder;
import com.aisip.OnO.backend.folder.repository.FolderRepository;
import com.aisip.OnO.backend.folder.service.FolderService;
import com.aisip.OnO.backend.problem.dto.ProblemRegisterDto;
import com.aisip.OnO.backend.problem.support.ProblemTestSupport;
import com.aisip.OnO.backend.problem.service.ProblemService;
import com.aisip.OnO.backend.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 문제 등록과 폴더 삭제의 동시 요청.
 *
 * <p>문제 등록은 겉보기와 달리 여러 테이블을 건드린다. 문제 저장에 이어 미션 적립이
 * 같은 트랜잭션에서 일어나고, 미션 적립은 user 행을 갱신한다. 예전에는 mission_log INSERT 가
 * user 에 거는 공유 잠금과 포인트 갱신이 필요로 하는 배타 잠금이 충돌해,
 * 같은 사용자가 문제를 동시에 올리면 {@code Deadlock found when trying to get lock} 이
 * 그대로 500 으로 나갔다. 여기가 그 회귀 지점이다.
 *
 * <p>폴더 삭제는 하위 폴더와 문제를 함께 지우는 다단계 작업이라, 같은 요청이 겹치면
 * 이미 지운 것을 또 지우려 들 수 있다. 어떤 순서로 처리되든 500 없이 끝나고
 * 최종 상태가 하나로 수렴해야 한다.
 */
@DisplayName("동시성 - 문제 등록과 폴더 삭제")
class ProblemFolderConcurrencyTest extends ProblemTestSupport {

    private static final int THREAD_COUNT = 8;

    @Autowired
    private ProblemService problemService;

    @Autowired
    private FolderService folderService;

    @Autowired
    private FolderRepository folderRepository;

    private User user;
    private Folder rootFolder;

    @BeforeEach
    void setUpOwnerAndFolder() {
        user = fixtures.createUser();
        rootFolder = fixtures.createRootFolder(user.getId());
    }

    @Nested
    @DisplayName("문제 동시 등록")
    class ConcurrentRegistration {

        @Test
        @DisplayName("같은 사용자가 문제 8개를 동시에 올려도 교착 없이 전부 저장된다")
        void registersEveryProblemWithoutDeadlock() {
            Folder folder = fixtures.createFolder(user.getId(), "동시 등록", rootFolder);

            ConcurrentRunner.Outcome outcome = ConcurrentRunner.runConcurrently(THREAD_COUNT, index ->
                    problemService.registerProblem(
                            new ProblemRegisterDto(null, "메모" + index, "출처" + index, folder.getId(), null),
                            user.getId()));

            assertThat(outcome.failures())
                    .as("등록 경로에서 교착이 나면 사용자에게 500 이 나간다")
                    .isEmpty();
            assertThat(problemRepository.findAllByUserId(user.getId()))
                    .as("여덟 건이 모두 저장돼야 한다")
                    .hasSize(THREAD_COUNT);
        }

        @Test
        @DisplayName("남의 폴더에 동시에 등록을 시도해도 한 건도 들어가지 않는다")
        void rejectsEveryRegistrationIntoAnotherUsersFolder() {
            User other = fixtures.createOtherUser();
            Folder othersFolder = fixtures.createRootFolder(other.getId());

            ConcurrentRunner.Outcome outcome = ConcurrentRunner.runConcurrently(THREAD_COUNT, index ->
                    problemService.registerProblem(
                            new ProblemRegisterDto(null, "메모" + index, null, othersFolder.getId(), null),
                            user.getId()));

            assertThat(outcome.successCount())
                    .as("소유권 검증은 동시 요청에서도 예외 없이 전부 막아야 한다")
                    .isZero();
            assertThat(outcome.serverErrors()).as("소유권 위반은 403/404 계열이지 500 이 아니다").isEmpty();
            assertThat(problemRepository.findAllByUserId(other.getId())).isEmpty();
        }
    }

    @Nested
    @DisplayName("폴더 동시 삭제")
    class ConcurrentFolderDeletion {

        @Test
        @DisplayName("같은 폴더 삭제 요청이 8번 겹쳐 들어와도 500 없이 폴더와 문제가 정리된다")
        void deletesFolderOnceUnderConcurrentRequests() {
            Folder target = fixtures.createFolder(user.getId(), "삭제 대상", rootFolder);
            saveProblem(user.getId(), target);
            saveProblem(user.getId(), target);

            ConcurrentRunner.Outcome outcome = ConcurrentRunner.runConcurrently(THREAD_COUNT,
                    () -> folderService.deleteFoldersWithProblems(user.getId(), List.of(target.getId())));

            assertThat(outcome.serverErrors())
                    .as("이미 지워진 폴더를 다시 지우는 요청은 404 로 거절되어야지 500 이면 안 된다")
                    .isEmpty();
            assertThat(folderRepository.findById(target.getId()))
                    .as("폴더는 소프트 삭제되어 더 이상 조회되지 않는다")
                    .isEmpty();
            assertThat(problemRepository.findAllByUserId(user.getId()))
                    .as("폴더 안 문제도 함께 정리된다")
                    .isEmpty();
        }

        @Test
        @DisplayName("남의 폴더를 동시에 지우려 해도 한 건도 지워지지 않는다")
        void rejectsEveryDeletionOfAnotherUsersFolder() {
            User other = fixtures.createOtherUser();
            Folder othersRoot = fixtures.createRootFolder(other.getId());
            Folder othersFolder = fixtures.createFolder(other.getId(), "남의 폴더", othersRoot);
            saveProblem(other.getId(), othersFolder);

            ConcurrentRunner.Outcome outcome = ConcurrentRunner.runConcurrently(THREAD_COUNT,
                    () -> folderService.deleteFoldersWithProblems(user.getId(), List.of(othersFolder.getId())));

            assertThat(outcome.successCount()).as("전부 거절돼야 한다").isZero();
            assertThat(outcome.serverErrors()).isEmpty();
            assertThat(folderRepository.findById(othersFolder.getId())).as("남의 폴더는 그대로").isPresent();
            assertThat(problemRepository.findAllByUserId(other.getId())).hasSize(1);
        }
    }
}
