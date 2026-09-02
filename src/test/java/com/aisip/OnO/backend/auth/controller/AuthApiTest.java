package com.aisip.OnO.backend.auth.controller;

import com.aisip.OnO.backend.auth.dto.TokenRequestDto;
import com.aisip.OnO.backend.auth.entity.Authority;
import com.aisip.OnO.backend.auth.exception.AuthErrorCase;
import com.aisip.OnO.backend.auth.repository.RefreshTokenRepository;
import com.aisip.OnO.backend.auth.service.JwtTokenizer;
import com.aisip.OnO.backend.support.IntegrationTestSupport;
import com.aisip.OnO.backend.user.dto.UserRegisterDto;
import com.aisip.OnO.backend.user.exception.UserErrorCase;
import com.aisip.OnO.backend.user.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Date;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 인증 API 의 HTTP 계약을 끝에서 끝까지 확인한다.
 *
 * <p>여기서 검증하는 것은 상태코드와 errorCode 조합이다.
 * 프론트(HttpService)는 <b>errorCode 1005 또는 errorCode 없는 401</b> 일 때만 토큰 갱신을 시도하고,
 * 1001·1002·1003·1004·1006 은 재로그인으로 처리한다. 이 대응이 어긋나면
 * 정상 사용자가 갱신 기회를 잃고 로그아웃당한다.
 */
@DisplayName("인증 API")
class AuthApiTest extends IntegrationTestSupport {

    private static final String PROTECTED_API = "/api/users";
    private static final String OTHER_SECRET =
            "b3RoZXItc2VjcmV0LWtleS1vdGhlci1zZWNyZXQta2V5LW90aGVyLXNlY3JldC0zMmJ5dGVz";

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Value("${jwt.accessToken.secret}")
    private String accessSecret;

    @Value("${jwt.refreshToken.secret}")
    private String refreshSecret;

    /** 이미 만료된 토큰만 발급한다. 서명 키는 애플리케이션과 동일하다. */
    private JwtTokenizer expiredTokenizer;

    /** 서명은 유효하지만 DB 에 세션이 없는 토큰을 만들 때 쓴다. */
    private JwtTokenizer validTokenizer;

    @BeforeEach
    void setUpTokenizers() {
        expiredTokenizer = new JwtTokenizer(-60_000L, -60_000L, accessSecret, refreshSecret);
        validTokenizer = new JwtTokenizer(1_800_000L, 604_800_000L, accessSecret, refreshSecret);
    }

