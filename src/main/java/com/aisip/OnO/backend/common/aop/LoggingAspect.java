package com.aisip.OnO.backend.common.aop;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.After;
import org.aspectj.lang.annotation.AfterThrowing;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Aspect
@Component
@Slf4j
class LoggingAspect {
    private static final Logger logger = LoggerFactory.getLogger(LoggingAspect.class);

    // 예외 발생 시 로깅
    @AfterThrowing(pointcut = "execution(* com.aisip..service..*(..))",
            throwing = "ex")
    public void logAfterThrowing(JoinPoint joinPoint, Throwable ex) {
        String methodName = joinPoint.getSignature().getName();
        String className = joinPoint.getSignature().getDeclaringTypeName();
        String layer = getLayerName(className);
        logger.error("[{}] [Exception] {}.{}(): {}", layer, className, methodName, ex.getMessage());
    }

    // 메서드 실행 이전 로깅
    @Before("execution(* com.aisip..service..*(..))")
    public void logBefore(JoinPoint joinPoint) {
        String methodName = joinPoint.getSignature().getName();
        String className = joinPoint.getSignature().getDeclaringTypeName();
        String layer = getLayerName(className);
        logger.info("[{}] [Executing] {}.{}()", layer, className, methodName);
    }

    // 메서드 실행 이후 로깅
    @After("execution(* com.aisip..service..*(..))")
    public void logAfter(JoinPoint joinPoint) {
        String methodName = joinPoint.getSignature().getName();
        String className = joinPoint.getSignature().getDeclaringTypeName();
        String layer = getLayerName(className);
        logger.info("[{}] [Completed] {}.{}()", layer, className, methodName);
    }

    // 클래스 이름으로부터 계층(layer) 이름 추출
    private String getLayerName(String className) {
        if (className.contains("service")) {
            return "Service";
        }
        return "Unknown";
    }
}