package com.aisip.OnO.blackbox.support;

import com.fasterxml.jackson.databind.JsonNode;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Base64;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 이미지 업로드. 앱이 실제로 쓰는 presigned URL 방식(발급 → S3 에 직접 PUT → fileUrl 을 서버에 등록)을 그대로 따른다.
 */
public final class Images {

    /** 1x1 투명 PNG. 서버가 시그니처(매직 바이트)까지 확인하는 경로가 있어 진짜 PNG 를 쓴다. */
    public static final byte[] PNG = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNkYPhfDwAChwGA60e6kgAAAABJRU5ErkJggg==");

    private static final String S3_URL_MARKER = ".amazonaws.com/";
    private static final HttpClient CLIENT = HttpClient.newHttpClient();

    private Images() {
    }

    /** presigned URL 을 받아 S3(가짜 서버)에 올리고, 서버에 등록할 fileUrl 을 돌려준다. */
    public static String uploadViaPresignedUrl(TestUser user) {
        JsonNode issued = user.api()
                .get("/api/fileUpload/presigned-urls", Map.of("count", 1, "contentType", "image/png"))
                .ok().get(0);
        String presignedUrl = issued.path("presignedUrl").asText();
        String fileUrl = issued.path("fileUrl").asText();
        putToS3(presignedUrl, PNG);
        assertThat(BlackboxStack.get().objectExists(objectKey(fileUrl)))
                .as("presigned URL 로 올린 파일이 S3 에 있어야 합니다: %s", fileUrl)
                .isTrue();
        return fileUrl;
    }

    /** 앱이 내려준 공개 URL(https://bucket.s3.ap-northeast-2.amazonaws.com/key) 에서 key 를 뽑는다. */
    public static String objectKey(String fileUrl) {
        int index = fileUrl.indexOf(S3_URL_MARKER);
        assertThat(index).as("S3 URL 형식이 아닙니다: %s", fileUrl).isPositive();
        return fileUrl.substring(index + S3_URL_MARKER.length());
    }

    /**
     * presigned URL 의 호스트는 컨테이너 네트워크 안의 이름(s3mock:9090)이다. 호스트에서는 그 이름을 풀 수 없어서
     * 매핑 포트로 보내고, Host 헤더는 서명된 값 그대로 둔다.
     */
    public static void putToS3(String presignedUrl, byte[] content) {
        URI signed = URI.create(presignedUrl);
        assertThat(signed.getAuthority()).isEqualTo(BlackboxStack.S3_HOST_IN_NETWORK);
        URI target = URI.create(BlackboxStack.get().s3BaseUrl() + signed.getRawPath()
                + (signed.getRawQuery() == null ? "" : "?" + signed.getRawQuery()));
        HttpRequest request = HttpRequest.newBuilder(target)
                .header("Host", signed.getAuthority())
                .header("Content-Type", "image/png")
                .PUT(HttpRequest.BodyPublishers.ofByteArray(content))
                .build();
        try {
            HttpResponse<String> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            assertThat(response.statusCode()).as("S3 PUT 응답: %s", response.body()).isEqualTo(200);
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
