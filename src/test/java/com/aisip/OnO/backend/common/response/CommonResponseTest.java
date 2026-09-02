package com.aisip.OnO.backend.common.response;

import com.aisip.OnO.backend.util.fcm.exception.FcmErrorCase;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 앱과 맞춰 둔 응답 봉투(envelope) 계약.
 *
 * <p>앱은 {@code errorCode} 존재 여부로 성공/실패를 가르고 {@code data} 를 읽는다.
 * 필드가 하나라도 더 나가거나 빠지면 파싱이 깨지므로 직렬화 결과 자체를 고정한다.
 */
@DisplayName("공통 응답 포맷")
class CommonResponseTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Nested
    @DisplayName("성공 응답")
    class SuccessResponse {

        @Test
        @DisplayName("data 만 담기고 errorCode/message 는 직렬화되지 않는다")
        void serializesOnlyData() throws Exception {
            String json = objectMapper.writeValueAsString(CommonResponse.success(Map.of("id", 1)));

            Map<String, Object> parsed = objectMapper.readValue(json, new TypeReference<>() {});
            assertThat(parsed)
                    .as("null 필드는 응답에서 빠져야 한다")
                    .containsOnlyKeys("data");
        }

        @Test
        @DisplayName("데이터 없는 성공은 message=success 만 담는다")
        void serializesMessageOnlyWhenNoData() throws Exception {
            String json = objectMapper.writeValueAsString(CommonResponse.success());

            Map<String, Object> parsed = objectMapper.readValue(json, new TypeReference<>() {});
            assertThat(parsed).containsExactly(Map.entry("message", "success"));
        }

        @Test
        @DisplayName("리스트 데이터도 그대로 실린다")
        void keepsListData() {
            CommonResponse<List<String>> response = CommonResponse.success(List.of("a", "b"));

            assertThat(response.getData()).containsExactly("a", "b");
            assertThat(response.getErrorCode()).isNull();
        }

        @Test
        @DisplayName("data 가 null 이어도 예외 없이 빈 응답이 된다")
        void allowsNullData() throws Exception {
            String json = objectMapper.writeValueAsString(CommonResponse.success(null));

            assertThat(json).isEqualTo("{}");
        }
    }

    @Nested
    @DisplayName("에러 응답")
    class ErrorResponse {

        @Test
        @DisplayName("ErrorCase 의 errorCode/message 를 그대로 옮긴다")
        void mapsErrorCase() throws Exception {
            String json = objectMapper.writeValueAsString(CommonResponse.error(FcmErrorCase.FCM_TOKEN_NOT_FOUND));

            Map<String, Object> parsed = objectMapper.readValue(json, new TypeReference<>() {});
            assertThat(parsed).containsOnlyKeys("errorCode", "message");
            assertThat(parsed.get("errorCode")).isEqualTo(FcmErrorCase.FCM_TOKEN_NOT_FOUND.getErrorCode());
            assertThat(parsed.get("message")).isEqualTo(FcmErrorCase.FCM_TOKEN_NOT_FOUND.getMessage());
        }

        @Test
        @DisplayName("직접 지정한 코드/메시지도 같은 형태로 나간다")
        void mapsRawCodeAndMessage() {
            CommonResponse<Object> response = CommonResponse.error(400, "잘못된 요청입니다.");

            assertThat(response.getErrorCode()).isEqualTo(400);
            assertThat(response.getMessage()).isEqualTo("잘못된 요청입니다.");
            assertThat(response.getData()).isNull();
        }
    }

    @Nested
    @DisplayName("커서 페이지 응답")
    class CursorPage {

        @Test
        @DisplayName("다음 페이지가 있으면 nextCursor 와 hasNext 를 함께 내려준다")
        void exposesNextCursor() {
            CursorPageResponse<String> response = CursorPageResponse.of(List.of("a", "b"), 12L, true, 2);

            assertThat(response.content()).containsExactly("a", "b");
            assertThat(response.nextCursor()).isEqualTo(12L);
            assertThat(response.hasNext()).isTrue();
            assertThat(response.size()).isEqualTo(2);
        }

        @Test
        @DisplayName("마지막 페이지는 nextCursor 가 null 이고 hasNext 가 false 다")
        void marksLastPage() {
            CursorPageResponse<String> response = CursorPageResponse.last(List.of("a"), 20);

            assertThat(response.nextCursor()).isNull();
            assertThat(response.hasNext()).isFalse();
            assertThat(response.size()).isEqualTo(20);
        }

        @Test
        @DisplayName("빈 결과도 nextCursor null 로 안전하게 직렬화된다")
        void serializesEmptyPage() throws Exception {
            String json = objectMapper.writeValueAsString(CursorPageResponse.last(List.of(), 10));

            Map<String, Object> parsed = objectMapper.readValue(json, new TypeReference<>() {});
            assertThat(parsed).containsOnlyKeys("content", "nextCursor", "hasNext", "size");
            assertThat(parsed.get("nextCursor")).isNull();
            assertThat(parsed.get("hasNext")).isEqualTo(false);
        }
    }
}
