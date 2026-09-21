package com.aisip.OnO.backend.auth.service;

import com.aisip.OnO.backend.auth.dto.TokenResponseDto;
import com.aisip.OnO.backend.auth.entity.Authority;
import com.aisip.OnO.backend.auth.entity.RefreshToken;
import com.aisip.OnO.backend.auth.exception.AuthErrorCase;
import com.aisip.OnO.backend.auth.repository.RefreshTokenRepository;
import com.aisip.OnO.backend.common.exception.ApplicationException;
import com.aisip.OnO.backend.util.redis.RedisTokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * 토큰 발급 → 회전 → 폐기의 단위 계약.
 *
 * <p>refresh 는 세션 하나(제출된 토큰이 가리키는 row)만 회전시켜야 한다.
 * userId 로 찾아 덮어쓰면 여러 기기 로그인 중 한 기기가 갱신할 때
 * 나머지 기기가 전부 1002 로 튕긴다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("JwtTokenService")
class JwtTokenServiceTest {

    private static final String ACCESS_SECRET =
            "dGVzdC1hY2Nlc3MtdG9rZW4tc2VjcmV0LXRlc3QtYWNjZXNzLXRva2VuLXNlY3JldC0zMmJ5dGVz";
    private static final String REFRESH_SECRET =
            "dGVzdC1yZWZyZXNoLXRva2VuLXNlY3JldC10ZXN0LXJlZnJlc2gtdG9rZW4tc2VjcmV0LTMyYnl0ZXM=";

    private static final Long USER_ID = 1L;

    @Mock
    private RedisTokenService redisTokenService;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    private JwtTokenizer jwtTokenizer;
    private JwtTokenizer expiredTokenizer;
    private JwtTokenService jwtTokenService;

    @BeforeEach
    void setUp() {
        jwtTokenizer = new JwtTokenizer(1_800_000L, 604_800_000L, ACCESS_SECRET, REFRESH_SECRET);
        expiredTokenizer = new JwtTokenizer(-60_000L, -60_000L, ACCESS_SECRET, REFRESH_SECRET);
        jwtTokenService = new JwtTokenService(jwtTokenizer, redisTokenService, refreshTokenRepository);

        given(refreshTokenRepository.save(any(RefreshToken.class)))
                .willAnswer(invocation -> invocation.getArgument(0));
    }

    private String refreshTokenOf(Authority authority) {
        return jwtTokenizer.createRefreshToken(String.valueOf(USER_ID), Map.of("authority", authority));
    }

    @Nested
    @DisplayName("토큰 발급")
    class GenerateTokens {

        @Test
        @DisplayName("액세스·리프레시 토큰을 함께 발급하고 리프레시 토큰 row 를 새로 저장한다")
        void issuesBothTokensAndPersistsSession() {
            TokenResponseDto response = jwtTokenService.generateTokens(USER_ID, Authority.ROLE_MEMBER);

            ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
            verify(refreshTokenRepository).save(captor.capture());

            assertThat(response.getAccessToken()).startsWith(JwtTokenizer.BEARER_PREFIX);
            assertThat(captor.getValue().getUserId()).isEqualTo(USER_ID);
            assertThat(captor.getValue().getAuthority()).isEqualTo(Authority.ROLE_MEMBER);
            assertThat(captor.getValue().getRefreshToken())
                    .as("응답으로 준 토큰과 저장한 토큰이 다르면 갱신 시 1002 가 난다")
                    .isEqualTo(response.getRefreshToken());
        }

        @Test
        @DisplayName("기존 세션을 userId 로 찾아 덮어쓰지 않는다")
        void doesNotOverwriteExistingSessionByUserId() {
            jwtTokenService.generateTokens(USER_ID, Authority.ROLE_MEMBER);

            verify(refreshTokenRepository, never()).findByUserId(anyLong());
            verify(refreshTokenRepository, never()).deleteByUserId(anyLong());
        }

