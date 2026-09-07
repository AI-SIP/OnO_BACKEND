package com.aisip.OnO.backend.auth.service;

import com.aisip.OnO.backend.auth.entity.Authority;
import com.aisip.OnO.backend.auth.exception.AuthErrorCase;
import com.aisip.OnO.backend.common.exception.ApplicationException;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Date;
import java.util.Map;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * JWT 발급·검증의 단위 계약을 고정한다.
 *
 * <p>여기서 정하는 예외 타입이 그대로 {@code JwtTokenFilter} 의 분기 조건이 되고,
 * 필터가 고른 {@link AuthErrorCase} 가 프론트의 토큰 갱신 트리거가 된다.
 * 즉 이 파일의 assertion 은 프론트 동작과 직결된 계약이다.
 */
@DisplayName("JwtTokenizer")
class JwtTokenizerTest {

    private static final String ACCESS_SECRET =
            "dGVzdC1hY2Nlc3MtdG9rZW4tc2VjcmV0LXRlc3QtYWNjZXNzLXRva2VuLXNlY3JldC0zMmJ5dGVz";
    private static final String REFRESH_SECRET =
            "dGVzdC1yZWZyZXNoLXRva2VuLXNlY3JldC10ZXN0LXJlZnJlc2gtdG9rZW4tc2VjcmV0LTMyYnl0ZXM=";
    private static final String OTHER_SECRET =
            "b3RoZXItc2VjcmV0LWtleS1vdGhlci1zZWNyZXQta2V5LW90aGVyLXNlY3JldC0zMmJ5dGVz";

    private static final long ACCESS_TOKEN_EXPIRATION_MILLIS = 1_800_000L;
    private static final long REFRESH_TOKEN_EXPIRATION_MILLIS = 604_800_000L;

    private final JwtTokenizer tokenizer = newTokenizer(ACCESS_TOKEN_EXPIRATION_MILLIS, REFRESH_TOKEN_EXPIRATION_MILLIS);

    /** 이미 만료된 토큰만 발급하는 토크나이저. 시계를 조작하지 않고 만료 상황을 재현한다. */
    private final JwtTokenizer expiredTokenizer = newTokenizer(-60_000L, -60_000L);

    private static JwtTokenizer newTokenizer(long accessExpiration, long refreshExpiration) {
        return new JwtTokenizer(accessExpiration, refreshExpiration, ACCESS_SECRET, REFRESH_SECRET);
    }

    private String accessTokenOf(long userId, Authority authority) {
        return stripBearer(tokenizer.createAccessToken(String.valueOf(userId), Map.of("authority", authority)));
    }

    private String refreshTokenOf(long userId, Authority authority) {
        return tokenizer.createRefreshToken(String.valueOf(userId), Map.of("authority", authority));
    }

    private static String stripBearer(String token) {
        return token.replace(JwtTokenizer.BEARER_PREFIX, "");
    }

    @Nested
    @DisplayName("액세스 토큰 발급")
    class CreateAccessToken {

        @Test
        @DisplayName("Authorization 헤더에 그대로 넣을 수 있도록 Bearer 프리픽스를 붙여 발급한다")
        void prefixesBearer() {
            String token = tokenizer.createAccessToken("42", Map.of("authority", Authority.ROLE_MEMBER));

            assertThat(token)
                    .as("프론트는 발급값을 그대로 Authorization 헤더에 넣는다")
                    .startsWith("Bearer ");
        }

        @Test
        @DisplayName("subject 와 authority 클레임이 그대로 왕복한다")
        void carriesSubjectAndAuthority() {
            String token = accessTokenOf(42L, Authority.ROLE_GUEST);

            assertThat(tokenizer.getUserIdFromAccessToken(token)).isEqualTo(42L);
            assertThat(tokenizer.getAuthorityFromAccessToken(token)).isEqualTo(Authority.ROLE_GUEST);
        }

        @ParameterizedTest(name = "{0} 권한도 손실 없이 왕복한다")
        @EnumSource(Authority.class)
        void carriesEveryAuthority(Authority authority) {
            String token = accessTokenOf(7L, authority);

            assertThat(tokenizer.getAuthorityFromAccessToken(token)).isEqualTo(authority);
        }

