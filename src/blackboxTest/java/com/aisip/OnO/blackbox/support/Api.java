package com.aisip.OnO.blackbox.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.MissingNode;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.StringJoiner;
import java.util.UUID;

/**
 * 앱에 HTTP 요청을 보내는 클라이언트. 토큰이 있으면 Authorization 헤더에 싣는다.
 *
 * <p>리다이렉트는 따라가지 않는다. 인증 실패나 관리자 로그인이 리다이렉트로 답하는데,
 * 따라가면 최종 페이지의 200 만 보여서 무엇이 일어났는지 알 수 없다.
 */
public class Api {

    public static final ObjectMapper JSON = new ObjectMapper();

    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    private final String baseUrl;
    private final String authorization;
    private final Map<String, String> extraHeaders;

    private Api(String baseUrl, String authorization, Map<String, String> extraHeaders) {
        this.baseUrl = baseUrl;
        this.authorization = authorization;
        this.extraHeaders = extraHeaders;
    }

    public static Api anonymous() {
        return new Api(BlackboxStack.get().appBaseUrl(), null, Map.of());
    }

    /** 앱이 내려준 accessToken 은 이미 "Bearer " 로 시작한다. 그대로 싣는다. */
    public Api withToken(String accessToken) {
        String value = accessToken.startsWith("Bearer ") ? accessToken : "Bearer " + accessToken;
        return new Api(baseUrl, value, extraHeaders);
    }

    public Api withHeader(String name, String value) {
        Map<String, String> headers = new LinkedHashMap<>(extraHeaders);
        headers.put(name, value);
        return new Api(baseUrl, authorization, headers);
    }

    public ApiResponse get(String path) {
        return send("GET", path, null);
    }

    public ApiResponse get(String path, Map<String, ?> query) {
        return send("GET", path + queryString(query), null);
    }

    public ApiResponse post(String path, Object body) {
        return send("POST", path, body);
    }

    public ApiResponse post(String path) {
        return send("POST", path, null);
    }

    public ApiResponse put(String path, Object body) {
        return send("PUT", path, body);
    }

    public ApiResponse patch(String path, Object body) {
        return send("PATCH", path, body);
    }

    public ApiResponse patch(String path) {
        return send("PATCH", path, null);
    }

    public ApiResponse delete(String path, Object body) {
        return send("DELETE", path, body);
    }

    public ApiResponse delete(String path) {
        return send("DELETE", path, null);
    }

    /** 실제 앱이 이미지를 올리는 방식(multipart)으로 파일 하나를 보낸다. */
    public ApiResponse multipart(String method, String path, Map<String, String> fields, String fileField,
                                 String fileName, String contentType, byte[] content) {
        String boundary = "----blackbox" + UUID.randomUUID();
        var out = new java.io.ByteArrayOutputStream();
        try {
            for (var field : fields.entrySet()) {
                out.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"" + field.getKey()
                        + "\"\r\n\r\n" + field.getValue() + "\r\n").getBytes(StandardCharsets.UTF_8));
            }
            if (fileField != null) {
                out.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"" + fileField
                        + "\"; filename=\"" + fileName + "\"\r\nContent-Type: " + contentType + "\r\n\r\n")
                        .getBytes(StandardCharsets.UTF_8));
                out.write(content);
                out.write("\r\n".getBytes(StandardCharsets.UTF_8));
            }
            out.write(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        HttpRequest.Builder builder = request(path)
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .method(method, HttpRequest.BodyPublishers.ofByteArray(out.toByteArray()));
        return execute(method, path, builder);
    }

    /** 폼 로그인처럼 application/x-www-form-urlencoded 로 보낸다. 쿠키가 필요하면 헤더로 넘긴다. */
    public ApiResponse form(String path, Map<String, String> fields) {
        StringJoiner joiner = new StringJoiner("&");
        fields.forEach((k, v) -> joiner.add(encode(k) + "=" + encode(v)));
        HttpRequest.Builder builder = request(path)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(joiner.toString()));
        return execute("POST", path, builder);
    }

    private ApiResponse send(String method, String path, Object body) {
        HttpRequest.Builder builder = request(path);
        if (body == null) {
            builder.method(method, HttpRequest.BodyPublishers.noBody());
        } else {
            String json = body instanceof String raw ? raw : write(body);
            builder.header("Content-Type", "application/json")
                    .method(method, HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8));
        }
        return execute(method, path, builder);
    }

    private HttpRequest.Builder request(String path) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(baseUrl + path))
                .timeout(Duration.ofSeconds(30))
                .header("Accept", "application/json");
        if (authorization != null) {
            builder.header("Authorization", authorization);
        }
        extraHeaders.forEach(builder::header);
        return builder;
    }

    private static ApiResponse execute(String method, String path, HttpRequest.Builder builder) {
        try {
            HttpResponse<String> response = CLIENT.send(builder.build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            return new ApiResponse(method, path, response.statusCode(), response.body(),
                    parse(response.body()), response.headers());
        } catch (IOException e) {
            throw new IllegalStateException(method + " " + path + " 요청 실패", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    static JsonNode parse(String body) {
        if (body == null || body.isBlank()) {
            return MissingNode.getInstance();
        }
        try {
            return JSON.readTree(body);
        } catch (IOException e) {
            return MissingNode.getInstance();
        }
    }

    public static String write(Object value) {
        try {
            return JSON.writeValueAsString(value);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String queryString(Map<String, ?> query) {
        if (query == null || query.isEmpty()) {
            return "";
        }
        StringJoiner joiner = new StringJoiner("&", "?", "");
        query.forEach((k, v) -> {
            if (v instanceof Iterable<?> values) {
                values.forEach(item -> joiner.add(encode(k) + "=" + encode(String.valueOf(item))));
            } else if (v != null) {
                joiner.add(encode(k) + "=" + encode(String.valueOf(v)));
            }
        });
        return joiner.toString();
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