        @Test
        @DisplayName("발급한 토큰의 권한 클레임은 요청한 권한과 같다")
        void embedsRequestedAuthority() {
            TokenResponseDto response = jwtTokenService.generateTokens(USER_ID, Authority.ROLE_GUEST);

            assertThat(jwtTokenizer.getAuthorityFromRefreshToken(response.getRefreshToken()))
                    .isEqualTo(Authority.ROLE_GUEST);
        }

        @Test
        @DisplayName("발급 단계에서는 Redis 를 쓰지 않는다")
        void doesNotTouchRedis() {
            jwtTokenService.generateTokens(USER_ID, Authority.ROLE_MEMBER);

            verify(redisTokenService, never()).saveRefreshToken(anyLong(), anyString(), anyLong());
        }
    }

    @Nested
    @DisplayName("토큰 갱신")
    class RefreshAccessToken {

        @Test
        @DisplayName("제출한 리프레시 토큰이 가리키는 세션만 회전시킨다")
        void rotatesOnlySubmittedSession() {
            String oldRefreshToken = refreshTokenOf(Authority.ROLE_MEMBER);
            RefreshToken session = RefreshToken.from(USER_ID, Authority.ROLE_MEMBER, oldRefreshToken);
            given(refreshTokenRepository.findByRefreshToken(oldRefreshToken)).willReturn(Optional.of(session));

            TokenResponseDto response = jwtTokenService.refreshAccessToken(oldRefreshToken);

            assertThat(response.getAccessToken()).startsWith(JwtTokenizer.BEARER_PREFIX);
            assertThat(response.getRefreshToken()).isNotEqualTo(oldRefreshToken);
            assertThat(session.getRefreshToken())
                    .as("DB row 도 새 토큰으로 갱신돼야 다음 갱신이 통한다")
                    .isEqualTo(response.getRefreshToken());
            verify(refreshTokenRepository).save(session);
            verify(refreshTokenRepository, never()).findByUserId(anyLong());
        }

        @Test
        @DisplayName("갱신해도 권한은 그대로 유지된다")
        void keepsAuthority() {
            String oldRefreshToken = refreshTokenOf(Authority.ROLE_GUEST);
            given(refreshTokenRepository.findByRefreshToken(oldRefreshToken))
                    .willReturn(Optional.of(RefreshToken.from(USER_ID, Authority.ROLE_GUEST, oldRefreshToken)));

            TokenResponseDto response = jwtTokenService.refreshAccessToken(oldRefreshToken);

            assertThat(jwtTokenizer.getAuthorityFromAccessToken(
                    response.getAccessToken().replace(JwtTokenizer.BEARER_PREFIX, "")))
                    .as("게스트가 갱신 한 번으로 멤버 권한을 얻으면 안 된다")
                    .isEqualTo(Authority.ROLE_GUEST);
        }

        @Test
        @DisplayName("DB 에 없는 리프레시 토큰이면 1002 로 거절한다")
        void rejectsUnknownRefreshTokenWithNotFound() {
            String unknown = refreshTokenOf(Authority.ROLE_MEMBER);
            given(refreshTokenRepository.findByRefreshToken(unknown)).willReturn(Optional.empty());

            assertThatThrownBy(() -> jwtTokenService.refreshAccessToken(unknown))
                    .isInstanceOf(ApplicationException.class)
                    .extracting(e -> ((ApplicationException) e).getErrorCase())
                    .as("프로덕션 1002 응답이 나오는 지점")
                    .isEqualTo(AuthErrorCase.REFRESH_TOKEN_NOT_FOUND);
        }

