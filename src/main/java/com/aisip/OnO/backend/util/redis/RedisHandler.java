package com.aisip.OnO.backend.util.redis;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class RedisHandler {

    private final RedisConfig redisConfig;

    /**
     * 리스트에 접근하여 다양한 연산을 수행합니다.
     *
     * @return ListOperations<String, Object>
     */
    public ListOperations<String, Object> getListOperations() {
        return redisConfig.redisTemplate().opsForList();
    }

    /**
     * 단일 데이터에 접근하여 다양한 연산을 수행합니다.
     *
     * @return ValueOperations<String, Object>
     */
    public ValueOperations<String, Object> getValueOperations() {
        return redisConfig.redisTemplate().opsForValue();
    }


    /**
     * Redis 작업중 등록, 수정, 삭제에 대해서 처리 및 예외처리를 수행합니다.
     * 예외는 호출부로 던지지 않고 경고 로그만 남긴다. 운영 로그 수집에 잡히도록 stdout 대신 로거를 쓴다(#410).
     *
     * @param operation 실행할 Redis 쓰기·삭제 작업
     * @return 성공(1), 실패(0)
     */
    public int executeOperation(Runnable operation) {
        try {
            operation.run();
            return 1;
        } catch (Exception e) {
            log.warn("Redis 작업 오류 발생 :: {}: {}", e.getClass().getSimpleName(), e.getMessage());
            return 0;
        }
    }
}
