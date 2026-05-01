package com.aisip.OnO.backend.common.aop;

import com.aisip.OnO.backend.common.exception.ApplicationException;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterThrowing;
import org.aspectj.lang.annotation.Aspect;
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

        log.error("Unhandled service exception - method: {}", joinPoint.getSignature().toShortString(), ex);
    }
}
