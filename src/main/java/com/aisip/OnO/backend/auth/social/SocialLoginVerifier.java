package com.aisip.OnO.backend.auth.social;

import com.aisip.OnO.backend.auth.exception.AuthErrorCase;
import com.aisip.OnO.backend.common.exception.ApplicationException;
import com.aisip.OnO.backend.user.dto.UserRegisterDto;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 소셜 로그인 요청의 토큰을 확인해, 토큰이 가리키는 사용자가 요청의 identifier 와 같은지 본다.
 *
 * <p>4.1.1 까지의 앱은 토큰을 보내지 않는다. 그래서 {@code auth.social-verification.enforce} 로 단계를 나눈다.
 * <ul>
 *   <li>false(기본): 토큰이 오면 확인하고, 맞지 않으면 거절한다. 토큰이 없으면 예전처럼 통과시키고 메트릭으로 센다.</li>
 *   <li>true: 토큰이 없거나 확인하지 못하면 거절한다.</li>
 * </ul>
 * 새 앱이 퍼지고 {@code ono.auth.social_verification{result="missing"}} 이 충분히 줄면 true 로 바꾼다.
 *
 * <p>로그와 메트릭에는 토큰과 identifier 원문을 남기지 않는다. 플랫폼과 결과만 남긴다.
 */
@Slf4j
@Component
public class SocialLoginVerifier {

    static final String METRIC_NAME = "ono.auth.social_verification";

    private final Map<SocialPlatform, SocialTokenVerifier> verifiers = new EnumMap<>(SocialPlatform.class);
    private final MeterRegistry meterRegistry;
    private final boolean enforce;

    public SocialLoginVerifier(
            List<SocialTokenVerifier> verifiers,
            MeterRegistry meterRegistry,
            @Value("${auth.social-verification.enforce:false}") boolean enforce
    ) {
        verifiers.forEach(verifier -> this.verifiers.put(verifier.platform(), verifier));
        this.meterRegistry = meterRegistry;
        this.enforce = enforce;
    }

    @PostConstruct
    void reportConfiguration() {
        List<SocialPlatform> unconfigured = verifiers.values().stream()
                .filter(verifier -> !verifier.isConfigured())
                .map(SocialTokenVerifier::platform)
                .toList();
        if (unconfigured.isEmpty()) {
            log.info("social login verification ready - enforce: {}", enforce);
            return;
        }
        if (enforce) {
            log.error("social login verification enforced but not configured for {} - these logins will be rejected", unconfigured);
        } else {
            log.warn("social login verification not configured for {} - tokens from these platforms are not checked", unconfigured);
        }
    }

    public void verify(UserRegisterDto request) {
        Optional<SocialPlatform> platform = SocialPlatform.from(request.platform());
        String platformTag = platform.map(Enum::name).orElse("UNKNOWN");
        String token = request.socialToken();

        if (token == null || token.isBlank()) {
            count(platformTag, "missing");
            if (enforce) {
                throw new ApplicationException(AuthErrorCase.SOCIAL_TOKEN_REQUIRED);
            }
            log.info("social login without token - platform: {}", platformTag);
            return;
        }

        SocialTokenVerifier verifier = platform.map(verifiers::get).orElse(null);
        if (verifier == null) {
            count(platformTag, "unsupported");
            throw new ApplicationException(AuthErrorCase.INVALID_SOCIAL_TOKEN);
        }

        if (!verifier.isConfigured()) {
            count(platformTag, "unconfigured");
            if (enforce) {
                throw new ApplicationException(AuthErrorCase.SOCIAL_VERIFICATION_UNAVAILABLE);
            }
            return;
        }

        String subject;
        try {
            subject = verifier.verify(token);
        } catch (SocialTokenVerificationException e) {
            if (e.isUnavailable()) {
                // 플랫폼 서버에 닿지 못한 것은 사용자 탓이 아니다. 강제 전에는 예전처럼 통과시킨다.
                count(platformTag, "unavailable");
                log.warn("social login verification unavailable - platform: {}, reason: {}", platformTag, e.getMessage());
                if (enforce) {
                    throw new ApplicationException(AuthErrorCase.SOCIAL_VERIFICATION_UNAVAILABLE);
                }
                return;
            }
            count(platformTag, "invalid");
            log.info("social login token rejected - platform: {}, reason: {}", platformTag, e.getMessage());
            throw new ApplicationException(AuthErrorCase.INVALID_SOCIAL_TOKEN);
        }

        if (!subject.equals(request.identifier())) {
            count(platformTag, "mismatch");
            log.info("social login token belongs to another identifier - platform: {}", platformTag);
            throw new ApplicationException(AuthErrorCase.SOCIAL_IDENTIFIER_MISMATCH);
        }

        count(platformTag, "verified");
    }

    private void count(String platform, String result) {
        Counter.builder(METRIC_NAME)
                .description("소셜 로그인 토큰 확인 결과")
                .tag("platform", platform)
                .tag("result", result)
                .tag("enforce", String.valueOf(enforce))
                .register(meterRegistry)
                .increment();
    }
}
