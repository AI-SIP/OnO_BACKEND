package com.aisip.OnO.backend.util.redis;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.aisip.OnO.backend.common.transaction.AfterCommit;
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

    /**
     * 스트릭 캐시를 커밋이 끝난 뒤에 비운다. 트랜잭션 밖에서 부르면 바로 비운다.
     *
     * <p>문제 등록, 삭제와 복습 저장, 삭제가 모두 트랜잭션 안에서 이 메서드를 부른다.
     * 커밋 전에 비우면 그 사이 들어온 캘린더 조회가 옛 기록으로 캐시를 다시 채우고,
     * TTL 이 자정까지라 하루 종일 틀린 스트릭이 나간다. 호출부마다 미루지 않고
     * 여기서 한 번에 미뤄서 새 호출부가 생겨도 같은 순서를 지키게 한다.
     */
    public void evict(Long userId) {
        AfterCommit.run("스트릭 캐시 삭제 userId=" + userId,
                () -> redisSingleDataService.deleteSingleData(key(userId)));
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