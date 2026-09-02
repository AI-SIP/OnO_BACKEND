package com.aisip.OnO.backend.tag.service;

import com.aisip.OnO.backend.common.exception.ApplicationException;
import com.aisip.OnO.backend.tag.dto.TagCreateRequestDto;
import com.aisip.OnO.backend.tag.dto.TagDeleteRequestDto;
import com.aisip.OnO.backend.tag.dto.TagRecommendRequestDto;
import com.aisip.OnO.backend.tag.dto.TagResponseDto;
import com.aisip.OnO.backend.tag.entity.ProblemTagMapping;
import com.aisip.OnO.backend.tag.entity.Tag;
import com.aisip.OnO.backend.tag.exception.TagErrorCase;
import com.aisip.OnO.backend.tag.repository.ProblemTagMappingRepository;
import com.aisip.OnO.backend.tag.repository.TagRepository;
import com.aisip.OnO.backend.util.redis.RedisSingleDataService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class TagService {

    private static final int MAX_TAG_NAME_LENGTH = 30;
    private static final int RECOMMEND_TAG_LIMIT = 5;
    private static final String TAG_LIST_CACHE_PREFIX = "TAG_LIST:";
    private static final Duration TAG_CACHE_TTL = Duration.ofHours(1);

    private final TagRepository tagRepository;
    private final ProblemTagMappingRepository problemTagMappingRepository;
    private final TagWriter tagWriter;
    private final RedisSingleDataService redisSingleDataService;
    private final ObjectMapper objectMapper;

    /**
     * 태그를 만들거나, 이미 같은 이름이 있으면 그 태그를 그대로 돌려준다.
     *
     * <p>여기서 트랜잭션을 열지 않는 이유가 있다. 삽입은 (user_id, normalized_name)
     * 유니크 인덱스와 경쟁하는데, 실패한 트랜잭션 안에서는 재조회로 복구할 수 없다.
     * 조회/삽입을 각각 독립 트랜잭션으로 수행하는 {@link TagWriter} 에 맡기고,
     * 여기서는 실패 시 재조회 판단만 한다.
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public TagResponseDto createTag(Long userId, TagCreateRequestDto requestDto) {
        String tagName = normalizeDisplayName(requestDto == null ? null : requestDto.name());
        String normalizedName = tagName.toLowerCase(Locale.ROOT);

        Tag tag = tagWriter.findOrRestore(userId, tagName, normalizedName)
                .orElseGet(() -> insertOrFindConcurrentlyCreated(userId, tagName, normalizedName));

        evictTagCache(userId);
        log.info("userId: {} create tag: {}", userId, tag.getName());
        return TagResponseDto.from(tag);
    }

    private Tag insertOrFindConcurrentlyCreated(Long userId, String tagName, String normalizedName) {
        try {
            return tagWriter.insert(userId, tagName, normalizedName);
        } catch (DataIntegrityViolationException e) {
            // 같은 이름을 동시에 보낸 다른 요청이 먼저 커밋했다는 뜻이다.
            // 삽입 트랜잭션은 이미 롤백됐으므로, 새 트랜잭션에서 그 태그를 찾아 돌려준다.
            log.info("userId: {} tag insert lost the race, re-reading: {}", userId, tagName);
            return tagWriter.findOrRestore(userId, tagName, normalizedName)
                    .orElseThrow(() -> e);
        }
    }

    @Transactional(readOnly = true)
    public List<TagResponseDto> getUserTags(Long userId) {
        String cacheKey = TAG_LIST_CACHE_PREFIX + userId;

        List<TagResponseDto> cached = readTagCache(cacheKey);
        if (cached != null) {
            return cached;
        }

        List<TagResponseDto> tags = tagRepository.findAllByUserIdOrderByNameAsc(userId)
                .stream()
                .map(TagResponseDto::from)
                .toList();

        writeTagCache(cacheKey, tags);
        return tags;
    }

    public void deleteTag(Long userId, Long tagId) {
        deleteTags(userId, new TagDeleteRequestDto(List.of(tagId)));
    }

    public void deleteTags(Long userId, TagDeleteRequestDto requestDto) {
        Set<Long> tagIds = toDistinctIds(requestDto == null ? null : requestDto.deleteTagIdList());
        if (tagIds.isEmpty()) {
            throw new ApplicationException(TagErrorCase.TAG_NOT_FOUND);
        }

        List<Tag> tags = tagRepository.findAllById(new ArrayList<>(tagIds));
        if (tags.size() != tagIds.size()) {
            throw new ApplicationException(TagErrorCase.TAG_NOT_FOUND);
        }

        boolean hasOtherUsersTag = tags.stream().anyMatch(tag -> !tag.getUserId().equals(userId));
        if (hasOtherUsersTag) {
            throw new ApplicationException(TagErrorCase.TAG_USER_UNMATCHED);
        }

        List<ProblemTagMapping> mappings = problemTagMappingRepository.findAllByTagIdIn(new ArrayList<>(tagIds));
        if (!mappings.isEmpty()) {
            problemTagMappingRepository.deleteAll(mappings);
        }

        tagRepository.deleteAll(tags);
        evictTagCache(userId);
        log.info("userId: {} deleted tags count: {}", userId, tagIds.size());
    }

    @Transactional(readOnly = true)
    public List<TagResponseDto> recommendTags(Long userId, TagRecommendRequestDto requestDto) {
        List<Tag> userTags = tagRepository.findAllByUserIdOrderByNameAsc(userId);
        if (userTags.size() <= RECOMMEND_TAG_LIMIT) {
            return userTags.stream().map(TagResponseDto::from).toList();
        }

        List<ProblemTagMapping> recentMappings = problemTagMappingRepository.findAllByTagUserIdOrderByCreatedAtDesc(userId);
        Map<Long, TagResponseDto> uniqueRecentTags = new LinkedHashMap<>();

        for (ProblemTagMapping mapping : recentMappings) {
            Tag tag = mapping.getTag();
            uniqueRecentTags.putIfAbsent(tag.getId(), TagResponseDto.from(tag));
            if (uniqueRecentTags.size() >= RECOMMEND_TAG_LIMIT) {
                break;
            }
        }

        // 최근 사용 이력이 부족하면 남은 자리는 사용자 태그 목록으로 보완
        if (uniqueRecentTags.size() < RECOMMEND_TAG_LIMIT) {
            for (Tag tag : userTags) {
                uniqueRecentTags.putIfAbsent(tag.getId(), TagResponseDto.from(tag));
                if (uniqueRecentTags.size() >= RECOMMEND_TAG_LIMIT) {
                    break;
                }
            }
        }

        return new ArrayList<>(uniqueRecentTags.values());
    }

    private Set<Long> toDistinctIds(List<Long> ids) {
        if (ids == null) {
            return Set.of();
        }
        return ids.stream()
                .filter(java.util.Objects::nonNull)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
    }

    private List<TagResponseDto> readTagCache(String key) {
        try {
            String json = redisSingleDataService.getSingleData(key);
            if (json == null || json.isBlank()) {
                return null;
            }
            return objectMapper.readValue(json, new TypeReference<List<TagResponseDto>>() {});
        } catch (Exception e) {
            log.warn("Failed to read tag list cache. key={}, reason={}", key, e.getMessage());
            return null;
        }
    }

    private void writeTagCache(String key, List<TagResponseDto> tags) {
        try {
            String json = objectMapper.writeValueAsString(tags);
            redisSingleDataService.setSingleData(key, json, TAG_CACHE_TTL);
        } catch (Exception e) {
            log.warn("Failed to write tag list cache. key={}, reason={}", key, e.getMessage());
        }
    }

    /**
     * 캐시 무효화 실패가 태그 생성/삭제 자체를 실패시키면 안 된다.
     * Redis 가 죽어도 DB 반영은 이미 끝났고, 조회는 캐시 미스로 DB 를 타면 된다.
     */
    private void evictTagCache(Long userId) {
        try {
            redisSingleDataService.deleteSingleData(TAG_LIST_CACHE_PREFIX + userId);
        } catch (Exception e) {
            log.warn("Failed to evict tag list cache. userId={}, reason={}", userId, e.getMessage());
        }
    }

    private String normalizeDisplayName(String rawTagName) {
        String tagName = rawTagName == null ? "" : rawTagName.trim();
        if (tagName.startsWith("#")) {
            tagName = tagName.substring(1).trim();
        }

        if (tagName.isBlank()) {
            throw new ApplicationException(TagErrorCase.TAG_NAME_EMPTY);
        }

        if (tagName.length() > MAX_TAG_NAME_LENGTH) {
            throw new ApplicationException(TagErrorCase.TAG_NAME_TOO_LONG);
        }

        return tagName;
    }
}
