package com.aisip.OnO.backend.tag.repository;

import com.aisip.OnO.backend.problem.entity.Problem;
import com.aisip.OnO.backend.tag.TagTestSupport;
import com.aisip.OnO.backend.tag.entity.ProblemTagMapping;
import com.aisip.OnO.backend.tag.entity.Tag;
import com.aisip.OnO.backend.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("ProblemTagMappingRepository 쿼리")
class ProblemTagMappingRepositoryTest extends TagTestSupport {

    private User user;
    private User other;
    private Problem problem;

    @BeforeEach
    void setUpFixtures() {
        user = fixtures.createUser();
        other = fixtures.createOtherUser();
        problem = saveProblem(user.getId());
    }

    @Nested
    @DisplayName("문제 기준 조회")
    class ByProblem {

        @Test
        @DisplayName("해당 문제에 붙은 연결만 돌려준다")
        void findsMappingsOfGivenProblem() {
            Tag tag = saveTag(user.getId(), "태그A");
            ProblemTagMapping mapping = mapTag(problem, tag);
            mapTag(saveProblem(user.getId()), saveTag(user.getId(), "태그B"));

            assertThat(problemTagMappingRepository.findAllByProblemId(problem.getId()))
                    .extracting(ProblemTagMapping::getId)
                    .containsExactly(mapping.getId());
        }

        @Test
        @DisplayName("연결이 없으면 빈 목록이다")
        void returnsEmptyWhenNoMapping() {
            assertThat(problemTagMappingRepository.findAllByProblemId(problem.getId())).isEmpty();
        }

        @Test
        @DisplayName("문제와 태그 id 조합으로 좁혀 조회한다")
        void findsByProblemAndTagIds() {
            Tag first = saveTag(user.getId(), "태그A");
            Tag second = saveTag(user.getId(), "태그B");
            mapTag(problem, first);
            ProblemTagMapping secondMapping = mapTag(problem, second);

            assertThat(problemTagMappingRepository.findAllByProblemIdAndTagIdIn(problem.getId(), List.of(second.getId())))
                    .extracting(ProblemTagMapping::getId)
                    .containsExactly(secondMapping.getId());
        }

        @Test
        @DisplayName("소프트 삭제된 연결은 조회되지 않는다")
        void excludesSoftDeletedMapping() {
            ProblemTagMapping mapping = mapTag(problem, saveTag(user.getId(), "태그A"));
            problemTagMappingRepository.delete(mapping);

            assertThat(problemTagMappingRepository.findAllByProblemId(problem.getId())).isEmpty();
        }

        @Test
        @DisplayName("같은 문제에 같은 태그를 두 번 연결할 수 없다")
        void rejectsDuplicateMapping() {
            Tag tag = saveTag(user.getId(), "태그A");
            mapTag(problem, tag);

            assertThatThrownBy(() -> problemTagMappingRepository.saveAndFlush(ProblemTagMapping.from(problem, tag)))
                    .as("중복 연결이 생기면 문제 상세에 같은 태그가 두 번 보인다")
                    .isInstanceOf(DataIntegrityViolationException.class);
        }
    }

    @Nested
    @DisplayName("태그 기준 조회")
    class ByTag {

        @Test
        @DisplayName("태그 하나에 걸린 모든 연결을 돌려준다")
        void findsMappingsOfGivenTag() {
            Tag tag = saveTag(user.getId(), "태그A");
            Problem another = saveProblem(user.getId());
            mapTag(problem, tag);
            mapTag(another, tag);

            assertThat(problemTagMappingRepository.findAllByTagId(tag.getId())).hasSize(2);
        }

        @Test
        @DisplayName("여러 태그 id로 한 번에 조회한다 - 태그 일괄 삭제가 이 쿼리에 기댄다")
        void findsMappingsOfGivenTags() {
            Tag first = saveTag(user.getId(), "태그A");
            Tag second = saveTag(user.getId(), "태그B");
            Tag untouched = saveTag(user.getId(), "태그C");
            ProblemTagMapping firstMapping = mapTag(problem, first);
            ProblemTagMapping secondMapping = mapTag(problem, second);
            mapTag(problem, untouched);

            assertThat(problemTagMappingRepository.findAllByTagIdIn(List.of(first.getId(), second.getId())))
                    .extracting(ProblemTagMapping::getId)
                    .containsExactlyInAnyOrder(firstMapping.getId(), secondMapping.getId());
        }

        @Test
        @DisplayName("빈 태그 id 목록이면 빈 결과를 돌려준다")
        void handlesEmptyTagIdList() {
            mapTag(problem, saveTag(user.getId(), "태그A"));

            assertThat(problemTagMappingRepository.findAllByTagIdIn(List.of())).isEmpty();
        }
    }

    @Nested
    @DisplayName("사용자 기준 최근 사용 조회")
    class ByTagOwner {

        @Test
        @DisplayName("자기 태그의 연결만 최근 생성 순으로 돌려준다")
        void findsOwnMappingsOrderedByCreatedAtDesc() {
            Tag older = saveTag(user.getId(), "이전태그");
            Tag newer = saveTag(user.getId(), "최근태그");
            LocalDateTime base = LocalDateTime.of(2026, 1, 1, 0, 0);
            ProblemTagMapping olderMapping = mapTag(problem, older);
            ProblemTagMapping newerMapping = mapTag(problem, newer);
            forceCreatedAt(olderMapping, base);
            forceCreatedAt(newerMapping, base.plusMinutes(1));

            Problem othersProblem = saveProblem(other.getId());
            mapTag(othersProblem, saveTag(other.getId(), "남의태그"));

            assertThat(problemTagMappingRepository.findAllByTagUserIdOrderByCreatedAtDesc(user.getId()))
                    .extracting(ProblemTagMapping::getId)
                    .as("남의 연결이 섞이면 추천 태그에 남의 태그가 노출된다")
                    .containsExactly(newerMapping.getId(), olderMapping.getId());
        }

        @Test
        @DisplayName("연결이 없으면 빈 목록이다")
        void returnsEmptyWhenNoMapping() {
            assertThat(problemTagMappingRepository.findAllByTagUserIdOrderByCreatedAtDesc(user.getId())).isEmpty();
        }
    }
}
