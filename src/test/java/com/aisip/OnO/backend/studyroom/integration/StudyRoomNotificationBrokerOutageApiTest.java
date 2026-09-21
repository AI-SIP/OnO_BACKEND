package com.aisip.OnO.backend.studyroom.integration;

import com.aisip.OnO.backend.problem.entity.Problem;
import com.aisip.OnO.backend.studyroom.dto.StudyRoomDtos.ReactionToggleRequest;
import com.aisip.OnO.backend.studyroom.dto.StudyRoomDtos.SharedProblemCommentRequest;
import com.aisip.OnO.backend.studyroom.dto.StudyRoomDtos.SharedProblemCreateRequest;
import com.aisip.OnO.backend.studyroom.entity.StudyRoomSharedProblem;
import com.aisip.OnO.backend.studyroom.support.StudyRoomTestSupport;
import com.aisip.OnO.backend.util.fcm.support.BrokerOutageFcm;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.transaction.PlatformTransactionManager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * RabbitMQ 브로커 장애 중 스터디룸 쓰기 요청.
 *
 * <p>공유, 반응, 댓글은 저장 뒤 같은 트랜잭션 안에서 알림을 큐에 넣는다. 적재 실패는 호출부가
 * try/catch 로 삼키지만, 예전에는 {@code FcmService} 가 트랜잭션에 참여한 채 예외를 던져 바깥
 * 트랜잭션이 rollback-only 로 표시됐다. 그러면 커밋 단계에서 {@code UnexpectedRollbackException} 이 나
 * 500 이 응답되고 저장도 사라졌다. 알림은 곁다리 기능이라 본 작업을 되돌리면 안 된다.
 */
@DisplayName("스터디룸 알림 브로커 장애")
class StudyRoomNotificationBrokerOutageApiTest extends StudyRoomTestSupport {

    @Autowired
    private PlatformTransactionManager transactionManager;

    private BrokerOutageFcm brokerOutage;
    private RoomFixture fixture;

    @BeforeEach
    void brokerDown() {
        brokerOutage = BrokerOutageFcm.create(transactionManager);
        brokerOutage.routeFrom(fcmService);
        fixture = createRoomWithMemberAndOutsider();
    }

    @Test
    @DisplayName("문제 공유는 알림 적재가 실패해도 201 로 끝나고 저장된다")
    void shareProblemCommits() throws Exception {
        Problem problem = saveProblem(fixture.member().getId());
        authenticateAs(fixture.member().getId());

        mockMvc.perform(post("/api/study-room/{roomId}/shared-problems", fixture.roomId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SharedProblemCreateRequest(problem.getId(), "같이 봐요"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.problemId").value(problem.getId()));

        assertThat(brokerOutage.enqueueAttempts()).as("방장에게 알림 적재를 시도했다").isEqualTo(1);
        assertThat(sharedProblemRepository.findAll()).as("커밋된 공유 문제").hasSize(1);
    }

    @Test
    @DisplayName("반응은 알림 적재가 실패해도 200 으로 끝나고 저장된다")
    void toggleReactionCommits() throws Exception {
        StudyRoomSharedProblem shared = saveSharedProblem(fixture.room(), fixture.host(),
                saveProblem(fixture.host().getId()), null);
        authenticateAs(fixture.member().getId());

        mockMvc.perform(post("/api/study-room/{roomId}/shared-problems/{sharedProblemId}/reactions",
                                fixture.roomId(), shared.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ReactionToggleRequest(EMOJI))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reactions[0].count").value(1));

        assertThat(brokerOutage.enqueueAttempts()).as("공유한 사람에게 알림 적재를 시도했다").isEqualTo(1);
        assertThat(sharedProblemReactionRepository.findAll()).as("커밋된 반응").hasSize(1);
    }

    @Test
    @DisplayName("댓글은 알림 적재가 실패해도 201 로 끝나고 저장된다")
    void createCommentCommits() throws Exception {
        StudyRoomSharedProblem shared = saveSharedProblem(fixture.room(), fixture.host(),
                saveProblem(fixture.host().getId()), null);
        authenticateAs(fixture.member().getId());

        mockMvc.perform(post("/api/study-room/{roomId}/shared-problems/{sharedProblemId}/comments",
                                fixture.roomId(), shared.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SharedProblemCommentRequest("좋은 문제네요"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.content").value("좋은 문제네요"));

        assertThat(brokerOutage.enqueueAttempts()).as("공유한 사람에게 알림 적재를 시도했다").isEqualTo(1);
        assertThat(commentRepository.findAll()).as("커밋된 댓글").hasSize(1);
    }
}
