package com.aisip.OnO.backend.practicenote.service;

import com.aisip.OnO.backend.util.fcm.dto.NotificationRequestDto;
import com.aisip.OnO.backend.util.fcm.service.FcmService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.quartz.JobDataMap;
import org.quartz.JobExecutionContext;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

/**
 * 복습노트 반복 알림의 페이로드를 고정한다.
 *
 * <p>앱은 {@code data["type"]} 만 보고 화면을 고른다. 예전에는 이 잡이 {@code practiceId} 만 담아
 * 사용자가 직접 켠 알림을 눌러도 아무 화면이 열리지 않았다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PracticeNotificationJob")
class PracticeNotificationJobTest {

    private static final Long USER_ID = 7L;
    private static final Long PRACTICE_ID = 42L;

    @Mock
    private FcmService fcmService;

    @Mock
    private JobExecutionContext context;

    private PracticeNotificationJob job;

    @BeforeEach
    void setUp() {
        job = new PracticeNotificationJob();
        ReflectionTestUtils.setField(job, "fcmService", fcmService);

        JobDataMap dataMap = new JobDataMap();
        dataMap.put("userId", USER_ID);
        dataMap.put("practiceId", PRACTICE_ID);
        dataMap.put("practiceTitle", "미적분 복습 세트");
        given(context.getMergedJobDataMap()).willReturn(dataMap);
    }

    @Test
    @DisplayName("알림 데이터에 type 과 practiceId 를 담아 보낸다")
    void sendsNotificationTypeWithPracticeId() {
        job.executeInternal(context);

        ArgumentCaptor<NotificationRequestDto> dto = ArgumentCaptor.forClass(NotificationRequestDto.class);
        verify(fcmService).sendNotificationToAllUserDevice(eq(USER_ID), dto.capture());

        assertThat(dto.getValue().data())
                .as("알림 데이터")
                .containsEntry("type", "practice_note_reminder")
                .containsEntry("practiceId", String.valueOf(PRACTICE_ID));
        assertThat(dto.getValue().body()).as("알림 본문").contains("미적분 복습 세트");
    }
}
