package com.aisip.OnO.backend.folder.repository;

import com.aisip.OnO.backend.folder.entity.Folder;
import com.aisip.OnO.backend.folder.support.FolderTestSupport;
import com.aisip.OnO.backend.problem.entity.Problem;
import com.aisip.OnO.backend.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("FolderRepository")
class FolderRepositoryTest extends FolderTestSupport {

    private Long userId;
    private Long otherUserId;
    private FolderTree tree;

    @BeforeEach
    void setUpFolders() {
        User user = fixtures.createUser();
        User otherUser = fixtures.createOtherUser();
        userId = user.getId();
        otherUserId = otherUser.getId();
        tree = createFolderTree(userId);
    }

    @Nested
    @DisplayName("루트 폴더 조회")
    class RootFolder {

        @Test
        @DisplayName("부모가 없는 폴더를 루트로 찾는다")
        void findByUserIdAndParentFolderIsNull() {
            Optional<Folder> rootFolder = folderRepository.findByUserIdAndParentFolderIsNull(userId);

            assertThat(rootFolder).isPresent();
            assertThat(rootFolder.get().getId()).isEqualTo(tree.root().getId());
        }

        @Test
        @DisplayName("루트 폴더 id 만 조회한다")
        void findRootFolderId() {
            assertThat(folderRepository.findRootFolderId(userId)).contains(tree.root().getId());
        }

        @Test
        @DisplayName("루트 폴더를 하위 폴더까지 함께 가져온다")
        void findRootFolderFetchesSubFolders() {
            Optional<Folder> rootFolder = folderRepository.findRootFolder(userId);

            assertThat(rootFolder).isPresent();
            assertThat(rootFolder.get().getSubFolderList())
                    .as("fetch join 이므로 트랜잭션 밖에서도 접근할 수 있다")
                    .extracting(Folder::getId)
                    .containsExactlyInAnyOrder(tree.notebookA().getId(), tree.notebookB().getId());
        }

        @Test
        @DisplayName("폴더가 없는 사용자는 루트 폴더도 없다")
        void findRootFolderForUserWithoutFolders() {
            assertThat(folderRepository.findRootFolder(otherUserId)).isEmpty();
            assertThat(folderRepository.findRootFolderId(otherUserId)).isEmpty();
        }
    }

    @Nested
    @DisplayName("폴더 상세 조회")
    class FolderDetails {

        @Test
        @DisplayName("중간 폴더는 부모와 하위 폴더를 함께 가져온다")
        void findFolderWithDetailsByFolderId() {
            Optional<Folder> folder = folderRepository.findFolderWithDetailsByFolderId(tree.notebookA().getId());

            assertThat(folder).isPresent();
            assertThat(folder.get().getParentFolder().getId()).isEqualTo(tree.root().getId());
            assertThat(folder.get().getSubFolderList())
                    .extracting(Folder::getId)
                    .containsExactlyInAnyOrder(tree.leafA1().getId(), tree.leafA2().getId());
        }

        @Test
        @DisplayName("최하위 폴더는 하위 폴더가 없다")
        void findLeafFolderWithDetails() {
            Optional<Folder> folder = folderRepository.findFolderWithDetailsByFolderId(tree.leafB1().getId());

            assertThat(folder).isPresent();
            assertThat(folder.get().getParentFolder().getId()).isEqualTo(tree.notebookB().getId());
            assertThat(folder.get().getSubFolderList()).isEmpty();
        }

        @Test
        @DisplayName("존재하지 않는 폴더는 비어 있는 결과를 준다")
        void findMissingFolderWithDetails() {
            assertThat(folderRepository.findFolderWithDetailsByFolderId(nonExistentFolderId())).isEmpty();
        }

