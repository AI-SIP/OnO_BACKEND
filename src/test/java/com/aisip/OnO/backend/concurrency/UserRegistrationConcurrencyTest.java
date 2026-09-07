package com.aisip.OnO.backend.concurrency;

import com.aisip.OnO.backend.folder.entity.Folder;
import com.aisip.OnO.backend.folder.repository.FolderRepository;
import com.aisip.OnO.backend.support.IntegrationTestSupport;
import com.aisip.OnO.backend.user.dto.UserRegisterDto;
import com.aisip.OnO.backend.user.repository.UserRepository;
import com.aisip.OnO.backend.user.service.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 최초 로그인이 동시에 두 번 들어올 때.
 *
 * <p>{@code UserService.registerMemberUser} 는 identifier 로 찾아보고 없으면 만드는
 * check-then-act 인데 {@code user.identifier} 에는 유니크 인덱스가 걸려 있다.
 * 앱이 실행 직후 로그인 요청을 겹쳐 보내면 두 요청이 모두 "없음"을 읽고 INSERT 해
 * 뒤엣것이 유니크 제약에 걸린다. 태그 중복 생성 장애와 같은 구조인데, 터지는 자리가
 * 로그인이라 사용자는 앱에 아예 들어오지 못한다.
 *
 * <p>가입에 딸린 기본 폴더 생성도 함께 확인한다. 루트 폴더에는 유니크 제약이 없어
 * 두 번 만들어지면 {@code findByUserIdAndParentFolderIsNull} 이 결과를 하나로 좁히지 못하고
 * 그 뒤로 계속 실패한다.
 */
@DisplayName("동시성 - 최초 로그인 계정 생성")
class UserRegistrationConcurrencyTest extends IntegrationTestSupport {

    private static final int THREAD_COUNT = 8;

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private FolderRepository folderRepository;

    @Nested
    @DisplayName("같은 identifier 로 동시 가입")
    class ConcurrentFirstLogin {

        @Test
        @DisplayName("최초 로그인이 8번 겹쳐 들어와도 계정은 하나만 만들어진다")
        void createsSingleAccount() {
            UserRegisterDto request = UserRegisterDto.builder()
                    .email("racer@test.ono")
                    .name("racer")
                    .identifier("racer-identifier")
                    .platform("google")
                    .build();

            ConcurrentRunner.Outcome outcome = ConcurrentRunner.runConcurrently(
                    THREAD_COUNT, () -> userService.registerMemberUser(request));

            assertThat(outcome.serverErrors())
                    .as("유니크 제약 위반이 그대로 올라오면 로그인이 500 으로 실패한다")
                    .isEmpty();
            assertThat(userRepository.findAll())
                    .as("같은 identifier 로는 계정이 하나만 생겨야 한다")
                    .hasSize(1);
        }

        @Test
        @DisplayName("동시 가입에도 루트 폴더는 하나만 생긴다")
        void createsSingleRootFolder() {
            UserRegisterDto request = UserRegisterDto.builder()
                    .email("racer2@test.ono")
                    .name("racer2")
                    .identifier("racer2-identifier")
                    .platform("google")
                    .build();

            ConcurrentRunner.Outcome outcome = ConcurrentRunner.runConcurrently(
                    THREAD_COUNT, () -> userService.registerMemberUser(request));

            assertThat(outcome.serverErrors()).isEmpty();
            assertThat(folderRepository.findAll().stream()
                    .filter(folder -> folder.getParentFolder() == null)
                    .map(Folder::getId))
                    .as("루트 폴더가 둘이면 이후 루트 조회가 계속 실패한다")
                    .hasSize(1);
        }
    }
}
