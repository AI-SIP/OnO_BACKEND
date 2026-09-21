package com.aisip.OnO.backend.folder.service;

import com.aisip.OnO.backend.common.exception.ApplicationException;
import com.aisip.OnO.backend.common.response.CursorPageResponse;
import com.aisip.OnO.backend.folder.dto.FolderRegisterDto;
import com.aisip.OnO.backend.folder.dto.FolderResponseDto;
import com.aisip.OnO.backend.folder.dto.FolderThumbnailResponseDto;
import com.aisip.OnO.backend.folder.entity.Folder;
import com.aisip.OnO.backend.folder.exception.FolderErrorCase;
import com.aisip.OnO.backend.folder.support.FolderTestSupport;
import com.aisip.OnO.backend.problem.entity.Problem;
import com.aisip.OnO.backend.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("FolderService")
class FolderServiceTest extends FolderTestSupport {

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
    @DisplayName("폴더 조회")
    class FindFolder {

        @Test
        @DisplayName("루트 폴더를 조회하면 하위 폴더와 문제 목록이 함께 내려온다")
        void findRootFolderReturnsSubFoldersAndProblems() {
            List<Problem> rootProblems = saveProblems(userId, tree.root(), 2);
            saveProblems(userId, tree.notebookA(), 3);

            FolderResponseDto response = folderService.findRootFolder(userId);

            assertThat(response.folderId()).as("루트 폴더 id").isEqualTo(tree.root().getId());
            assertThat(response.parentFolder()).as("루트는 부모가 없다").isNull();
            assertThat(response.subFolderList())
                    .as("루트의 하위 폴더 (조회 순서는 보장되지 않는다)")
                    .extracting(FolderThumbnailResponseDto::folderId)
                    .containsExactlyInAnyOrder(tree.notebookA().getId(), tree.notebookB().getId());
            assertThat(response.subFolderList())
                    .filteredOn(dto -> dto.folderId().equals(tree.notebookA().getId()))
                    .singleElement()
                    .extracting(FolderThumbnailResponseDto::problemCount)
                    .as("하위 폴더 썸네일의 문제 수")
                    .isEqualTo(3L);
            assertThat(response.problemIdList())
                    .as("루트 폴더에 직접 담긴 문제")
                    .containsExactlyInAnyOrderElementsOf(rootProblems.stream().map(Problem::getId).toList());
        }

        @Test
        @DisplayName("루트 폴더가 없는 사용자는 FOLDER_NOT_FOUND 예외를 받는다")
        void findRootFolderWithoutRootFolderThrows() {
            assertThatThrownBy(() -> folderService.findRootFolder(otherUserId))
                    .isInstanceOf(ApplicationException.class)
                    .hasMessageContaining(FolderErrorCase.FOLDER_NOT_FOUND.getMessage());
        }

        @Test
        @DisplayName("중간 폴더를 조회하면 부모 썸네일과 하위 폴더가 함께 내려온다")
        void findFolderReturnsParentAndSubFolders() {
            saveProblems(userId, tree.root(), 2);
            List<Problem> notebookProblems = saveProblems(userId, tree.notebookA(), 1);

            FolderResponseDto response = folderService.findFolder(tree.notebookA().getId(), userId);

            assertThat(response.folderId()).isEqualTo(tree.notebookA().getId());
            assertThat(response.parentFolder().folderId()).as("부모 폴더 id").isEqualTo(tree.root().getId());
            assertThat(response.parentFolder().problemCount()).as("부모 폴더의 문제 수").isEqualTo(2L);
            assertThat(response.subFolderList())
                    .extracting(FolderThumbnailResponseDto::folderId)
                    .containsExactlyInAnyOrder(tree.leafA1().getId(), tree.leafA2().getId());
            assertThat(response.problemIdList())
                    .containsExactly(notebookProblems.get(0).getId());
        }

        @Test
        @DisplayName("최하위 폴더는 하위 폴더 목록이 비어 있다")
        void findLeafFolderHasNoSubFolders() {
            FolderResponseDto response = folderService.findFolder(tree.leafA1().getId(), userId);

            assertThat(response.subFolderList()).isEmpty();
            assertThat(response.problemIdList()).isEmpty();
        }

