package com.aisip.OnO.backend.studyroom.integration;

import com.aisip.OnO.backend.studyroom.dto.StudyRoomDtos.ReactionToggleRequest;
import com.aisip.OnO.backend.studyroom.dto.StudyRoomDtos.SharedProblemCommentRequest;
import com.aisip.OnO.backend.studyroom.dto.StudyRoomDtos.SharedProblemCreateRequest;
import com.aisip.OnO.backend.studyroom.entity.StudyRoomSharedProblem;
import com.aisip.OnO.backend.studyroom.support.StudyRoomTestSupport;
import com.aisip.OnO.backend.util.fcm.dto.NotificationRequestDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 스터디룸 알림이 앱과 약속한 {@code type} 값을 담아 나가는지 고정한다.
 *
 * <p>앱은 {@code data["type"]} 하나로 화면을 고른다. 값이 바뀌면 알림을 눌러도 아무 화면이
 * 열리지 않으므로, 리터럴을 그대로 적어 두고 상수 이름이 아니라 실제 문자열을 검증한다.
 */
@DisplayName("스터디룸 알림 type")
class StudyRoomNotificationTypeApiTest extends StudyRoomTestSupport {

    private RoomFixture fixture;

    @BeforeEach
    void setUpRoom() {
        fixture = createRoomWithMemberAndOutsider();
    }

    @Test
    @DisplayName("문제 공유 알림은 shared_problem 을 담는다")
    void shareProblemCarriesType() throws Exception {
        Long problemId = saveProblem(fixture.member().getId()).getId();
        authenticateAs(fixture.member().getId());

        mockMvc.perform(post("/api/study-room/{roomId}/shared-problems", fixture.roomId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SharedProblemCreateRequest(problemId, "같이 봐요"))))
                .andExpect(status().isCreated());

        Long sharedProblemId = sharedProblemRepository.findAll().get(0).getId();
        assertThat(capturedNotification(fixture.host().getId()).data())
                .as("공유 알림 데이터")
                .containsEntry("type", "shared_problem")
                .containsEntry("roomId", String.valueOf(fixture.roomId()))
                .containsEntry("sharedProblemId", String.valueOf(sharedProblemId));
    }

    @Test
    @DisplayName("공유 문제 반응 알림은 shared_problem_reaction 을 담는다")
    void reactionCarriesType() throws Exception {
        StudyRoomSharedProblem shared = saveSharedProblem(fixture.room(), fixture.host(),
                saveProblem(fixture.host().getId()), null);
        authenticateAs(fixture.member().getId());

        mockMvc.perform(post("/api/study-room/{roomId}/shared-problems/{sharedProblemId}/reactions",
                                fixture.roomId(), shared.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ReactionToggleRequest(EMOJI))))
                .andExpect(status().isOk());

        assertThat(capturedNotification(fixture.host().getId()).data())
                .as("반응 알림 데이터")
                .containsEntry("type", "shared_problem_reaction")
                .containsEntry("roomId", String.valueOf(fixture.roomId()))
                .containsEntry("sharedProblemId", String.valueOf(shared.getId()));
    }

    @Test
    @DisplayName("공유 문제 댓글 알림은 shared_problem_comment 를 담는다")
    void commentCarriesType() throws Exception {
        StudyRoomSharedProblem shared = saveSharedProblem(fixture.room(), fixture.host(),
                saveProblem(fixture.host().getId()), null);
        authenticateAs(fixture.member().getId());

        mockMvc.perform(post("/api/study-room/{roomId}/shared-problems/{sharedProblemId}/comments",
                                fixture.roomId(), shared.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SharedProblemCommentRequest("좋은 문제네요"))))
                .andExpect(status().isCreated());

        assertThat(capturedNotification(fixture.host().getId()).data())
                .as("댓글 알림 데이터")
                .containsEntry("type", "shared_problem_comment")
                .containsEntry("roomId", String.valueOf(fixture.roomId()))
                .containsEntry("sharedProblemId", String.valueOf(shared.getId()));
    }

    private NotificationRequestDto capturedNotification(Long userId) {
        ArgumentCaptor<NotificationRequestDto> captor = ArgumentCaptor.forClass(NotificationRequestDto.class);
        verify(fcmService).sendNotificationToAllUserDevice(eq(userId), captor.capture());
        return captor.getValue();
    }
}
