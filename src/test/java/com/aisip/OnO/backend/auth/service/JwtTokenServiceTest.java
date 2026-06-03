package com.aisip.OnO.backend.auth.service;

import com.aisip.OnO.backend.auth.dto.TokenResponseDto;
import com.aisip.OnO.backend.auth.entity.Authority;
import com.aisip.OnO.backend.auth.entity.RefreshToken;
import com.aisip.OnO.backend.auth.repository.RefreshTokenRepository;
import com.aisip.OnO.backend.util.redis.RedisTokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class JwtTokenServiceTest {

    private static final String ACCESS_SECRET =
            "dGVzdC1hY2Nlc3MtdG9rZW4tc2VjcmV0LXRlc3QtYWNjZXNzLXRva2VuLXNlY3JldC0zMmJ5dGVz";
    private static final String REFRESH_SECRET =
            "dGVzdC1yZWZyZXNoLXRva2VuLXNlY3JldC10ZXN0LXJlZnJlc2gtdG9rZW4tc2VjcmV0LTMyYnl0ZXM=";

    @Mock
    private RedisTokenService redisTokenService;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    private JwtTokenizer jwtTokenizer;
    private JwtTokenService jwtTokenService;

    @BeforeEach
    void setUp() {
        jwtTokenizer = new JwtTokenizer(
                1_800_000L,
                60_480_000_000L,
                ACCESS_SECRET,
                REFRESH_SECRET
        );
        jwtTokenService = new JwtTokenService(jwtTokenizer, redisTokenService, refreshTokenRepository);
    }

    @Test
    @DisplayName("로그인 시 기존 세션을 덮어쓰지 않고 새 refresh token row를 저장한다")
    void generateTokensCreatesNewRefreshTokenSession() {
        // given
        Long userId = 1L;
        Authority authority = Authority.ROLE_MEMBER;
        given(refreshTokenRepository.save(any(RefreshToken.class))).willAnswer(invocation -> invocation.getArgument(0));

        // when
        TokenResponseDto response = jwtTokenService.generateTokens(userId, authority);

        // then
        assertThat(response.getAccessToken()).startsWith(JwtTokenizer.BEARER_PREFIX);

        ArgumentCaptor<RefreshToken> tokenCaptor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).save(tokenCaptor.capture());
        verify(refreshTokenRepository, never()).findByUserId(userId);
        verify(redisTokenService, never()).saveRefreshToken(eq(userId), eq(response.getRefreshToken()), any(Long.class));

        RefreshToken savedRefreshToken = tokenCaptor.getValue();
        assertThat(savedRefreshToken.getUserId()).isEqualTo(userId);
        assertThat(savedRefreshToken.getAuthority()).isEqualTo(authority);
        assertThat(savedRefreshToken.getRefreshToken()).isEqualTo(response.getRefreshToken());
    }

    @Test
    @DisplayName("refresh API 호출 시 제출된 refresh token 세션만 회전한다")
    void refreshAccessTokenRotatesCurrentRefreshTokenSession() {
        // given
        Long userId = 1L;
        Authority authority = Authority.ROLE_MEMBER;
        String oldRefreshToken = jwtTokenizer.createRefreshToken(String.valueOf(userId), Map.of("authority", authority));
        RefreshToken existingRefreshToken = RefreshToken.from(userId, authority, oldRefreshToken);

        given(refreshTokenRepository.findByRefreshToken(oldRefreshToken)).willReturn(Optional.of(existingRefreshToken));
        given(refreshTokenRepository.save(any(RefreshToken.class))).willAnswer(invocation -> invocation.getArgument(0));

        // when
        TokenResponseDto response = jwtTokenService.refreshAccessToken(oldRefreshToken);

        // then
        assertThat(response.getAccessToken()).startsWith(JwtTokenizer.BEARER_PREFIX);
        assertThat(response.getRefreshToken()).isNotEqualTo(oldRefreshToken);
        assertThat(existingRefreshToken.getRefreshToken()).isEqualTo(response.getRefreshToken());

        verify(refreshTokenRepository).save(existingRefreshToken);
        verify(refreshTokenRepository, never()).findByUserId(userId);
        verify(redisTokenService, never()).saveRefreshToken(eq(userId), eq(response.getRefreshToken()), any(Long.class));
    }
}
