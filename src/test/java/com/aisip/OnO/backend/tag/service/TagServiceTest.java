package com.aisip.OnO.backend.tag.service;

import com.aisip.OnO.backend.common.exception.ApplicationException;
import com.aisip.OnO.backend.problem.entity.Problem;
import com.aisip.OnO.backend.tag.TagTestSupport;
import com.aisip.OnO.backend.tag.dto.TagCreateRequestDto;
import com.aisip.OnO.backend.tag.dto.TagDeleteRequestDto;
import com.aisip.OnO.backend.tag.dto.TagRecommendRequestDto;
import com.aisip.OnO.backend.tag.dto.TagResponseDto;
import com.aisip.OnO.backend.tag.entity.ProblemTagMapping;
import com.aisip.OnO.backend.tag.entity.Tag;
import com.aisip.OnO.backend.tag.exception.TagErrorCase;
import com.aisip.OnO.backend.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("TagService")
class TagServiceTest extends TagTestSupport {

    @Autowired
    private TagService tagService;

    private User user;
    private User other;

    @BeforeEach
    void setUpUsers() {
        user = fixtures.createUser();
        other = fixtures.createOtherUser();
    }

    private void assertTagError(TagErrorCase expected, Runnable action) {
        assertThatThrownBy(action::run)
                .isInstanceOf(ApplicationException.class)
                .extracting(thrown -> ((ApplicationException) thrown).getErrorCase())
                .as("에러 코드가 달라지면 앱은 다른 화면을 띄운다")
                .isEqualTo(expected);
    }

    @Nested
    @DisplayName("태그 생성")
    class CreateTag {

        @Test
        @DisplayName("새 태그를 만들면 id와 이름이 담긴 응답을 돌려주고 DB에 저장된다")
        void createsNewTag() {
            TagResponseDto response = tagService.createTag(user.getId(), new TagCreateRequestDto("발상 부족"));

            assertThat(response.tagId()).isNotNull();
            assertThat(response.name()).isEqualTo("발상 부족");
            assertThat(tagRepository.findById(response.tagId()))
                    .as("응답만 만들고 저장이 안 되면 다음 조회에서 사라진다")
                    .isPresent()
                    .get()
                    .satisfies(tag -> {
                        assertThat(tag.getUserId()).isEqualTo(user.getId());
                        assertThat(tag.getNormalizedName()).isEqualTo("발상 부족");
                    });
        }

        @Test
        @DisplayName("앞뒤 공백은 잘라내고 저장한다")
        void trimsSurroundingWhitespace() {
            TagResponseDto response = tagService.createTag(user.getId(), new TagCreateRequestDto("   계산 실수   "));

            assertThat(response.name()).isEqualTo("계산 실수");
        }

        @Test
        @DisplayName("앞에 붙은 # 하나는 해시태그 표기로 보고 제거한다")
        void stripsLeadingHash() {
            assertThat(tagService.createTag(user.getId(), new TagCreateRequestDto("#계산실수")).name())
                    .isEqualTo("계산실수");
            assertThat(tagService.createTag(other.getId(), new TagCreateRequestDto("#  띄어쓰기  ")).name())
                    .as("# 을 떼고 남은 공백도 잘라낸다")
                    .isEqualTo("띄어쓰기");
        }

        @Test
        @DisplayName("# 두 개는 하나만 제거해 이름에 #이 남는다")
        void stripsOnlyTheFirstHash() {
            assertThat(tagService.createTag(user.getId(), new TagCreateRequestDto("##중요")).name())
                    .isEqualTo("#중요");
        }

        @Test
        @DisplayName("1자 태그도 만들 수 있다")
        void allowsSingleCharacterName() {
            assertThat(tagService.createTag(user.getId(), new TagCreateRequestDto("수")).name())
                    .isEqualTo("수");
        }

