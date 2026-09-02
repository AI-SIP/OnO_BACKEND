package com.aisip.OnO.backend.auth.service;

import com.aisip.OnO.backend.auth.dto.TokenRequestDto;
import com.aisip.OnO.backend.auth.dto.TokenResponseDto;
import com.aisip.OnO.backend.auth.entity.Authority;
import com.aisip.OnO.backend.auth.repository.RefreshTokenRepository;
import com.aisip.OnO.backend.support.IntegrationTestSupport;
import com.aisip.OnO.backend.user.dto.UserRegisterDto;
import com.aisip.OnO.backend.user.entity.User;
import com.aisip.OnO.backend.user.repository.UserRepository;
import com.aisip.OnO.backend.user.service.UserService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * 소셜 로그인 → 토큰 발급 → 저장 → 갱신까지 실제 MySQL 위에서 왕복시킨다.
 *
 * <p>프로덕션에서 관측된 1002(리프레시 토큰 정보를 찾을 수 없습니다) 는
 * "발급한 토큰이 DB 에 그대로 저장되지 않는" 경우에도 생긴다.
 * refresh_token 컬럼이 varchar 이므로 토큰이 컬럼보다 길면 저장이 깨진다.
 */
@DisplayName("소셜 로그인·토큰 저장 통합")
class UserAuthServiceIntegrationTest extends IntegrationTestSupport {

    @Autowired
    private UserAuthService userAuthService;

    @Autowired
    private JwtTokenService jwtTokenService;

    @Autowired
    private JwtTokenizer jwtTokenizer;

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @PersistenceContext
    private EntityManager entityManager;

    private static UserRegisterDto socialLogin(String identifier, String platform) {
        return UserRegisterDto.builder()
                .identifier(identifier)
                .platform(platform)
                .name("소셜사용자")
                .email(identifier + "@test.ono")
                .build();
    }

    private int refreshTokenColumnLength() {
        Number length = (Number) entityManager.createNativeQuery("""
                        SELECT character_maximum_length
                        FROM information_schema.columns
                        WHERE table_schema = DATABASE()
                          AND table_name = 'refresh_token'
                          AND column_name = 'refresh_token'
                        """)
                .getSingleResult();
        return length.intValue();
    }

    @Nested
    @DisplayName("가입")
    class SignUp {

        @Test
        @DisplayName("게스트 가입은 사용자와 리프레시 토큰 세션을 함께 남긴다")
        void guestSignUpPersistsUserAndSession() {
            TokenResponseDto tokens = userAuthService.signUpGuestUser();

            assertThat(userRepository.count()).isEqualTo(1);
            assertThat(refreshTokenRepository.findByRefreshToken(tokens.getRefreshToken()))
                    .isPresent()
                    .get()
                    .satisfies(session -> assertThat(session.getAuthority()).isEqualTo(Authority.ROLE_GUEST));
        }

        @ParameterizedTest(name = "{0} 소셜 로그인으로 가입한다")
        @ValueSource(strings = {"GOOGLE", "APPLE", "KAKAO"})
        void memberSignUpPerPlatform(String platform) {
            String identifier = platform.toLowerCase() + "-sub-abc123";

            TokenResponseDto tokens = userAuthService.signUpMemberUser(socialLogin(identifier, platform));

            assertThat(jwtTokenizer.getAuthorityFromRefreshToken(tokens.getRefreshToken()))
                    .isEqualTo(Authority.ROLE_MEMBER);
            assertThat(userRepository.findByIdentifier(identifier))
                    .isPresent()
                    .get()
                    .satisfies(user -> assertThat(user.getPlatform()).isEqualTo(platform));
        }

        @Test
        @DisplayName("같은 소셜 계정으로 다시 로그인하면 계정을 새로 만들지 않는다")
        void reloginKeepsSingleAccount() {
            userAuthService.signUpMemberUser(socialLogin("google-sub-relogin", "GOOGLE"));
            userAuthService.signUpMemberUser(socialLogin("google-sub-relogin", "GOOGLE"));

            assertThat(userRepository.count()).isEqualTo(1);
        }

        @Test
        @DisplayName("탈퇴한 사용자는 같은 소셜 계정으로 다시 가입할 수 있다")
        void allowsRejoinAfterWithdrawal() {
            userAuthService.signUpMemberUser(socialLogin("google-sub-rejoin", "GOOGLE"));
            Long userId = userRepository.findByIdentifier("google-sub-rejoin").orElseThrow().getId();

            userService.deleteUserById(userId);

            assertThatCode(() -> userAuthService.signUpMemberUser(socialLogin("google-sub-rejoin", "GOOGLE")))
                    .as("탈퇴 후 재가입이 막히면 사용자는 영영 서비스를 못 쓴다")
                    .doesNotThrowAnyException();

            User rejoined = userRepository.findByIdentifier("google-sub-rejoin").orElseThrow();
            assertThat(rejoined.getId())
                    .as("재가입은 새 계정이어야 한다")
                    .isNotEqualTo(userId);
            assertThat(userRepository.findById(userId))
                    .as("탈퇴한 계정은 조회에서 빠진다")
                    .isEmpty();
        }
    }