        @Test
        @DisplayName("만료 시각은 설정한 accessToken 만료 시간과 같다")
        void expiresAfterConfiguredDuration() {
            String token = accessTokenOf(1L, Authority.ROLE_MEMBER);

            assertThat(tokenizer.getRemainingExpirationTime(token))
                    .as("설정값 %d ms 안쪽이어야 한다", ACCESS_TOKEN_EXPIRATION_MILLIS)
                    .isBetween(ACCESS_TOKEN_EXPIRATION_MILLIS / 1000 - 5, ACCESS_TOKEN_EXPIRATION_MILLIS / 1000);
            assertThat(tokenizer.getAccessTokenExpirationSeconds()).isEqualTo(1_800L);
        }
    }

    @Nested
    @DisplayName("리프레시 토큰 발급")
    class CreateRefreshToken {

        @Test
        @DisplayName("리프레시 토큰에는 Bearer 프리픽스를 붙이지 않는다")
        void hasNoBearerPrefix() {
            assertThat(refreshTokenOf(42L, Authority.ROLE_MEMBER))
                    .as("DB 에 저장되고 body 로 오가는 값이라 프리픽스가 붙으면 조회가 어긋난다")
                    .doesNotStartWith("Bearer ");
        }

        @Test
        @DisplayName("같은 사용자가 연속 발급해도 jti 덕분에 매번 다른 토큰이 나온다")
        void issuesDistinctTokensForSameUser() {
            String first = refreshTokenOf(42L, Authority.ROLE_MEMBER);
            String second = refreshTokenOf(42L, Authority.ROLE_MEMBER);

            assertThat(second)
                    .as("같은 값이면 회전(rotation)해도 이전 세션과 구분되지 않는다")
                    .isNotEqualTo(first);
        }

        @Test
        @DisplayName("리프레시 토큰 문자열이 refresh_token 컬럼(varchar 255)에 들어간다")
        void fitsInRefreshTokenColumn() {
            int longest = IntStream.range(0, 50)
                    .map(i -> refreshTokenOf(Long.MAX_VALUE, Authority.ROLE_MEMBER).length())
                    .max()
                    .orElseThrow();

            System.out.println("발급된 refresh token 최대 길이 = " + longest);
            assertThat(longest)
                    .as("길이가 255 를 넘으면 저장 시 잘리거나 실패해 이후 조회가 1002 로 떨어진다")
                    .isLessThan(255);
        }

        @Test
        @DisplayName("만료 시각은 설정한 refreshToken 만료 시간과 같다")
        void expiresAfterConfiguredDuration() {
            String token = refreshTokenOf(1L, Authority.ROLE_MEMBER);

            assertThat(tokenizer.getRemainingRefreshExpirationTime(token))
                    .isBetween(REFRESH_TOKEN_EXPIRATION_MILLIS / 1000 - 5, REFRESH_TOKEN_EXPIRATION_MILLIS / 1000);
            assertThat(tokenizer.getRefreshTokenExpirationSeconds()).isEqualTo(604_800L);
        }
    }

    @Nested
    @DisplayName("액세스 토큰 검증")
    class ValidateAccessToken {

        @Test
        @DisplayName("정상 토큰은 그대로 통과한다")
        void acceptsValidToken() {
            assertThatCode(() -> tokenizer.validateAccessToken(accessTokenOf(1L, Authority.ROLE_MEMBER)))
                    .doesNotThrowAnyException();
        }

        /**
         * 만료만은 ApplicationException 으로 감싸지 않고 ExpiredJwtException 그대로 던진다.
         * JwtTokenFilter 가 ExpiredJwtException 을 잡아 ACCESS_TOKEN_EXPIRED(1005) 로 응답하고,
         * 프론트는 1005 를 보고 토큰 갱신을 시도하기 때문이다.
         */
        @Test
        @DisplayName("만료된 토큰은 ACCESS_TOKEN_EXPIRED 로 구분되어 올라간다")
        void distinguishesExpiredToken() {
            String expired = stripBearer(
                    expiredTokenizer.createAccessToken("1", Map.of("authority", Authority.ROLE_MEMBER)));

            assertThatThrownBy(() -> tokenizer.validateAccessToken(expired))
                    .as("만료가 다른 실패와 섞이면 프론트가 갱신 기회를 잃는다")
                    .isInstanceOf(ApplicationException.class)
                    .extracting(e -> ((ApplicationException) e).getErrorCase())
                    .isEqualTo(AuthErrorCase.ACCESS_TOKEN_EXPIRED);
        }

