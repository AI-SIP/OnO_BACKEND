package com.aisip.OnO.backend.user.service;

import com.aisip.OnO.backend.common.exception.ApplicationException;
import com.aisip.OnO.backend.config.rabbitmq.producer.S3DeleteProducer;
import com.aisip.OnO.backend.folder.service.FolderService;
import com.aisip.OnO.backend.practicenote.service.PracticeNoteService;
import com.aisip.OnO.backend.problem.reminder.ProblemReviewReminderService;
import com.aisip.OnO.backend.problem.service.ProblemService;
import com.aisip.OnO.backend.studyroom.repository.StudyRoomSharedProblemCommentReactionRepository;
import com.aisip.OnO.backend.studyroom.repository.StudyRoomSharedProblemCommentRepository;
import com.aisip.OnO.backend.user.dto.UserRegisterDto;
import com.aisip.OnO.backend.user.dto.UserResponseDto;
import com.aisip.OnO.backend.user.entity.User;
import com.aisip.OnO.backend.user.exception.UserErrorCase;
import com.aisip.OnO.backend.user.repository.UserRepository;
import com.aisip.OnO.backend.util.fileupload.exception.FileUploadErrorCase;
import com.aisip.OnO.backend.util.fileupload.service.FileUploadService;
import com.aisip.OnO.backend.util.webhook.DiscordWebhookNotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.util.ReflectionTestUtils.setField;

