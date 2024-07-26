package com.aisip.OnO.backend.Auth;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.jackson2.JacksonFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Collections;

@Component
public class GoogleTokenVerifier {

    /*
    @Value("${spring.security.oauth2.client.registration.google.client-id}")
    private String clientId; // static 제거
     */

    @Value("${spring.security.oauth2.client.registration.google.client-id.android}")
    private String androidClientId;

    @Value("${spring.security.oauth2.client.registration.google.client-id.ios}")
    private String iosClientId;

    public GoogleIdToken.Payload verifyToken(String idTokenString, String platform) throws GeneralSecurityException, IOException {
        String clientId = getClientIdByPlatform(platform);

        GoogleIdTokenVerifier verifier = new GoogleIdTokenVerifier.Builder(new NetHttpTransport(), new JacksonFactory())
                .setAudience(Collections.singletonList(clientId))
                .build();

        GoogleIdToken idToken = verifier.verify(idTokenString);
        if (idToken != null) {
            return idToken.getPayload();
        } else {
            throw new GeneralSecurityException("Invalid ID token.");
        }
    }

    private String getClientIdByPlatform(String platform) {
        switch (platform.toLowerCase()) {
            case "android":
                return androidClientId;
            case "ios":
                return iosClientId;
            default:
                throw new IllegalArgumentException("Invalid platform specified");
        }
    }
}