        @Test
        @DisplayName("30자 태그는 허용하고 31자는 TAG_NAME_TOO_LONG 으로 거절한다")
        void enforcesMaxLengthBoundary() {
            String thirty = "가".repeat(30);
            String thirtyOne = "가".repeat(31);

            assertThat(tagService.createTag(user.getId(), new TagCreateRequestDto(thirty)).name())
                    .as("컬럼이 varchar(30)이라 30자는 반드시 통과해야 한다")
                    .hasSize(30);

            assertTagError(TagErrorCase.TAG_NAME_TOO_LONG,
                    () -> tagService.createTag(user.getId(), new TagCreateRequestDto(thirtyOne)));
        }

        @Test
        @DisplayName("# 을 포함해 31자여도 # 제거 후 30자면 통과한다")
        void measuresLengthAfterStrippingHash() {
            assertThatCode(() -> tagService.createTag(user.getId(), new TagCreateRequestDto("#" + "나".repeat(30))))
                    .doesNotThrowAnyException();

            assertThat(tagNames(user.getId())).containsExactly("나".repeat(30));
        }

        @Test
        @DisplayName("빈 문자열, 공백뿐인 이름, # 하나뿐인 이름은 TAG_NAME_EMPTY 로 거절한다")
        void rejectsBlankNames() {
            assertTagError(TagErrorCase.TAG_NAME_EMPTY,
                    () -> tagService.createTag(user.getId(), new TagCreateRequestDto("")));
            assertTagError(TagErrorCase.TAG_NAME_EMPTY,
                    () -> tagService.createTag(user.getId(), new TagCreateRequestDto("   ")));
            assertTagError(TagErrorCase.TAG_NAME_EMPTY,
                    () -> tagService.createTag(user.getId(), new TagCreateRequestDto("#")));
            assertThat(tagNames(user.getId())).isEmpty();
        }

        @Test
        @DisplayName("이름이 null 이거나 요청 자체가 null 이어도 500이 아니라 TAG_NAME_EMPTY 다")
        void rejectsNullInputWithClientError() {
            assertTagError(TagErrorCase.TAG_NAME_EMPTY,
                    () -> tagService.createTag(user.getId(), new TagCreateRequestDto(null)));
            assertTagError(TagErrorCase.TAG_NAME_EMPTY,
                    () -> tagService.createTag(user.getId(), null));
        }

        @Test
        @DisplayName("한글과 이모지 태그가 잘리지 않고 원문 그대로 저장된다")
        void storesUnicodeNamesIntact() {
            String emojiName = "🔥실수노트🔥";

            Long tagId = tagService.createTag(user.getId(), new TagCreateRequestDto(emojiName)).tagId();

            assertThat(tagRepository.findById(tagId).orElseThrow().getName())
                    .as("utf8mb4 가 아니면 여기서 깨지거나 저장이 실패한다")
                    .isEqualTo(emojiName);
        }

        @Test
        @DisplayName("같은 이름을 다시 만들면 새로 만들지 않고 기존 태그를 돌려준다")
        void returnsExistingTagForDuplicateName() {
            Long first = tagService.createTag(user.getId(), new TagCreateRequestDto("발상 부족")).tagId();
            Long second = tagService.createTag(user.getId(), new TagCreateRequestDto("  발상 부족 ")).tagId();

            assertThat(second).isEqualTo(first);
            assertThat(tagNames(user.getId())).hasSize(1);
        }

        @Test
        @DisplayName("대소문자만 다른 이름은 같은 태그로 보고 처음 등록한 표기를 유지한다")
        void keepsFirstDisplayNameForCaseVariants() {
            Long first = tagService.createTag(user.getId(), new TagCreateRequestDto("Algebra")).tagId();
            TagResponseDto second = tagService.createTag(user.getId(), new TagCreateRequestDto("ALGEBRA"));

            assertThat(second.tagId()).isEqualTo(first);
            assertThat(second.name()).isEqualTo("Algebra");
        }