        @Test
        @DisplayName("사용자의 모든 폴더를 id 오름차순으로 중복 없이 가져온다")
        void findAllFoldersWithDetailsByUserId() {
            fixtures.createRootFolder(otherUserId);

            List<Folder> folders = folderRepository.findAllFoldersWithDetailsByUserId(userId);

            assertThat(folders)
                    .extracting(Folder::getId)
                    .as("하위 폴더 fetch join 으로 인한 중복 행이 없어야 한다")
                    .containsExactlyElementsOf(idsOf(tree.all()));
        }

        @Test
        @DisplayName("다른 사용자의 폴더는 조회되지 않는다")
        void findAllByUserIdIsolatesUsers() {
            Folder otherUserFolder = fixtures.createRootFolder(otherUserId);

            assertThat(folderRepository.findAllByUserId(userId))
                    .extracting(Folder::getId)
                    .containsExactlyInAnyOrderElementsOf(idsOf(tree.all()))
                    .doesNotContain(otherUserFolder.getId());
            assertThat(folderRepository.findAllByUserId(otherUserId))
                    .extracting(Folder::getId)
                    .containsExactly(otherUserFolder.getId());
        }
    }

    @Nested
    @DisplayName("폴더별 문제 조회")
    class FolderProblems {

        @Test
        @DisplayName("폴더의 문제 id 를 오름차순으로 가져온다")
        void findProblemIdsByFolder() {
            List<Problem> problems = saveProblems(userId, tree.notebookA(), 3);
            saveProblems(userId, tree.notebookB(), 2);

            assertThat(folderRepository.findProblemIdsByFolder(tree.notebookA().getId()))
                    .containsExactlyElementsOf(problems.stream().map(Problem::getId).sorted().toList());
        }

        @Test
        @DisplayName("문제가 없는 폴더는 빈 리스트를 준다")
        void findProblemIdsByEmptyFolder() {
            assertThat(folderRepository.findProblemIdsByFolder(tree.leafA2().getId())).isEmpty();
        }

        @Test
        @DisplayName("여러 폴더의 문제 id 를 폴더별로 묶어 준다")
        void findProblemIdsByFolderIds() {
            List<Problem> notebookProblems = saveProblems(userId, tree.notebookA(), 2);
            List<Problem> leafProblems = saveProblems(userId, tree.leafB1(), 1);

            Map<Long, List<Long>> problemIdsByFolder = folderRepository.findProblemIdsByFolderIds(
                    List.of(tree.notebookA().getId(), tree.leafB1().getId(), tree.leafA1().getId()));

            assertThat(problemIdsByFolder)
                    .containsOnlyKeys(tree.notebookA().getId(), tree.leafB1().getId());
            assertThat(problemIdsByFolder.get(tree.notebookA().getId()))
                    .containsExactlyElementsOf(notebookProblems.stream().map(Problem::getId).sorted().toList());
            assertThat(problemIdsByFolder.get(tree.leafB1().getId()))
                    .containsExactly(leafProblems.get(0).getId());
        }

        @Test
        @DisplayName("빈 폴더 id 목록에는 빈 맵을 준다")
        void findProblemIdsByEmptyFolderIds() {
            assertThat(folderRepository.findProblemIdsByFolderIds(List.of())).isEmpty();
            assertThat(folderRepository.findProblemIdsByFolderIds(null)).isEmpty();
        }

        @Test
        @DisplayName("폴더별 문제 수를 집계한다")
        void countProblemsByFolderIds() {
            saveProblems(userId, tree.notebookA(), 3);
            saveProblems(userId, tree.leafA1(), 1);

            Map<Long, Long> counts = folderRepository.countProblemsByFolderIds(idsOf(tree.all())).stream()
                    .collect(Collectors.toMap(row -> (Long) row[0], row -> (Long) row[1]));

            assertThat(counts.get(tree.notebookA().getId())).isEqualTo(3L);
            assertThat(counts.get(tree.leafA1().getId())).isEqualTo(1L);
            assertThat(counts)
                    .as("문제가 없는 폴더는 집계 결과에 아예 없다")
                    .doesNotContainKey(tree.leafA2().getId());
        }
    }