        @Test
        @DisplayName("존재하지 않는 폴더를 조회하면 FOLDER_NOT_FOUND 예외가 발생한다")
        void findFolderNotFound() {
            Long missingFolderId = nonExistentFolderId();

            assertThatThrownBy(() -> folderService.findFolder(missingFolderId, userId))
                    .isInstanceOf(ApplicationException.class)
                    .hasMessageContaining(FolderErrorCase.FOLDER_NOT_FOUND.getMessage());
        }

        @Test
        @DisplayName("폴더 엔티티 조회도 소유자 검증을 거친다")
        void findFolderEntityValidatesOwner() {
            Folder folder = folderService.findFolderEntity(tree.notebookA().getId(), userId);

            assertThat(folder.getId()).isEqualTo(tree.notebookA().getId());
            assertThat(folder.getName()).isEqualTo("공책 A");
        }
    }

    @Nested
    @DisplayName("폴더 목록 조회")
    class FindFolderList {

        @Test
        @DisplayName("썸네일 목록은 본인 폴더만 문제 수와 함께 돌려준다")
        void findAllUserFolderThumbnails() {
            saveProblems(userId, tree.leafA1(), 2);
            Folder otherUserFolder = fixtures.createRootFolder(otherUserId);
            saveProblems(otherUserId, otherUserFolder, 5);

            List<FolderThumbnailResponseDto> thumbnails = folderService.findAllUserFolderThumbnails(userId);

            assertThat(thumbnails)
                    .as("다른 사용자의 폴더는 섞이지 않는다")
                    .extracting(FolderThumbnailResponseDto::folderId)
                    .containsExactlyInAnyOrderElementsOf(idsOf(tree.all()));
            assertThat(thumbnails)
                    .filteredOn(dto -> dto.folderId().equals(tree.leafA1().getId()))
                    .singleElement()
                    .extracting(FolderThumbnailResponseDto::problemCount)
                    .isEqualTo(2L);
        }

        @Test
        @DisplayName("폴더가 하나도 없는 사용자의 썸네일 목록은 빈 리스트다")
        void findAllUserFolderThumbnailsForUserWithoutFolders() {
            assertThat(folderService.findAllUserFolderThumbnails(otherUserId)).isEmpty();
        }

        @Test
        @DisplayName("전체 폴더 상세 목록은 id 오름차순이며 부모·하위 폴더 정보를 담는다")
        void findAllUserFolders() {
            saveProblems(userId, tree.notebookB(), 1);

            List<FolderResponseDto> folders = folderService.findAllUserFolders(userId);

            assertThat(folders)
                    .extracting(FolderResponseDto::folderId)
                    .as("id 오름차순 정렬")
                    .containsExactlyElementsOf(idsOf(tree.all()));

            FolderResponseDto root = folders.get(0);
            assertThat(root.parentFolder()).isNull();
            assertThat(root.subFolderList())
                    .extracting(FolderThumbnailResponseDto::folderId)
                    .containsExactlyInAnyOrder(tree.notebookA().getId(), tree.notebookB().getId());
            assertThat(root.subFolderList())
                    .filteredOn(dto -> dto.folderId().equals(tree.notebookB().getId()))
                    .singleElement()
                    .extracting(FolderThumbnailResponseDto::problemCount)
                    .isEqualTo(1L);

            FolderResponseDto leaf = folders.get(folders.size() - 1);
            assertThat(leaf.subFolderList()).isEmpty();
            assertThat(leaf.parentFolder().folderId()).isEqualTo(tree.notebookB().getId());
        }

        @Test
        @DisplayName("폴더가 없는 사용자의 상세 목록은 빈 리스트다")
        void findAllUserFoldersForUserWithoutFolders() {
            assertThat(folderService.findAllUserFolders(otherUserId)).isEmpty();
        }
    }

    @Nested
    @DisplayName("커서 페이징")
    class CursorPaging {

        @Test
        @DisplayName("하위 폴더 커서 조회는 size 만큼 끊어 주고 다음 커서를 알려준다")
        void findSubFoldersWithCursor() {
            CursorPageResponse<FolderThumbnailResponseDto> firstPage =
                    folderService.findSubFoldersWithCursor(tree.notebookA().getId(), userId, null, 1);

            assertThat(firstPage.content()).hasSize(1);
            assertThat(firstPage.hasNext()).as("leafA2 가 남아 있다").isTrue();
            assertThat(firstPage.nextCursor()).isEqualTo(firstPage.content().get(0).folderId());

            CursorPageResponse<FolderThumbnailResponseDto> secondPage =
                    folderService.findSubFoldersWithCursor(tree.notebookA().getId(), userId, firstPage.nextCursor(), 1);

            assertThat(secondPage.content())
                    .extracting(FolderThumbnailResponseDto::folderId)
                    .containsExactly(tree.leafA2().getId());
            assertThat(secondPage.hasNext()).isFalse();
            assertThat(secondPage.nextCursor()).isNull();
        }