        @Test
        @DisplayName("다른 사용자와 같은 이름의 태그는 서로 다른 태그로 만들어진다")
        void isolatesTagNamespacePerUser() {
            Long mine = tagService.createTag(user.getId(), new TagCreateRequestDto("발상 부족")).tagId();
            Long theirs = tagService.createTag(other.getId(), new TagCreateRequestDto("발상 부족")).tagId();

            assertThat(theirs).isNotEqualTo(mine);
            assertThat(tagNames(user.getId())).containsExactly("발상 부족");
            assertThat(tagNames(other.getId())).containsExactly("발상 부족");
        }

        @Test
        @DisplayName("삭제한 태그와 같은 이름을 다시 만들 수 있다 - 소프트 삭제된 행이 유니크 인덱스를 점유해도 500이 나면 안 된다")
        void recreatesTagAfterDeletion() {
            Long deletedTagId = tagService.createTag(user.getId(), new TagCreateRequestDto("발상 부족")).tagId();
            tagService.deleteTag(user.getId(), deletedTagId);

            TagResponseDto recreated = tagService.createTag(user.getId(), new TagCreateRequestDto("발상 부족"));

            assertThat(recreated.name()).isEqualTo("발상 부족");
            assertThat(tagNames(user.getId()))
                    .as("다시 만든 태그는 목록에 보여야 한다")
                    .containsExactly("발상 부족");
            assertThat(countRowsIncludingDeleted(user.getId()))
                    .as("같은 (user_id, normalized_name) 행은 유니크 인덱스상 하나뿐이어야 한다")
                    .isEqualTo(1);
        }

        @Test
        @DisplayName("삭제 후 대소문자만 바꿔 다시 만들어도 되살아난 태그가 새 표기를 갖는다")
        void restoredTagTakesNewDisplayName() {
            Long tagId = tagService.createTag(user.getId(), new TagCreateRequestDto("algebra")).tagId();
            tagService.deleteTag(user.getId(), tagId);

            TagResponseDto recreated = tagService.createTag(user.getId(), new TagCreateRequestDto("ALGEBRA"));

            assertThat(recreated.name()).isEqualTo("ALGEBRA");
            assertThat(tagNames(user.getId())).containsExactly("ALGEBRA");
        }
    }

    @Nested
    @DisplayName("태그 목록 조회")
    class GetUserTags {

        @Test
        @DisplayName("자기 태그만 이름 오름차순으로 돌려준다")
        void returnsOwnTagsSortedByName() {
            saveTag(user.getId(), "다항식");
            saveTag(user.getId(), "가정 오류");
            saveTag(user.getId(), "나눗셈");
            saveTag(other.getId(), "남의태그");

            List<TagResponseDto> tags = tagService.getUserTags(user.getId());

            assertThat(tags).extracting(TagResponseDto::name)
                    .as("정렬이 흔들리면 앱의 태그 목록 순서가 매번 달라진다")
                    .containsExactly("가정 오류", "나눗셈", "다항식");
        }

        @Test
        @DisplayName("태그가 하나도 없으면 빈 목록을 돌려준다")
        void returnsEmptyListWhenNoTags() {
            assertThat(tagService.getUserTags(user.getId())).isEmpty();
        }

        @Test
        @DisplayName("다른 사용자의 태그는 절대 섞이지 않는다")
        void neverLeaksOtherUsersTags() {
            saveTag(other.getId(), "남의태그");

            assertThat(tagService.getUserTags(user.getId())).isEmpty();
            assertThat(tagService.getUserTags(other.getId())).extracting(TagResponseDto::name)
                    .containsExactly("남의태그");
        }

        @Test
        @DisplayName("두 번째 조회는 캐시에서 나온다 - 서비스를 거치지 않은 DB 변경은 보이지 않는다")
        void servesSecondCallFromCache() {
            saveTag(user.getId(), "캐시대상");
            assertThat(tagService.getUserTags(user.getId())).hasSize(1);

            saveTag(user.getId(), "직접추가");

            assertThat(tagService.getUserTags(user.getId()))
                    .as("캐시가 동작하지 않으면 매 조회가 DB를 때린다")
                    .hasSize(1);
        }

