package com.aisip.OnO.backend.auth.filter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;

@Slf4j
@Component
public class GoogleTokenVerifier {

    @Value("${spring.security.oauth2.client.registration.google.client-id.android}")
    private String androidClientId;

    @Value("${spring.security.oauth2.client.registration.google.client-id.ios}")
    private String iosClientId;

    public JsonNode verifyToken(String accessToken, String platform) throws IOException {
        String clientId = getClientIdByPlatform(platform);

        String tokenInfoUrl = "https://www.googleapis.com/oauth2/v3/tokeninfo?access_token=" + accessToken;
        URL url = new URL(tokenInfoUrl);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");

        int responseCode = connection.getResponseCode();
        if (responseCode == 200) {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode tokenInfo = mapper.readTree(connection.getInputStream());

            if (!tokenInfo.get("aud").asText().equals(clientId)) {
                throw new SecurityException("Invalid client ID");
            }

            log.info("google token verify success");
            return tokenInfo;
        } else {
            throw new IOException("Invalid access token");
        }
    }

    private String getClientIdByPlatform(String platform) {
        return switch (platform.toLowerCase()) {
            case "android" -> androidClientId;
            case "ios" -> iosClientId;
            default -> throw new IllegalArgumentException("Invalid platform specified");
        };
    }
}