        @Test
        @DisplayName("이미 회전시킨 옛 리프레시 토큰을 재사용하면 1002 로 거절한다")
        void rejectsReusedRefreshToken() {
            String oldRefreshToken = refreshTokenOf(Authority.ROLE_MEMBER);
            RefreshToken session = RefreshToken.from(USER_ID, Authority.ROLE_MEMBER, oldRefreshToken);
            given(refreshTokenRepository.findByRefreshToken(oldRefreshToken)).willReturn(Optional.of(session));

            jwtTokenService.refreshAccessToken(oldRefreshToken);

            // 회전 후에는 옛 토큰으로 더 이상 세션을 찾을 수 없다.
            given(refreshTokenRepository.findByRefreshToken(oldRefreshToken)).willReturn(Optional.empty());

            assertThatThrownBy(() -> jwtTokenService.refreshAccessToken(oldRefreshToken))
                    .isInstanceOf(ApplicationException.class)
                    .extracting(e -> ((ApplicationException) e).getErrorCase())
                    .isEqualTo(AuthErrorCase.REFRESH_TOKEN_NOT_FOUND);
        }

        @Test
        @DisplayName("만료된 리프레시 토큰은 DB 조회 전에 1006 으로 거절한다")
        void rejectsExpiredRefreshTokenBeforeLookup() {
            String expired = expiredTokenizer.createRefreshToken(
                    String.valueOf(USER_ID), Map.of("authority", Authority.ROLE_MEMBER));

            assertThatThrownBy(() -> jwtTokenService.refreshAccessToken(expired))
                    .isInstanceOf(ApplicationException.class)
                    .extracting(e -> ((ApplicationException) e).getErrorCase())
                    .isEqualTo(AuthErrorCase.REFRESH_TOKEN_EXPIRED);

            verify(refreshTokenRepository, never()).findByRefreshToken(anyString());
        }

        @Test
        @DisplayName("형식이 깨진 리프레시 토큰은 1001 로 거절한다")
        void rejectsMalformedRefreshToken() {
            assertThatThrownBy(() -> jwtTokenService.refreshAccessToken("not-a-jwt"))
                    .isInstanceOf(ApplicationException.class)
                    .extracting(e -> ((ApplicationException) e).getErrorCase())
                    .isEqualTo(AuthErrorCase.INVALID_REFRESH_TOKEN);

            verify(refreshTokenRepository, never()).findByRefreshToken(anyString());
        }

        @Test
        @DisplayName("refreshToken 이 null 이면 500 이 아니라 1001 로 거절한다")
        void rejectsNullRefreshToken() {
            assertThatThrownBy(() -> jwtTokenService.refreshAccessToken(null))
                    .isInstanceOf(ApplicationException.class)
                    .extracting(e -> ((ApplicationException) e).getErrorCase())
                    .isEqualTo(AuthErrorCase.INVALID_REFRESH_TOKEN);
        }
    }

    @Nested
    @DisplayName("로그아웃")
    class Logout {

        @Test
        @DisplayName("제출한 리프레시 토큰 세션을 지우고 액세스 토큰을 남은 시간만큼 블랙리스트에 넣는다")
        void deletesSessionAndBlacklistsAccessToken() {
            String accessToken = jwtTokenizer.createAccessToken(
                    String.valueOf(USER_ID), Map.of("authority", Authority.ROLE_MEMBER));
            String refreshToken = refreshTokenOf(Authority.ROLE_MEMBER);
            RefreshToken session = RefreshToken.from(USER_ID, Authority.ROLE_MEMBER, refreshToken);
            given(refreshTokenRepository.findByRefreshToken(refreshToken)).willReturn(Optional.of(session));

            jwtTokenService.logout(accessToken, USER_ID, refreshToken);

            verify(refreshTokenRepository).delete(session);

            ArgumentCaptor<Long> ttlCaptor = ArgumentCaptor.forClass(Long.class);
            verify(redisTokenService).addToBlacklist(
                    eq(accessToken.replace(JwtTokenizer.BEARER_PREFIX, "")), ttlCaptor.capture());
            assertThat(ttlCaptor.getValue())
                    .as("남은 만료 시간만큼만 블랙리스트에 두어야 Redis 가 새지 않는다")
                    .isBetween(1L, 1_800L);
        }