    @Nested
    @DisplayName("커서 기반 조회")
    class CursorQueries {

        @Test
        @DisplayName("하위 폴더를 커서 이후부터 size + 1 개까지 가져온다")
        void findSubFoldersWithCursor() {
            List<Folder> firstPage = folderRepository.findSubFoldersWithCursor(tree.notebookA().getId(), null, 1);

            assertThat(firstPage)
                    .as("hasNext 판단을 위해 size + 1 개를 조회한다")
                    .extracting(Folder::getId)
                    .containsExactly(tree.leafA1().getId(), tree.leafA2().getId());

            List<Folder> secondPage =
                    folderRepository.findSubFoldersWithCursor(tree.notebookA().getId(), tree.leafA1().getId(), 1);

            assertThat(secondPage)
                    .extracting(Folder::getId)
                    .containsExactly(tree.leafA2().getId());
        }

        @Test
        @DisplayName("사용자의 모든 폴더를 커서 순서대로 가져온다")
        void findAllUserFolderThumbnailsWithCursor() {
            fixtures.createRootFolder(otherUserId);

            List<Folder> page = folderRepository.findAllUserFolderThumbnailsWithCursor(userId, null, 3);

            assertThat(page).hasSize(4);
            assertThat(page)
                    .extracting(Folder::getId)
                    .as("본인 폴더만, id 오름차순으로")
                    .containsExactlyElementsOf(idsOf(tree.all()).subList(0, 4));
        }

        @Test
        @DisplayName("커서가 마지막 폴더면 빈 결과를 준다")
        void findAllUserFolderThumbnailsWithCursorAtEnd() {
            Long lastFolderId = idsOf(tree.all()).get(tree.all().size() - 1);

            assertThat(folderRepository.findAllUserFolderThumbnailsWithCursor(userId, lastFolderId, 20)).isEmpty();
        }
    }

    @Nested
    @DisplayName("소프트 삭제")
    class SoftDelete {

        @Test
        @DisplayName("벌크 소프트 삭제된 폴더는 이후 조회에 잡히지 않는다")
        void softDeleteAllByIdIn() {
            // flushAutomatically 를 쓰므로 트랜잭션 안에서만 호출할 수 있다.
            inTransaction(() ->
                    folderRepository.softDeleteAllByIdIn(List.of(tree.leafA1().getId(), tree.leafA2().getId())));

            assertThat(folderRepository.findAllByUserId(userId))
                    .extracting(Folder::getId)
                    .doesNotContain(tree.leafA1().getId(), tree.leafA2().getId())
                    .hasSize(4);
            assertThat(folderRepository.findById(tree.leafA1().getId())).isEmpty();
            assertThat(folderRepository.findFolderWithDetailsByFolderId(tree.notebookA().getId()).orElseThrow()
                    .getSubFolderList())
                    .as("부모의 하위 폴더 목록에서도 빠진다")
                    .isEmpty();
        }

        @Test
        @DisplayName("영속성 컨텍스트에 폴더가 올라와 있어도 소프트 삭제가 취소되지 않는다")
        void softDeleteIsNotCancelledByPersistenceContext() {
            inTransaction(() -> {
                // 하위 폴더 컬렉션을 초기화해 두면 cascade persist 가 삭제를 되돌릴 여지가 생긴다.
                Folder notebookA = folderRepository.findById(tree.notebookA().getId()).orElseThrow();
                assertThat(notebookA.getSubFolderList()).hasSize(2);

                folderRepository.softDeleteAllByIdIn(
                        List.of(tree.notebookA().getId(), tree.leafA1().getId(), tree.leafA2().getId()));
            });

            assertThat(folderRepository.findAllByUserId(userId))
                    .extracting(Folder::getId)
                    .containsExactlyInAnyOrder(tree.root().getId(), tree.notebookB().getId(), tree.leafB1().getId());
        }
    }
}
