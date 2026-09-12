package com.aisip.OnO.backend.user.dto;

import com.aisip.OnO.backend.user.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.util.ReflectionTestUtils.setField;

/**
 * 앱의 프로필·레벨 게이지가 그대로 이 응답을 그린다.
 * 레벨 상한 처리와 민감정보 노출 여부를 고정한다.
 */
@DisplayName("UserResponseDto")
class UserResponseDtoTest {

    private static User user() {
        return User.from(new UserRegisterDto(
                "test@example.com", "testUser", "google-sub-123", "GOOGLE", "secret-password"));
    }

    @Nested
    @DisplayName("기본 매핑")
    class BasicMapping {

        @Test
        @DisplayName("식별자와 비밀번호는 응답에 담지 않는다")
        void hidesCredentials() {
            User user = user();
            setField(user, "id", 7L);

            UserResponseDto response = UserResponseDto.from(user);

            assertThat(response.userId()).isEqualTo(7L);
            assertThat(response.name()).isEqualTo("testUser");
            assertThat(response.email()).isEqualTo("test@example.com");
            assertThat(response.toString())
                    .as("소셜 식별자나 비밀번호가 응답에 섞이면 그대로 로그·앱에 남는다")
                    .doesNotContain("google-sub-123")
                    .doesNotContain("secret-password");
        }

        @Test
        @DisplayName("프로필 이미지와 가입·수정 시각을 그대로 담는다")
        void carriesProfileAndTimestamps() {
            User user = user();
            user.updateProfileImageUrl("https://cdn.test.ono/profile.png");
            LocalDateTime createdAt = LocalDateTime.of(2026, 3, 1, 9, 0);
            setField(user, "createdAt", createdAt);
            setField(user, "updatedAt", createdAt.plusDays(1));

            UserResponseDto response = UserResponseDto.from(user);

            assertThat(response.profileImageUrl()).isEqualTo("https://cdn.test.ono/profile.png");
            assertThat(response.createdAt()).isEqualTo(createdAt);
            assertThat(response.updatedAt()).isEqualTo(createdAt.plusDays(1));
        }

        @Test
        @DisplayName("갓 가입한 사용자는 모든 능력치가 1레벨 0포인트로 나간다")
        void startsAtLevelOne() {
            UserResponseDto response = UserResponseDto.from(user());

            assertThat(response.attendanceLevel()).isEqualTo(1L);
            assertThat(response.attendancePoint()).isZero();
            assertThat(response.noteWriteLevel()).isEqualTo(1L);
            assertThat(response.problemPracticeLevel()).isEqualTo(1L);
            assertThat(response.notePracticeLevel()).isEqualTo(1L);
            assertThat(response.totalStudyLevel()).isEqualTo(1L);
            assertThat(response.totalStudyCurrentPoint()).isZero();
            assertThat(response.totalStudyNextLevelThreshold())
                    .as("1레벨의 다음 레벨 기준치는 개별 능력치 기준의 4배다")
                    .isEqualTo(40L);
        }

        @Test
        @DisplayName("알림 설정 값을 응답에 그대로 담는다")
        void carriesNotificationEnabled() {
            User user = user();

            assertThat(UserResponseDto.from(user).notificationEnabled())
                    .as("기본값은 켜짐이다")
                    .isTrue();

            user.updateNotificationEnabled(false);

            assertThat(UserResponseDto.from(user).notificationEnabled())
                    .as("응답에서 빠지면 앱이 true 로 복원해, 알림을 꺼도 다시 켜진 것처럼 보인다")
                    .isFalse();
        }
    }

    @Nested
    @DisplayName("레벨 상한")
    class LevelCap {

        @Test
        @DisplayName("상한 미만은 저장된 값을 그대로 내보낸다")
        void keepsValuesBelowMaxLevel() {
            User user = user();
            user.getUserMissionStatus().setAttendanceLevel(3L, 7L);
            user.getUserMissionStatus().setTotalStudyLevel(5L, 30L);

            UserResponseDto response = UserResponseDto.from(user);

            assertThat(response.attendanceLevel()).isEqualTo(3L);
            assertThat(response.attendancePoint()).isEqualTo(7L);
            assertThat(response.totalStudyLevel()).isEqualTo(5L);
            assertThat(response.totalStudyCurrentPoint()).isEqualTo(30L);
            assertThat(response.totalStudyNextLevelThreshold()).isEqualTo(200L);
        }

