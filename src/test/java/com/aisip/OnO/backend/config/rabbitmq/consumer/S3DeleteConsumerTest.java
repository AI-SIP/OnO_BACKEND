package com.aisip.OnO.backend.config.rabbitmq.consumer;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * S3 이미지 삭제 컨슈머 테스트.
 *
 * <p>{@code FileUploadService} 는 베이스에서 목으로 잡혀 있어 실제 S3 객체가 지워지지 않는다.
 * 삭제는 되돌릴 수 없으므로 이 테스트는 <b>어떤 URL 로 삭제가 요청되는지</b>를 검증하는 데 집중한다.
 *
 * <p>계약: 삭제 실패는 예외로 전파해 재시도·DLQ 를 태우고, DLQ 알림에는 S3 객체 키를 마스킹해 남긴다.
 */
@DisplayName("S3DeleteConsumer")
class S3DeleteConsumerTest extends RabbitConsumerTestSupport {

    private static final String IMAGE_URL =
            "https://test-ono-bucket.s3.ap-northeast-2.amazonaws.com/problem/2026/ab12cd34-ef56-7890-abcd-ef1234567890.png";

    @Autowired
    private S3DeleteConsumer consumer;

    private String capturedDlqDetails() {
        ArgumentCaptor<String> details = ArgumentCaptor.forClass(String.class);
        verify(discordWebhookNotificationService).sendErrorNotification(
                eq("RabbitMQ DLQ - S3 Delete"), details.capture(), eq("ERROR"), anyString());
        return details.getValue();
    }

    // ════════════════════════════ 정상 처리 ════════════════════════════

    @Nested
    @DisplayName("정상 처리")
    class Delete {

        @Test
        @DisplayName("메시지의 이미지 URL 을 그대로 S3 삭제에 넘긴다")
        void deletesRequestedImage() {
            consumer.handleS3DeleteMessage(s3Message(IMAGE_URL, 10L));

            verify(fileUploadService).deleteImageFileFromS3(IMAGE_URL);
            verifyNoInteractions(discordWebhookNotificationService);
        }

        @Test
        @DisplayName("삭제 대상이 아닌 다른 URL 로는 삭제를 요청하지 않는다")
        void deletesOnlyTheGivenUrl() {
            consumer.handleS3DeleteMessage(s3Message(IMAGE_URL, 10L));

            verify(fileUploadService, never()).deleteImageFileFromS3(
                    "https://test-ono-bucket.s3.ap-northeast-2.amazonaws.com/other.png");
        }
    }

    // ════════════════════════════ 실패 전파 ════════════════════════════

    @Nested
    @DisplayName("삭제 실패")
    class Failure {

        @Test
        @DisplayName("S3 삭제가 실패하면 예외를 던져 재시도·DLQ 로 넘긴다")
        void propagatesFailure() {
            willThrow(new IllegalStateException("S3 timeout"))
                    .given(fileUploadService).deleteImageFileFromS3(IMAGE_URL);

            assertThatThrownBy(() -> consumer.handleS3DeleteMessage(s3Message(IMAGE_URL, 77L)))
                    .as("예외를 삼키면 지워지지 않은 이미지가 조용히 남는다")
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("77")
                    .hasRootCauseMessage("S3 timeout");
        }

        @Test
        @DisplayName("실패 시점에는 Discord 알림을 보내지 않는다 (재시도 여지가 남아 있다)")
        void doesNotNotifyOnFirstFailure() {
            willThrow(new IllegalStateException("S3 timeout"))
                    .given(fileUploadService).deleteImageFileFromS3(IMAGE_URL);

            assertThatThrownBy(() -> consumer.handleS3DeleteMessage(s3MessageWithRetryCount(IMAGE_URL, 77L, 2)))
                    .isInstanceOf(RuntimeException.class);

            verifyNoInteractions(discordWebhookNotificationService);
        }
    }

    // ════════════════════════════ 메시지 결손 ════════════════════════════

    @Nested
    @DisplayName("메시지 필드 결손")
    class MissingFields {

