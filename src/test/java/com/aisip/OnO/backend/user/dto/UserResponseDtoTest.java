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
        assertThat(response.totalStudyNextLevelThreshold()).isZero();
    }
}
