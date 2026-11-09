package com.aisip.OnO.blackbox.support;

import com.fasterxml.jackson.databind.JsonNode;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * 앱 대신 외부 서버(OpenAI, 소셜 로그인, Discord) 역할을 하는 WireMock 을 관리 API 로 다룬다.
 * WireMock 클라이언트 라이브러리를 들이지 않으려고 관리 API 를 직접 부른다.
 */
public final class WireMock {

    private static final HttpClient CLIENT = HttpClient.newHttpClient();

    private WireMock() {
    }

    /** urlPath 로 오는 요청에 고정 응답을 등록한다. priority 가 낮을수록 먼저 맞춰 본다. */
    public static String stub(String method, String urlPath, int status, Object responseBody, int priority) {
        Map<String, Object> mapping = Map.of(
                "priority", priority,
                "request", Map.of("method", method, "urlPath", urlPath),
                "response", Map.of(
                        "status", status,
                        "headers", Map.of("Content-Type", "application/json"),
                        "body", responseBody instanceof String s ? s : Api.write(responseBody)));
        return post("/__admin/mappings", mapping).path("id").asText();
    }

    /** 요청 본문이나 헤더 조건까지 붙이고 싶을 때 매핑을 통째로 넘긴다. */
    public static String stubRaw(Map<String, Object> mapping) {
        return post("/__admin/mappings", mapping).path("id").asText();
    }

    public static void remove(String mappingId) {
        send("DELETE", "/__admin/mappings/" + mappingId, null);
    }

    /** urlPath 로 들어온 요청 수. */
    public static int count(String method, String urlPath) {
        return post("/__admin/requests/count", Map.of("method", method, "urlPath", urlPath))
                .path("count").asInt();
    }

    /** urlPath 로 들어온 요청 중 본문에 text 가 들어간 것의 수. */
    public static int countContaining(String method, String urlPath, String text) {
        return post("/__admin/requests/count", Map.of(
                "method", method,
                "urlPath", urlPath,
                "bodyPatterns", java.util.List.of(Map.of("contains", text))))
                .path("count").asInt();
    }

    /** urlPath 로 들어온 요청 본문들. 앱이 외부로 무엇을 보냈는지 확인할 때 쓴다. */
    public static JsonNode requests(String method, String urlPath) {
        return post("/__admin/requests/find", Map.of("method", method, "urlPath", urlPath)).path("requests");
    }

    private static JsonNode post(String path, Object body) {
        return send("POST", path, Api.write(body));
    }

    private static JsonNode send(String method, String path, String body) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(BlackboxStack.get().wiremockBaseUrl() + path))
                .header("Content-Type", "application/json")
                .method(method, body == null
                        ? HttpRequest.BodyPublishers.noBody()
                        : HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
        try {
            HttpResponse<String> response = CLIENT.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 300) {
                throw new IllegalStateException("WireMock " + method + " " + path + " 실패: " + response.body());
            }
            return Api.parse(response.body());
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
