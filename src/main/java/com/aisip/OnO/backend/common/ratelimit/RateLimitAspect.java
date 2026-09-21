package com.aisip.OnO.backend.common.ratelimit;

import com.aisip.OnO.backend.auth.exception.AuthErrorCase;
import com.aisip.OnO.backend.common.exception.ApplicationException;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Aspect
@Component
@RequiredArgsConstructor
public class RateLimitAspect {

    private final RateLimitService rateLimitService;

    @Before("@annotation(rateLimit)")
    public void checkRateLimit(RateLimit rateLimit) {
        Long userId = resolveUserId();
        if (!rateLimitService.tryConsume(rateLimit.key(), userId, rateLimit.limitPerDay())) {
            throw new ApplicationException(rateLimit.scope().getErrorCase());
        }
    }

    /**
     * 인증 정보가 없으면 사용자별 한도를 셀 수 없다.
     * 예전에는 여기서 NPE/ClassCastException 이 나 500 으로 나갔으므로 401 로 명시한다.
     */
    private Long resolveUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof Long userId)) {
            throw new ApplicationException(AuthErrorCase.AUTHENTICATION_FAILED);
        }
        return userId;
    }
}