        @Test
        @DisplayName("다른 키로 서명한 위조 토큰은 유효하지 않은 액세스 토큰으로 거절한다")
        void rejectsForgedSignature() {
            String forged = Jwts.builder()
                    .setClaims(Map.of("authority", Authority.ROLE_ADMIN))
                    .setSubject("1")
                    .setIssuedAt(new Date())
                    .setExpiration(new Date(System.currentTimeMillis() + 60_000))
                    .signWith(Keys.hmacShaKeyFor(Base64.getDecoder().decode(OTHER_SECRET)), SignatureAlgorithm.HS256)
                    .compact();

            assertThatThrownBy(() -> tokenizer.validateAccessToken(forged))
                    .isInstanceOf(ApplicationException.class)
                    .extracting(e -> ((ApplicationException) e).getErrorCase())
                    .isEqualTo(AuthErrorCase.INVALID_ACCESS_TOKEN);
        }

        @Test
        @DisplayName("페이로드를 갈아끼운 토큰은 서명 불일치로 거절한다")
        void rejectsTamperedPayload() {
            String token = accessTokenOf(1L, Authority.ROLE_MEMBER);
            String[] parts = token.split("\\.");
            String tamperedPayload = Base64.getUrlEncoder().withoutPadding().encodeToString(
                    new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8)
                            .replace("\"sub\":\"1\"", "\"sub\":\"999\"")
                            .getBytes(StandardCharsets.UTF_8));
            String tampered = parts[0] + "." + tamperedPayload + "." + parts[2];

            assertThatThrownBy(() -> tokenizer.validateAccessToken(tampered))
                    .as("남의 userId 로 갈아끼운 토큰이 통과하면 전 사용자 데이터가 열린다")
                    .isInstanceOf(ApplicationException.class);
        }

        @Test
        @DisplayName("서명 없는 alg=none 토큰은 거절한다")
        void rejectsUnsignedToken() {
            String header = Base64.getUrlEncoder().withoutPadding()
                    .encodeToString("{\"alg\":\"none\"}".getBytes(StandardCharsets.UTF_8));
            String payload = Base64.getUrlEncoder().withoutPadding()
                    .encodeToString("{\"sub\":\"1\",\"authority\":\"ROLE_ADMIN\"}".getBytes(StandardCharsets.UTF_8));

            assertThatThrownBy(() -> tokenizer.validateAccessToken(header + "." + payload + "."))
                    .isInstanceOf(ApplicationException.class);
        }

        @Test
        @DisplayName("리프레시 토큰을 액세스 토큰 자리에 넣으면 거절한다")
        void rejectsRefreshTokenAsAccessToken() {
            assertThatThrownBy(() -> tokenizer.validateAccessToken(refreshTokenOf(1L, Authority.ROLE_MEMBER)))
                    .as("서명 키가 분리돼 있어야 리프레시 토큰만으로 API 를 호출할 수 없다")
                    .isInstanceOf(ApplicationException.class);
        }

        @ParameterizedTest(name = "형식이 깨진 토큰 [{0}] 은 거절한다")
        @ValueSource(strings = {"", "   ", "not-a-jwt", "a.b", "a.b.c.d", "Bearer eyJhbGciOiJIUzI1NiJ9"})
        void rejectsMalformedToken(String malformed) {
            assertThatThrownBy(() -> tokenizer.validateAccessToken(malformed))
                    .isInstanceOf(ApplicationException.class);
        }

