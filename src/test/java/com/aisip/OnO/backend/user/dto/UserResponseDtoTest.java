package com.aisip.OnO.backend.user.dto;

import com.aisip.OnO.backend.user.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UserResponseDtoTest {

    @Test
    @DisplayName("15레벨 초과 미션 정보는 15레벨 풀 게이지로 응답한다")
    void fromCapsMissionStatusOverMaxLevel() {
        User user = User.from(new UserRegisterDto(
                "test@example.com",
                "testUser",
                "testIdentifier",
                "MEMBER",
                null
        ));

        user.getUserMissionStatus().setAttendanceLevel(16L, 10L);
        user.getUserMissionStatus().setNoteWriteLevel(17L, 20L);
        user.getUserMissionStatus().setProblemPracticeLevel(18L, 30L);
        user.getUserMissionStatus().setNotePracticeLevel(19L, 40L);
        user.getUserMissionStatus().setTotalStudyLevel(16L, 50L);

        UserResponseDto response = UserResponseDto.from(user);

        assertThat(response.attendanceLevel()).isEqualTo(15L);
        assertThat(response.attendancePoint()).isEqualTo(150L);
        assertThat(response.noteWriteLevel()).isEqualTo(15L);
        assertThat(response.noteWritePoint()).isEqualTo(150L);
        assertThat(response.problemPracticeLevel()).isEqualTo(15L);
        assertThat(response.problemPracticePoint()).isEqualTo(150L);
        assertThat(response.notePracticeLevel()).isEqualTo(15L);
        assertThat(response.notePracticePoint()).isEqualTo(150L);
        assertThat(response.totalStudyLevel()).isEqualTo(15L);
        assertThat(response.totalStudyCurrentPoint()).isEqualTo(600L);
        // 만렙에서는 현재치와 임계값이 같아야 게이지가 가득 찬다.
        // 0 을 내려주면 프론트가 현재치/임계값을 계산할 때 0 으로 나누게 된다
        assertThat(response.totalStudyNextLevelThreshold()).isEqualTo(600L);
    }

    @Test
    @DisplayName("알림 설정 값이 응답에 그대로 실린다")
    void fromIncludesNotificationEnabled() {
        User user = User.from(new UserRegisterDto(
                "test@example.com",
                "testUser",
                "testIdentifier",
                "MEMBER",
                null
        ));

        // 기본값
        assertThat(UserResponseDto.from(user).notificationEnabled()).isTrue();

        // 꺼둔 상태가 응답에 반영되지 않으면 프론트가 true 로 복원해 스위치가 다시 켜져 보인다
        user.updateNotificationEnabled(false);
        assertThat(UserResponseDto.from(user).notificationEnabled()).isFalse();
    }
}
