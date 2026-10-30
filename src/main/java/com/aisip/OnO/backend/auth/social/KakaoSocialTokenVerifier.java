package com.aisip.OnO.backend.auth.social;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Set;

/**
 * Kakao 액세스 토큰을 확인한다.
 *
 * <p>Kakao 액세스 토큰은 JWT 가 아니라서 서버가 직접 서명을 볼 수 없다. 대신 Kakao 의
 * 토큰 정보 조회(access_token_info)를 불러 토큰이 살아 있는지, 어느 앱에 발급됐는지(app_id),
 * 누구의 토큰인지(id)를 받는다. app_id 가 우리 앱이 아니면 거절한다.
 */
@Component
public class KakaoSocialTokenVerifier implements SocialTokenVerifier {

    private static final int CONNECT_TIMEOUT_MS = 2_000;
    private static final int READ_TIMEOUT_MS = 3_000;

    private final Set<String> appIds;
    private final String tokenInfoUrl;
    private final RestClient restClient;

    @Autowired
    public KakaoSocialTokenVerifier(
            @Value("${auth.social-verification.kakao.app-ids:}") String appIds,
            @Value("${auth.social-verification.kakao.token-info-url:https://kapi.kakao.com/v1/user/access_token_info}") String tokenInfoUrl
    ) {
        // 이 확인기 전용 클라이언트다. 타임아웃을 짧게 둬서 Kakao 가 느려도 로그인 요청이 오래 붙잡히지 않게 한다.
        this(SocialVerificationSettings.parseList(appIds), tokenInfoUrl,
                RestClient.builder().requestFactory(timeoutRequestFactory()).build());
    }

    KakaoSocialTokenVerifier(Set<String> appIds, String tokenInfoUrl, RestClient restClient) {
        this.appIds = appIds;
        this.tokenInfoUrl = tokenInfoUrl;
        this.restClient = restClient;
    }

    private static SimpleClientHttpRequestFactory timeoutRequestFactory() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(CONNECT_TIMEOUT_MS);
        factory.setReadTimeout(READ_TIMEOUT_MS);
        return factory;
    }

    @Override
    public SocialPlatform platform() {
        return SocialPlatform.KAKAO;
    }

    @Override
    public boolean isConfigured() {
        return !appIds.isEmpty();
    }

    @Override
    public String verify(String token) {
        KakaoTokenInfo info;
        try {
            info = restClient.get()
                    .uri(tokenInfoUrl)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .retrieve()
                    .body(KakaoTokenInfo.class);
        } catch (HttpClientErrorException e) {
            int status = e.getStatusCode().value();
            // 401(만료되거나 잘못된 토큰), 400(형식 오류)만 토큰 탓이다.
            // 429(호출 한도)나 403(앱 설정) 같은 나머지 4xx 는 사용자가 고칠 수 없으니 확인 불가로 본다.
            if (status == 400 || status == 401) {
                throw SocialTokenVerificationException.invalid("kakao access token rejected: " + status, e);
            }
            throw SocialTokenVerificationException.unavailable("kakao token info unavailable: " + status, e);
        } catch (IllegalArgumentException e) {
            // 헤더에 실을 수 없는 문자가 섞인 토큰
            throw SocialTokenVerificationException.invalid("kakao access token is malformed", e);
        } catch (RestClientException e) {
            // 5xx, 타임아웃, 연결 실패
            throw SocialTokenVerificationException.unavailable("kakao token info unavailable", e);
        }

        if (info == null || info.id() == null) {
            throw SocialTokenVerificationException.invalid("kakao token info has no user id");
        }
        if (info.appId() == null || !appIds.contains(String.valueOf(info.appId()))) {
            throw SocialTokenVerificationException.invalid("kakao access token was issued for another app");
        }
        return String.valueOf(info.id());
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record KakaoTokenInfo(
            Long id,
            @JsonProperty("expires_in") Long expiresIn,
            @JsonProperty("app_id") Long appId
    ) {
    }
}
