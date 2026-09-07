package com.aisip.OnO.backend.common.exception;

import com.aisip.OnO.backend.common.response.CommonResponse;
import com.aisip.OnO.backend.util.fileupload.exception.FileUploadErrorCase;
import com.aisip.OnO.backend.util.webhook.DiscordWebhookNotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
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
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
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

    /**
     * 요청 본문 검증 실패 처리.
     *
     * <p>이전 시그니처는 첫 인자로 {@link BindingResult} 를 받고 있었다. @ExceptionHandler 는
     * BindingResult 를 인자로 지원하지 않아 핸들러 자체가 해석되지 못했고, 그 결과 모든
     * @Valid 검증 실패가 400 이 아니라 마지막 Exception 핸들러로 떨어져 500 으로 나갔다.
     * 검증 실패는 클라이언트 입력 오류이므로 400 이어야 한다.
     */
    @ExceptionHandler(value = MethodArgumentNotValidException.class)
    public ResponseEntity<CommonResponse> handleValidException(MethodArgumentNotValidException ex,
                                                               WebRequest request) {
        BindingResult bindingResult = ex.getBindingResult();
        String message = bindingResult.getAllErrors().isEmpty()
                ? "잘못된 요청입니다."
                : bindingResult.getAllErrors().get(0).getDefaultMessage();
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
     * 업로드 용량 초과는 사용자가 큰 사진을 고른 것뿐이지 서버 결함이 아니다.
     *
     * <p>{@link MaxUploadSizeExceededException} 은 {@code ErrorResponse} 가 아니라서
     * 마지막 Exception 핸들러로 떨어졌고, 그 결과 500 응답 + Discord 에러 알림까지 나갔다.
     * 이미 정의돼 있던 {@link FileUploadErrorCase#FILE_SIZE_EXCEEDED}(400) 로 되돌린다.
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<CommonResponse> handleMaxUploadSizeExceededException(MaxUploadSizeExceededException ex,
                                                                              WebRequest request) {
        return handleFileUploadErrorCase(ex, FileUploadErrorCase.FILE_SIZE_EXCEEDED);
    }

    /**
     * 멀티파트 본문 자체가 깨진 요청도 클라이언트 오류다. 여기서 500 이 되면
     * 앱의 잘못된 업로드 재시도가 그대로 Discord 알림 폭주로 이어진다.
     */
    @ExceptionHandler(MultipartException.class)
    public ResponseEntity<CommonResponse> handleMultipartException(MultipartException ex, WebRequest request) {
        return handleFileUploadErrorCase(ex, FileUploadErrorCase.FILE_UPLOAD_FAILED);
    }

    private ResponseEntity<CommonResponse> handleFileUploadErrorCase(Exception ex, FileUploadErrorCase errorCase) {
        HttpStatusCode status = HttpStatusCode.valueOf(errorCase.getHttpStatusCode());

        putErrorMdc(errorCase.getErrorCode(), ex);
        logByStatus(status, "Multipart exception handled", ex.getMessage(), ex);

        return ResponseEntity
                .status(status)
                .body(CommonResponse.error(errorCase));
    }

    /**
     * DB 제약 조건 위반은 서버 결함이 아니라 클라이언트 입력 문제다.
     *
     * <p>핸들러가 없던 시절에는 태그 중복 생성({@code Duplicate entry ... for key 'tag.idx_tag_user_normalized'})과
     * 컬럼 길이 초과({@code Data truncation: Data too long for column})가 전부 500으로 나가면서
     * Discord 에러 알림까지 발송됐다. 원인별로 4xx로 분류해 응답한다.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<CommonResponse> handleDataIntegrityViolationException(DataIntegrityViolationException ex,
                                                                               WebRequest request) {
        HttpStatus status = resolveDataIntegrityStatus(ex);
        String message = resolveDataIntegrityMessage(ex, status);
        CommonResponse commonResponse = CommonResponse.error(status.value(), message);

        putErrorMdc(status.value(), ex);
        logByStatus(status, "Data integrity exception handled", rootCauseMessage(ex), ex);

        return ResponseEntity
                .status(status)
                .body(commonResponse);
    }

    private HttpStatus resolveDataIntegrityStatus(DataIntegrityViolationException ex) {
        if (ex instanceof DuplicateKeyException || isDuplicateEntry(ex)) {
            return HttpStatus.CONFLICT;
        }
        return HttpStatus.BAD_REQUEST;
    }

    private String resolveDataIntegrityMessage(DataIntegrityViolationException ex, HttpStatus status) {
        if (status == HttpStatus.CONFLICT) {
            return "이미 존재하는 데이터입니다.";
        }

        String cause = rootCauseMessage(ex).toLowerCase();
        if (cause.contains("data too long") || cause.contains("data truncation")) {
            return "입력값이 허용된 길이를 초과했습니다.";
        }
        if (cause.contains("cannot be null") || cause.contains("not-null")) {
            return "필수 입력값이 누락되었습니다.";
        }
        return "요청 데이터가 저장 조건을 만족하지 않습니다.";
    }

    private boolean isDuplicateEntry(DataIntegrityViolationException ex) {
        String cause = rootCauseMessage(ex).toLowerCase();
        return cause.contains("duplicate entry") || cause.contains("unique constraint");
    }

    private String rootCauseMessage(DataIntegrityViolationException ex) {
        Throwable cause = ex.getMostSpecificCause();
        String message = cause == null ? ex.getMessage() : cause.getMessage();
        return message == null ? "" : message;
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