        @Test
        @DisplayName("총 학습 15레벨은 더 이상 만렙이 아니다 - 게이지가 계속 올라간다")
        void totalStudyKeepsGrowingPastFifteen() {
            User user = user();
            user.getUserMissionStatus().setTotalStudyLevel(15L, 100L);

            UserResponseDto response = UserResponseDto.from(user);

            assertThat(response.totalStudyLevel()).isEqualTo(15L);
            assertThat(response.totalStudyCurrentPoint()).isEqualTo(100L);
            assertThat(response.totalStudyNextLevelThreshold())
                    .as("15 에서 멈추면 총 학습 16·18·19·20 에 걸린 치장이 앱에서 영영 안 보인다")
                    .isEqualTo(600L);
        }

        @Test
        @DisplayName("정확히 20레벨이면 게이지 기준치는 20레벨 기준으로 고정된다")
        void pinsThresholdAtMaxTotalStudyLevel() {
            User user = user();
            user.getUserMissionStatus().setTotalStudyLevel(20L, 100L);

            UserResponseDto response = UserResponseDto.from(user);

            assertThat(response.totalStudyLevel()).isEqualTo(20L);
            assertThat(response.totalStudyCurrentPoint()).isEqualTo(100L);
            assertThat(response.totalStudyNextLevelThreshold()).isEqualTo(800L);
        }

        @Test
        @DisplayName("총 학습 16~20 은 그대로 나간다 - 능력치 상한 15 와 다른 값이다")
        void totalStudyLevelIsNotCappedAtFifteen() {
            User user = user();
            user.getUserMissionStatus().setTotalStudyLevel(18L, 120L);

            UserResponseDto response = UserResponseDto.from(user);

            assertThat(response.totalStudyLevel()).isEqualTo(18L);
            assertThat(response.totalStudyCurrentPoint()).isEqualTo(120L);
            assertThat(response.totalStudyNextLevelThreshold()).isEqualTo(720L);
        }

        @Test
        @DisplayName("능력치는 15레벨을 넘겨도 15레벨 풀 게이지로 눌러서 응답한다")
        void capsAbilityStatusOverMaxLevel() {
            User user = user();
            user.getUserMissionStatus().setAttendanceLevel(16L, 10L);
            user.getUserMissionStatus().setNoteWriteLevel(17L, 20L);
            user.getUserMissionStatus().setProblemPracticeLevel(18L, 30L);
            user.getUserMissionStatus().setNotePracticeLevel(19L, 40L);
            user.getUserMissionStatus().setTotalStudyLevel(21L, 50L);

            UserResponseDto response = UserResponseDto.from(user);

            assertThat(response.attendanceLevel())
                    .as("능력치 게이지는 15칸이고 치장 해금표도 능력치 15 가 마지막이다")
                    .isEqualTo(15L);
            assertThat(response.attendancePoint()).isEqualTo(150L);
            assertThat(response.noteWriteLevel()).isEqualTo(15L);
            assertThat(response.noteWritePoint()).isEqualTo(150L);
            assertThat(response.problemPracticeLevel()).isEqualTo(15L);
            assertThat(response.problemPracticePoint()).isEqualTo(150L);
            assertThat(response.notePracticeLevel()).isEqualTo(15L);
            assertThat(response.notePracticePoint()).isEqualTo(150L);
            assertThat(response.totalStudyLevel())
                    .as("총 학습만 상한이 20 이다")
                    .isEqualTo(20L);
            assertThat(response.totalStudyCurrentPoint())
                    .as("게이지가 넘치지 않도록 20레벨 만점으로 눌러 보낸다")
                    .isEqualTo(800L);
            assertThat(response.totalStudyNextLevelThreshold()).isEqualTo(800L);
        }
    }
}
