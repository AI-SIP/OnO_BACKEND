package com.aisip.OnO.backend.util.fcm.controller;

import com.aisip.OnO.backend.auth.exception.AuthErrorCase;
import com.aisip.OnO.backend.support.IntegrationTestSupport;
import com.aisip.OnO.backend.user.entity.User;
import com.aisip.OnO.backend.util.fcm.dto.FcmTokenRequestDto;
import com.aisip.OnO.backend.util.fcm.dto.NotificationRequestDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * FCM 토큰 등록 API.
 *
 * <p>여기는 실사용자 푸시의 입구다. 등록되는 토큰이 누구 것으로 저장되는지가 잘못되면
 * 남의 기기로 알림이 나간다. 그래서 "요청 본문이 아니라 인증 주체로만 소유자가 정해지는가"를
 * 가장 먼저 고정한다.
 *
 * <p>{@code FcmService} 는 {@link IntegrationTestSupport} 에서 목이므로 Firebase 로 실제 발송이
 * 나갈 수 없다. 각 테스트는 목에 어떤 인자가 전달됐는지로 컨트롤러 계약을 검증한다.
 */
@DisplayName("FCM 컨트롤러")
class FcmControllerTest extends IntegrationTestSupport {

    private MockHttpServletRequestBuilder registerToken(String body) {
        return post("/api/fcm/token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body);
    }

    private String tokenBody(String token) {
        return token == null
                ? "{\"token\":null}"
                : "{\"token\":\"" + token + "\"}";
    }

    @Nested
    @DisplayName("토큰 등록 - 소유자 결정")
    class TokenOwnership {

        @Test
        @DisplayName("등록된 토큰의 소유자는 인증된 사용자다")
        void registersTokenForAuthenticatedUser() throws Exception {
            User user = fixtures.createUser();
            authenticateAs(user.getId());

            mockMvc.perform(registerToken(tokenBody("device-token-a")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.errorCode").doesNotExist());

            ArgumentCaptor<FcmTokenRequestDto> dto = ArgumentCaptor.forClass(FcmTokenRequestDto.class);
            ArgumentCaptor<Long> ownerId = ArgumentCaptor.forClass(Long.class);
            verify(fcmService).registerToken(dto.capture(), ownerId.capture());

            assertThat(dto.getValue().token()).isEqualTo("device-token-a");
            assertThat(ownerId.getValue())
                    .as("토큰 소유자는 인증 주체여야 한다")
                    .isEqualTo(user.getId());
        }

        @Test
        @DisplayName("본문에 다른 사용자 id 를 넣어도 무시하고 인증 주체로 등록한다")
        void ignoresUserIdInRequestBody() throws Exception {
            User owner = fixtures.createUser();
            User victim = fixtures.createOtherUser();
            authenticateAs(owner.getId());

            mockMvc.perform(registerToken(
                            "{\"token\":\"stolen-device\",\"userId\":" + victim.getId() + "}"))
                    .andExpect(status().isOk());

            ArgumentCaptor<Long> ownerId = ArgumentCaptor.forClass(Long.class);
            verify(fcmService).registerToken(any(), ownerId.capture());

            assertThat(ownerId.getValue())
                    .as("본문으로 남의 기기에 토큰을 붙일 수 있으면 안 된다")
                    .isEqualTo(owner.getId())
                    .isNotEqualTo(victim.getId());
        }

        @Test
        @DisplayName("사용자가 바뀌면 각자의 id 로 따로 등록된다")
        void registersSeparatelyPerUser() throws Exception {
            User first = fixtures.createUser();
            User second = fixtures.createOtherUser();

            authenticateAs(first.getId());
            mockMvc.perform(registerToken(tokenBody("shared-device")))
                    .andExpect(status().isOk());

            authenticateAs(second.getId());
            mockMvc.perform(registerToken(tokenBody("shared-device")))
                    .andExpect(status().isOk());

            ArgumentCaptor<Long> ownerId = ArgumentCaptor.forClass(Long.class);
            verify(fcmService, org.mockito.Mockito.times(2)).registerToken(any(), ownerId.capture());

            assertThat(ownerId.getAllValues())
                    .as("기기를 물려받아도 등록 주체는 각자여야 한다")
                    .containsExactly(first.getId(), second.getId());
        }
    }

    @Nested
    @DisplayName("토큰 등록 - 입력 검증")
    class TokenValidation {

        @Test
        @DisplayName("token 이 null 이면 400 으로 거절하고 서비스까지 가지 않는다")
        void rejectsNullToken() throws Exception {
            User user = fixtures.createUser();
            authenticateAs(user.getId());

            mockMvc.perform(registerToken(tokenBody(null)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value(400))
                    .andExpect(jsonPath("$.message").value("FCM 토큰은 필수입니다."));

            verifyNoInteractions(fcmService);
        }

        @Test
        @DisplayName("token 필드 자체가 없어도 400 이다")
        void rejectsMissingTokenField() throws Exception {
            User user = fixtures.createUser();
            authenticateAs(user.getId());

            mockMvc.perform(registerToken("{}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value(400));

            verifyNoInteractions(fcmService);
        }

        @ParameterizedTest(name = "공백 토큰 \"{0}\"")
        @ValueSource(strings = {"", " ", "   ", "\\t"})
        @DisplayName("빈 문자열/공백 토큰은 400 이다 - 발송 불가능한 행을 만들지 않는다")
        void rejectsBlankToken(String blank) throws Exception {
            User user = fixtures.createUser();
            authenticateAs(user.getId());

            mockMvc.perform(registerToken(tokenBody(blank)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value(400));

            verifyNoInteractions(fcmService);
        }

        @Test
        @DisplayName("255자 토큰은 통과한다 - 컬럼 길이 경계")
        void acceptsTokenAtColumnLengthLimit() throws Exception {
            User user = fixtures.createUser();
            authenticateAs(user.getId());
            String maxLengthToken = "a".repeat(255);

            mockMvc.perform(registerToken(tokenBody(maxLengthToken)))
                    .andExpect(status().isOk());

            ArgumentCaptor<FcmTokenRequestDto> dto = ArgumentCaptor.forClass(FcmTokenRequestDto.class);
            verify(fcmService).registerToken(dto.capture(), anyLong());
            assertThat(dto.getValue().token()).hasSize(255);
        }

        @Test
        @DisplayName("256자 토큰은 500 이 아니라 400 이다 - DB 길이 초과까지 가지 않는다")
        void rejectsTokenLongerThanColumn() throws Exception {
            User user = fixtures.createUser();
            authenticateAs(user.getId());

            mockMvc.perform(registerToken(tokenBody("a".repeat(256))))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value(400))
                    .andExpect(jsonPath("$.message").value("FCM 토큰 길이가 허용 범위를 초과했습니다."));

            verifyNoInteractions(fcmService);
        }

        @Test
        @DisplayName("깨진 JSON 본문은 400 이다")
        void rejectsBrokenJsonBody() throws Exception {
            User user = fixtures.createUser();
            authenticateAs(user.getId());

            mockMvc.perform(registerToken("{\"token\":"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value(400));

            verifyNoInteractions(fcmService);
        }

        @Test
        @DisplayName("입력 검증 실패로는 어떤 푸시도 발생하지 않는다")
        void neverSendsPushOnValidationFailure() throws Exception {
            User user = fixtures.createUser();
            authenticateAs(user.getId());

            mockMvc.perform(registerToken(tokenBody("")));
            mockMvc.perform(registerToken(tokenBody(null)));

            verify(fcmService, never()).sendNotification(any());
            verify(fcmService, never()).sendNotificationToAllUserDevice(anyLong(), any());
        }
    }

    @Nested
    @DisplayName("토큰 등록 - 인증")
    class TokenAuthentication {

        @Test
        @DisplayName("인증 없이 등록하면 401 이고 서비스가 호출되지 않는다")
        void rejectsAnonymousRegistration() throws Exception {
            clearAuthentication();

            mockMvc.perform(registerToken(tokenBody("anonymous-device")))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.errorCode").value(AuthErrorCase.AUTHENTICATION_FAILED.getErrorCode()));

            verifyNoInteractions(fcmService);
        }

        @Test
        @DisplayName("허용되지 않은 권한이면 403 이다")
        void rejectsUnknownRole() throws Exception {
            User user = fixtures.createUser();
            authenticateAs(user.getId(), "ROLE_UNKNOWN");

            mockMvc.perform(registerToken(tokenBody("device-token")))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.errorCode").value(AuthErrorCase.ACCESS_DENIED.getErrorCode()));

            verifyNoInteractions(fcmService);
        }
    }

    @Nested
    @DisplayName("발송 트리거")
    class SendNotification {

        @Test
        @DisplayName("본인 기기로만 큐잉한다 - 요청 스레드에서 Firebase 를 직접 부르지 않는다")
        void queuesNotificationForSelfOnly() throws Exception {
            User user = fixtures.createUser();
            User other = fixtures.createOtherUser();
            authenticateAs(user.getId());

            mockMvc.perform(post("/api/fcm/send"))
                    .andExpect(status().isOk());

            ArgumentCaptor<Long> targetId = ArgumentCaptor.forClass(Long.class);
            ArgumentCaptor<NotificationRequestDto> payload =
                    ArgumentCaptor.forClass(NotificationRequestDto.class);
            verify(fcmService).sendNotificationToAllUserDevice(targetId.capture(), payload.capture());

            assertThat(targetId.getValue())
                    .as("발송 대상은 인증 주체 본인이어야 한다")
                    .isEqualTo(user.getId())
                    .isNotEqualTo(other.getId());
            assertThat(payload.getValue().token())
                    .as("대상 토큰은 서비스가 사용자 기준으로 조회한다")
                    .isNull();
            assertThat(payload.getValue().title()).isNotBlank();

            verify(fcmService, never()).sendNotification(any());
        }

        @Test
        @DisplayName("인증 없이 호출하면 401 이고 큐잉되지 않는다")
        void rejectsAnonymousSend() throws Exception {
            clearAuthentication();

            mockMvc.perform(post("/api/fcm/send"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.errorCode").value(AuthErrorCase.AUTHENTICATION_FAILED.getErrorCode()));

            verifyNoInteractions(fcmService);
        }

        @Test
        @DisplayName("GET 으로는 호출할 수 없다")
        void rejectsWrongHttpMethod() throws Exception {
            User user = fixtures.createUser();
            authenticateAs(user.getId());

            mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                            .get("/api/fcm/send"))
                    .andExpect(status().isMethodNotAllowed());

            verifyNoInteractions(fcmService);
        }
    }
}
