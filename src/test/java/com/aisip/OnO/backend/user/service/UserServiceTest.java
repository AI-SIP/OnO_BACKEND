package com.aisip.OnO.backend.user.service;

import com.aisip.OnO.backend.common.exception.ApplicationException;
import com.aisip.OnO.backend.folder.service.FolderService;
import com.aisip.OnO.backend.practicenote.service.PracticeNoteService;
import com.aisip.OnO.backend.problem.service.ProblemService;
import com.aisip.OnO.backend.user.dto.UserRegisterDto;
import com.aisip.OnO.backend.user.dto.UserResponseDto;
import com.aisip.OnO.backend.user.entity.User;
import com.aisip.OnO.backend.user.exception.UserErrorCase;
import com.aisip.OnO.backend.user.repository.UserRepository;
import com.aisip.OnO.backend.util.webhook.DiscordWebhookNotificationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;  // Mock 객체로 UserRepository 생성 -> 실제 DB와는 연결 X
    @Mock
    private FolderService folderService;
    @Mock
    private ProblemService problemService;
    @Mock
    private PracticeNoteService practiceNoteService;
    @Mock
    private DiscordWebhookNotificationService discordWebhookNotificationService;

    @InjectMocks
    private UserService userService;    // Mock으로 선언된 userRepository를 자동으로 주입해준다.

    private UserRegisterDto userRegisterDto;

    // 각 테스트 전에 UserRegisterDto 객체를 미리 생성한다.
    @BeforeEach
    void setUp() {
        userRegisterDto = new UserRegisterDto(
                "test@example.com",
                "testUser",
                "testIdentifier",
                "MEMBER",
                null
        );
    }

    @AfterEach
    void tearDown() {
    }

    @Test
    @DisplayName("Identifier로 유저 찾기 - 존재하는 경우")
    void findUserEntityByIdentifier() {
        // Given
        String identifier = "testIdentifier";
        User existingUser = User.from(userRegisterDto);

        when(userRepository.findByIdentifier(identifier)).thenReturn(Optional.of(existingUser));

        // When
        User user = userService.findUserEntityByIdentifier(identifier);

        // Then
        assertThat(user).isNotNull();
        assertThat(user.getIdentifier()).isEqualTo(identifier);

        verify(userRepository, times(1)).findByIdentifier(identifier);
    }


    @Test
    @DisplayName("Identifier로 사용자 찾기 - 존재하지 않는 경우")
    void findUserEntityByIdentifier_NotFound() {
        // Given
        String identifier = "nonExistentIdentifier";

        when(userRepository.findByIdentifier(identifier)).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> userService.findUserEntityByIdentifier(identifier))
                .isInstanceOf(ApplicationException.class)
                .hasMessageContaining(UserErrorCase.USER_NOT_FOUND.getMessage());

        verify(userRepository, times(1)).findByIdentifier(identifier);
    }

    @Test
    @DisplayName("게스트 유저 등록")
    void registerGuestUser() {
        // Given
        User guestUser = User.from(new UserRegisterDto(
                "guest_1234@ono.com",
                "Guest1234",
                "guestIdentifier",
                "GUEST",
                null
        ));

        when(userRepository.save(any(User.class))).thenReturn(guestUser);

        // When
        UserResponseDto response = userService.registerGuestUser();

        // Then
        assertThat(response.name()).contains("Guest");
        assertThat(response.email()).contains("guest");

        verify(userRepository, times(1)).save(any(User.class));
        verify(folderService, times(1)).ensureOnboardingFolders(any(Long.class));
    }

    @Test
    @DisplayName("신규 멤버 유저 가입")
    void registerMemberUser() {
        // Given
        when(userRepository.findByIdentifier(userRegisterDto.identifier()))
                .thenReturn(Optional.empty()); // 기존 유저 없음

        User newUser = User.from(userRegisterDto);
        when(userRepository.save(any(User.class))).thenReturn(newUser); // 저장되면 newUser를 반환하도록 설정

        // When
        UserResponseDto response = userService.registerMemberUser(userRegisterDto);

        // Then
        assertThat(response.name()).isEqualTo("testUser");
        assertThat(response.email()).isEqualTo("test@example.com");

        // save()가 반드시 한 번 호출되어야 함
        verify(userRepository, times(1)).save(any(User.class));
        verify(folderService, times(1)).ensureOnboardingFolders(any(Long.class));
    }

    @Test
    @DisplayName("이미 존재하는 멤버 가입")
    void registerMemberUserAlreadyExist() {
        // Given
        User existingUser = User.from(userRegisterDto);
        when(userRepository.findByIdentifier(userRegisterDto.identifier()))
                .thenReturn(Optional.of(existingUser)); // 기존 유저가 존재하는 경우 Optional.of 형태로 반환

        // When
        UserResponseDto response = userService.registerMemberUser(userRegisterDto);

        // Then
        assertThat(response.name()).isEqualTo("testUser");
        assertThat(response.email()).isEqualTo("test@example.com");

        // 기존 유저가 존재하면 save()가 호출되지 않아야 함
        verify(userRepository, never()).save(any(User.class));
        verify(folderService, times(1)).ensureOnboardingFolders(any(Long.class));
    }

    @Test
    void findUser() {
        // Given
        User newUser = User.from(userRegisterDto);
        when(userRepository.findById(1L)).thenReturn(Optional.of(newUser)); // 존재하는 유저 반환

        // When
        UserResponseDto userResponseDto = userService.findUser(1L);

        // Then
        assertThat(userResponseDto.name()).isEqualTo("testUser");
        assertThat(userResponseDto.email()).isEqualTo("test@example.com");

        // ✅ findById()를 호출했는지 검증
        verify(userRepository, times(1)).findById(1L);
        verify(userRepository, never()).save(any(User.class)); // save()는 호출되지 않아야 함
    }

    @Test
    @DisplayName("모든 유저 조회")
    void findAllUsers() {
        // Given
        User user1 = User.from(new UserRegisterDto("user1@example.com", "user1", "id1", "MEMBER", null));
        User user2 = User.from(new UserRegisterDto("user2@example.com", "user2", "id2", "MEMBER", null));

        when(userRepository.findAll()).thenReturn(List.of(user1, user2));

        // When
        List<UserResponseDto> users = userService.findAllUsers();

        // Then
        assertThat(users).hasSize(2);
        assertThat(users.get(0).name()).isEqualTo("user1");
        assertThat(users.get(1).name()).isEqualTo("user2");

        verify(userRepository, times(1)).findAll();
    }

    @Test
    @DisplayName("유저 정보 업데이트")
    void updateUser() {
        // Given
        Long userId = 1L;
        User existingUser = User.from(userRegisterDto);

        when(userRepository.findById(userId)).thenReturn(Optional.of(existingUser));

        UserRegisterDto updateDto = new UserRegisterDto(
                "updated@example.com", "UpdatedUser", "updatedIdentifier", "MEMBER", null
        );

        // When
        userService.updateUser(userId, updateDto);

        // Then
        assertThat(existingUser.getName()).isEqualTo("UpdatedUser");
        assertThat(existingUser.getEmail()).isEqualTo("updated@example.com");

        verify(userRepository, times(1)).findById(userId);
    }

    @Test
    @DisplayName("사용자 삭제")
    void deleteUserById() {
        // Given
        Long userId = 1L;
        doNothing().when(userRepository).deleteById(userId);

        // When
        userService.deleteUserById(userId);

        // Then
        verify(userRepository, times(1)).deleteById(userId);
    }
}
