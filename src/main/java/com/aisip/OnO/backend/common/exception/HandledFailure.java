package com.aisip.OnO.backend.common.exception;

/**
 * 호출한 쪽이 이미 처리하기로 되어 있는 실패에 붙이는 표시.
 *
 * LoggingAspect 는 HTTP 요청 밖(RabbitMQ consumer, 배치 스레드)에서 올라온 예외를
 * "아무도 안 잡은 예외"로 보고 error 로 남기는데, consumer 가 잡아서 정상 종료시키는
 * 예외까지 Sentry 에 fatal 로 올라가 노이즈가 됐다 (Sentry JAVA-SPRING-BOOT-3A).
 * 이 인터페이스를 구현하면 aspect 가 error 로 올리지 않는다.
 */
public interface HandledFailure {
}
