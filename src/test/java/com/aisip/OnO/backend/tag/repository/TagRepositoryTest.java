package com.aisip.OnO.backend.tag.repository;

import com.aisip.OnO.backend.tag.TagTestSupport;
import com.aisip.OnO.backend.tag.entity.Tag;
import com.aisip.OnO.backend.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("TagRepository 쿼리")
class TagRepositoryTest extends TagTestSupport {

    private User user;
    private User other;

    @BeforeEach
    void setUpUsers() {
        user = fixtures.createUser();
        other = fixtures.createOtherUser();
    }

    @Nested
    @DisplayName("findByUserIdAndNormalizedName")
    class FindByUserIdAndNormalizedName {

        @Test
        @DisplayName("같은 정규화 이름이라도 자기 태그만 찾는다")
        void findsOnlyOwnTag() {
            Tag mine = saveTag(user.getId(), "algebra");
            saveTag(other.getId(), "algebra");

            assertThat(tagRepository.findByUserIdAndNormalizedName(user.getId(), "algebra"))
                    .get()
                    .extracting(Tag::getId)
                    .as("남의 태그가 잡히면 태그 생성이 남의 태그를 돌려준다")
                    .isEqualTo(mine.getId());
        }

        @Test
        @DisplayName("소프트 삭제된 태그는 조회되지 않는다")
        void skipsSoftDeletedTag() {
            Tag tag = saveTag(user.getId(), "algebra");
            tagRepository.delete(tag);

            assertThat(tagRepository.findByUserIdAndNormalizedName(user.getId(), "algebra")).isEmpty();
            assertThat(countRowsIncludingDeleted(user.getId()))
                    .as("소프트 삭제라 행 자체는 남아 있다")
                    .isEqualTo(1);
        }

        @Test
        @DisplayName("정규화 이름이 다르면 찾지 못한다 - 대소문자 정규화는 서비스 책임이다")
        void doesNotNormalizeInsideQuery() {
            saveTag(user.getId(), "algebra");

            assertThat(tagRepository.findByUserIdAndNormalizedName(user.getId(), "beta")).isEmpty();
        }
    }

    @Nested
    @DisplayName("findAllByUserIdOrderByNameAsc")
    class FindAllByUserIdOrderByNameAsc {

        @Test
        @DisplayName("자기 태그만 이름 오름차순으로 돌려준다")
        void returnsOwnTagsSorted() {
            saveTag(user.getId(), "cherry");
            saveTag(user.getId(), "apple");
            saveTag(user.getId(), "banana");
            saveTag(other.getId(), "aardvark");

            assertThat(tagRepository.findAllByUserIdOrderByNameAsc(user.getId()))
                    .extracting(Tag::getName)
                    .containsExactly("apple", "banana", "cherry");
        }

        @Test
        @DisplayName("소프트 삭제된 태그는 목록에서 빠진다")
        void excludesSoftDeleted() {
            Tag deleted = saveTag(user.getId(), "deleted");
            saveTag(user.getId(), "alive");
            tagRepository.delete(deleted);

            assertThat(tagRepository.findAllByUserIdOrderByNameAsc(user.getId()))
                    .extracting(Tag::getName)
                    .containsExactly("alive");
        }

        @Test
        @DisplayName("태그가 없으면 빈 목록이다")
        void returnsEmptyList() {
            assertThat(tagRepository.findAllByUserIdOrderByNameAsc(user.getId())).isEmpty();
        }
    }

    @Nested
    @DisplayName("findAllByIdInAndUserId")
    class FindAllByIdInAndUserId {

        @Test
        @DisplayName("남의 태그 id가 섞여 있으면 자기 것만 돌려준다")
        void filtersOutOtherUsersTags() {
            Tag mine = saveTag(user.getId(), "mine");
            Tag theirs = saveTag(other.getId(), "theirs");

            List<Tag> found = tagRepository.findAllByIdInAndUserId(List.of(mine.getId(), theirs.getId()), user.getId());

            assertThat(found)
                    .as("이 필터가 문제-태그 연결의 소유권 방어선이다")
                    .extracting(Tag::getId)
                    .containsExactly(mine.getId());
        }

