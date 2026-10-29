package com.aisip.OnO.backend.admin.service;

import com.aisip.OnO.backend.admin.support.AdminTestSupport;
import com.aisip.OnO.backend.user.dto.UserRegisterDto;
import com.aisip.OnO.backend.user.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("AdminService")
class AdminServiceTest extends AdminTestSupport {

    @Autowired
    private AdminService adminService;

    @Autowired
    private PasswordEncoder encoder;

    @Nested
    @DisplayName("관리자 조회")
    class LoadUserByUsername {

        @Test
        @DisplayName("설정된 관리자 identifier 로 조회하면 ROLE_ADMIN 권한과 userId 를 담아 돌려준다")
        void returnsAdminDetailsWithAdminRole() {
            User admin = createAdminUser();

            UserDetails details = adminService.loadUserByUsername(adminIdentifier);

            assertThat(details).isInstanceOf(CustomAdminService.class);
            assertThat(((CustomAdminService) details).getUserId()).isEqualTo(admin.getId());
            assertThat(details.getUsername()).isEqualTo(adminIdentifier);
            assertThat(details.getAuthorities())
                    .extracting(GrantedAuthority::getAuthority)
                    .as("여기서 ROLE_ADMIN 이 빠지면 /admin/** 전체가 잠긴다")
                    .containsExactly("ROLE_ADMIN");
            assertThat(encoder.matches(adminPassword, details.getPassword()))
                    .as("암호화된 비밀번호를 그대로 전달해야 폼 로그인이 검증할 수 있다")
                    .isTrue();
        }

        @Test
        @DisplayName("계정 상태 플래그는 모두 사용 가능으로 열려 있다")
        void marksAccountAsUsable() {
            createAdminUser();

            UserDetails details = adminService.loadUserByUsername(adminIdentifier);

            assertThat(details.isEnabled()).isTrue();
            assertThat(details.isAccountNonExpired()).isTrue();
            assertThat(details.isAccountNonLocked()).isTrue();
            assertThat(details.isCredentialsNonExpired()).isTrue();
        }

        @Test
        @DisplayName("존재하지 않는 identifier 는 UsernameNotFoundException 으로 거절한다")
        void rejectsUnknownIdentifier() {
            assertThatThrownBy(() -> adminService.loadUserByUsername("존재하지-않는-관리자"))
                    .isInstanceOf(UsernameNotFoundException.class);
        }

        @Test
        @DisplayName("설정된 관리자 identifier 라도 계정이 아직 없으면 거절한다")
        void rejectsAdminIdentifierWithoutAccount() {
            assertThatThrownBy(() -> adminService.loadUserByUsername(adminIdentifier))
                    .isInstanceOf(UsernameNotFoundException.class);
        }

        @Test
        @DisplayName("일반 회원의 identifier 는 관리자 계정으로 불러오지 않는다")
        void rejectsMemberIdentifier() {
            createAdminUser();
            User member = fixtures.createUser();

            assertThatThrownBy(() -> adminService.loadUserByUsername(member.getIdentifier()))
                    .as("관리자 로그인은 설정된 관리자 계정만 받는다")
                    .isInstanceOf(UsernameNotFoundException.class);
        }

        @Test
        @DisplayName("비밀번호가 저장된 회원이라도 관리자 identifier 가 아니면 불러오지 않는다")
        void rejectsMemberWithPassword() {
            User member = userRepository.save(User.from(new UserRegisterDto(
                    "member@test.ono", "회원", "member-with-password", "GOOGLE", encoder.encode("pw"))));

            assertThatThrownBy(() -> adminService.loadUserByUsername(member.getIdentifier()))
                    .isInstanceOf(UsernameNotFoundException.class);
        }

        @ParameterizedTest(name = "identifier [{0}] 는 거절한다")
        @NullAndEmptySource
        @ValueSource(strings = {"   "})
        void rejectsBlankIdentifier(String identifier) {
            createAdminUser();

            assertThatThrownBy(() -> adminService.loadUserByUsername(identifier))
                    .isInstanceOf(UsernameNotFoundException.class);
        }
    }
}