        @Test
        @DisplayName("하위 폴더가 없으면 빈 페이지를 돌려준다")
        void findSubFoldersWithCursorForLeafFolder() {
            CursorPageResponse<FolderThumbnailResponseDto> page =
                    folderService.findSubFoldersWithCursor(tree.leafA1().getId(), userId, null, 20);

            assertThat(page.content()).isEmpty();
            assertThat(page.hasNext()).isFalse();
            assertThat(page.nextCursor()).isNull();
        }

        @Test
        @DisplayName("다른 사용자의 폴더로 하위 폴더 커서 조회를 하면 예외가 발생한다")
        void findSubFoldersWithCursorForOtherUserFolder() {
            assertThatThrownBy(() -> folderService.findSubFoldersWithCursor(tree.notebookA().getId(), otherUserId, null, 20))
                    .isInstanceOf(ApplicationException.class)
                    .hasMessageContaining(FolderErrorCase.FOLDER_USER_UNMATCHED.getMessage());
        }

        @Test
        @DisplayName("전체 폴더 썸네일 커서 조회는 본인 폴더만 페이지로 끊어 준다")
        void findAllUserFolderThumbnailsWithCursor() {
            fixtures.createRootFolder(otherUserId);

            CursorPageResponse<FolderThumbnailResponseDto> firstPage =
                    folderService.findAllUserFolderThumbnailsWithCursor(userId, null, 4);

            assertThat(firstPage.content()).hasSize(4);
            assertThat(firstPage.hasNext()).isTrue();

            CursorPageResponse<FolderThumbnailResponseDto> secondPage =
                    folderService.findAllUserFolderThumbnailsWithCursor(userId, firstPage.nextCursor(), 4);

            assertThat(secondPage.content()).hasSize(2);
            assertThat(secondPage.hasNext()).isFalse();
            assertThat(secondPage.content())
                    .extracting(FolderThumbnailResponseDto::folderId)
                    .as("다른 사용자의 폴더는 페이지에 들어오지 않는다")
                    .isSubsetOf(idsOf(tree.all()));
        }
    }

    @Nested
    @DisplayName("기본 폴더 초기화")
    class InitializeDefaultFolders {

        @Test
        @DisplayName("루트가 없으면 책장과 공책을 만든다")
        void createsRootAndDefaultSubFolder() {
            folderService.initializeDefaultFoldersIfAbsent(otherUserId);

            Optional<Folder> root = folderRepository.findRootFolder(otherUserId);
            assertThat(root).isPresent();
            assertThat(root.get().getName()).isEqualTo("책장");
            assertThat(root.get().getSubFolderList())
                    .singleElement()
                    .extracting(Folder::getName)
                    .isEqualTo("공책");
        }

        @Test
        @DisplayName("여러 번 호출해도 기본 폴더를 중복 생성하지 않는다")
        void isIdempotent() {
            folderService.initializeDefaultFoldersIfAbsent(otherUserId);
            folderService.initializeDefaultFoldersIfAbsent(otherUserId);
            folderService.initializeDefaultFoldersIfAbsent(otherUserId);

            assertThat(folderRepository.findAllByUserId(otherUserId))
                    .as("책장 + 공책 두 개만 있어야 한다")
                    .hasSize(2);
        }
    }

    @Nested
    @DisplayName("폴더 생성")
    class CreateFolder {

        @Test
        @DisplayName("부모 폴더 아래에 폴더를 만든다")
        void createFolderUnderParent() {
            Long folderId = folderService.createFolder(
                    new FolderRegisterDto("새 공책", null, tree.root().getId()), userId);

            Folder created = folderRepository.findById(folderId).orElseThrow();
            assertThat(created.getName()).isEqualTo("새 공책");
            assertThat(created.getUserId()).isEqualTo(userId);
            assertThat(folderRepository.findFolderWithDetailsByFolderId(folderId).orElseThrow().getParentFolder().getId())
                    .isEqualTo(tree.root().getId());
        }