        @Test
        @DisplayName("빈 id 목록이면 빈 결과를 돌려준다")
        void handlesEmptyIdList() {
            saveTag(user.getId(), "mine");

            assertThat(tagRepository.findAllByIdInAndUserId(List.of(), user.getId())).isEmpty();
        }

        @Test
        @DisplayName("소프트 삭제된 태그 id는 결과에서 빠진다")
        void excludesSoftDeleted() {
            Tag deleted = saveTag(user.getId(), "deleted");
            tagRepository.delete(deleted);

            assertThat(tagRepository.findAllByIdInAndUserId(List.of(deleted.getId()), user.getId())).isEmpty();
        }
    }

    @Nested
    @DisplayName("유니크 인덱스 (user_id, normalized_name)")
    class UniqueIndex {

        @Test
        @DisplayName("같은 사용자가 같은 정규화 이름을 두 번 저장하면 DB가 거절한다")
        void rejectsDuplicateNormalizedNamePerUser() {
            saveTag(user.getId(), "algebra");

            assertThatThrownBy(() -> tagRepository.saveAndFlush(Tag.from(user.getId(), "Algebra", "algebra")))
                    .as("이 제약이 있어야 태그가 중복 생성되지 않는다")
                    .isInstanceOf(DataIntegrityViolationException.class);
        }

        @Test
        @DisplayName("사용자가 다르면 같은 이름을 각자 저장할 수 있다")
        void allowsSameNameForDifferentUsers() {
            saveTag(user.getId(), "algebra");

            assertThat(tagRepository.saveAndFlush(Tag.from(other.getId(), "algebra", "algebra")).getId()).isNotNull();
        }

        @Test
        @DisplayName("소프트 삭제된 행도 이름을 계속 점유한다 - 그래서 재생성은 복구로 처리해야 한다")
        void softDeletedRowStillOccupiesTheIndex() {
            Tag tag = saveTag(user.getId(), "algebra");
            tagRepository.delete(tag);
            tagRepository.flush();

            assertThatThrownBy(() -> tagRepository.saveAndFlush(Tag.from(user.getId(), "algebra", "algebra")))
                    .isInstanceOf(DataIntegrityViolationException.class);
        }
    }

    @Nested
    @DisplayName("restoreDeletedTag")
    class RestoreDeletedTag {

        @Test
        @DisplayName("소프트 삭제된 태그를 되살리고 표기 이름을 새 값으로 바꾼다")
        void restoresSoftDeletedTag() {
            Tag tag = saveTag(user.getId(), "algebra");
            tagRepository.delete(tag);
            tagRepository.flush();

            int restored = tagRepository.restoreDeletedTag(user.getId(), "ALGEBRA", "algebra");

            assertThat(restored).isEqualTo(1);
            assertThat(tagRepository.findByUserIdAndNormalizedName(user.getId(), "algebra"))
                    .get()
                    .satisfies(found -> {
                        assertThat(found.getId()).isEqualTo(tag.getId());
                        assertThat(found.getName()).isEqualTo("ALGEBRA");
                        assertThat(found.getDeletedAt()).isNull();
                    });
        }

        @Test
        @DisplayName("살아있는 태그는 건드리지 않는다")
        void leavesActiveTagUntouched() {
            saveTag(user.getId(), "algebra");

            int restored = tagRepository.restoreDeletedTag(user.getId(), "CHANGED", "algebra");

            assertThat(restored).isZero();
            assertThat(tagRepository.findByUserIdAndNormalizedName(user.getId(), "algebra"))
                    .get()
                    .extracting(Tag::getName)
                    .isEqualTo("algebra");
        }

        @Test
        @DisplayName("다른 사용자의 삭제된 태그는 되살리지 않는다")
        void neverRestoresOtherUsersTag() {
            Tag othersTag = saveTag(other.getId(), "algebra");
            tagRepository.delete(othersTag);
            tagRepository.flush();

            assertThat(tagRepository.restoreDeletedTag(user.getId(), "algebra", "algebra")).isZero();
        }

        @Test
        @DisplayName("되살릴 태그가 없으면 0을 돌려준다")
        void returnsZeroWhenNothingToRestore() {
            assertThat(tagRepository.restoreDeletedTag(user.getId(), "algebra", "algebra")).isZero();
        }
    }
}
