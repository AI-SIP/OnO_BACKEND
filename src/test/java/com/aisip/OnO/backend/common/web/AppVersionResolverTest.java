package com.aisip.OnO.backend.common.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 요청에서 앱 버전을 꺼내는 자리.
 *
 * <p>핵심은 <b>HTTP 요청이 없는 곳에서 불려도 터지지 않는 것</b>이다. 적립은 지금 전부 HTTP 요청 안에서
 * 불리지만, 배치나 비동기 경로가 하나라도 늘면 여기서 예외가 나 그 경로가 통째로 죽는다.
 */
@DisplayName("앱 버전 헤더 읽기")
class AppVersionResolverTest {

    private final AppVersionResolver resolver = new AppVersionResolver();

    @AfterEach
    void clearRequestContext() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    @DisplayName("헤더를 보내면 그 버전을 읽는다")
    void readsHeader() {
        bindRequestWithHeader("4.0.0+70");

        assertThat(resolver.resolve()).contains(new AppVersion(4, 0, 0));
    }

    @Test
    @DisplayName("헤더가 없으면 빈 값이다")
    void emptyWhenHeaderMissing() {
        bindRequestWithHeader(null);

        assertThat(resolver.resolve()).isEmpty();
    }

    @Test
    @DisplayName("읽을 수 없는 헤더도 예외 없이 빈 값이다")
    void emptyWhenHeaderUnparsable() {
        bindRequestWithHeader("abc");

        assertThat(resolver.resolve()).isEmpty();
    }

    @Test
    @DisplayName("HTTP 요청이 없으면 예외 대신 빈 값이다")
    void emptyOutsideHttpRequest() {
        RequestContextHolder.resetRequestAttributes();

        assertThatCode(resolver::resolve).doesNotThrowAnyException();
        assertThat(resolver.resolve()).isEmpty();
    }

    @Test
    @DisplayName("요청 컨텍스트가 물려지지 않는 다른 스레드에서도 터지지 않는다")
    void emptyOnThreadWithoutInheritedContext() throws Exception {
        bindRequestWithHeader("4.0.0+70");

        // @Async 나 큐 소비자처럼 요청 스레드가 아닌 자리를 흉내 낸다.
        // RequestContextHolder 는 기본적으로 자식 스레드에 물려지지 않는다.
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<Boolean> empty = executor.submit(() -> resolver.resolve().isEmpty());

            assertThat(empty.get()).isTrue();
        } finally {
            executor.shutdownNow();
        }
    }

    private void bindRequestWithHeader(String appVersion) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        if (appVersion != null) {
            request.addHeader(AppVersionResolver.APP_VERSION_HEADER, appVersion);
        }
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }
}