        @Test
        @DisplayName("깊게 중첩된 폴더도 만들 수 있다")
        void createDeeplyNestedFolder() {
            Folder parent = tree.leafA1();
            for (int depth = 0; depth < 5; depth++) {
                Long folderId = folderService.createFolder(
                        new FolderRegisterDto("깊이 " + depth, null, parent.getId()), userId);
                parent = folderRepository.findById(folderId).orElseThrow();
            }

            assertThat(folderRepository.findAllByUserId(userId))
                    .as("기존 6개 + 새로 만든 5개")
                    .hasSize(11);
        }

        @Test
        @DisplayName("존재하지 않는 부모 폴더를 지정하면 FOLDER_NOT_FOUND 예외가 발생한다")
        void createFolderWithMissingParent() {
            FolderRegisterDto registerDto = new FolderRegisterDto("새 공책", null, nonExistentFolderId());

            assertThatThrownBy(() -> folderService.createFolder(registerDto, userId))
                    .isInstanceOf(ApplicationException.class)
                    .hasMessageContaining(FolderErrorCase.FOLDER_NOT_FOUND.getMessage());
        }

        @Test
        @DisplayName("부모 폴더 id 가 없으면 FOLDER_NOT_FOUND 예외가 발생한다")
        void createFolderWithNullParent() {
            FolderRegisterDto registerDto = new FolderRegisterDto("새 공책", null, null);

            assertThatThrownBy(() -> folderService.createFolder(registerDto, userId))
                    .isInstanceOf(ApplicationException.class)
                    .hasMessageContaining(FolderErrorCase.FOLDER_NOT_FOUND.getMessage());
        }

        @Test
        @DisplayName("다른 사용자의 폴더를 부모로 지정하면 FOLDER_USER_UNMATCHED 예외가 발생한다")
        void createFolderUnderOtherUserFolder() {
            Folder otherUserFolder = fixtures.createRootFolder(otherUserId);
            FolderRegisterDto registerDto = new FolderRegisterDto("남의 폴더 아래", null, otherUserFolder.getId());

            assertThatThrownBy(() -> folderService.createFolder(registerDto, userId))
                    .isInstanceOf(ApplicationException.class)
                    .hasMessageContaining(FolderErrorCase.FOLDER_USER_UNMATCHED.getMessage());
        }
    }

    @Nested
    @DisplayName("폴더 수정")
    class UpdateFolder {

        @Test
        @DisplayName("폴더 이름을 바꾼다")
        void updateFolderName() {
            folderService.updateFolder(
                    new FolderRegisterDto("바뀐 이름", tree.notebookA().getId(), null), userId);

            assertThat(folderRepository.findById(tree.notebookA().getId()).orElseThrow().getName())
                    .isEqualTo("바뀐 이름");
        }

        @Test
        @DisplayName("이름이 null 이면 기존 이름을 유지한다")
        void updateFolderWithNullNameKeepsName() {
            folderService.updateFolder(
                    new FolderRegisterDto(null, tree.leafA1().getId(), tree.notebookB().getId()), userId);

            Folder updated = folderRepository.findFolderWithDetailsByFolderId(tree.leafA1().getId()).orElseThrow();
            assertThat(updated.getName()).isEqualTo("단원 A-1");
            assertThat(updated.getParentFolder().getId()).isEqualTo(tree.notebookB().getId());
        }

        @Test
        @DisplayName("루트 폴더는 수정할 수 없다")
        void rootFolderCannotBeUpdated() {
            FolderRegisterDto registerDto = new FolderRegisterDto("루트 이름 변경", tree.root().getId(), null);

            assertThatThrownBy(() -> folderService.updateFolder(registerDto, userId))
                    .isInstanceOf(ApplicationException.class)
                    .hasMessageContaining(FolderErrorCase.ROOT_FOLDER_CANNOT_UPDATE.getMessage());
        }

