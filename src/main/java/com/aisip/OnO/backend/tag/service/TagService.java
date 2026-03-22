package com.aisip.OnO.backend.tag.service;

import com.aisip.OnO.backend.common.exception.ApplicationException;
import com.aisip.OnO.backend.tag.dto.TagCreateRequestDto;
import com.aisip.OnO.backend.tag.dto.TagResponseDto;
import com.aisip.OnO.backend.tag.entity.Tag;
import com.aisip.OnO.backend.tag.exception.TagErrorCase;
import com.aisip.OnO.backend.tag.repository.TagRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class TagService {

    private static final int MAX_TAG_NAME_LENGTH = 30;

    private final TagRepository tagRepository;

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
