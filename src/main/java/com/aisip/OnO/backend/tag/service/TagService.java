package com.aisip.OnO.backend.tag.service;

import com.aisip.OnO.backend.common.exception.ApplicationException;
import com.aisip.OnO.backend.tag.dto.TagCreateRequestDto;
import com.aisip.OnO.backend.tag.dto.TagDeleteRequestDto;
import com.aisip.OnO.backend.tag.dto.TagResponseDto;
import com.aisip.OnO.backend.tag.entity.ProblemTagMapping;
import com.aisip.OnO.backend.tag.entity.Tag;
import com.aisip.OnO.backend.tag.exception.TagErrorCase;
import com.aisip.OnO.backend.tag.repository.ProblemTagMappingRepository;
import com.aisip.OnO.backend.tag.repository.TagRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class TagService {

    private static final int MAX_TAG_NAME_LENGTH = 30;

    private final TagRepository tagRepository;
    private final ProblemTagMappingRepository problemTagMappingRepository;

    public TagResponseDto createTag(Long userId, TagCreateRequestDto requestDto) {
        String tagName = normalizeDisplayName(requestDto.name());
        String normalizedName = tagName.toLowerCase(Locale.ROOT);

        Tag tag = tagRepository.findByUserIdAndNormalizedName(userId, normalizedName)
                .orElseGet(() -> tagRepository.save(Tag.from(userId, tagName, normalizedName)));

        log.info("userId: {} create tag: {}", userId, tag.getName());
        return TagResponseDto.from(tag);
    }

    @Transactional(readOnly = true)
    public List<TagResponseDto> getUserTags(Long userId) {
        return tagRepository.findAllByUserIdOrderByNameAsc(userId)
                .stream()
                .map(TagResponseDto::from)
                .toList();
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
        log.info("userId: {} deleted tags count: {}", userId, tagIds.size());
    }

    private Set<Long> toDistinctIds(List<Long> ids) {
        if (ids == null) {
            return Set.of();
        }
        return ids.stream()
                .filter(java.util.Objects::nonNull)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
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
