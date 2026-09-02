package com.aisip.OnO.backend.auth.service;

import com.aisip.OnO.backend.auth.dto.TokenRequestDto;
import com.aisip.OnO.backend.auth.dto.TokenResponseDto;
import com.aisip.OnO.backend.auth.entity.Authority;
import com.aisip.OnO.backend.common.exception.ApplicationException;
import com.aisip.OnO.backend.user.dto.UserRegisterDto;
import com.aisip.OnO.backend.user.dto.UserResponseDto;
import com.aisip.OnO.backend.user.exception.UserErrorCase;
import com.aisip.OnO.backend.user.service.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * 가입 경로별로 부여되는 권한과, 인증 흐름의 위임 관계를 고정한다.
 *
 * <p>게스트에게 ROLE_MEMBER 가 새어나가면 게스트 계정으로 멤버 전용 경로가 열린다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("UserAuthService")
class UserAuthServiceTest {

    @Mock
    private JwtTokenService jwtTokenService;

    @Mock
    private UserService userService;

    @InjectMocks
    private UserAuthService userAuthService;

    private static UserResponseDto userResponse(Long userId) {
        return new UserResponseDto(
                userId, "홍길동", "user@test.ono", null,
                1L, 0L, 1L, 0L, 1L, 0L, 1L, 0L, 1L, 0L, 40L,
                LocalDateTime.now(), LocalDateTime.now()
        );
    }

    @Nested
    @DisplayName("게스트 가입")
    class SignUpGuest {

        @Test
        @DisplayName("게스트 사용자를 만들고 ROLE_GUEST 토큰을 발급한다")
        void issuesGuestAuthorityToken() {
            given(userService.registerGuestUser()).willReturn(userResponse(10L));
            given(jwtTokenService.generateTokens(10L, Authority.ROLE_GUEST))
                    .willReturn(new TokenResponseDto("Bearer access", "refresh"));

            TokenResponseDto response = userAuthService.signUpGuestUser();

            assertThat(response.getAccessToken()).isEqualTo("Bearer access");
            verify(jwtTokenService).generateTokens(10L, Authority.ROLE_GUEST);
            verify(jwtTokenService, never()).generateTokens(anyLong(), eq(Authority.ROLE_MEMBER));
        }
    }

    @Nested
    @DisplayName("멤버 가입")
    class SignUpMember {

        @Test
        @DisplayName("소셜 로그인 정보를 넘기면 ROLE_MEMBER 토큰을 발급한다")
        void issuesMemberAuthorityToken() {
            UserRegisterDto registerDto = UserRegisterDto.builder()
                    .email("member@test.ono")
                    .name("홍길동")
                    .identifier("google-sub-123")
                    .platform("GOOGLE")
                    .build();
            given(userService.registerMemberUser(registerDto)).willReturn(userResponse(20L));
            given(jwtTokenService.generateTokens(20L, Authority.ROLE_MEMBER))
                    .willReturn(new TokenResponseDto("Bearer access", "refresh"));

            TokenResponseDto response = userAuthService.signUpMemberUser(registerDto);

            assertThat(response.getRefreshToken()).isEqualTo("refresh");
            verify(userService).registerMemberUser(registerDto);
            verify(jwtTokenService).generateTokens(20L, Authority.ROLE_MEMBER);
        }

        @Test
        @DisplayName("사용자 등록이 실패하면 토큰을 발급하지 않는다")
        void doesNotIssueTokenWhenRegistrationFails() {
            UserRegisterDto registerDto = UserRegisterDto.builder().identifier("broken").build();
            willThrow(new ApplicationException(UserErrorCase.USER_NOT_FOUND))
                    .given(userService).registerMemberUser(registerDto);

            assertThatThrownBy(() -> userAuthService.signUpMemberUser(registerDto))
                    .isInstanceOf(ApplicationException.class);

            verify(jwtTokenService, never()).generateTokens(anyLong(), any(Authority.class));
        }
    }

    @Nested
    @DisplayName("갱신·로그아웃 위임")
    class Delegation {

        @Test
        @DisplayName("본문의 refreshToken 을 그대로 토큰 서비스에 넘긴다")
        void passesRefreshTokenThrough() {
            given(jwtTokenService.refreshAccessToken("refresh"))
                    .willReturn(new TokenResponseDto("Bearer new-access", "new-refresh"));

            TokenResponseDto response = userAuthService.refreshAccessToken(
                    new TokenRequestDto("Bearer old-access", "refresh"));

            assertThat(response.getAccessToken()).isEqualTo("Bearer new-access");
            verify(jwtTokenService).refreshAccessToken("refresh");
        }

        @Test
        @DisplayName("로그아웃은 액세스 토큰·userId·리프레시 토큰을 그대로 전달한다")
        void passesLogoutArgumentsThrough() {
            userAuthService.logout("Bearer access", 30L, "refresh");

            verify(jwtTokenService).logout("Bearer access", 30L, "refresh");
        }
    }
}