        @Test
        @DisplayName("태그를 만들면 캐시가 무효화되어 새 태그가 바로 보인다")
        void evictsCacheOnCreate() {
            tagService.createTag(user.getId(), new TagCreateRequestDto("첫번째"));
            assertThat(tagService.getUserTags(user.getId())).hasSize(1);

            tagService.createTag(user.getId(), new TagCreateRequestDto("두번째"));

            assertThat(tagService.getUserTags(user.getId()))
                    .as("생성 직후 목록에 안 보이면 사용자는 태그가 안 만들어졌다고 생각한다")
                    .extracting(TagResponseDto::name)
                    .containsExactly("두번째", "첫번째");
        }

        @Test
        @DisplayName("태그를 삭제하면 캐시가 무효화되어 목록에서 즉시 사라진다")
        void evictsCacheOnDelete() {
            Long tagId = tagService.createTag(user.getId(), new TagCreateRequestDto("지울태그")).tagId();
            tagService.createTag(user.getId(), new TagCreateRequestDto("남을태그"));
            assertThat(tagService.getUserTags(user.getId())).hasSize(2);

            tagService.deleteTag(user.getId(), tagId);

            assertThat(tagService.getUserTags(user.getId())).extracting(TagResponseDto::name)
                    .containsExactly("남을태그");
        }

        @Test
        @DisplayName("한 사용자의 캐시 무효화가 다른 사용자의 캐시를 건드리지 않는다")
        void evictsCachePerUser() {
            tagService.createTag(user.getId(), new TagCreateRequestDto("내태그"));
            tagService.createTag(other.getId(), new TagCreateRequestDto("남의태그"));
            tagService.getUserTags(user.getId());
            tagService.getUserTags(other.getId());

            tagService.createTag(user.getId(), new TagCreateRequestDto("내태그2"));

            assertThat(tagService.getUserTags(user.getId())).hasSize(2);
            assertThat(tagService.getUserTags(other.getId())).extracting(TagResponseDto::name)
                    .containsExactly("남의태그");
        }
    }

    @Nested
    @DisplayName("태그 삭제")
    class DeleteTags {

        @Test
        @DisplayName("자기 태그를 삭제하면 목록에서 사라진다")
        void deletesOwnTag() {
            Tag tag = saveTag(user.getId(), "지울태그");

            tagService.deleteTag(user.getId(), tag.getId());

            assertThat(tagNames(user.getId())).isEmpty();
        }

        @Test
        @DisplayName("여러 태그를 한 번에 삭제한다")
        void deletesMultipleTags() {
            Tag first = saveTag(user.getId(), "첫번째");
            Tag second = saveTag(user.getId(), "두번째");
            saveTag(user.getId(), "남을태그");

            tagService.deleteTags(user.getId(), new TagDeleteRequestDto(List.of(first.getId(), second.getId())));

            assertThat(tagNames(user.getId())).containsExactly("남을태그");
        }

        @Test
        @DisplayName("같은 id가 중복으로 들어와도 정상 삭제된다")
        void ignoresDuplicatedIds() {
            Tag tag = saveTag(user.getId(), "지울태그");

            tagService.deleteTags(user.getId(), new TagDeleteRequestDto(List.of(tag.getId(), tag.getId())));

            assertThat(tagNames(user.getId())).isEmpty();
        }

        @Test
        @DisplayName("null 요소가 섞인 목록은 걸러내고 나머지를 삭제한다")
        void filtersOutNullIds() {
            Tag tag = saveTag(user.getId(), "지울태그");

            tagService.deleteTags(user.getId(), new TagDeleteRequestDto(Arrays.asList(tag.getId(), null)));

            assertThat(tagNames(user.getId())).isEmpty();
        }

        @Test
        @DisplayName("다른 사용자의 태그는 삭제할 수 없고 그대로 남는다")
        void rejectsOtherUsersTag() {
            Tag othersTag = saveTag(other.getId(), "남의태그");

            assertTagError(TagErrorCase.TAG_USER_UNMATCHED,
                    () -> tagService.deleteTag(user.getId(), othersTag.getId()));

            assertThat(tagNames(other.getId()))
                    .as("남의 태그가 지워지면 데이터 격리가 깨진 것이다")
                    .containsExactly("남의태그");
        }

