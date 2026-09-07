package com.aisip.OnO.backend.notice.service;

import com.aisip.OnO.backend.util.redis.RedisSingleDataService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * "그만 보기"를 누른 유저를 기억한다.
 *
 * <p>유저 수만큼 행이 생기는데 24시간이 지나면 의미가 없어지는 값이라 DB 가 아니라
 * Redis 에 두고 TTL 로 정리되게 했다. Redis 가 죽어서 조회에 실패하면 숨기지 않고
 * 그냥 노출하는 쪽으로 둔다. 공지를 한 번 더 보는 것보다 못 보는 쪽이 손해가 크다.
 *
 * <p>키에 공지 ID 가 들어가므로 관리자가 공지를 내리고 새로 올리면 이전에 그만 보기를
 * 누른 유저에게도 다시 보인다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NoticeDismissService {

    private static final String KEY_PREFIX = "NOTICE:DISMISS";
    private static final String MARKER = "1";
    private static final Duration MAX_TTL = Duration.ofHours(24);

    private final RedisSingleDataService redisSingleDataService;

    public boolean isDismissed(Long noticeId, Long userId) {
        try {
            String value = redisSingleDataService.getSingleData(key(noticeId, userId));
            return value != null && !value.isBlank();
        } catch (Exception e) {
            log.warn("공지 그만 보기 조회에 실패했다. noticeId={}, userId={}, reason={}",
                    noticeId, userId, e.getMessage());
            return false;
        }
    }

    /**
     * 24시간 동안 숨긴다. 공지가 그 전에 만료되면 남은 기간만큼만 잡아 키를 남기지 않는다.
     *
     * @param remaining 공지 만료까지 남은 시간
     */
    public void dismiss(Long noticeId, Long userId, Duration remaining) {
        Duration ttl = remaining.compareTo(MAX_TTL) < 0 ? remaining : MAX_TTL;
        if (ttl.isNegative() || ttl.isZero()) {
            return;
        }
        redisSingleDataService.setSingleData(key(noticeId, userId), MARKER, ttl);
    }

    private String key(Long noticeId, Long userId) {
        return KEY_PREFIX + ":" + noticeId + ":" + userId;
    }
}
