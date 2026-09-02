package com.aisip.OnO.backend.user.service;

import com.aisip.OnO.backend.common.exception.ApplicationException;
import com.aisip.OnO.backend.folder.repository.FolderRepository;
import com.aisip.OnO.backend.support.IntegrationTestSupport;
import com.aisip.OnO.backend.user.dto.UserRegisterDto;
import com.aisip.OnO.backend.user.dto.UserResponseDto;
import com.aisip.OnO.backend.user.entity.User;
import com.aisip.OnO.backend.user.exception.UserErrorCase;
import com.aisip.OnO.backend.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * UserService 를 실제 MySQL 위에서 돌린다.
 *
 * <p>가입 시 기본 데이터 생성, 암호화된 identifier 조회, 소프트 삭제 격리처럼
 * 목으로는 확인할 수 없는 부분만 여기서 검증한다.
 */
@DisplayName("UserService 통합")
class UserServiceIntegrationTest extends IntegrationTestSupport {

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private FolderRepository folderRepository;

    private static UserRegisterDto registerDto(String identifier) {
        return UserRegisterDto.builder()
                .identifier(identifier)
                .platform("GOOGLE")
                .name("사용자-" + identifier)
                .email(identifier + "@test.ono")
                .build();
    }

    @Nested
    @DisplayName("가입")
    class Register {

        @Test
        @DisplayName("게스트 가입 시 기본 폴더가 함께 만들어진다")
        void createsDefaultDataForGuest() {
            UserResponseDto guest = userService.registerGuestUser();

            assertThat(folderRepository.findAllByUserId(guest.userId()))
                    .as("기본 폴더가 없으면 첫 문제 등록부터 실패한다")
                    .isNotEmpty();
        }

        @Test
        @DisplayName("멤버 가입 시 기본 폴더가 함께 만들어진다")
        void createsDefaultDataForMember() {
            UserResponseDto member = userService.registerMemberUser(registerDto("google-sub-default"));

            assertThat(folderRepository.findAllByUserId(member.userId())).isNotEmpty();
        }

        @Test
        @DisplayName("같은 identifier 로 재가입하면 기존 계정을 그대로 돌려주고 기본 데이터를 또 만들지 않는다")
        void reusesExistingAccount() {
            UserResponseDto first = userService.registerMemberUser(registerDto("google-sub-twice"));
            int foldersAfterFirst = folderRepository.findAllByUserId(first.userId()).size();

            UserResponseDto second = userService.registerMemberUser(registerDto("google-sub-twice"));

            assertThat(second.userId()).isEqualTo(first.userId());
            assertThat(userRepository.count()).isEqualTo(1);
            assertThat(folderRepository.findAllByUserId(first.userId()))
                    .as("재로그인마다 기본 폴더가 늘어나면 화면이 중복 폴더로 덮인다")
                    .hasSize(foldersAfterFirst);
        }
    }

    @Nested
    @DisplayName("조회와 수정")
    class ReadAndWrite {

        @Test
        @DisplayName("암호화된 identifier 로도 사용자를 찾아낸다")
        void findsByEncryptedIdentifier() {
            userService.registerMemberUser(registerDto("google-sub-encrypted"));

            User found = userService.findUserEntityByIdentifier("google-sub-encrypted");

            assertThat(found.getIdentifier()).isEqualTo("google-sub-encrypted");
        }

        @Test
        @DisplayName("다른 사용자의 identifier 로 바꾸려 하면 유니크 제약에 걸린다")
        void rejectsIdentifierCollision() {
            userService.registerMemberUser(registerDto("google-sub-a"));
            UserResponseDto second = userService.registerMemberUser(registerDto("google-sub-b"));

            assertThatThrownBy(() -> {
                userService.updateUser(second.userId(), registerDto("google-sub-a"));
                userRepository.flush();
            })
                    .as("두 계정이 같은 소셜 계정을 가리키면 로그인 대상이 모호해진다")
                    .isInstanceOf(Exception.class);
        }

