package com.aisip.OnO.backend.util.redis;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import java.util.TreeSet;

@Slf4j
@Service
@RequiredArgsConstructor
public class StreakCacheService {

    private static final String KEY_PREFIX = "STREAK";
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final RedisSingleDataService redisSingleDataService;
    private final ObjectMapper objectMapper;

    public Optional<TreeSet<LocalDate>> get(Long userId) {
        try {
            String json = redisSingleDataService.getSingleData(key(userId));
            if (json == null || json.isBlank()) {
                return Optional.empty();
            }
            return Optional.of(objectMapper.readValue(json, new TypeReference<TreeSet<LocalDate>>() {}));
        } catch (Exception e) {
            log.warn("Failed to read streak cache. userId={}, reason={}", userId, e.getMessage());
            return Optional.empty();
        }
    }

    public void put(Long userId, TreeSet<LocalDate> dates) {
        try {
            String json = objectMapper.writeValueAsString(dates);
            redisSingleDataService.setSingleData(key(userId), json, ttlUntilNextMidnight());
        } catch (Exception e) {
            log.warn("Failed to write streak cache. userId={}, reason={}", userId, e.getMessage());
        }
    }

    public void evict(Long userId) {
        redisSingleDataService.deleteSingleData(key(userId));
    }

    private String key(Long userId) {
        return KEY_PREFIX + ":" + userId;
    }

    private Duration ttlUntilNextMidnight() {
        LocalDateTime now = LocalDateTime.now(KST);
        LocalDateTime nextMidnight = now.toLocalDate().plusDays(1).atStartOfDay();
        Duration ttl = Duration.between(now, nextMidnight);
        return ttl.isNegative() || ttl.isZero() ? Duration.ofSeconds(1) : ttl;
    }
}