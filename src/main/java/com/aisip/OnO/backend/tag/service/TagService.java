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
import org.springframework.stereotype.Service;
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
    private final RedisSingleDataService redisSingleDataService;
    private final ObjectMapper objectMapper;

    public TagResponseDto createTag(Long userId, TagCreateRequestDto requestDto) {
        String tagName = normalizeDisplayName(requestDto.name());
        String normalizedName = tagName.toLowerCase(Locale.ROOT);

        Tag tag = tagRepository.findByUserIdAndNormalizedName(userId, normalizedName)
                .orElseGet(() -> tagRepository.save(Tag.from(userId, tagName, normalizedName)));

        evictTagCache(userId);
        log.info("userId: {} create tag: {}", userId, tag.getName());
        return TagResponseDto.from(tag);
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
        Set<Long> tagIds = toDistinctIds(requestDto.deleteTagIdList());
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

    private void evictTagCache(Long userId) {
        redisSingleDataService.deleteSingleData(TAG_LIST_CACHE_PREFIX + userId);
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