        @Test
        @DisplayName("마지막 활동 시각 갱신이 DB 에 반영된다")
        void persistsLastActiveAt() {
            User user = fixtures.createUser();

            userService.touchLastActiveAt(user.getId());

            assertThat(userRepository.findById(user.getId()).orElseThrow().getLastActiveAt()).isNotNull();
        }

        @Test
        @DisplayName("전체 사용자 수는 소프트 삭제된 사용자를 빼고 센다")
        void countsOnlyLivingUsers() {
            User survivor = fixtures.createUser();
            User leaver = fixtures.createOtherUser();
            userService.deleteUserById(leaver.getId());

            assertThat(userService.countAllUsers()).isEqualTo(1);
            assertThat(userService.findAllUsers())
                    .extracting(UserResponseDto::userId)
                    .containsExactly(survivor.getId());
        }

        @Test
        @DisplayName("날짜별 가입자 조회는 그 날 가입한 사용자만 돌려준다")
        void findsUsersByJoinDate() {
            fixtures.createUser();

            List<UserResponseDto> today = userService.getUsersByDate(LocalDate.now());
            List<UserResponseDto> yesterday = userService.getUsersByDate(LocalDate.now().minusDays(1));

            assertThat(today).hasSize(1);
            assertThat(yesterday).isEmpty();
        }
    }

    @Nested
    @DisplayName("탈퇴")
    class Withdraw {

        @Test
        @DisplayName("탈퇴한 사용자는 조회되지 않고 identifier 는 마스킹된다")
        void removesUserFromQueries() {
            UserResponseDto user = userService.registerMemberUser(registerDto("google-sub-leaving"));

            userService.deleteUserById(user.userId());

            assertThatThrownBy(() -> userService.findUser(user.userId()))
                    .isInstanceOf(ApplicationException.class)
                    .extracting(e -> ((ApplicationException) e).getErrorCase())
                    .isEqualTo(UserErrorCase.USER_NOT_FOUND);
            assertThat(userRepository.findByIdentifier("google-sub-leaving"))
                    .as("탈퇴 후에도 identifier 가 남아 있으면 같은 계정으로 재가입할 수 없다")
                    .isEmpty();
        }

        @Test
        @DisplayName("탈퇴는 본인 데이터만 지운다")
        void deletesOnlyOwnData() {
            User me = fixtures.createUser();
            User other = fixtures.createOtherUser();
            fixtures.createRootFolder(other.getId());

            userService.deleteUserById(me.getId());

            assertThat(userRepository.findById(other.getId())).isPresent();
            assertThat(folderRepository.findAllByUserId(other.getId()))
                    .as("남의 폴더까지 지워지면 복구 불가능한 사고다")
                    .isNotEmpty();
        }

        @Test
        @DisplayName("탈퇴한 사용자를 다시 탈퇴시키려 하면 3001 이 난다")
        void rejectsDoubleWithdrawal() {
            User user = fixtures.createUser();
            userService.deleteUserById(user.getId());

            assertThatThrownBy(() -> userService.deleteUserById(user.getId()))
                    .isInstanceOf(ApplicationException.class)
                    .extracting(e -> ((ApplicationException) e).getErrorCase())
                    .isEqualTo(UserErrorCase.USER_NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("관리자 목록")
    class AdminListing {

        @Test
        @DisplayName("페이지 크기만큼 끊어서 돌려준다")
        void paginates() {
            for (int i = 0; i < 3; i++) {
                fixtures.createUser();
            }

            var page = userService.findAdminUsers(0, 2, "createdAt", "desc");

            assertThat(page.getContent()).hasSize(2);
            assertThat(page.getTotalElements()).isEqualTo(3);
        }

        @Test
        @DisplayName("탈퇴한 사용자는 관리자 목록에도 나오지 않는다")
        void excludesDeletedUsers() {
            User survivor = fixtures.createUser();
            User leaver = fixtures.createOtherUser();
            userService.deleteUserById(leaver.getId());

            var page = userService.findAdminUsers(0, 10, "createdAt", "desc");

            assertThat(page.getContent())
                    .extracting(dto -> dto.userId())
                    .containsExactly(survivor.getId());
        }
    }
}