    private JsonNode dataOf(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8)).path("data");
    }

    private JsonNode signUpGuest() throws Exception {
        return dataOf(mockMvc.perform(post("/api/auth/signup/guest"))
                .andExpect(status().isOk())
                .andReturn());
    }

    private JsonNode signUpMember(String identifier, String platform) throws Exception {
        return dataOf(mockMvc.perform(post("/api/auth/signup/member")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(UserRegisterDto.builder()
                                .identifier(identifier)
                                .platform(platform)
                                .name("소셜사용자")
                                .email(identifier + "@test.ono")
                                .build())))
                .andExpect(status().isOk())
                .andReturn());
    }

    private ResultActions callProtectedApi(String authorizationHeader) throws Exception {
        return authorizationHeader == null
                ? mockMvc.perform(get(PROTECTED_API))
                : mockMvc.perform(get(PROTECTED_API).header("Authorization", authorizationHeader));
    }

    private ResultActions refresh(String refreshToken) throws Exception {
        return mockMvc.perform(post("/api/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new TokenRequestDto(null, refreshToken))));
    }

    @Nested
    @DisplayName("가입과 로그인")
    class SignUp {

        @Test
        @DisplayName("게스트 가입은 사용자와 리프레시 토큰 세션을 함께 만든다")
        void guestSignUpCreatesUserAndSession() throws Exception {
            JsonNode tokens = signUpGuest();

            assertThat(tokens.path("accessToken").asText()).startsWith("Bearer ");
            assertThat(tokens.path("refreshToken").asText()).isNotBlank();
            assertThat(userRepository.count()).isEqualTo(1);
            assertThat(refreshTokenRepository.findByRefreshToken(tokens.path("refreshToken").asText()))
                    .as("세션이 저장되지 않으면 첫 갱신부터 1002 가 난다")
                    .isPresent();
        }

        @Test
        @DisplayName("게스트 토큰에는 ROLE_GUEST 권한이 담긴다")
        void guestTokenCarriesGuestAuthority() throws Exception {
            String accessToken = signUpGuest().path("accessToken").asText();

            assertThat(validTokenizer.getAuthorityFromAccessToken(accessToken.replace("Bearer ", "")))
                    .isEqualTo(Authority.ROLE_GUEST);
        }

        @ParameterizedTest(name = "{0} 로그인으로 가입하면 멤버 토큰을 받는다")
        @ValueSource(strings = {"GOOGLE", "APPLE"})
        void memberSignUpPerPlatform(String platform) throws Exception {
            JsonNode tokens = signUpMember(platform.toLowerCase() + "-sub-1", platform);

            assertThat(validTokenizer.getAuthorityFromAccessToken(
                    tokens.path("accessToken").asText().replace("Bearer ", "")))
                    .isEqualTo(Authority.ROLE_MEMBER);
            assertThat(userRepository.findByIdentifier(platform.toLowerCase() + "-sub-1"))
                    .isPresent()
                    .get()
                    .satisfies(user -> assertThat(user.getPlatform()).isEqualTo(platform));
        }

        @Test
        @DisplayName("같은 소셜 계정으로 다시 로그인해도 계정이 새로 생기지 않는다")
        void reloginDoesNotDuplicateAccount() throws Exception {
            JsonNode first = signUpMember("google-sub-repeat", "GOOGLE");
            JsonNode second = signUpMember("google-sub-repeat", "GOOGLE");

            assertThat(userRepository.count())
                    .as("같은 identifier 로 계정이 갈리면 사용자는 자기 오답노트를 잃는다")
                    .isEqualTo(1);
            assertThat(second.path("accessToken").asText()).isNotBlank();
            assertThat(validTokenizer.getUserIdFromAccessToken(
                    second.path("accessToken").asText().replace("Bearer ", "")))
                    .isEqualTo(validTokenizer.getUserIdFromAccessToken(
                            first.path("accessToken").asText().replace("Bearer ", "")));
        }

        @Test
        @DisplayName("로그인할 때마다 기기별 리프레시 토큰 세션이 따로 쌓인다")
        void keepsSessionPerLogin() throws Exception {
            JsonNode phone = signUpMember("google-sub-multi", "GOOGLE");
            JsonNode tablet = signUpMember("google-sub-multi", "GOOGLE");

            assertThat(refreshTokenRepository.findByRefreshToken(phone.path("refreshToken").asText()))
                    .as("나중 로그인이 앞선 기기의 세션을 지우면 그 기기는 1002 로 튕긴다")
                    .isPresent();
            assertThat(refreshTokenRepository.findByRefreshToken(tablet.path("refreshToken").asText())).isPresent();
        }

        @Test
        @DisplayName("본문이 없는 가입 요청은 400 으로 거절한다")
        void rejectsEmptyBody() throws Exception {
            mockMvc.perform(post("/api/auth/signup/member").contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isBadRequest());
        }

        /**
         * 프로덕션에서 관측된 "소셜 로그인 실패. 잘못된 유저 정보입니다" 계열 요청.
         * identifier 없이 가입시키면 다음 로그인에서 같은 계정을 찾지 못해 계정이 계속 늘어난다.
         */
        @Test
        @DisplayName("identifier 없는 소셜 로그인은 계정을 만들지 않고 400 + 3002 로 거절한다")
        void rejectsSignUpWithoutIdentifier() throws Exception {
            mockMvc.perform(post("/api/auth/signup/member")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(UserRegisterDto.builder()
                                    .platform("GOOGLE")
                                    .name("소셜사용자")
                                    .email("no-identifier@test.ono")
                                    .build())))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode")
                            .value(UserErrorCase.INVALID_USER_IDENTIFIER.getErrorCode()));

            assertThat(userRepository.count())
                    .as("잘못된 요청으로 유령 계정이 생기면 안 된다")
                    .isZero();
        }

        @Test
        @DisplayName("빈 identifier 로 두 번 요청해도 계정이 쌓이지 않는다")
        void rejectsBlankIdentifierRepeatedly() throws Exception {
            for (int i = 0; i < 2; i++) {
                mockMvc.perform(post("/api/auth/signup/member")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(UserRegisterDto.builder()
                                        .identifier("   ")
                                        .platform("APPLE")
                                        .name("소셜사용자")
                                        .build())))
                        .andExpect(status().isBadRequest());
            }

            assertThat(userRepository.count()).isZero();
        }
    }

    @Nested
    @DisplayName("액세스 토큰 검증 계약")
    class AccessTokenContract {

        @Test
        @DisplayName("정상 토큰은 보호된 API 를 통과한다")
        void allowsValidToken() throws Exception {
            String accessToken = signUpGuest().path("accessToken").asText();

            callProtectedApi(accessToken)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.userId").isNumber());
        }

        /**
         * 프론트는 1005 를 받아야 refreshAccessToken() 을 호출한다.
         * 여기가 1007 로 나가면 만료된 사용자는 갱신 없이 인증 실패 처리된다.
         */
        @Test
        @DisplayName("만료된 액세스 토큰은 401 + 1005 로 응답해 프론트가 갱신하게 한다")
        void expiredAccessTokenReturnsAccessTokenExpired() throws Exception {
            String expired = expiredTokenizer.createAccessToken("1", Map.of("authority", Authority.ROLE_MEMBER));

            callProtectedApi(expired)
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.errorCode").value(AuthErrorCase.ACCESS_TOKEN_EXPIRED.getErrorCode()))
                    .andExpect(jsonPath("$.message").value(AuthErrorCase.ACCESS_TOKEN_EXPIRED.getMessage()));
        }

        @Test
        @DisplayName("Authorization 헤더가 없으면 401 + 1007 로 응답한다")
        void missingHeaderReturnsAuthenticationFailed() throws Exception {
            callProtectedApi(null)
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.errorCode").value(AuthErrorCase.AUTHENTICATION_FAILED.getErrorCode()));
        }

        @Test
        @DisplayName("Bearer 프리픽스 없이 토큰만 보내면 401 + 1007 로 응답한다")
        void missingBearerPrefixReturnsAuthenticationFailed() throws Exception {
            String rawToken = signUpGuest().path("accessToken").asText().replace("Bearer ", "");

            callProtectedApi(rawToken)
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.errorCode").value(AuthErrorCase.AUTHENTICATION_FAILED.getErrorCode()));
        }

        @ParameterizedTest(name = "형식이 깨진 헤더 [{0}] 는 401 + 1007 로 응답한다")
        @ValueSource(strings = {"Bearer ", "Bearer not-a-jwt", "Bearer a.b.c", "Basic dXNlcjpwYXNz"})
        void malformedHeaderReturnsAuthenticationFailed(String header) throws Exception {
            callProtectedApi(header)
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.errorCode").value(AuthErrorCase.AUTHENTICATION_FAILED.getErrorCode()));
        }

        @Test
        @DisplayName("다른 키로 서명한 위조 토큰은 401 + 1007 로 거절한다")
        void forgedTokenReturnsAuthenticationFailed() throws Exception {
            String forged = "Bearer " + Jwts.builder()
                    .setClaims(Map.of("authority", Authority.ROLE_ADMIN))
                    .setSubject("1")
                    .setExpiration(new Date(System.currentTimeMillis() + 600_000))
                    .signWith(Keys.hmacShaKeyFor(Base64.getDecoder().decode(OTHER_SECRET)), SignatureAlgorithm.HS256)
                    .compact();

            callProtectedApi(forged)
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.errorCode").value(AuthErrorCase.AUTHENTICATION_FAILED.getErrorCode()));
        }

        @Test
        @DisplayName("리프레시 토큰으로는 보호된 API 를 호출할 수 없다")
        void refreshTokenCannotAccessApi() throws Exception {
            String refreshToken = signUpGuest().path("refreshToken").asText();

            callProtectedApi("Bearer " + refreshToken)
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.errorCode").value(AuthErrorCase.AUTHENTICATION_FAILED.getErrorCode()));
        }

        @Test
        @DisplayName("서명은 맞지만 존재하지 않는 사용자의 토큰은 인증을 통과한 뒤 사용자 조회에서 걸린다")
        void tokenOfDeletedUserFailsAtLookup() throws Exception {
            String orphanToken = validTokenizer.createAccessToken("999999", Map.of("authority", Authority.ROLE_MEMBER));

            callProtectedApi(orphanToken)
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.errorCode").isNumber());
        }
    }

    @Nested
    @DisplayName("토큰 갱신")
    class RefreshToken {

        @Test
        @DisplayName("리프레시 토큰으로 액세스·리프레시 토큰을 함께 재발급한다")
        void reissuesBothTokens() throws Exception {
            JsonNode issued = signUpGuest();
            String oldRefreshToken = issued.path("refreshToken").asText();

            JsonNode refreshed = dataOf(refresh(oldRefreshToken).andExpect(status().isOk()).andReturn());

            assertThat(refreshed.path("accessToken").asText()).startsWith("Bearer ");
            assertThat(refreshed.path("refreshToken").asText()).isNotEqualTo(oldRefreshToken);
            callProtectedApi(refreshed.path("accessToken").asText()).andExpect(status().isOk());
        }

        @Test
        @DisplayName("갱신해도 권한은 그대로 유지된다")
        void keepsAuthorityAcrossRefresh() throws Exception {
            String refreshToken = signUpGuest().path("refreshToken").asText();

            JsonNode refreshed = dataOf(refresh(refreshToken).andExpect(status().isOk()).andReturn());

            assertThat(validTokenizer.getAuthorityFromAccessToken(
                    refreshed.path("accessToken").asText().replace("Bearer ", "")))
                    .as("갱신 한 번으로 권한이 올라가면 안 된다")
                    .isEqualTo(Authority.ROLE_GUEST);
        }

        @Test
        @DisplayName("회전된 옛 리프레시 토큰을 다시 쓰면 404 + 1002 로 거절한다")
        void rejectsRotatedRefreshToken() throws Exception {
            String oldRefreshToken = signUpGuest().path("refreshToken").asText();
            refresh(oldRefreshToken).andExpect(status().isOk());

            refresh(oldRefreshToken)
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.errorCode").value(AuthErrorCase.REFRESH_TOKEN_NOT_FOUND.getErrorCode()));
        }

        @Test
        @DisplayName("서명은 유효하지만 저장된 적 없는 리프레시 토큰은 404 + 1002 로 거절한다")
        void rejectsUnknownRefreshToken() throws Exception {
            String neverStored = validTokenizer.createRefreshToken("1", Map.of("authority", Authority.ROLE_MEMBER));

            refresh(neverStored)
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.errorCode").value(AuthErrorCase.REFRESH_TOKEN_NOT_FOUND.getErrorCode()));
        }

        @Test
        @DisplayName("만료된 리프레시 토큰은 401 + 1006 으로 거절한다")
        void rejectsExpiredRefreshToken() throws Exception {
            String expired = expiredTokenizer.createRefreshToken("1", Map.of("authority", Authority.ROLE_MEMBER));

            refresh(expired)
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.errorCode").value(AuthErrorCase.REFRESH_TOKEN_EXPIRED.getErrorCode()));
        }

        @ParameterizedTest(name = "형식이 깨진 리프레시 토큰 [{0}] 은 400 + 1001 로 거절한다")
        @ValueSource(strings = {"not-a-jwt", "a.b.c", ""})
        void rejectsMalformedRefreshToken(String refreshToken) throws Exception {
            refresh(refreshToken)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value(AuthErrorCase.INVALID_REFRESH_TOKEN.getErrorCode()));
        }

        @Test
        @DisplayName("refreshToken 을 빠뜨린 요청도 500 이 아니라 400 + 1001 로 거절한다")
        void rejectsMissingRefreshToken() throws Exception {
            refresh(null)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value(AuthErrorCase.INVALID_REFRESH_TOKEN.getErrorCode()));
        }

        @Test
        @DisplayName("갱신은 인증 없이도 호출할 수 있다")
        void doesNotRequireAccessToken() throws Exception {
            String refreshToken = signUpGuest().path("refreshToken").asText();

            refresh(refreshToken).andExpect(status().isOk());
        }
    }

    @Nested
    @DisplayName("로그아웃")
    class Logout {

        private ResultActions logout(String accessToken, String refreshToken) throws Exception {
            return mockMvc.perform(post("/api/auth/logout")
                    .header("Authorization", accessToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(new TokenRequestDto(accessToken, refreshToken))));
        }

        @Test
        @DisplayName("로그아웃하면 세션이 지워지고 액세스 토큰은 블랙리스트로 막힌다")
        void invalidatesSessionAndAccessToken() throws Exception {
            JsonNode tokens = signUpGuest();
            String accessToken = tokens.path("accessToken").asText();
            String refreshToken = tokens.path("refreshToken").asText();

            logout(accessToken, refreshToken).andExpect(status().isOk());

            assertThat(refreshTokenRepository.findByRefreshToken(refreshToken)).isEmpty();
            callProtectedApi(accessToken)
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.errorCode").value(AuthErrorCase.INVALID_ACCESS_TOKEN.getErrorCode()));
        }

        @Test
        @DisplayName("로그아웃한 리프레시 토큰으로는 갱신할 수 없다")
        void cannotRefreshAfterLogout() throws Exception {
            JsonNode tokens = signUpGuest();
            logout(tokens.path("accessToken").asText(), tokens.path("refreshToken").asText())
                    .andExpect(status().isOk());

            refresh(tokens.path("refreshToken").asText())
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.errorCode").value(AuthErrorCase.REFRESH_TOKEN_NOT_FOUND.getErrorCode()));
        }

        @Test
        @DisplayName("리프레시 토큰 없이 로그아웃해도 액세스 토큰은 막힌다")
        void worksWithoutRefreshToken() throws Exception {
            String accessToken = signUpGuest().path("accessToken").asText();

            mockMvc.perform(post("/api/auth/logout").header("Authorization", accessToken))
                    .andExpect(status().isOk());

            callProtectedApi(accessToken).andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("남의 리프레시 토큰을 넣어 로그아웃해도 그 사람의 세션은 살아 있다")
        void cannotKillOtherUsersSession() throws Exception {
            JsonNode victim = signUpMember("google-sub-victim", "GOOGLE");
            JsonNode attacker = signUpMember("google-sub-attacker", "GOOGLE");

            logout(attacker.path("accessToken").asText(), victim.path("refreshToken").asText())
                    .andExpect(status().isOk());

            assertThat(refreshTokenRepository.findByRefreshToken(victim.path("refreshToken").asText()))
                    .as("남의 세션을 지울 수 있으면 토큰 문자열만으로 강제 로그아웃이 가능해진다")
                    .isPresent();
            refresh(victim.path("refreshToken").asText()).andExpect(status().isOk());
        }

        /**
         * 계약 불일치(현재 동작을 그대로 고정한다).
         *
         * <p>CustomAuthenticationEntryPoint 는 요청 URI 가 {@code /api/auth} 로 시작하면
         * 아무것도 쓰지 않고 반환한다. 그래서 인증 실패한 로그아웃은 401 + 1007 이 아니라
         * <b>200 + 빈 본문</b>으로 나간다. 프론트는 errorCode 가 없는 응답을 받게 되고,
         * 이것이 "errorCode: null 인 UnauthorizedException" 관측과 맞물린다.
         * 수정 지점은 common/auth 패키지라 여기서는 손대지 않고 동작만 못 박는다.
         */
        @Test
        @DisplayName("만료된 액세스 토큰으로 로그아웃하면 401 이 아니라 200 빈 본문이 나간다")
        void expiredAccessTokenLogoutReturnsEmptyOk() throws Exception {
            JsonNode tokens = signUpGuest();
            String expiredAccessToken = expiredTokenizer.createAccessToken(
                    "1", Map.of("authority", Authority.ROLE_GUEST));

            MvcResult result = mockMvc.perform(post("/api/auth/logout")
                            .header("Authorization", expiredAccessToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(
                                    new TokenRequestDto(expiredAccessToken, tokens.path("refreshToken").asText()))))
                    .andExpect(status().isOk())
                    .andReturn();

            assertThat(result.getResponse().getContentAsString(StandardCharsets.UTF_8))
                    .as("본문이 비어 있어 프론트는 errorCode 없이 이 응답을 해석해야 한다")
                    .isEmpty();
            assertThat(refreshTokenRepository.findByRefreshToken(tokens.path("refreshToken").asText()))
                    .as("인증이 안 됐으므로 컨트롤러까지 가지 못해 세션은 그대로 남는다")
                    .isPresent();
        }

        @Test
        @DisplayName("인증 없이 로그아웃해도 401 이 아니라 200 빈 본문이 나간다")
        void anonymousLogoutReturnsEmptyOk() throws Exception {
            MvcResult result = mockMvc.perform(post("/api/auth/logout"))
                    .andExpect(status().isOk())
                    .andReturn();

            assertThat(result.getResponse().getContentAsString(StandardCharsets.UTF_8)).isEmpty();
        }
    }

    @Nested
    @DisplayName("권한 경계")
    class AuthorityBoundary {

        @Test
        @DisplayName("게스트 토큰도 사용자 API 는 쓸 수 있다")
        void guestCanUseUserApi() throws Exception {
            callProtectedApi(signUpGuest().path("accessToken").asText()).andExpect(status().isOk());
        }

        @Test
        @DisplayName("멤버 토큰으로 관리자 경로에 접근하면 403 + 1008 로 막는다")
        void memberCannotAccessAdminPath() throws Exception {
            String memberToken = signUpMember("google-sub-member", "GOOGLE").path("accessToken").asText();

            mockMvc.perform(get("/admin/main").header("Authorization", memberToken))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.errorCode").value(AuthErrorCase.ACCESS_DENIED.getErrorCode()));
        }

        @Test
        @DisplayName("인증 없이 관리자 경로에 접근하면 401 + 1007 로 막는다")
        void anonymousCannotAccessAdminPath() throws Exception {
            mockMvc.perform(get("/admin/main"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.errorCode").value(AuthErrorCase.AUTHENTICATION_FAILED.getErrorCode()));
        }
    }
}