        @Test
        @DisplayName("imageUrl 이 null 이면 삭제를 시도하지 않고 종료한다")
        void skipsNullImageUrl() {
            assertThatCode(() -> consumer.handleS3DeleteMessage(s3Message(null, 10L)))
                    .as("삭제할 대상이 없는 메시지는 재시도해도 영원히 실패한다")
                    .doesNotThrowAnyException();

            verifyNoInteractions(fileUploadService);
        }

        @Test
        @DisplayName("imageUrl 이 빈 문자열이어도 삭제를 시도하지 않는다")
        void skipsBlankImageUrl() {
            assertThatCode(() -> consumer.handleS3DeleteMessage(s3Message("   ", 10L)))
                    .doesNotThrowAnyException();

            verifyNoInteractions(fileUploadService);
        }

        @Test
        @DisplayName("problemId 가 null 이어도 삭제는 정상 수행된다")
        void deletesEvenWithoutProblemId() {
            assertThatCode(() -> consumer.handleS3DeleteMessage(s3Message(IMAGE_URL, null)))
                    .doesNotThrowAnyException();

            verify(fileUploadService).deleteImageFileFromS3(IMAGE_URL);
        }
    }

    // ════════════════════════════ DLQ ════════════════════════════

    @Nested
    @DisplayName("DLQ 핸들러")
    class DeadLetter {

        @Test
        @DisplayName("최종 실패 메시지는 Discord 로 알린다")
        void notifiesDiscord() {
            consumer.handleS3DeleteDLQ(s3MessageWithRetryCount(IMAGE_URL, 77L, 3));

            assertThat(capturedDlqDetails())
                    .as("어떤 문제의 몇 번째 재시도가 최종 실패했는지 알 수 있어야 한다")
                    .contains("77")
                    .contains("3");
        }

        @Test
        @DisplayName("DLQ 알림에 S3 객체 키 원문을 그대로 넣지 않는다")
        void masksObjectKey() {
            consumer.handleS3DeleteDLQ(s3Message(IMAGE_URL, 77L));

            String details = capturedDlqDetails();
            assertThat(details)
                    .as("객체 키 원문이 외부 채널로 그대로 나가면 안 된다")
                    .doesNotContain("ab12cd34-ef56-7890-abcd-ef1234567890.png")
                    .doesNotContain(IMAGE_URL);
            assertThat(details).as("마스킹 흔적은 남아야 추적이 가능하다").contains("***");
        }

        @Test
        @DisplayName("짧은 객체 키는 통째로 가린다")
        void masksShortObjectKeyCompletely() {
            consumer.handleS3DeleteDLQ(
                    s3Message("https://test-ono-bucket.s3.amazonaws.com/a.png", 77L));

            assertThat(capturedDlqDetails())
                    .as("앞뒤를 남기면 짧은 키는 사실상 노출된다")
                    .doesNotContain("a.png")
                    .contains("***");
        }

        @Test
        @DisplayName("imageUrl 이 null 이면 unknown 으로 알린다")
        void reportsUnknownForNullUrl() {
            assertThatCode(() -> consumer.handleS3DeleteDLQ(s3Message(null, 77L)))
                    .doesNotThrowAnyException();

            assertThat(capturedDlqDetails()).contains("unknown");
        }

        @Test
        @DisplayName("DLQ 처리에서 S3 삭제를 다시 시도하지는 않는다")
        void doesNotRetryDeletion() {
            consumer.handleS3DeleteDLQ(s3Message(IMAGE_URL, 77L));

            verifyNoInteractions(fileUploadService);
        }

        @Test
        @DisplayName("Discord 알림이 실패해도 DLQ 핸들러는 예외를 던지지 않는다")
        void swallowsDiscordFailure() {
            willThrow(new IllegalStateException("webhook down"))
                    .given(discordWebhookNotificationService)
                    .sendErrorNotification(anyString(), anyString(), anyString(), anyString());

            assertThatCode(() -> consumer.handleS3DeleteDLQ(s3Message(IMAGE_URL, 77L)))
                    .as("DLQ 에서 예외를 던지면 최종 실패 기록마저 잃는다")
                    .doesNotThrowAnyException();
        }
    }
}