    @Nested
    @DisplayName("리프레시 토큰 저장")
    class RefreshTokenPersistence {

        @Test
        @DisplayName("발급한 토큰이 refresh_token 컬럼에 잘리지 않고 그대로 저장된다")
        void storesTokenWithoutTruncation() {
            TokenResponseDto tokens = userAuthService.signUpGuestUser();

            String stored = (String) entityManager
                    .createNativeQuery("SELECT refresh_token FROM refresh_token WHERE user_id IS NOT NULL LIMIT 1")
                    .getSingleResult();

            assertThat(stored)
                    .as("한 글자라도 잘리면 이후 갱신 요청이 전부 1002 가 된다")
                    .isEqualTo(tokens.getRefreshToken());
        }

        @Test
        @DisplayName("발급 가능한 최대 토큰 길이가 refresh_token 컬럼 길이 안에 들어간다")
        void tokenFitsInColumn() {
            int columnLength = refreshTokenColumnLength();
            int longestToken = IntStream.range(0, 30)
                    .map(i -> jwtTokenizer.createRefreshToken(
                            String.valueOf(Long.MAX_VALUE),
                            java.util.Map.of("authority", Authority.ROLE_MEMBER)).length())
                    .max()
                    .orElseThrow();

            System.out.printf("refresh_token 컬럼=%d, 발급 토큰 최대 길이=%d, 여유=%d%n",
                    columnLength, longestToken, columnLength - longestToken);

            assertThat(longestToken)
                    .as("컬럼(%d)보다 긴 토큰은 저장되지 못하고 갱신이 1002 로 실패한다", columnLength)
                    .isLessThanOrEqualTo(columnLength);
            assertThat(columnLength - longestToken)
                    .as("클레임이 하나만 늘어도 넘칠 만큼 여유가 없으면 안 된다")
                    .isGreaterThanOrEqualTo(64);
        }

        @Test
        @DisplayName("저장한 토큰 문자열로 세션을 다시 찾아 갱신까지 왕복한다")
        void roundTripsThroughDatabase() {
            TokenResponseDto issued = userAuthService.signUpGuestUser();

            TokenResponseDto refreshed = userAuthService.refreshAccessToken(
                    new TokenRequestDto(issued.getAccessToken(), issued.getRefreshToken()));

            assertThat(refreshTokenRepository.findByRefreshToken(refreshed.getRefreshToken()))
                    .as("회전된 새 토큰도 그대로 저장돼야 다음 갱신이 이어진다")
                    .isPresent();
            assertThat(refreshTokenRepository.findByRefreshToken(issued.getRefreshToken())).isEmpty();
            assertThat(refreshTokenRepository.count())
                    .as("갱신은 세션 수를 늘리지 않고 회전만 시킨다")
                    .isEqualTo(1);
        }

        @Test
        @DisplayName("여러 기기에서 로그인하면 세션이 기기 수만큼 쌓이고 각자 갱신된다")
        void keepsIndependentSessionsPerDevice() {
            TokenResponseDto phone = userAuthService.signUpMemberUser(socialLogin("google-sub-devices", "GOOGLE"));
            TokenResponseDto tablet = userAuthService.signUpMemberUser(socialLogin("google-sub-devices", "GOOGLE"));

            assertThat(refreshTokenRepository.count()).isEqualTo(2);

            userAuthService.refreshAccessToken(new TokenRequestDto(null, phone.getRefreshToken()));

            assertThat(refreshTokenRepository.findByRefreshToken(tablet.getRefreshToken()))
                    .as("한 기기의 갱신이 다른 기기 세션을 무효화하면 안 된다")
                    .isPresent();
        }

        @Test
        @DisplayName("로그아웃하면 세션 행이 사라진다")
        void deletesSessionOnLogout() {
            TokenResponseDto tokens = userAuthService.signUpGuestUser();
            Long userId = jwtTokenizer.getUserIdFromRefreshToken(tokens.getRefreshToken());

            jwtTokenService.logout(tokens.getAccessToken(), userId, tokens.getRefreshToken());

            assertThat(refreshTokenRepository.findByRefreshToken(tokens.getRefreshToken())).isEmpty();
        }
    }
}
