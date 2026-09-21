package com.aisip.OnO.backend.auth.service;

import com.aisip.OnO.backend.auth.entity.Authority;
import com.aisip.OnO.backend.auth.exception.AuthErrorCase;
import com.aisip.OnO.backend.common.exception.ApplicationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 만료된 액세스 토큰이 어떤 ErrorCase 로 나가는지 고정한다.
 *
 * 프론트(HttpService.dart)는 errorCode 1005 일 때만 토큰 갱신을 재시도하고,
 * 1000~1999 의 다른 코드는 인증 실패로 보고 강제 로그아웃시킨다.
 * 그래서 "만료 → 1005" 계약이 깨지면 리프레시 토큰이 멀쩡한 사용자가 로그아웃된다.
 */
class JwtTokenizerValidationTest {

    private static final String ACCESS_SECRET =
            "dGVzdC1hY2Nlc3MtdG9rZW4tc2VjcmV0LXRlc3QtYWNjZXNzLXRva2VuLXNlY3JldC0zMmJ5dGVz";
    private static final String REFRESH_SECRET =
            "dGVzdC1yZWZyZXNoLXRva2VuLXNlY3JldC10ZXN0LXJlZnJlc2gtdG9rZW4tc2VjcmV0LTMyYnl0ZXM=";

    private JwtTokenizer expiringTokenizer;
    private JwtTokenizer validTokenizer;

    @BeforeEach
    void setUp() {
        // 액세스 토큰 유효기간을 음수로 줘서 발급 즉시 만료된 토큰을 만든다
        expiringTokenizer = new JwtTokenizer(-1_000L, 60_480_000_000L, ACCESS_SECRET, REFRESH_SECRET);
        validTokenizer = new JwtTokenizer(1_800_000L, 60_480_000_000L, ACCESS_SECRET, REFRESH_SECRET);
    }

    private String issueAccessToken(JwtTokenizer tokenizer) {
        return tokenizer
                .createAccessToken("1", Map.of("authority", Authority.ROLE_MEMBER.name()))
                .substring(JwtTokenizer.BEARER_PREFIX.length())
                .trim();
    }

    @Test
    @DisplayName("만료된 액세스 토큰은 ACCESS_TOKEN_EXPIRED(1005) 로 판정된다")
    void expiredAccessTokenMapsTo1005() {
        String expired = issueAccessToken(expiringTokenizer);

        assertThatThrownBy(() -> validTokenizer.validateAccessToken(expired))
                .isInstanceOf(ApplicationException.class)
                .satisfies(e -> assertThat(((ApplicationException) e).getErrorCase())
                        .isEqualTo(AuthErrorCase.ACCESS_TOKEN_EXPIRED));

        assertThat(AuthErrorCase.ACCESS_TOKEN_EXPIRED.getErrorCode()).isEqualTo(1005);
    }

    @Test
    @DisplayName("서명이 틀린 액세스 토큰은 만료가 아니라 INVALID_ACCESS_TOKEN(1009) 로 판정된다")
    void tamperedAccessTokenMapsTo1009() {
        String token = issueAccessToken(validTokenizer);
        // 서명부 한 글자를 바꿔 검증에 실패하게 만든다.
        // 마지막 글자는 32바이트를 base64url 43글자로 담고 남은 비트라 바꿔도 같은 바이트로 디코딩될 수 있다.
        // (HS256 서명이 'A' 로 끝나면 'B' 로 바꿔도 서명이 그대로여서 검증을 통과해 버렸다)
        // 첫 글자는 6비트가 모두 쓰이므로 다른 글자로 바꾸면 서명이 반드시 달라진다.
        int signatureStart = token.lastIndexOf('.') + 1;
        String tampered = token.substring(0, signatureStart)
                + (token.charAt(signatureStart) == 'A' ? 'B' : 'A')
                + token.substring(signatureStart + 1);

        assertThatThrownBy(() -> validTokenizer.validateAccessToken(tampered))
                .isInstanceOf(ApplicationException.class)
                .satisfies(e -> assertThat(((ApplicationException) e).getErrorCase())
                        .isEqualTo(AuthErrorCase.INVALID_ACCESS_TOKEN));
    }

    @Test
    @DisplayName("정상 액세스 토큰은 예외 없이 통과한다")
    void validAccessTokenPasses() {
        String token = issueAccessToken(validTokenizer);

        validTokenizer.validateAccessToken(token);
    }
}
