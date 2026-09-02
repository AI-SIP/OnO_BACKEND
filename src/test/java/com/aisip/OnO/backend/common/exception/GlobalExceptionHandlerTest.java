package com.aisip.OnO.backend.common.exception;

import com.aisip.OnO.backend.util.fcm.exception.FcmErrorCase;
import com.aisip.OnO.backend.util.fileupload.exception.FileUploadErrorCase;
import com.aisip.OnO.backend.util.webhook.DiscordWebhookNotificationService;
import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.MDC;
import org.springframework.core.MethodParameter;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindException;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.sql.SQLIntegrityConstraintViolationException;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 전역 예외 핸들러 계약 테스트.
 *
 * <p>스프링 컨텍스트 없이 standalone MockMvc 로 {@link GlobalExceptionHandler} 만 붙여
 * "어떤 예외가 어떤 상태코드/바디/알림으로 나가는가"를 직접 검증한다.
 *
 * <p>프로덕션에서 여기로 뭉개져 500 이 되던 케이스(스캐너 404, DB 제약 위반)를 함께 고정한다.
 */
@DisplayName("전역 예외 핸들러")
class GlobalExceptionHandlerTest {

    private DiscordWebhookNotificationService discordWebhookNotificationService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        MDC.clear();
        discordWebhookNotificationService = mock(DiscordWebhookNotificationService.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new ExceptionThrowingController())
                .setControllerAdvice(new GlobalExceptionHandler(discordWebhookNotificationService))
                .build();
    }

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    @Nested
    @DisplayName("ApplicationException")
    class ApplicationExceptionHandling {

        @Test
        @DisplayName("ErrorCase 의 httpStatus/errorCode/message 가 응답에 그대로 실린다")
        void writesErrorCaseFieldsIntoResponse() throws Exception {
            mockMvc.perform(get("/test/application-exception/client"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.errorCode").value(FcmErrorCase.FCM_TOKEN_NOT_FOUND.getErrorCode()))
                    .andExpect(jsonPath("$.message").value(FcmErrorCase.FCM_TOKEN_NOT_FOUND.getMessage()))
                    .andExpect(jsonPath("$.data").doesNotExist());
        }

        @Test
        @DisplayName("errorCode 는 HTTP 상태코드가 아니라 도메인 에러코드다")
        void errorCodeIsDomainCodeNotHttpStatus() throws Exception {
            mockMvc.perform(get("/test/application-exception/upload"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value(2003))
                    .andExpect(jsonPath("$.message").value(FileUploadErrorCase.INVALID_IMAGE_FILE.getMessage()));
        }

        @Test
        @DisplayName("4xx ErrorCase 는 Discord 알림을 보내지 않는다")
        void doesNotNotifyDiscordForClientErrorCase() throws Exception {
            mockMvc.perform(get("/test/application-exception/client"));

            verifyNoInteractions(discordWebhookNotificationService);
        }

        @Test
        @DisplayName("5xx ErrorCase 는 Discord 알림을 보낸다")
        void notifiesDiscordForServerErrorCase() throws Exception {
            mockMvc.perform(get("/test/application-exception/server"))
                    .andExpect(status().isBadGateway())
                    .andExpect(jsonPath("$.errorCode").value(FcmErrorCase.FCM_SEND_FAILED.getErrorCode()));

            verify(discordWebhookNotificationService).sendErrorNotification(
                    anyString(), anyString(), anyString(), anyString());
        }

        @Test
        @DisplayName("MDC 에 errorCode 와 exceptionType 을 남긴다")
        void putsErrorCodeAndExceptionTypeIntoMdc() throws Exception {
            mockMvc.perform(get("/test/application-exception/client"));

            assertThat(MDC.get("errorCode"))
                    .as("로그 집계는 MDC errorCode 로 이슈를 묶는다")
                    .isEqualTo(String.valueOf(FcmErrorCase.FCM_TOKEN_NOT_FOUND.getErrorCode()));
            assertThat(MDC.get("exceptionType")).isEqualTo("ApplicationException");
        }
    }

    @Nested
    @DisplayName("클라이언트 입력 오류는 400 으로 나간다")
    class BadRequestHandling {

        @Test
        @DisplayName("MethodArgumentNotValidException - 첫 번째 검증 메시지를 그대로 내려준다")
        void returnsFirstValidationMessage() throws Exception {
            mockMvc.perform(get("/test/method-argument-not-valid"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value(400))
                    .andExpect(jsonPath("$.message").value("이름은 필수입니다."));

            verifyNoInteractions(discordWebhookNotificationService);
        }

        @Test
        @DisplayName("HttpMessageNotReadableException - 깨진 JSON 본문")
        void handlesUnreadableBody() throws Exception {
            mockMvc.perform(post("/test/body")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\":"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value(400))
                    .andExpect(jsonPath("$.message").value("잘못된 요청입니다."));
        }

        @Test
        @DisplayName("MethodArgumentTypeMismatchException - 숫자 자리에 문자열")
        void handlesTypeMismatch() throws Exception {
            mockMvc.perform(get("/test/type-mismatch/not-a-number"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("잘못된 요청입니다."));
        }

        @Test
        @DisplayName("MissingServletRequestParameterException - 필수 쿼리 파라미터 누락")
        void handlesMissingRequestParameter() throws Exception {
            mockMvc.perform(get("/test/required-param"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value(400));
        }

        @Test
        @DisplayName("MissingRequestHeaderException - 필수 헤더 누락")
        void handlesMissingRequestHeader() throws Exception {
            mockMvc.perform(get("/test/required-header"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value(400));
        }

        @Test
        @DisplayName("BindException")
        void handlesBindException() throws Exception {
            mockMvc.perform(get("/test/bind-exception"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value(400));
        }

        @Test
        @DisplayName("ConstraintViolationException")
        void handlesConstraintViolationException() throws Exception {
            mockMvc.perform(get("/test/constraint-violation"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value(400));
        }

        @Test
        @DisplayName("400 계열은 어떤 경우에도 Discord 알림을 보내지 않는다")
        void neverNotifiesDiscordForBadRequests() throws Exception {
            mockMvc.perform(get("/test/bind-exception"));
            mockMvc.perform(get("/test/constraint-violation"));
            mockMvc.perform(get("/test/required-param"));
            mockMvc.perform(get("/test/required-header"));

            verifyNoInteractions(discordWebhookNotificationService);
        }
    }

    @Nested
    @DisplayName("존재하지 않는 리소스 요청(스캐너 트래픽)")
    class NoResourceFoundHandling {

        @Test
        @DisplayName("NoResourceFoundException 은 404 로 응답한다")
        void returnsNotFound() throws Exception {
            mockMvc.perform(get("/test/no-resource"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.errorCode").value(404))
                    .andExpect(jsonPath("$.message").value("요청한 리소스를 찾을 수 없습니다."));
        }

        @Test
        @DisplayName("/actuator/httptrace 같은 스캐너 요청으로 Discord 알림이 나가면 안 된다")
        void doesNotNotifyDiscordForScannerTraffic() throws Exception {
            mockMvc.perform(get("/test/no-resource"));

            verify(discordWebhookNotificationService, never())
                    .sendErrorNotification(anyString(), any(), anyString(), anyString());
            verifyNoInteractions(discordWebhookNotificationService);
        }

        @Test
        @DisplayName("MDC 에는 404 가 기록된다 - 5xx 로 집계되면 안 된다")
        void recordsNotFoundInMdc() throws Exception {
            mockMvc.perform(get("/test/no-resource"));

            assertThat(MDC.get("errorCode")).isEqualTo("404");
            assertThat(MDC.get("exceptionType")).isEqualTo("NoResourceFoundException");
        }
    }

    @Nested
    @DisplayName("DB 제약 위반은 500 이 아니라 4xx 다")
    class DataIntegrityViolationHandling {

        @Test
        @DisplayName("Duplicate entry 는 409 로 응답한다")
        void returnsConflictForDuplicateEntry() throws Exception {
            mockMvc.perform(get("/test/data-integrity/duplicate"))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.errorCode").value(409))
                    .andExpect(jsonPath("$.message").value("이미 존재하는 데이터입니다."));
        }

        @Test
        @DisplayName("DuplicateKeyException 하위 타입도 409 로 응답한다")
        void returnsConflictForDuplicateKeyException() throws Exception {
            mockMvc.perform(get("/test/data-integrity/duplicate-key"))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.message").value("이미 존재하는 데이터입니다."));
        }

        @Test
        @DisplayName("Data too long 은 400 + 길이 초과 안내로 응답한다")
        void returnsBadRequestForDataTruncation() throws Exception {
            mockMvc.perform(get("/test/data-integrity/too-long"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value(400))
                    .andExpect(jsonPath("$.message").value("입력값이 허용된 길이를 초과했습니다."));
        }

        @Test
        @DisplayName("NOT NULL 위반은 400 + 필수값 누락 안내로 응답한다")
        void returnsBadRequestForNullViolation() throws Exception {
            mockMvc.perform(get("/test/data-integrity/null-column"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("필수 입력값이 누락되었습니다."));
        }

        @Test
        @DisplayName("분류되지 않은 제약 위반도 기본 400 으로 떨어진다")
        void returnsBadRequestForUnclassifiedViolation() throws Exception {
            mockMvc.perform(get("/test/data-integrity/unknown"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("요청 데이터가 저장 조건을 만족하지 않습니다."));
        }

        @Test
        @DisplayName("제약 위반으로는 Discord 알림이 나가지 않는다")
        void doesNotNotifyDiscord() throws Exception {
            mockMvc.perform(get("/test/data-integrity/duplicate"));
            mockMvc.perform(get("/test/data-integrity/too-long"));

            verifyNoInteractions(discordWebhookNotificationService);
        }

        @Test
        @DisplayName("MDC 에 4xx 상태코드와 예외 타입을 남긴다")
        void putsClientErrorIntoMdc() throws Exception {
            mockMvc.perform(get("/test/data-integrity/duplicate"));

            assertThat(MDC.get("errorCode")).isEqualTo("409");
            assertThat(MDC.get("exceptionType")).isEqualTo("DataIntegrityViolationException");
        }
    }

    @Nested
    @DisplayName("그 외 예외")
    class FallbackHandling {

        @Test
        @DisplayName("미처리 예외는 500 과 고정 메시지로 응답한다 - 내부 정보를 노출하지 않는다")
        void returnsInternalServerErrorWithoutLeakingDetails() throws Exception {
            mockMvc.perform(get("/test/unexpected"))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.errorCode").value(500))
                    .andExpect(jsonPath("$.message").value("서버 내부 오류가 발생했습니다."));
        }

        @Test
        @DisplayName("500 이면 요청 경로/상태/예외타입을 담아 Discord 로 알린다")
        void notifiesDiscordWithRequestContext() throws Exception {
            mockMvc.perform(get("/test/unexpected"));

            ArgumentCaptor<String> path = ArgumentCaptor.forClass(String.class);
            ArgumentCaptor<String> message = ArgumentCaptor.forClass(String.class);
            ArgumentCaptor<String> status = ArgumentCaptor.forClass(String.class);
            ArgumentCaptor<String> exceptionType = ArgumentCaptor.forClass(String.class);

            verify(discordWebhookNotificationService).sendErrorNotification(
                    path.capture(), message.capture(), status.capture(), exceptionType.capture());

            assertThat(path.getValue()).isEqualTo("/test/unexpected");
            assertThat(message.getValue()).isEqualTo("예상치 못한 오류");
            assertThat(status.getValue()).contains(String.valueOf(HttpStatus.INTERNAL_SERVER_ERROR.value()));
            assertThat(exceptionType.getValue()).isEqualTo("IllegalStateException");
        }

        @Test
        @DisplayName("ErrorResponseException 은 자신의 상태코드를 유지한다")
        void keepsErrorResponseExceptionStatus() throws Exception {
            mockMvc.perform(get("/test/error-response"))
                    .andExpect(status().isMethodNotAllowed())
                    .andExpect(jsonPath("$.errorCode").value(405));

            verifyNoInteractions(discordWebhookNotificationService);
        }
    }

    @RestController
    @RequestMapping("/test")
    static class ExceptionThrowingController {

        @GetMapping("/application-exception/client")
        String applicationExceptionClient() {
            throw new ApplicationException(FcmErrorCase.FCM_TOKEN_NOT_FOUND);
        }

        @GetMapping("/application-exception/upload")
        String applicationExceptionUpload() {
            throw new ApplicationException(FileUploadErrorCase.INVALID_IMAGE_FILE);
        }

        @GetMapping("/application-exception/server")
        String applicationExceptionServer() {
            throw new ApplicationException(FcmErrorCase.FCM_SEND_FAILED);
        }

        @GetMapping("/method-argument-not-valid")
        String methodArgumentNotValid() throws Exception {
            BeanPropertyBindingResult bindingResult =
                    new BeanPropertyBindingResult(new SampleRequest(""), "sampleRequest");
            bindingResult.rejectValue("name", "NotBlank", "이름은 필수입니다.");

            MethodParameter parameter = new MethodParameter(
                    ExceptionThrowingController.class.getDeclaredMethod("body", SampleRequest.class), 0);
            throw new MethodArgumentNotValidException(parameter, bindingResult);
        }

        @PostMapping("/body")
        String body(@RequestBody SampleRequest request) {
            return request.name();
        }

        @GetMapping("/type-mismatch/{id}")
        String typeMismatch(@PathVariable("id") Long id) {
            return String.valueOf(id);
        }

        @GetMapping("/required-param")
        String requiredParam(@RequestParam("size") int size) {
            return String.valueOf(size);
        }

        @GetMapping("/required-header")
        String requiredHeader(@RequestHeader("X-Client-Version") String version) {
            return version;
        }

        @GetMapping("/bind-exception")
        String bindException() throws BindException {
            throw new BindException(new SampleRequest("x"), "sampleRequest");
        }

        @GetMapping("/constraint-violation")
        String constraintViolation() {
            throw new ConstraintViolationException("name must not be blank", Set.of());
        }

        @GetMapping("/no-resource")
        String noResource() throws NoResourceFoundException {
            throw new NoResourceFoundException(HttpMethod.GET, "/actuator/httptrace");
        }

        @GetMapping("/error-response")
        String errorResponse() {
            throw new ErrorResponseException(HttpStatus.METHOD_NOT_ALLOWED);
        }

        @GetMapping("/data-integrity/duplicate")
        String duplicateEntry() {
            throw new DataIntegrityViolationException(
                    "could not execute statement",
                    new SQLIntegrityConstraintViolationException(
                            "Duplicate entry '477-발상 부족' for key 'tag.idx_tag_user_normalized'"));
        }

        @GetMapping("/data-integrity/duplicate-key")
        String duplicateKey() {
            throw new DuplicateKeyException("unique index or primary key violation");
        }

        @GetMapping("/data-integrity/too-long")
        String dataTooLong() {
            throw new DataIntegrityViolationException(
                    "could not execute statement",
                    new java.sql.SQLException("Data truncation: Data too long for column 'memo' at row 1"));
        }

        @GetMapping("/data-integrity/null-column")
        String nullColumn() {
            throw new DataIntegrityViolationException(
                    "could not execute statement",
                    new java.sql.SQLException("Column 'user_id' cannot be null"));
        }

        @GetMapping("/data-integrity/unknown")
        String unknownViolation() {
            throw new DataIntegrityViolationException("알 수 없는 제약 위반");
        }

        @GetMapping("/unexpected")
        String unexpected() {
            throw new IllegalStateException("예상치 못한 오류");
        }
    }

    record SampleRequest(String name) {
    }
}