        @Test
        @DisplayName("부모 폴더를 옮기면 이전 부모에서 빠지고 새 부모에 붙는다")
        void moveFolderToAnotherParent() {
            folderService.updateFolder(
                    new FolderRegisterDto(null, tree.notebookA().getId(), tree.notebookB().getId()), userId);

            Folder moved = folderRepository.findFolderWithDetailsByFolderId(tree.notebookA().getId()).orElseThrow();
            assertThat(moved.getParentFolder().getId()).isEqualTo(tree.notebookB().getId());

            Folder oldParent = folderRepository.findFolderWithDetailsByFolderId(tree.root().getId()).orElseThrow();
            assertThat(oldParent.getSubFolderList())
                    .extracting(Folder::getId)
                    .as("루트에는 공책 B 만 남는다")
                    .containsExactly(tree.notebookB().getId());
        }

        @Test
        @DisplayName("존재하지 않는 폴더를 수정하면 FOLDER_NOT_FOUND 예외가 발생한다")
        void updateMissingFolder() {
            FolderRegisterDto registerDto = new FolderRegisterDto("이름", nonExistentFolderId(), null);

            assertThatThrownBy(() -> folderService.updateFolder(registerDto, userId))
                    .isInstanceOf(ApplicationException.class)
                    .hasMessageContaining(FolderErrorCase.FOLDER_NOT_FOUND.getMessage());
        }

        @Test
        @DisplayName("존재하지 않는 폴더로 옮기면 FOLDER_NOT_FOUND 예외가 발생한다")
        void moveFolderToMissingParent() {
            FolderRegisterDto registerDto = new FolderRegisterDto(null, tree.leafA1().getId(), nonExistentFolderId());

            assertThatThrownBy(() -> folderService.updateFolder(registerDto, userId))
                    .isInstanceOf(ApplicationException.class)
                    .hasMessageContaining(FolderErrorCase.FOLDER_NOT_FOUND.getMessage());
        }

        @Test
        @DisplayName("자기 자신을 부모로 지정하면 INVALID_PARENT_FOLDER 예외가 발생한다")
        void folderCannotBeItsOwnParent() {
            Long folderId = tree.notebookA().getId();
            FolderRegisterDto registerDto = new FolderRegisterDto(null, folderId, folderId);

            assertThatThrownBy(() -> folderService.updateFolder(registerDto, userId))
                    .isInstanceOf(ApplicationException.class)
                    .hasMessageContaining(FolderErrorCase.INVALID_PARENT_FOLDER.getMessage());

            assertThat(folderRepository.findFolderWithDetailsByFolderId(folderId).orElseThrow().getParentFolder().getId())
                    .as("부모는 그대로 유지된다")
                    .isEqualTo(tree.root().getId());
        }

        @Test
        @DisplayName("자기 하위 폴더를 부모로 지정하면 순환이 생기므로 예외가 발생한다")
        void folderCannotBeMovedUnderItsOwnDescendant() {
            FolderRegisterDto registerDto =
                    new FolderRegisterDto(null, tree.notebookA().getId(), tree.leafA1().getId());

            assertThatThrownBy(() -> folderService.updateFolder(registerDto, userId))
                    .isInstanceOf(ApplicationException.class)
                    .hasMessageContaining(FolderErrorCase.INVALID_PARENT_FOLDER.getMessage());
        }

        @Test
        @DisplayName("다른 사용자의 폴더는 수정할 수 없다")
        void cannotUpdateOtherUserFolder() {
            FolderRegisterDto registerDto = new FolderRegisterDto("남의 폴더", tree.notebookA().getId(), null);

            assertThatThrownBy(() -> folderService.updateFolder(registerDto, otherUserId))
                    .isInstanceOf(ApplicationException.class)
                    .hasMessageContaining(FolderErrorCase.FOLDER_USER_UNMATCHED.getMessage());
        }

        @Test
        @DisplayName("다른 사용자의 폴더 아래로는 옮길 수 없다")
        void cannotMoveFolderUnderOtherUserFolder() {
            Folder otherUserFolder = fixtures.createRootFolder(otherUserId);
            FolderRegisterDto registerDto =
                    new FolderRegisterDto(null, tree.leafA1().getId(), otherUserFolder.getId());

            assertThatThrownBy(() -> folderService.updateFolder(registerDto, userId))
                    .isInstanceOf(ApplicationException.class)
                    .hasMessageContaining(FolderErrorCase.FOLDER_USER_UNMATCHED.getMessage());
        }
    }

    @Nested
    @DisplayName("폴더 삭제")
    class DeleteFolder {

