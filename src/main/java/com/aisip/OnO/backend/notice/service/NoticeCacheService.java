package com.aisip.OnO.backend.notice.service;

import com.aisip.OnO.backend.notice.dto.NoticeResponseDto;
import com.aisip.OnO.backend.util.redis.RedisSingleDataService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * 지금 노출할 공지를 Redis 에 캐싱한다.
 *
 * <p>공지 조회는 앱 메인에 들어올 때마다 호출되는데 실제로 공지가 걸려 있는 날은
 * 드물다. 그래서 공지가 없다는 사실까지 같이 캐싱해서 빈 조회가 DB 로 내려가지
 * 않게 한다.
 *
 * <p>TTL 을 60초로 짧게 잡은 이유는 {@code RedisHandler.executeOperation} 이 삭제
 * 실패를 예외 없이 0 으로만 알려 주기 때문이다. 관리자가 공지를 내렸는데 evict 가
 * 실패하면 캐시가 계속 살아 있게 되는데, TTL 이 짧으면 늦어도 60초 뒤에는 스스로
 * 바로잡힌다. 캐시 목적이 트래픽 흡수라 60초로도 DB 부하는 충분히 걷힌다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NoticeCacheService {

    private static final String ACTIVE_KEY = "NOTICE:ACTIVE";
    private static final String EMPTY_MARKER = "NONE";
    private static final Duration TTL = Duration.ofSeconds(60);

    private final RedisSingleDataService redisSingleDataService;
    private final ObjectMapper objectMapper;

    /**
     * 캐시된 활성 공지를 읽는다.
     *
     * @return 비어 있으면 캐시 미스라서 DB 를 봐야 한다는 뜻이고,
     * 값이 있으면서 {@link ActiveNoticeSnapshot#isEmpty()} 이면 활성 공지가 없다고 캐싱된 상태다.
     */
    public Optional<ActiveNoticeSnapshot> read(LocalDateTime now) {
        try {
            String cached = redisSingleDataService.getSingleData(ACTIVE_KEY);
            if (cached == null || cached.isBlank()) {
                return Optional.empty();
            }
            if (EMPTY_MARKER.equals(cached)) {
                return Optional.of(new ActiveNoticeSnapshot(null));
            }

            NoticeResponseDto notice = objectMapper.readValue(cached, NoticeResponseDto.class);
            // TTL 안에 공지가 만료될 수 있다. 캐시에 남아 있어도 기간이 지났으면 쓰지 않는다.
            if (notice.expiresAt() == null || !notice.expiresAt().isAfter(now)) {
                return Optional.empty();
            }
            return Optional.of(new ActiveNoticeSnapshot(notice));
        } catch (Exception e) {
            log.warn("공지 캐시 조회에 실패했다. reason={}", e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * 활성 공지를 캐싱한다. {@code notice} 가 null 이면 "공지 없음"으로 캐싱한다.
     */
    public void write(NoticeResponseDto notice) {
        try {
            String value = (notice == null) ? EMPTY_MARKER : objectMapper.writeValueAsString(notice);
            redisSingleDataService.setSingleData(ACTIVE_KEY, value, TTL);
        } catch (Exception e) {
            log.warn("공지 캐시 저장에 실패했다. reason={}", e.getMessage());
        }
    }

    /**
     * 관리자가 공지를 등록하거나 내렸을 때 캐시를 비운다.
     */
    public void evict() {
        int result = redisSingleDataService.deleteSingleData(ACTIVE_KEY);
        if (result == 0) {
            log.warn("공지 캐시 삭제에 실패했다. 최대 {}초 동안 이전 상태가 노출될 수 있다.", TTL.toSeconds());
        }
    }

    /**
     * 캐시에 담긴 활성 공지 상태. {@code notice} 가 null 이면 활성 공지가 없다는 뜻이다.
     */
    public record ActiveNoticeSnapshot(NoticeResponseDto notice) {

        public boolean isEmpty() {
            return notice == null;
        }
    }
}
