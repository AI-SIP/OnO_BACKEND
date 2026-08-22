package com.aisip.OnO.backend.common.exception;

import com.aisip.OnO.backend.common.response.CommonResponse;
import com.aisip.OnO.backend.util.webhook.DiscordWebhookNotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.MDC;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.BindingResult;
import org.springframework.web.ErrorResponse;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@Slf4j
@ControllerAdvice
@RequiredArgsConstructor
public class GlobalExceptionHandler {

    private final DiscordWebhookNotificationService discordWebhookNotificationService;

    @ExceptionHandler(ApplicationException.class)
    public ResponseEntity<CommonResponse> handleApplicationException(ApplicationException e, WebRequest request) {
        CommonResponse commonResponse = CommonResponse.error(e.getErrorCase());

        HttpStatusCode status = HttpStatusCode.valueOf(e.getErrorCase().getHttpStatusCode());
        putErrorMdc(e.getErrorCase().getErrorCode(), e);
        logByStatus(status, "Application exception handled", e.getMessage(), e);
        notifyIfServerError(e, request, status);

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
        logByStatus(HttpStatus.BAD_REQUEST, "Validation exception handled", message, ex);

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(commonResponse);
    }

    @ExceptionHandler(ErrorResponseException.class)
    public ResponseEntity<CommonResponse> handleErrorResponseException(ErrorResponseException ex, WebRequest request) {
        return handleSpringStatusException(ex, request, ex.getStatusCode(), resolveErrorMessage(ex));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<CommonResponse> handleNoResourceFoundException(NoResourceFoundException ex, WebRequest request) {
        return handleSpringStatusException(ex, request, ex.getStatusCode(), "요청한 리소스를 찾을 수 없습니다.");
    }

    @ExceptionHandler({
            HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class,
            MissingRequestHeaderException.class,
            BindException.class,
            ConstraintViolationException.class
    })
    public ResponseEntity<CommonResponse> handleBadRequestException(Exception ex, WebRequest request) {
        return handleSpringStatusException(ex, request, HttpStatus.BAD_REQUEST, "잘못된 요청입니다.");
    }

    /**
     * 업로드 용량 초과(413)는 4xx 라서 logByStatus 를 타면 log.warn 이 되고,
     * 그러면 Sentry Logback appender(ERROR 이상)에 걸리지 않아 조용히 묻힌다.
     * 사용자가 파일을 못 올리고 있다는 신호라 반드시 보여야 해서 error 로 올린다.
     * (spring.servlet.multipart.max-file-size = 50MB)
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<CommonResponse> handleMaxUploadSizeExceededException(MaxUploadSizeExceededException ex,
                                                                              WebRequest request) {
        HttpStatus status = HttpStatus.PAYLOAD_TOO_LARGE;
        String message = "업로드 가능한 파일 크기를 초과했습니다.";
        CommonResponse commonResponse = CommonResponse.error(status.value(), message);

        putErrorMdc(status.value(), ex);
        log.error("Upload size exceeded - status: {}, exceptionType: {}, detail: {}",
                status.value(),
                ex.getClass().getSimpleName(),
                ex.getMessage(),
                ex);

        return ResponseEntity
                .status(status)
                .body(commonResponse);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<CommonResponse> handleException(Exception ex, WebRequest request) {
        if (ex instanceof ErrorResponse errorResponse) {
            return handleSpringStatusException(ex, request, errorResponse.getStatusCode(), resolveErrorMessage(errorResponse));
        }

        CommonResponse commonResponse = CommonResponse.error(500, "서버 내부 오류가 발생했습니다.");

        putErrorMdc(500, ex);
        logByStatus(HttpStatus.INTERNAL_SERVER_ERROR, "Unhandled exception handled", ex.getMessage(), ex);
        notifyIfServerError(ex, request, HttpStatus.INTERNAL_SERVER_ERROR);

        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(commonResponse);
    }

    private ResponseEntity<CommonResponse> handleSpringStatusException(Exception ex,
                                                                      WebRequest request,
                                                                      HttpStatusCode status,
                                                                      String message) {
        CommonResponse commonResponse = CommonResponse.error(status.value(), message);

        putErrorMdc(status.value(), ex);
        logByStatus(status, "Spring MVC exception handled", ex.getMessage(), ex);
        notifyIfServerError(ex, request, status);

        return ResponseEntity
                .status(status)
                .body(commonResponse);
    }

    private void notifyIfServerError(Exception ex, WebRequest request, HttpStatusCode status) {
        if (!status.is5xxServerError()) {
            return;
        }
        sendToDiscord(ex, request, status);
    }

    private void sendToDiscord(Exception ex, WebRequest request, HttpStatusCode status) {
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

    private void logByStatus(HttpStatusCode status, String event, String detail, Exception ex) {
        String exceptionType = ex.getClass().getSimpleName();
        if (status.is5xxServerError()) {
            log.error("{} - status: {}, exceptionType: {}, detail: {}",
                    event,
                    status.value(),
                    exceptionType,
                    detail,
                    ex);
            return;
        }
        log.warn("{} - status: {}, exceptionType: {}, detail: {}",
                event,
                status.value(),
                exceptionType,
                detail);
    }

    private String resolveErrorMessage(ErrorResponseException ex) {
        return resolveErrorMessage((ErrorResponse) ex);
    }

    private String resolveErrorMessage(ErrorResponse errorResponse) {
        if (errorResponse.getBody() == null) {
            return "잘못된 요청입니다.";
        }

        String detail = errorResponse.getBody().getDetail();
        if (detail == null || detail.isBlank()) {
            return "잘못된 요청입니다.";
        }
        return detail;
    }
}