        @Test
        @DisplayName("폴더를 지우면 하위 폴더와 그 안의 문제까지 함께 사라진다")
        void deleteFolderRemovesSubTreeAndProblems() {
            saveProblems(userId, tree.notebookA(), 2);
            saveProblems(userId, tree.leafA1(), 3);
            List<Problem> keptProblems = saveProblems(userId, tree.notebookB(), 1);

            folderService.deleteFoldersWithProblems(userId, List.of(tree.notebookA().getId()));

            assertThat(folderRepository.findAllByUserId(userId))
                    .extracting(Folder::getId)
                    .as("공책 A 서브트리만 사라진다")
                    .containsExactlyInAnyOrder(tree.root().getId(), tree.notebookB().getId(), tree.leafB1().getId());
            assertThat(problemRepository.findAllByUserId(userId))
                    .extracting(Problem::getId)
                    .as("남은 문제는 공책 B 의 문제뿐")
                    .containsExactly(keptProblems.get(0).getId());
        }

        @Test
        @DisplayName("최상위 폴더를 모두 지우면 루트만 남는다")
        void deleteAllTopLevelFolders() {
            saveProblems(userId, tree.leafA1(), 2);
            saveProblems(userId, tree.leafB1(), 2);

            folderService.deleteFoldersWithProblems(
                    userId, List.of(tree.notebookA().getId(), tree.notebookB().getId()));

            assertThat(folderRepository.findAllByUserId(userId))
                    .extracting(Folder::getId)
                    .containsExactly(tree.root().getId());
        }

        @Test
        @DisplayName("루트 폴더는 삭제할 수 없고 아무 폴더도 지워지지 않는다")
        void rootFolderCannotBeDeleted() {
            List<Long> deleteIds = List.of(tree.root().getId());

            assertThatThrownBy(() -> folderService.deleteFoldersWithProblems(userId, deleteIds))
                    .isInstanceOf(ApplicationException.class)
                    .hasMessageContaining(FolderErrorCase.ROOT_FOLDER_CANNOT_REMOVE.getMessage());

            assertThat(folderRepository.findAllByUserId(userId)).hasSize(6);
        }

        @Test
        @DisplayName("존재하지 않는 폴더를 지우면 FOLDER_NOT_FOUND 예외가 발생한다")
        void deleteMissingFolder() {
            List<Long> deleteIds = List.of(nonExistentFolderId());

            assertThatThrownBy(() -> folderService.deleteFoldersWithProblems(userId, deleteIds))
                    .isInstanceOf(ApplicationException.class)
                    .hasMessageContaining(FolderErrorCase.FOLDER_NOT_FOUND.getMessage());
        }

        @Test
        @DisplayName("다른 사용자의 폴더는 삭제할 수 없다")
        void cannotDeleteOtherUserFolder() {
            List<Long> deleteIds = List.of(tree.notebookA().getId());

            assertThatThrownBy(() -> folderService.deleteFoldersWithProblems(otherUserId, deleteIds))
                    .isInstanceOf(ApplicationException.class)
                    .hasMessageContaining(FolderErrorCase.FOLDER_USER_UNMATCHED.getMessage());

            assertThat(folderRepository.findAllByUserId(userId)).hasSize(6);
        }

        @Test
        @DisplayName("빈 삭제 목록은 아무것도 지우지 않는다")
        void deleteWithEmptyListDoesNothing() {
            folderService.deleteFoldersWithProblems(userId, List.of());

            assertThat(folderRepository.findAllByUserId(userId)).hasSize(6);
        }

        @Test
        @DisplayName("전체 삭제는 본인 폴더와 문제만 지우고 다른 사용자 데이터는 남긴다")
        void deleteAllUserFoldersWithProblems() {
            saveProblems(userId, tree.notebookA(), 2);
            Folder otherUserFolder = fixtures.createRootFolder(otherUserId);
            saveProblems(otherUserId, otherUserFolder, 2);

            folderService.deleteAllUserFoldersWithProblems(userId);

            assertThat(folderRepository.findAllByUserId(userId)).isEmpty();
            assertThat(problemRepository.findAllByUserId(userId)).isEmpty();
            assertThat(folderRepository.findAllByUserId(otherUserId))
                    .as("다른 사용자 폴더는 그대로")
                    .hasSize(1);
            assertThat(problemRepository.findAllByUserId(otherUserId))
                    .as("다른 사용자 문제도 그대로")
                    .hasSize(2);
        }
    }
}