        @Test
        @DisplayName("Bearer 프리픽스 없이 액세스 토큰만 보내도 블랙리스트에 넣는다")
        void acceptsRawAccessToken() {
            String rawAccessToken = jwtTokenizer
                    .createAccessToken(String.valueOf(USER_ID), Map.of("authority", Authority.ROLE_MEMBER))
                    .replace(JwtTokenizer.BEARER_PREFIX, "");

            jwtTokenService.logout(rawAccessToken, USER_ID, null);

            verify(redisTokenService).addToBlacklist(eq(rawAccessToken), anyLong());
        }

        @Test
        @DisplayName("리프레시 토큰을 함께 보내지 않으면 세션 삭제를 시도하지 않는다")
        void skipsSessionDeletionWithoutRefreshToken() {
            String accessToken = jwtTokenizer.createAccessToken(
                    String.valueOf(USER_ID), Map.of("authority", Authority.ROLE_MEMBER));

            jwtTokenService.logout(accessToken, USER_ID, "   ");

            verify(refreshTokenRepository, never()).findByRefreshToken(anyString());
            verify(refreshTokenRepository, never()).delete(any(RefreshToken.class));
        }

        @Test
        @DisplayName("다른 사용자의 리프레시 토큰을 넘기면 그 세션을 지우지 않는다")
        void doesNotDeleteOtherUsersSession() {
            String accessToken = jwtTokenizer.createAccessToken(
                    String.valueOf(USER_ID), Map.of("authority", Authority.ROLE_MEMBER));
            String othersRefreshToken = refreshTokenOf(Authority.ROLE_MEMBER);
            given(refreshTokenRepository.findByRefreshToken(othersRefreshToken))
                    .willReturn(Optional.of(RefreshToken.from(999L, Authority.ROLE_MEMBER, othersRefreshToken)));

            jwtTokenService.logout(accessToken, USER_ID, othersRefreshToken);

            verify(refreshTokenRepository, never()).delete(any(RefreshToken.class));
        }

        @Test
        @DisplayName("이미 만료된 액세스 토큰은 블랙리스트에 넣지 않는다")
        void doesNotBlacklistExpiredAccessToken() {
            String expiredAccessToken = expiredTokenizer.createAccessToken(
                    String.valueOf(USER_ID), Map.of("authority", Authority.ROLE_MEMBER));

            assertThatCode(() -> jwtTokenService.logout(expiredAccessToken, USER_ID, null))
                    .as("만료 토큰으로 로그아웃해도 500 이 나면 안 된다")
                    .doesNotThrowAnyException();

            verify(redisTokenService, never()).addToBlacklist(anyString(), anyLong());
        }

        @Test
        @DisplayName("형식이 깨진 액세스 토큰으로 로그아웃해도 예외 없이 끝난다")
        void toleratesMalformedAccessToken() {
            assertThatCode(() -> jwtTokenService.logout("Bearer not-a-jwt", USER_ID, null))
                    .doesNotThrowAnyException();

            verify(redisTokenService, never()).addToBlacklist(anyString(), anyLong());
        }

        @Test
        @DisplayName("Redis 가 죽어 블랙리스트 등록이 실패해도 로그아웃은 성공 처리한다")
        void survivesRedisFailure() {
            String accessToken = jwtTokenizer.createAccessToken(
                    String.valueOf(USER_ID), Map.of("authority", Authority.ROLE_MEMBER));
            willThrow(new IllegalStateException("redis down"))
                    .given(redisTokenService).addToBlacklist(anyString(), anyLong());

            assertThatCode(() -> jwtTokenService.logout(accessToken, USER_ID, null))
                    .as("로그아웃이 실패하면 사용자는 앱에서 빠져나가지 못한다")
                    .doesNotThrowAnyException();
        }
    }
}
