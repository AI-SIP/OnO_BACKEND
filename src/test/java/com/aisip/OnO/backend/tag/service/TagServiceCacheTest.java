package com.aisip.OnO.backend.tag.service;

import com.aisip.OnO.backend.tag.dto.TagCreateRequestDto;
import com.aisip.OnO.backend.tag.dto.TagDeleteRequestDto;
import com.aisip.OnO.backend.tag.dto.TagResponseDto;
import com.aisip.OnO.backend.tag.entity.Tag;
import com.aisip.OnO.backend.tag.repository.ProblemTagMappingRepository;
import com.aisip.OnO.backend.tag.repository.TagRepository;
import com.aisip.OnO.backend.util.redis.RedisSingleDataService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * 태그 목록 캐시 동작만 떼어 본 단위 테스트.
 *
 * <p>Redis 장애 상황은 실제 Redis 로는 만들기 어렵고, 통합 테스트에 목을 끼워 넣으면
 * 스프링 컨텍스트가 하나 더 뜬다. 캐시 분기 자체는 순수 로직이므로 여기서 검증한다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("TagService 태그 목록 캐시")
class TagServiceCacheTest {

    private static final Long USER_ID = 7L;
    private static final String CACHE_KEY = "TAG_LIST:7";

    @Mock
    private TagRepository tagRepository;

    @Mock
    private ProblemTagMappingRepository problemTagMappingRepository;

    @Mock
    private TagWriter tagWriter;

    @Mock
    private RedisSingleDataService redisSingleDataService;

    private TagService tagService;

    @BeforeEach
    void setUp() {
        tagService = new TagService(
                tagRepository,
                problemTagMappingRepository,
                tagWriter,
                redisSingleDataService,
                new ObjectMapper()
        );
    }

    @Nested
    @DisplayName("조회 캐시")
    class ReadThroughCache {

        @Test
        @DisplayName("캐시가 있으면 DB를 조회하지 않는다")
        void servesFromCacheWithoutHittingDatabase() {
            given(redisSingleDataService.getSingleData(CACHE_KEY))
                    .willReturn("[{\"tagId\":1,\"name\":\"캐시태그\"}]");

            List<TagResponseDto> tags = tagService.getUserTags(USER_ID);

            assertThat(tags).extracting(TagResponseDto::name).containsExactly("캐시태그");
            verify(tagRepository, never()).findAllByUserIdOrderByNameAsc(any());
        }

        @Test
        @DisplayName("캐시가 비어 있으면 DB를 조회하고 결과를 캐시에 적재한다")
        void loadsFromDatabaseAndWarmsCache() {
            given(redisSingleDataService.getSingleData(CACHE_KEY)).willReturn("");
            given(tagRepository.findAllByUserIdOrderByNameAsc(USER_ID))
                    .willReturn(List.of(Tag.from(USER_ID, "DB태그", "db태그")));

            List<TagResponseDto> tags = tagService.getUserTags(USER_ID);

            assertThat(tags).extracting(TagResponseDto::name).containsExactly("DB태그");
            verify(redisSingleDataService).setSingleData(eq(CACHE_KEY), anyString(), any(Duration.class));
        }

        @Test
        @DisplayName("캐시에 깨진 JSON이 들어 있어도 DB 결과로 응답한다")
        void fallsBackToDatabaseOnCorruptedCache() {
            given(redisSingleDataService.getSingleData(CACHE_KEY)).willReturn("{not-json");
            given(tagRepository.findAllByUserIdOrderByNameAsc(USER_ID))
                    .willReturn(List.of(Tag.from(USER_ID, "DB태그", "db태그")));

            assertThat(tagService.getUserTags(USER_ID))
                    .as("캐시가 오염됐다고 조회 API가 500이 되면 안 된다")
                    .extracting(TagResponseDto::name)
                    .containsExactly("DB태그");
        }

        @Test
        @DisplayName("Redis 읽기가 실패해도 DB 결과로 응답한다")
        void survivesCacheReadFailure() {
            given(redisSingleDataService.getSingleData(CACHE_KEY))
                    .willThrow(new RuntimeException("redis down"));
            given(tagRepository.findAllByUserIdOrderByNameAsc(USER_ID))
                    .willReturn(List.of(Tag.from(USER_ID, "DB태그", "db태그")));

            assertThat(tagService.getUserTags(USER_ID)).hasSize(1);
        }

        @Test
        @DisplayName("Redis 쓰기가 실패해도 조회 결과는 정상 반환된다")
        void survivesCacheWriteFailure() {
            given(redisSingleDataService.getSingleData(CACHE_KEY)).willReturn(null);
            given(tagRepository.findAllByUserIdOrderByNameAsc(USER_ID))
                    .willReturn(List.of(Tag.from(USER_ID, "DB태그", "db태그")));
            willThrow(new RuntimeException("redis down"))
                    .given(redisSingleDataService).setSingleData(anyString(), anyString(), any(Duration.class));

            assertThat(tagService.getUserTags(USER_ID)).hasSize(1);
        }
    }

    @Nested
    @DisplayName("캐시 무효화")
    class CacheEviction {

        @Test
        @DisplayName("태그를 만들면 그 사용자의 캐시만 지운다")
        void evictsOwnCacheOnCreate() {
            given(tagWriter.findOrRestore(USER_ID, "새태그", "새태그"))
                    .willReturn(Optional.of(Tag.from(USER_ID, "새태그", "새태그")));

            tagService.createTag(USER_ID, new TagCreateRequestDto("새태그"));

            verify(redisSingleDataService).deleteSingleData(CACHE_KEY);
        }

        @Test
        @DisplayName("Redis 무효화가 실패해도 태그 생성은 성공으로 끝난다")
        void survivesEvictionFailureOnCreate() {
            given(tagWriter.findOrRestore(USER_ID, "새태그", "새태그"))
                    .willReturn(Optional.of(Tag.from(USER_ID, "새태그", "새태그")));
            willThrow(new RuntimeException("redis down"))
                    .given(redisSingleDataService).deleteSingleData(CACHE_KEY);

            assertThatCode(() -> tagService.createTag(USER_ID, new TagCreateRequestDto("새태그")))
                    .as("DB 저장은 이미 끝났는데 캐시 때문에 500을 내면 사용자는 실패로 인식한다")
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("Redis 무효화가 실패해도 태그 삭제는 성공으로 끝난다")
        void survivesEvictionFailureOnDelete() {
            Tag tag = Tag.from(USER_ID, "지울태그", "지울태그");
            given(tagRepository.findAllById(List.of(1L))).willReturn(List.of(tag));
            given(problemTagMappingRepository.findAllByTagIdIn(List.of(1L))).willReturn(List.of());
            willThrow(new RuntimeException("redis down"))
                    .given(redisSingleDataService).deleteSingleData(CACHE_KEY);

            assertThatCode(() -> tagService.deleteTags(USER_ID, new TagDeleteRequestDto(List.of(1L))))
                    .doesNotThrowAnyException();

            verify(tagRepository).deleteAll(List.of(tag));
        }
    }
}
