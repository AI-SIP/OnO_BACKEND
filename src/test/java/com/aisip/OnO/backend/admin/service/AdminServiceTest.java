package com.aisip.OnO.backend.admin.service;

import com.aisip.OnO.backend.admin.support.AdminTestSupport;
import com.aisip.OnO.backend.common.exception.ApplicationException;
import com.aisip.OnO.backend.user.entity.User;
import com.aisip.OnO.backend.user.exception.UserErrorCase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
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
        @DisplayName("존재하지 않는 identifier 는 USER_NOT_FOUND 로 거절한다")
        void rejectsUnknownIdentifier() {
            assertThatThrownBy(() -> adminService.loadUserByUsername("존재하지-않는-관리자"))
                    .isInstanceOf(ApplicationException.class)
                    .extracting(thrown -> ((ApplicationException) thrown).getErrorCase())
                    .isEqualTo(UserErrorCase.USER_NOT_FOUND);
        }

        @Test
        @DisplayName("일반 사용자의 identifier 로도 조회되면 그 사용자에게 ROLE_ADMIN 이 붙는다는 사실을 고정해 둔다")
        void grantsAdminRoleToAnyIdentifierItResolves() {
            User member = fixtures.createUser();
            String memberIdentifier = member.getIdentifier();

            UserDetails details = adminService.loadUserByUsername(memberIdentifier);

            assertThat(details.getAuthorities())
                    .extracting(GrantedAuthority::getAuthority)
                    .as("""
                            AdminService 는 identifier 로 찾은 사용자에게 무조건 ROLE_ADMIN 을 부여한다.
                            즉 관리자 여부를 가르는 유일한 방어선은 '비밀번호를 아는가' 하나뿐이다.
                            일반 사용자는 password 가 null 이라 폼 로그인은 통과하지 못하지만,
                            이 특성을 바꿀 때는 반드시 이 테스트를 다시 보게 만든다.""")
                    .containsExactly("ROLE_ADMIN");
            assertThat(details.getPassword())
                    .as("소셜 로그인 사용자는 비밀번호가 없어 폼 로그인으로는 인증될 수 없다")
                    .isNull();
        }
    }
}
