package com.aisip.OnO.backend.common.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.web.servlet.filter.OrderedFormContentFilter;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.converter.support.AllEncompassingFormHttpMessageConverter;
import org.springframework.stereotype.Component;
import org.springframework.util.MultiValueMap;
import org.springframework.web.servlet.HandlerExceptionResolver;
import org.springframework.web.servlet.ModelAndView;

import java.io.IOException;

/**
 * 깨진 form 본문을 400 으로 돌려주는 FormContentFilter. (#400)
 *
 * <p>Spring Boot 기본 FormContentFilter 는 PUT, PATCH, DELETE 의
 * {@code application/x-www-form-urlencoded} 본문을 {@code URLDecoder} 로 푼다.
 * {@code %zz} 처럼 깨진 퍼센트 인코딩이 오면 디코딩 예외(지금 버전은 HttpMessageNotReadableException,
 * 예전 버전은 IllegalArgumentException)가 필터에서 그대로 올라가 {@code @ControllerAdvice} 를 거치지 못하고
 * 500 이 됐다(Sentry JAVA-SPRING-BOOT-75, 스캐너 요청).
 *
 * <p>같은 타입의 빈을 등록하면 Boot 의 기본 필터는 만들어지지 않는다(@ConditionalOnMissingBean).
 * 필터 순서와 파싱 방식은 기본 필터와 같고, 디코딩 실패만 HandlerExceptionResolver 로 넘겨
 * GlobalExceptionHandler 의 공통 400 응답(HttpMessageNotReadableException)으로 처리한다.
 */
@Component
public class MalformedFormRejectingFormContentFilter extends OrderedFormContentFilter {

    private final HandlerExceptionResolver handlerExceptionResolver;

    public MalformedFormRejectingFormContentFilter(
            @Qualifier("handlerExceptionResolver") HandlerExceptionResolver handlerExceptionResolver) {
        this.handlerExceptionResolver = handlerExceptionResolver;
        setFormConverter(new MalformedFormRejectingConverter());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        try {
            super.doFilterInternal(request, response, filterChain);
        } catch (MalformedFormBodyException ex) {
            // 이 예외는 아래 컨버터만 던진다. 뒤쪽 체인에서 난 예외를 잘못 삼키지 않는다.
            ModelAndView resolved = handlerExceptionResolver.resolveException(request, response, null, ex);
            if (resolved == null) {
                throw ex;
            }
        }
    }

    static class MalformedFormRejectingConverter extends AllEncompassingFormHttpMessageConverter {

        @Override
        public MultiValueMap<String, String> read(Class<? extends MultiValueMap<String, ?>> clazz,
                                                  HttpInputMessage inputMessage)
                throws IOException, HttpMessageNotReadableException {
            try {
                return super.read(clazz, inputMessage);
            } catch (HttpMessageNotReadableException | IllegalArgumentException ex) {
                throw new MalformedFormBodyException(ex, inputMessage);
            }
        }
    }

    static class MalformedFormBodyException extends HttpMessageNotReadableException {

        MalformedFormBodyException(Exception cause, HttpInputMessage inputMessage) {
            super("form 본문의 퍼센트 인코딩이 올바르지 않습니다.", cause, inputMessage);
        }
    }
}