        @Test
        @DisplayName("null 토큰도 예외 계약을 지킨다")
        void rejectsNullToken() {
            assertThatThrownBy(() -> tokenizer.validateAccessToken(null))
                    .isInstanceOf(ApplicationException.class);
        }
    }

    @Nested
    @DisplayName("리프레시 토큰 검증")
    class ValidateRefreshToken {

        @Test
        @DisplayName("정상 토큰은 그대로 통과한다")
        void acceptsValidToken() {
            assertThatCode(() -> tokenizer.validateRefreshToken(refreshTokenOf(1L, Authority.ROLE_MEMBER)))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("만료된 리프레시 토큰은 1006 으로 거절한다")
        void rejectsExpiredWithRefreshTokenExpired() {
            String expired = expiredTokenizer.createRefreshToken("1", Map.of("authority", Authority.ROLE_MEMBER));

            assertThatThrownBy(() -> tokenizer.validateRefreshToken(expired))
                    .isInstanceOf(ApplicationException.class)
                    .extracting(e -> ((ApplicationException) e).getErrorCase())
                    .as("프론트는 1006 을 재로그인 신호로 쓴다")
                    .isEqualTo(AuthErrorCase.REFRESH_TOKEN_EXPIRED);
        }

        @Test
        @DisplayName("위조 서명 리프레시 토큰은 1001 로 거절한다")
        void rejectsForgedWithInvalidRefreshToken() {
            String forged = Jwts.builder()
                    .setSubject("1")
                    .setClaims(Map.of("authority", Authority.ROLE_ADMIN))
                    .setExpiration(new Date(System.currentTimeMillis() + 60_000))
                    .signWith(Keys.hmacShaKeyFor(Base64.getDecoder().decode(OTHER_SECRET)), SignatureAlgorithm.HS256)
                    .compact();

            assertThatThrownBy(() -> tokenizer.validateRefreshToken(forged))
                    .isInstanceOf(ApplicationException.class)
                    .extracting(e -> ((ApplicationException) e).getErrorCase())
                    .isEqualTo(AuthErrorCase.INVALID_REFRESH_TOKEN);
        }

        @Test
        @DisplayName("액세스 토큰을 리프레시 토큰 자리에 넣으면 1001 로 거절한다")
        void rejectsAccessTokenAsRefreshToken() {
            assertThatThrownBy(() -> tokenizer.validateRefreshToken(accessTokenOf(1L, Authority.ROLE_MEMBER)))
                    .isInstanceOf(ApplicationException.class)
                    .extracting(e -> ((ApplicationException) e).getErrorCase())
                    .isEqualTo(AuthErrorCase.INVALID_REFRESH_TOKEN);
        }

        @ParameterizedTest(name = "형식이 깨진 리프레시 토큰 [{0}] 은 1001 로 거절한다")
        @ValueSource(strings = {"", "   ", "not-a-jwt", "a.b.c"})
        void rejectsMalformedToken(String malformed) {
            assertThatThrownBy(() -> tokenizer.validateRefreshToken(malformed))
                    .isInstanceOf(ApplicationException.class)
                    .extracting(e -> ((ApplicationException) e).getErrorCase())
                    .isEqualTo(AuthErrorCase.INVALID_REFRESH_TOKEN);
        }

        @Test
        @DisplayName("null 리프레시 토큰도 1001 로 거절한다")
        void rejectsNullToken() {
            assertThatThrownBy(() -> tokenizer.validateRefreshToken(null))
                    .as("본문에 refreshToken 을 빠뜨린 요청이 500 이 되면 안 된다")
                    .isInstanceOf(ApplicationException.class)
                    .extracting(e -> ((ApplicationException) e).getErrorCase())
                    .isEqualTo(AuthErrorCase.INVALID_REFRESH_TOKEN);
        }
    }

    @Nested
    @DisplayName("클레임 추출")
    class ExtractClaims {

        @Test
        @DisplayName("리프레시 토큰에서 userId 와 권한을 읽는다")
        void readsUserIdAndAuthorityFromRefreshToken() {
            String token = refreshTokenOf(123L, Authority.ROLE_ADMIN);

            assertThat(tokenizer.getUserIdFromRefreshToken(token)).isEqualTo(123L);
            assertThat(tokenizer.getAuthorityFromRefreshToken(token)).isEqualTo(Authority.ROLE_ADMIN);
        }

        @Test
        @DisplayName("만료된 토큰에서 클레임을 읽으면 ExpiredJwtException 이 난다")
        void failsToReadClaimsFromExpiredToken() {
            String expired = stripBearer(
                    expiredTokenizer.createAccessToken("1", Map.of("authority", Authority.ROLE_MEMBER)));

            assertThatThrownBy(() -> tokenizer.getRemainingExpirationTime(expired))
                    .as("로그아웃 시 만료 토큰을 블랙리스트에 넣지 않도록 하는 근거가 되는 동작이다")
                    .isInstanceOf(ExpiredJwtException.class);
        }

        @Test
        @DisplayName("권한 클레임이 없는 토큰은 권한 조회에서 실패한다")
        void failsWhenAuthorityClaimMissing() {
            String noAuthority = Jwts.builder()
                    .setSubject("1")
                    .setIssuedAt(new Date())
                    .setExpiration(new Date(System.currentTimeMillis() + 60_000))
                    .signWith(Keys.hmacShaKeyFor(Base64.getDecoder().decode(REFRESH_SECRET)), SignatureAlgorithm.HS256)
                    .compact();

            assertThatThrownBy(() -> tokenizer.getAuthorityFromRefreshToken(noAuthority))
                    .isInstanceOf(Exception.class);
        }
    }
}
