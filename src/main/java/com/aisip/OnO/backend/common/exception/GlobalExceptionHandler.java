package com.aisip.OnO.backend.common.exception;

import com.aisip.OnO.backend.common.response.CommonResponse;
import com.aisip.OnO.backend.util.webhook.DiscordWebhookNotificationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.http.*;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;

@Slf4j
@ControllerAdvice
@RequiredArgsConstructor
public class GlobalExceptionHandler {

    private final ObjectMapper objectMapper;
    private final DiscordWebhookNotificationService discordWebhookNotificationService;

    @ExceptionHandler(ApplicationException.class)
    public ResponseEntity<CommonResponse> handleApplicationException(ApplicationException e, WebRequest request) {
        CommonResponse commonResponse = CommonResponse.error(e.getErrorCase());

        HttpStatus status = HttpStatus.valueOf(e.getErrorCase().getHttpStatusCode());
        putErrorMdc(e.getErrorCase().getErrorCode(), e);
        logByStatus(status, "Application exception handled: {}", e.getMessage(), e);
        sendToDiscord(e, request, status);

        return ResponseEntity
                .status(e.getErrorCase().getHttpStatusCode())
                .body(commonResponse);
    }

    @ExceptionHandler(value = MethodArgumentNotValidException.class)
    public ResponseEntity<CommonResponse> handleValidException(BindingResult bindingResult,
                                                               MethodArgumentNotValidException ex,
                                                               WebRequest request) {
        String message = bindingResult.getAllErrors().get(0).getDefaultMessage();
        CommonResponse commonResponse = CommonResponse.error(400, message);

        putErrorMdc(400, ex);
        log.warn("Validation exception handled: {}", message);
        sendToDiscord(ex, request, HttpStatus.BAD_REQUEST);

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(commonResponse);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<CommonResponse> handleException(Exception ex, WebRequest request) {
        CommonResponse commonResponse = CommonResponse.error(500, "서버 내부 오류가 발생했습니다.");

        putErrorMdc(500, ex);
        log.error("Unhandled exception occurred", ex);
        sendToDiscord(ex, request, HttpStatus.INTERNAL_SERVER_ERROR);

        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(commonResponse);
    }

    private void sendToDiscord(Exception ex, WebRequest request, HttpStatus status) {
        String path = ((ServletWebRequest) request).getRequest().getRequestURI();
        String errorMessage = ex.getMessage();
        String exceptionType = ex.getClass().getSimpleName();

        discordWebhookNotificationService.sendErrorNotification(
                path,
                errorMessage,
                status.toString(),
                exceptionType
        );
    }

    private void putErrorMdc(Integer errorCode, Exception ex) {
        MDC.put("errorCode", String.valueOf(errorCode));
        MDC.put("exceptionType", ex.getClass().getSimpleName());
    }

    private void logByStatus(HttpStatus status, String message, String detail, Exception ex) {
        if (status.is5xxServerError()) {
            log.error(message, detail, ex);
            return;
        }
        log.warn(message, detail);
    }
}
