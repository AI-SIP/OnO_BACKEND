package com.aisip.OnO.backend.common.ratelimit;

import com.aisip.OnO.backend.common.exception.ApplicationException;
import com.aisip.OnO.backend.problem.exception.ProblemErrorCase;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Aspect
@Component
@RequiredArgsConstructor
public class RateLimitAspect {

    private final RateLimitService rateLimitService;

    @Before("@annotation(rateLimit)")
    public void checkRateLimit(RateLimit rateLimit) {
        Long userId = (Long) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (!rateLimitService.tryConsume(rateLimit.key(), userId, rateLimit.limitPerDay())) {
            throw new ApplicationException(ProblemErrorCase.ANALYSIS_RATE_LIMIT_EXCEEDED);
        }
    }
}
