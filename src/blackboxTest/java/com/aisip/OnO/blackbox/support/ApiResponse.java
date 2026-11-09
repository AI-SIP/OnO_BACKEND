package com.aisip.OnO.blackbox.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.MissingNode;

import java.net.http.HttpHeaders;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 앱 응답 하나. 모든 API 는 {@code CommonResponse} 로 감싸서 내려온다.
 *
 * <pre>
 * 성공: {"data": ...}  또는 {"message": "success"}
 * 실패: {"errorCode": 4001, "message": "..."}
 * </pre>
 */
public record ApiResponse(String method, String path, int status, String rawBody, JsonNode body, HttpHeaders headers) {

    /** 200 이고 errorCode 가 없는지 확인한 뒤 data 를 돌려준다. */
    public JsonNode ok() {
        return expectStatus(200).data();
    }

    /** 성공 상태 코드(2xx)를 확인한 뒤 data 를 돌려준다. 201 을 쓰는 API 가 있다. */
    public JsonNode success() {
        assertThat(status)
                .as("%s %s 가 성공해야 합니다. 응답: %s", method, path, rawBody)
                .isBetween(200, 299);
        assertThat(body.path("errorCode").isMissingNode() || body.path("errorCode").isNull())
                .as("%s %s 성공 응답에 errorCode 가 없어야 합니다. 응답: %s", method, path, rawBody)
                .isTrue();
        return data();
    }

    public ApiResponse expectStatus(int expected) {
        assertThat(status)
                .as("%s %s 상태 코드. 응답: %s", method, path, rawBody)
                .isEqualTo(expected);
        if (expected >= 200 && expected < 300) {
            assertThat(body.path("errorCode").isMissingNode() || body.path("errorCode").isNull())
                    .as("%s %s 성공 응답에 errorCode 가 없어야 합니다. 응답: %s", method, path, rawBody)
                    .isTrue();
        }
        return this;
    }

    /** 실패 상태 코드와 앱의 errorCode 를 같이 확인한다. */
    public ApiResponse expectError(int expectedStatus, int expectedErrorCode) {
        expectStatus(expectedStatus);
        assertThat(errorCode())
                .as("%s %s errorCode. 응답: %s", method, path, rawBody)
                .isEqualTo(expectedErrorCode);
        return this;
    }

    /** 실패이기만 하면 되는 경우(4xx). 서버 오류(5xx)는 실패가 아니라 결함이므로 막는다. */
    public ApiResponse expectClientError() {
        assertThat(status)
                .as("%s %s 는 4xx 여야 합니다. 응답: %s", method, path, rawBody)
                .isBetween(400, 499);
        return this;
    }

    /**
     * 남의 데이터에 접근했을 때. 도메인마다 403(소유자 불일치) 또는 404(없는 것으로 취급)를 쓰므로
     * 둘 중 하나면서 데이터가 실리지 않았는지를 본다. 어느 쪽인지는 각 시나리오가 따로 고정한다.
     */
    public ApiResponse expectDenied() {
        assertThat(status)
                .as("%s %s 는 남의 데이터라 403 또는 404 여야 합니다. 응답: %s", method, path, rawBody)
                .isIn(403, 404);
        assertThat(data().isMissingNode() || data().isNull())
                .as("%s %s 거절 응답에 데이터가 실리면 안 됩니다. 응답: %s", method, path, rawBody)
                .isTrue();
        return this;
    }

    /** 응답의 Set-Cookie 를 다음 요청의 Cookie 헤더 값으로 바꾼다. 관리자 폼 로그인 세션에 쓴다. */
    public String cookies() {
        return String.join("; ", headers.allValues("set-cookie").stream()
                .map(c -> c.split(";", 2)[0])
                .toList());
    }

    public String location() {
        return headers.firstValue("location").orElse("");
    }

    public Integer errorCode() {
        JsonNode code = body.path("errorCode");
        return code.isMissingNode() || code.isNull() ? null : code.asInt();
    }

    public JsonNode data() {
        return body == null ? MissingNode.getInstance() : body.path("data");
    }
}