        @Test
        @DisplayName("내 태그와 남의 태그가 섞인 요청은 하나도 지우지 않는다")
        void rejectsMixedOwnershipRequestAtomically() {
            Tag mine = saveTag(user.getId(), "내태그");
            Tag theirs = saveTag(other.getId(), "남의태그");

            assertTagError(TagErrorCase.TAG_USER_UNMATCHED,
                    () -> tagService.deleteTags(user.getId(), new TagDeleteRequestDto(List.of(mine.getId(), theirs.getId()))));

            assertThat(tagNames(user.getId())).containsExactly("내태그");
            assertThat(tagNames(other.getId())).containsExactly("남의태그");
        }

        @Test
        @DisplayName("존재하지 않는 태그 id는 TAG_NOT_FOUND 로 거절한다")
        void rejectsUnknownTagId() {
            assertTagError(TagErrorCase.TAG_NOT_FOUND,
                    () -> tagService.deleteTag(user.getId(), 999_999L));
        }

        @Test
        @DisplayName("일부만 존재하는 목록은 존재하는 태그도 지우지 않는다")
        void rejectsPartiallyUnknownIdsAtomically() {
            Tag tag = saveTag(user.getId(), "내태그");

            assertTagError(TagErrorCase.TAG_NOT_FOUND,
                    () -> tagService.deleteTags(user.getId(), new TagDeleteRequestDto(List.of(tag.getId(), 999_999L))));

            assertThat(tagNames(user.getId())).containsExactly("내태그");
        }

        @Test
        @DisplayName("이미 삭제된 태그를 다시 삭제하면 TAG_NOT_FOUND 다")
        void rejectsAlreadyDeletedTag() {
            Tag tag = saveTag(user.getId(), "지울태그");
            tagService.deleteTag(user.getId(), tag.getId());

            assertTagError(TagErrorCase.TAG_NOT_FOUND,
                    () -> tagService.deleteTag(user.getId(), tag.getId()));
        }

        @Test
        @DisplayName("빈 목록, null 목록, null 요청은 모두 TAG_NOT_FOUND 로 거절한다")
        void rejectsEmptyAndNullRequests() {
            assertTagError(TagErrorCase.TAG_NOT_FOUND,
                    () -> tagService.deleteTags(user.getId(), new TagDeleteRequestDto(List.of())));
            assertTagError(TagErrorCase.TAG_NOT_FOUND,
                    () -> tagService.deleteTags(user.getId(), new TagDeleteRequestDto(null)));
            assertTagError(TagErrorCase.TAG_NOT_FOUND,
                    () -> tagService.deleteTags(user.getId(), new TagDeleteRequestDto(Collections.singletonList(null))));
            assertTagError(TagErrorCase.TAG_NOT_FOUND,
                    () -> tagService.deleteTags(user.getId(), null));
        }

        @Test
        @DisplayName("태그를 지우면 문제와의 연결도 함께 끊기고 문제 자체는 남는다")
        void removesProblemMappingsButKeepsProblem() {
            Tag tag = saveTag(user.getId(), "지울태그");
            Problem problem = saveProblem(user.getId());
            mapTag(problem, tag);

            tagService.deleteTag(user.getId(), tag.getId());

            assertThat(problemTagMappingRepository.findAllByProblemId(problem.getId()))
                    .as("연결이 남으면 지운 태그가 문제 상세에서 계속 보인다")
                    .isEmpty();
            assertThat(problemRepository.findById(problem.getId())).isPresent();
        }
    }

    @Nested
    @DisplayName("태그 추천")
    class RecommendTags {

        @Test
        @DisplayName("태그가 5개 이하면 가진 태그 전부를 이름순으로 돌려준다")
        void returnsAllTagsWhenUnderLimit() {
            saveTag(user.getId(), "다");
            saveTag(user.getId(), "가");
            saveTag(user.getId(), "나");

            List<TagResponseDto> recommended = tagService.recommendTags(user.getId(), new TagRecommendRequestDto(List.of()));

            assertThat(recommended).extracting(TagResponseDto::name).containsExactly("가", "나", "다");
        }