/**
 * UserService 의 순수 로직(입력 검증·부분 수정·통계 정렬)을 DB 없이 고정한다.
 *
 * <p>DB 제약(식별자 암호화·유니크 인덱스·소프트 삭제)이 걸린 동작은
 * {@code UserServiceIntegrationTest} 에서 실제 MySQL 로 확인한다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("UserService")
class UserServiceTest {

    private static final Long USER_ID = 1L;

    @Mock
    private UserRepository userRepository;
    @Mock
    private FolderService folderService;
    @Mock
    private ProblemService problemService;
    @Mock
    private PracticeNoteService practiceNoteService;
    @Mock
    private ProblemReviewReminderService reminderService;
    @Mock
    private StudyRoomSharedProblemCommentRepository sharedProblemCommentRepository;
    @Mock
    private StudyRoomSharedProblemCommentReactionRepository sharedProblemCommentReactionRepository;
    @Mock
    private FileUploadService fileUploadService;
    @Mock
    private S3DeleteProducer s3DeleteProducer;
    @Mock
    private DiscordWebhookNotificationService discordWebhookNotificationService;

    @InjectMocks
    private UserService userService;

    private UserRegisterDto registerDto;

    @BeforeEach
    void setUp() {
        registerDto = new UserRegisterDto("test@example.com", "testUser", "testIdentifier", "GOOGLE", null);
    }

    private void givenExistingUser(User user) {
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user));
    }

    private static MockMultipartFile pngFile() {
        return new MockMultipartFile("profileImage", "profile.png", "image/png", new byte[]{
                (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00, 0x00, 0x00, 0x00
        });
    }

    @Nested
    @DisplayName("가입")
    class Register {

        @Test
        @DisplayName("게스트는 매번 새로운 랜덤 식별자로 만들어지고 기본 폴더·복습노트를 함께 받는다")
        void registersGuestWithGeneratedIdentity() {
            given(userRepository.save(any(User.class))).willAnswer(invocation -> invocation.getArgument(0));

            UserResponseDto first = userService.registerGuestUser();
            UserResponseDto second = userService.registerGuestUser();

            assertThat(first.name()).startsWith("Guest");
            assertThat(first.email()).startsWith("guest_").endsWith("@ono.com");
            assertThat(second.email())
                    .as("게스트끼리 식별자가 겹치면 유니크 인덱스에 걸린다")
                    .isNotEqualTo(first.email());
            verify(folderService, org.mockito.Mockito.times(2)).initializeDefaultFoldersIfAbsent(any());
            verify(practiceNoteService, org.mockito.Mockito.times(2)).registerDefaultPractice(any());
        }

        @Test
        @DisplayName("신규 멤버는 저장하고 기본 폴더·복습노트를 만들어 준다")
        void registersNewMember() {
            given(userRepository.findByIdentifier("testIdentifier")).willReturn(Optional.empty());
            given(userRepository.save(any(User.class))).willAnswer(invocation -> invocation.getArgument(0));

            UserResponseDto response = userService.registerMemberUser(registerDto);

            assertThat(response.name()).isEqualTo("testUser");
            assertThat(response.email()).isEqualTo("test@example.com");
            verify(userRepository).save(any(User.class));
            verify(folderService).initializeDefaultFoldersIfAbsent(any());
            verify(practiceNoteService).registerDefaultPractice(any());
        }

        @ParameterizedTest(name = "identifier 가 [{0}] 인 소셜 로그인은 3002 로 거절한다")
        @NullSource
        @ValueSource(strings = {"", "   "})
        void rejectsBlankIdentifier(String identifier) {
            UserRegisterDto broken = new UserRegisterDto("a@test.ono", "이름", identifier, "GOOGLE", null);

            assertThatThrownBy(() -> userService.registerMemberUser(broken))
                    .as("identifier 없이 가입시키면 다음 로그인마다 계정이 새로 생긴다")
                    .isInstanceOf(ApplicationException.class)
                    .extracting(e -> ((ApplicationException) e).getErrorCase())
                    .isEqualTo(UserErrorCase.INVALID_USER_IDENTIFIER);
            verify(userRepository, never()).save(any(User.class));
        }

        @Test
        @DisplayName("암호화하면 컬럼을 넘는 긴 identifier 는 500 이 아니라 3002 로 거절한다")
        void rejectsTooLongIdentifier() {
            UserRegisterDto tooLong = new UserRegisterDto(
                    "a@test.ono", "이름", "x".repeat(176), "GOOGLE", null);

            assertThatThrownBy(() -> userService.registerMemberUser(tooLong))
                    .isInstanceOf(ApplicationException.class)
                    .extracting(e -> ((ApplicationException) e).getErrorCase())
                    .isEqualTo(UserErrorCase.INVALID_USER_IDENTIFIER);
            verify(userRepository, never()).save(any(User.class));
        }

        @Test
        @DisplayName("컬럼 한계 직전 길이의 identifier 는 통과시킨다")
        void acceptsIdentifierAtLengthLimit() {
            String limit = "x".repeat(175);
            given(userRepository.findByIdentifier(limit)).willReturn(Optional.empty());
            given(userRepository.save(any(User.class))).willAnswer(invocation -> invocation.getArgument(0));

            userService.registerMemberUser(new UserRegisterDto("a@test.ono", "이름", limit, "GOOGLE", null));

            verify(userRepository).save(any(User.class));
        }

        @Test
        @DisplayName("같은 identifier 로 다시 로그인하면 새로 만들지 않고 기존 계정을 돌려준다")
        void reusesExistingMemberOnRelogin() {
            User existing = User.from(registerDto);
            setField(existing, "id", 99L);
            given(userRepository.findByIdentifier("testIdentifier")).willReturn(Optional.of(existing));

            UserResponseDto response = userService.registerMemberUser(registerDto);

            assertThat(response.userId())
                    .as("소셜 재로그인은 신규 가입이 아니라 기존 계정 복귀다")
                    .isEqualTo(99L);
            verify(userRepository, never()).save(any(User.class));
            verify(folderService, never()).initializeDefaultFoldersIfAbsent(anyLong());
            verify(practiceNoteService, never()).registerDefaultPractice(anyLong());
            verify(discordWebhookNotificationService, never()).sendMessage(anyString(), anyString());
        }
    }

    @Nested
    @DisplayName("조회")
    class Find {

        @Test
        @DisplayName("본인 userId 로 조회하면 프로필을 돌려준다")
        void findsOwnProfile() {
            givenExistingUser(User.from(registerDto));

            assertThat(userService.findUser(USER_ID).name()).isEqualTo("testUser");
        }

        @Test
        @DisplayName("없는 userId 는 3001 로 거절한다")
        void rejectsUnknownUser() {
            given(userRepository.findById(404L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> userService.findUser(404L))
                    .isInstanceOf(ApplicationException.class)
                    .extracting(e -> ((ApplicationException) e).getErrorCase())
                    .isEqualTo(UserErrorCase.USER_NOT_FOUND);
        }

        @Test
        @DisplayName("identifier 로 조회할 수 있고, 없으면 3001 이다")
        void findsByIdentifier() {
            given(userRepository.findByIdentifier("testIdentifier")).willReturn(Optional.of(User.from(registerDto)));
            given(userRepository.findByIdentifier("unknown")).willReturn(Optional.empty());

            assertThat(userService.findUserEntityByIdentifier("testIdentifier").getIdentifier())
                    .isEqualTo("testIdentifier");
            assertThatThrownBy(() -> userService.findUserEntityByIdentifier("unknown"))
                    .isInstanceOf(ApplicationException.class);
        }

        @Test
        @DisplayName("전체 사용자 목록은 가입 최신순으로 정렬한다")
        void sortsAllUsersByCreatedAtDesc() {
            User older = User.from(new UserRegisterDto("u1@test.ono", "user1", "id1", "GOOGLE", null));
            User newer = User.from(new UserRegisterDto("u2@test.ono", "user2", "id2", "GOOGLE", null));
            setField(older, "createdAt", LocalDateTime.of(2026, 2, 1, 10, 0));
            setField(newer, "createdAt", LocalDateTime.of(2026, 2, 2, 10, 0));
            given(userRepository.findAll()).willReturn(List.of(older, newer));

            assertThat(userService.findAllUsers())
                    .extracting(UserResponseDto::name)
                    .containsExactly("user2", "user1");
        }
    }

    @Nested
    @DisplayName("수정")
    class Update {

        @Test
        @DisplayName("보낸 필드만 바뀌고 null·공백 필드는 기존 값을 지킨다")
        void updatesOnlyProvidedFields() {
            User user = User.from(registerDto);
            givenExistingUser(user);

            userService.updateUser(USER_ID, new UserRegisterDto(null, "새이름", "   ", null, null));

            assertThat(user.getName()).isEqualTo("새이름");
            assertThat(user.getEmail())
                    .as("이메일을 안 보냈다고 지워지면 안 된다")
                    .isEqualTo("test@example.com");
            assertThat(user.getIdentifier())
                    .as("공백 identifier 로 덮어쓰면 소셜 로그인 연결이 끊긴다")
                    .isEqualTo("testIdentifier");
        }

        @ParameterizedTest(name = "알림 수신 설정을 {0} 으로 바꾼다")
        @CsvSource({"true", "false"})
        void updatesNotificationSettings(boolean enabled) {
            User user = User.from(registerDto);
            user.updateNotificationEnabled(!enabled);
            givenExistingUser(user);

            userService.updateNotificationSettings(USER_ID, enabled);

            assertThat(user.isNotificationEnabled()).isEqualTo(enabled);
        }

        @Test
        @DisplayName("마지막 활동 시각은 날짜가 바뀐 첫 조회에만 갱신한다")
        void touchesLastActiveAtOncePerDay() {
            User user = User.from(registerDto);
            LocalDateTime yesterday = LocalDateTime.now().minusDays(1);
            setField(user, "lastActiveAt", yesterday);
            givenExistingUser(user);

            userService.touchAndFindUser(USER_ID);
            LocalDateTime afterFirstTouch = user.getLastActiveAt();

            userService.touchAndFindUser(USER_ID);

            assertThat(afterFirstTouch).isAfter(yesterday);
            assertThat(user.getLastActiveAt())
                    .as("같은 날 재조회는 불필요한 DB write 를 만들지 않는다")
                    .isEqualTo(afterFirstTouch);
        }

        @ParameterizedTest(name = "{0} 레벨을 갱신한다")
        @CsvSource({"attendance", "noteWrite", "problemPractice", "notePractice", "totalStudy"})
        void updatesEachLevelType(String levelType) {
            User user = User.from(registerDto);
            givenExistingUser(user);
            given(userRepository.save(any(User.class))).willAnswer(invocation -> invocation.getArgument(0));

            userService.updateUserLevel(USER_ID, levelType, 5L, 30L);

            Map<String, Long> levels = Map.of(
                    "attendance", user.getUserMissionStatus().getAttendanceLevel(),
                    "noteWrite", user.getUserMissionStatus().getNoteWriteLevel(),
                    "problemPractice", user.getUserMissionStatus().getProblemPracticeLevel(),
                    "notePractice", user.getUserMissionStatus().getNotePracticeLevel(),
                    "totalStudy", user.getUserMissionStatus().getTotalStudyLevel()
            );
            assertThat(levels.get(levelType)).isEqualTo(5L);
        }

        @Test
        @DisplayName("모르는 레벨 종류는 예외로 막는다")
        void rejectsUnknownLevelType() {
            givenExistingUser(User.from(registerDto));

            assertThatThrownBy(() -> userService.updateUserLevel(USER_ID, "unknown", 5L, 30L))
                    .isInstanceOf(ApplicationException.class);
            verify(userRepository, never()).save(any(User.class));
        }
    }

    @Nested
    @DisplayName("프로필 이미지")
    class ProfileImage {

        @Test
        @DisplayName("정상 PNG 는 업로드하고 URL 을 프로필에 반영한다")
        void uploadsValidImage() {
            User user = User.from(registerDto);
            givenExistingUser(user);
            given(fileUploadService.uploadFileToS3(any())).willReturn("https://cdn.test.ono/profile.png");

            UserResponseDto response = userService.updateProfileImage(USER_ID, pngFile());

            assertThat(response.profileImageUrl()).isEqualTo("https://cdn.test.ono/profile.png");
            assertThat(user.getProfileImageUrl()).isEqualTo("https://cdn.test.ono/profile.png");
        }

        @Test
        @DisplayName("이미지를 교체하면 이전 이미지는 삭제 큐로 보낸다")
        void enqueuesPreviousImageDeletion() {
            User user = User.from(registerDto);
            user.updateProfileImageUrl("https://cdn.test.ono/old.png");
            givenExistingUser(user);
            given(fileUploadService.uploadFileToS3(any())).willReturn("https://cdn.test.ono/new.png");

            userService.updateProfileImage(USER_ID, pngFile());

            verify(s3DeleteProducer).sendDeleteMessage("https://cdn.test.ono/old.png", USER_ID);
        }

        @Test
        @DisplayName("이전 이미지가 없으면 삭제 메시지를 보내지 않는다")
        void skipsDeletionWithoutPreviousImage() {
            givenExistingUser(User.from(registerDto));
            given(fileUploadService.uploadFileToS3(any())).willReturn("https://cdn.test.ono/new.png");

            userService.updateProfileImage(USER_ID, pngFile());

            verify(s3DeleteProducer, never()).sendDeleteMessage(anyString(), anyLong());
        }

        @Test
        @DisplayName("빈 파일은 2003 으로 거절하고 S3 를 호출하지 않는다")
        void rejectsEmptyFile() {
            MultipartFile empty = new MockMultipartFile("profileImage", "profile.png", "image/png", new byte[0]);

            assertThatThrownBy(() -> userService.updateProfileImage(USER_ID, empty))
                    .isInstanceOf(ApplicationException.class)
                    .extracting(e -> ((ApplicationException) e).getErrorCase())
                    .isEqualTo(FileUploadErrorCase.INVALID_IMAGE_FILE);
            verify(fileUploadService, never()).uploadFileToS3(any());
        }

        @Test
        @DisplayName("5MB 를 넘는 이미지는 2004 로 거절한다")
        void rejectsOversizedFile() {
            byte[] tooLarge = new byte[5 * 1024 * 1024 + 1];
            tooLarge[0] = (byte) 0x89;
            MultipartFile file = new MockMultipartFile("profileImage", "profile.png", "image/png", tooLarge);

            assertThatThrownBy(() -> userService.updateProfileImage(USER_ID, file))
                    .isInstanceOf(ApplicationException.class)
                    .extracting(e -> ((ApplicationException) e).getErrorCase())
                    .isEqualTo(FileUploadErrorCase.FILE_SIZE_EXCEEDED);
        }

        @ParameterizedTest(name = "{0} / {1} 조합은 2003 으로 거절한다")
        @CsvSource({
                "profile.png, image/jpeg",
                "profile.jpg, image/png",
                "profile.gif, image/gif",
                "profile, image/png",
                "profile., image/png"
        })
        void rejectsMismatchedOrUnsupportedType(String filename, String contentType) {
            MultipartFile file = new MockMultipartFile("profileImage", filename, contentType, new byte[]{
                    (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00, 0x00, 0x00, 0x00
            });

            assertThatThrownBy(() -> userService.updateProfileImage(USER_ID, file))
                    .isInstanceOf(ApplicationException.class)
                    .extracting(e -> ((ApplicationException) e).getErrorCase())
                    .isEqualTo(FileUploadErrorCase.INVALID_IMAGE_FILE);
        }

        @Test
        @DisplayName("확장자만 이미지인 위장 파일은 시그니처 검사에서 걸러낸다")
        void rejectsDisguisedFile() {
            MultipartFile disguised = new MockMultipartFile(
                    "profileImage", "profile.png", "image/png", "<?php echo 1; ?>".getBytes());

            assertThatThrownBy(() -> userService.updateProfileImage(USER_ID, disguised))
                    .as("확장자만 믿으면 실행 가능한 파일이 버킷에 올라간다")
                    .isInstanceOf(ApplicationException.class)
                    .extracting(e -> ((ApplicationException) e).getErrorCase())
                    .isEqualTo(FileUploadErrorCase.INVALID_IMAGE_FILE);
            verify(fileUploadService, never()).uploadFileToS3(any());
        }

        @Test
        @DisplayName("URL 로 교체할 때는 우리 버킷 URL 인지 먼저 확인한다")
        void validatesUrlOwnership() {
            User user = User.from(registerDto);
            givenExistingUser(user);

            userService.updateProfileImageByUrl(USER_ID, "https://cdn.test.ono/new.png");

            verify(fileUploadService).validateS3Url("https://cdn.test.ono/new.png");
            assertThat(user.getProfileImageUrl()).isEqualTo("https://cdn.test.ono/new.png");
        }

        @Test
        @DisplayName("프로필 이미지를 지우면 URL 을 비우고 기존 파일을 삭제 큐로 보낸다")
        void deletesProfileImage() {
            User user = User.from(registerDto);
            user.updateProfileImageUrl("https://cdn.test.ono/old.png");
            givenExistingUser(user);

            UserResponseDto response = userService.deleteProfileImage(USER_ID);

            assertThat(response.profileImageUrl()).isNull();
            verify(s3DeleteProducer).sendDeleteMessage("https://cdn.test.ono/old.png", USER_ID);
        }
    }

    @Nested
    @DisplayName("탈퇴")
    class Withdraw {

        @Test
        @DisplayName("사용자 데이터를 모두 정리하고 identifier 를 마스킹한 뒤 삭제한다")
        void deletesUserWithAllRelatedData() {
            User user = User.from(registerDto);
            givenExistingUser(user);

            userService.deleteUserById(USER_ID);

            assertThat(user.getIdentifier())
                    .as("같은 소셜 계정으로 재가입할 수 있도록 식별자를 비워줘야 한다")
                    .startsWith("deleted:" + USER_ID + ":");
            verify(sharedProblemCommentReactionRepository).deleteByCommentAuthorId(USER_ID);
            verify(sharedProblemCommentReactionRepository).deleteByUserId(USER_ID);
            verify(sharedProblemCommentRepository).deleteByAuthorId(USER_ID);
            verify(practiceNoteService).deleteAllPracticesByUser(USER_ID);
            verify(reminderService).cancelAllByUser(USER_ID);
            verify(problemService).deleteAllUserProblems(USER_ID);
            verify(folderService).deleteAllUserFolders(USER_ID);
            verify(userRepository).deleteById(USER_ID);
        }

        @Test
        @DisplayName("없는 사용자를 탈퇴시키려 하면 3001 로 끝내고 아무것도 지우지 않는다")
        void rejectsUnknownUser() {
            given(userRepository.findById(404L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> userService.deleteUserById(404L))
                    .isInstanceOf(ApplicationException.class);
            verify(problemService, never()).deleteAllUserProblems(anyLong());
            verify(userRepository, never()).deleteById(anyLong());
        }

        @Test
        @DisplayName("탈퇴 시 프로필 이미지도 삭제 큐로 보낸다")
        void enqueuesProfileImageDeletion() {
            User user = User.from(registerDto);
            user.updateProfileImageUrl("https://cdn.test.ono/profile.png");
            givenExistingUser(user);

            userService.deleteUserById(USER_ID);

            verify(s3DeleteProducer).sendDeleteMessage("https://cdn.test.ono/profile.png", USER_ID);
        }
    }

    @Nested
    @DisplayName("가입 통계")
    class Statistics {

        @Test
        @DisplayName("일별 신규 가입자는 최신 날짜부터, 가입이 없는 날은 0 으로 채운다")
        void fillsMissingDaysWithZero() {
            LocalDate today = LocalDate.now();
            given(userRepository.countDailyNewUsers(any(), any())).willReturn(List.<Object[]>of(
                    new Object[]{java.sql.Date.valueOf(today), 3L}
            ));

            Map<LocalDate, Long> result = userService.getDailyNewUsersCount(3);

            assertThat(result).containsExactly(
                    java.util.Map.entry(today, 3L),
                    java.util.Map.entry(today.minusDays(1), 0L),
                    java.util.Map.entry(today.minusDays(2), 0L)
            );
        }

        @Test
        @DisplayName("특정 날짜 가입자는 그 날 00:00~23:59:59 범위로 조회한다")
        void queriesFullDayRange() {
            LocalDate date = LocalDate.of(2026, 3, 1);
            given(userRepository.findAllByCreatedAtBetweenOrderByCreatedAtDesc(any(), any()))
                    .willReturn(List.of(User.from(registerDto)));

            assertThat(userService.getUsersByDate(date)).hasSize(1);

            ArgumentCaptor<LocalDateTime> from = ArgumentCaptor.forClass(LocalDateTime.class);
            ArgumentCaptor<LocalDateTime> to = ArgumentCaptor.forClass(LocalDateTime.class);
            verify(userRepository).findAllByCreatedAtBetweenOrderByCreatedAtDesc(from.capture(), to.capture());
            assertThat(from.getValue()).isEqualTo(date.atStartOfDay());
            assertThat(to.getValue().toLocalDate()).isEqualTo(date);
        }
    }
}
