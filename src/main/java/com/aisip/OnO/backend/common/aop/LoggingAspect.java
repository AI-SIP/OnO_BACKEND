package com.aisip.OnO.backend.common.aop;

import com.aisip.OnO.backend.common.exception.ApplicationException;
import com.aisip.OnO.backend.common.exception.HandledFailure;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterThrowing;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

@Aspect
@Component
@Slf4j
class LoggingAspect {

    @AfterThrowing(pointcut = "execution(* com.aisip..service..*(..))",
            throwing = "ex")
    public void logAfterThrowing(JoinPoint joinPoint, Throwable ex) {
        if (ex instanceof ApplicationException) {
            return;
        }

        // 호출한 쪽이 이미 처리하는 실패는 여기서 error 로 올리지 않는다
        if (ex instanceof HandledFailure) {
            log.warn("Handled service failure - method: {}, reason: {}",
                    joinPoint.getSignature().toShortString(),
                    ex.getMessage());
            return;
        }

        if (MDC.get("traceId") != null) {
            log.debug("Service exception propagated to request handler - method: {}, exceptionType: {}",
                    joinPoint.getSignature().toShortString(),
                    ex.getClass().getSimpleName());
            return;
        }

        log.error("Unhandled service exception - method: {}", joinPoint.getSignature().toShortString(), ex);
    }
}