        @Test
        @DisplayName("태그가 정확히 5개면 전부 돌려준다 - 경계값")
        void returnsAllTagsAtLimit() {
            for (int i = 1; i <= 5; i++) {
                saveTag(user.getId(), "태그" + i);
            }

            assertThat(tagService.recommendTags(user.getId(), new TagRecommendRequestDto(List.of()))).hasSize(5);
        }

        @Test
        @DisplayName("태그가 6개 이상이면 최근에 문제에 붙인 태그 5개를 돌려준다")
        void returnsRecentlyUsedTagsWhenOverLimit() {
            List<Tag> tags = List.of(
                    saveTag(user.getId(), "태그1"), saveTag(user.getId(), "태그2"),
                    saveTag(user.getId(), "태그3"), saveTag(user.getId(), "태그4"),
                    saveTag(user.getId(), "태그5"), saveTag(user.getId(), "태그6"),
                    saveTag(user.getId(), "태그7"));
            Problem problem = saveProblem(user.getId());

            LocalDateTime base = LocalDateTime.of(2026, 1, 1, 0, 0);
            for (int i = 0; i < tags.size(); i++) {
                ProblemTagMapping mapping = mapTag(problem, tags.get(i));
                forceCreatedAt(mapping, base.plusMinutes(i));
            }

            List<TagResponseDto> recommended = tagService.recommendTags(user.getId(), new TagRecommendRequestDto(List.of()));

            assertThat(recommended).extracting(TagResponseDto::name)
                    .as("가장 최근에 쓴 태그가 먼저 나와야 한다")
                    .containsExactly("태그7", "태그6", "태그5", "태그4", "태그3");
        }

        @Test
        @DisplayName("최근 사용 이력이 5개보다 적으면 나머지는 태그 목록으로 채운다")
        void fillsRemainingSlotsFromTagList() {
            List<Tag> tags = List.of(
                    saveTag(user.getId(), "가"), saveTag(user.getId(), "나"),
                    saveTag(user.getId(), "다"), saveTag(user.getId(), "라"),
                    saveTag(user.getId(), "마"), saveTag(user.getId(), "바"));
            Problem problem = saveProblem(user.getId());
            mapTag(problem, tags.get(5));

            List<TagResponseDto> recommended = tagService.recommendTags(user.getId(), new TagRecommendRequestDto(List.of()));

            assertThat(recommended).extracting(TagResponseDto::name)
                    .as("최근 사용 태그가 앞에 오고 나머지는 이름순으로 채워진다")
                    .containsExactly("바", "가", "나", "다", "라");
        }

        @Test
        @DisplayName("추천 목록에 다른 사용자의 태그는 들어가지 않는다")
        void neverRecommendsOtherUsersTags() {
            for (int i = 1; i <= 6; i++) {
                saveTag(other.getId(), "남의태그" + i);
            }
            Problem othersProblem = saveProblem(other.getId());
            mapTag(othersProblem, saveTag(other.getId(), "남의최근태그"));
            saveTag(user.getId(), "내태그");

            assertThat(tagService.recommendTags(user.getId(), new TagRecommendRequestDto(List.of())))
                    .extracting(TagResponseDto::name)
                    .containsExactly("내태그");
        }

        @Test
        @DisplayName("태그가 없으면 빈 목록을 돌려준다")
        void returnsEmptyListWhenNoTags() {
            assertThat(tagService.recommendTags(user.getId(), new TagRecommendRequestDto(List.of()))).isEmpty();
        }

        @Test
        @DisplayName("이미지 URL 목록이 null 이거나 비어 있어도 추천은 동작한다")
        void toleratesNullImageUrls() {
            saveTag(user.getId(), "내태그");

            assertThat(tagService.recommendTags(user.getId(), new TagRecommendRequestDto(null)))
                    .extracting(TagResponseDto::name)
                    .containsExactly("내태그");
        }
    }
}
