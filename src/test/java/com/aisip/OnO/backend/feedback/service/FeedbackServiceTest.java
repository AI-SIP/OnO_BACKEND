package com.aisip.OnO.backend.feedback.service;

import com.aisip.OnO.backend.common.exception.ApplicationException;
import com.aisip.OnO.backend.feedback.dto.FeedbackRequestDto;
import com.aisip.OnO.backend.feedback.dto.FeedbackResponseDto;
import com.aisip.OnO.backend.feedback.entity.UserFeedback;
import com.aisip.OnO.backend.feedback.exception.FeedbackErrorCase;
import com.aisip.OnO.backend.feedback.repository.UserFeedbackRepository;
import com.aisip.OnO.backend.support.IntegrationTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.BDDMockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;

@DisplayName("FeedbackService")
class FeedbackServiceTest extends IntegrationTestSupport {

    @Autowired
    private FeedbackService feedbackService;

    @Autowired
    private UserFeedbackRepository feedbackRepository;

    private FeedbackRequestDto minimalRequest() {
        FeedbackRequestDto dto = new FeedbackRequestDto();
        dto.setNpsScore(8);
        return dto;
    }

    private UserFeedback onlySavedFeedback() {
        List<UserFeedback> all = feedbackRepository.findAll();
        assertThat(all).hasSize(1);
        return all.get(0);
    }

    @Nested
    @DisplayName("피드백 저장")
    class Save {

        @Test
        @DisplayName("응답 내용과 제출 시각, 접속 IP 를 함께 저장한다")
        void savesAnswersWithIpAndSubmittedAt() {
            LocalDateTime before = LocalDateTime.now().minusSeconds(1);
            FeedbackRequestDto dto = minimalRequest();
            dto.setUsageFrequency("매일");
            dto.setClassificationMethod("과목별");
            dto.setTemplateSatisfaction(4);
            dto.setMostUsedFeature("오답 등록");

            feedbackService.save(dto, "203.0.113.7");

            assertThat(onlySavedFeedback()).satisfies(saved -> {
                assertThat(saved.getNpsScore()).isEqualTo(8);
                assertThat(saved.getUsageFrequency()).isEqualTo("매일");
                assertThat(saved.getClassificationMethod()).isEqualTo("과목별");
                assertThat(saved.getTemplateSatisfaction()).isEqualTo(4);
                assertThat(saved.getMostUsedFeature()).isEqualTo("오답 등록");
                assertThat(saved.getIpAddress()).isEqualTo("203.0.113.7");
                assertThat(saved.getSubmittedAt()).isAfterOrEqualTo(before);
            });
        }

        @Test
        @DisplayName("모든 항목이 비어 있어도 저장된다 - 설문은 전부 선택 항목이다")
        void savesEmptySubmission() {
            feedbackService.save(new FeedbackRequestDto(), "203.0.113.7");

            assertThat(onlySavedFeedback()).satisfies(saved -> {
                assertThat(saved.getId()).isNotNull();
                assertThat(saved.getNpsScore()).isNull();
                assertThat(saved.getUsagePurpose()).isNull();
                assertThat(saved.getPracticeNoteUsed()).isNull();
            });
        }

        @Test
        @DisplayName("같은 IP 로 여러 번 제출해도 모두 남는다 - 중복 제출을 막지 않는다")
        void keepsEveryDuplicateSubmission() {
            feedbackService.save(minimalRequest(), "203.0.113.7");
            feedbackService.save(minimalRequest(), "203.0.113.7");

            assertThat(feedbackRepository.count())
                    .as("중복 차단을 도입하려면 이 테스트를 먼저 바꿔야 한다는 것을 드러내 둔다")
                    .isEqualTo(2);
        }

        @Test
        @DisplayName("피드백에는 작성자 식별자가 남지 않는다 - 익명 설문이다")
        void storesNoUserIdentity() {
            feedbackService.save(minimalRequest(), "203.0.113.7");

            assertThat(UserFeedback.class.getDeclaredFields())
                    .extracting(java.lang.reflect.Field::getName)
                    .as("userId 를 붙이는 순간 익명 설문이 아니게 되고 소유권 검증 규칙의 적용 대상이 된다")
                    .doesNotContain("userId", "user");
        }
    }

    @Nested
    @DisplayName("다중 선택 + 기타 입력 조합")
    class MultiSelectWithOther {

        @Test
        @DisplayName("선택지를 쉼표로 이어 붙인다")
        void joinsSelectedItemsWithComma() {
            FeedbackRequestDto dto = minimalRequest();
            dto.setUsagePurpose(List.of("시험 대비", "숙제", "복습"));

            feedbackService.save(dto, "1.1.1.1");

            assertThat(onlySavedFeedback().getUsagePurpose()).isEqualTo("시험 대비,숙제,복습");
        }

        @Test
        @DisplayName("선택지 없이 기타 텍스트만 있으면 '기타: 내용' 으로 저장한다")
        void storesOtherTextAloneWithPrefix() {
            FeedbackRequestDto dto = minimalRequest();
            dto.setUsagePurposeOther("  개인 기록용  ");

            feedbackService.save(dto, "1.1.1.1");

            assertThat(onlySavedFeedback().getUsagePurpose())
                    .as("앞뒤 공백은 잘라내야 한다")
                    .isEqualTo("기타: 개인 기록용");
        }

        @Test
        @DisplayName("선택지에 '기타' 가 있으면 기타 텍스트로 치환한다")
        void replacesOtherOptionWithTypedText() {
            FeedbackRequestDto dto = minimalRequest();
            dto.setUsagePurpose(List.of("시험 대비", "기타"));
            dto.setUsagePurposeOther("동아리 활동");

            feedbackService.save(dto, "1.1.1.1");

            assertThat(onlySavedFeedback().getUsagePurpose())
                    .as("'기타' 라는 선택지 자체가 그대로 남으면 집계에서 의미 없는 값이 된다")
                    .isEqualTo("시험 대비,기타: 동아리 활동");
        }

        @Test
        @DisplayName("선택지도 기타 텍스트도 없으면 null 로 둔다")
        void storesNullWhenNothingSelected() {
            feedbackService.save(minimalRequest(), "1.1.1.1");

            assertThat(onlySavedFeedback().getUsagePurpose()).isNull();
        }

        @ParameterizedTest(name = "기타 텍스트=\"{0}\"")
        @ValueSource(strings = {"", "   "})
        @DisplayName("공백뿐인 기타 텍스트는 입력하지 않은 것으로 본다")
        void treatsBlankOtherTextAsAbsent(String otherText) {
            FeedbackRequestDto dto = minimalRequest();
            dto.setUsagePurpose(List.of("시험 대비", "기타"));
            dto.setUsagePurposeOther(otherText);

            feedbackService.save(dto, "1.1.1.1");

            assertThat(onlySavedFeedback().getUsagePurpose()).isEqualTo("시험 대비,기타");
        }
    }

    @Nested
    @DisplayName("분기 응답 정리")
    class ConditionalAnswers {

        @Test
        @DisplayName("복습 세트를 쓰는 경우 알림 효과는 남기고 미사용 이유는 버린다")
        void keepsNotificationAnswerWhenPracticeNoteUsed() {
            FeedbackRequestDto dto = minimalRequest();
            dto.setPracticeNoteUsed(true);
            dto.setNotificationEffectiveness("도움이 된다");
            dto.setReviewSetNonUsageReason(List.of("몰랐다"));

            feedbackService.save(dto, "1.1.1.1");

            assertThat(onlySavedFeedback()).satisfies(saved -> {
                assertThat(saved.getNotificationEffectiveness()).isEqualTo("도움이 된다");
                assertThat(saved.getReviewSetNonUsageReason())
                        .as("사용 중이라고 답했는데 미사용 이유가 남으면 집계가 모순된다")
                        .isNull();
            });
        }

        @ParameterizedTest(name = "practiceNoteUsed={0}")
        @ValueSource(strings = {"false", "null"})
        @DisplayName("복습 세트를 쓰지 않으면 미사용 이유만 남기고 알림 효과는 버린다")
        void keepsNonUsageReasonWhenPracticeNoteUnused(String practiceNoteUsed) {
            FeedbackRequestDto dto = minimalRequest();
            dto.setPracticeNoteUsed("null".equals(practiceNoteUsed) ? null : Boolean.FALSE);
            dto.setNotificationEffectiveness("도움이 된다");
            dto.setReviewSetNonUsageReason(List.of("몰랐다", "기타"));
            dto.setReviewSetNonUsageReasonOther("시간이 없다");

            feedbackService.save(dto, "1.1.1.1");

            assertThat(onlySavedFeedback()).satisfies(saved -> {
                assertThat(saved.getNotificationEffectiveness()).isNull();
                assertThat(saved.getReviewSetNonUsageReason()).isEqualTo("몰랐다,기타: 시간이 없다");
            });
        }

        @Test
        @DisplayName("스터디룸을 쓰는 경우 챌린지·공유 만족도를 남기고 미사용 이유는 버린다")
        void keepsStudyRoomAnswersWhenInUse() {
            FeedbackRequestDto dto = minimalRequest();
            dto.setStudyRoomUsage("사용 중");
            dto.setChallengeMotivation(5);
            dto.setProblemSharingUsefulness(4);
            dto.setStudyRoomNonUsageReason(List.of("친구가 없다"));

            feedbackService.save(dto, "1.1.1.1");

            assertThat(onlySavedFeedback()).satisfies(saved -> {
                assertThat(saved.getChallengeMotivation()).isEqualTo(5);
                assertThat(saved.getProblemSharingUsefulness()).isEqualTo(4);
                assertThat(saved.getStudyRoomNonUsageReason()).isNull();
            });
        }

        @ParameterizedTest(name = "studyRoomUsage=\"{0}\"")
        @ValueSource(strings = {"사용하지 않음", "사용중", "사용 중 ", ""})
        @DisplayName("'사용 중' 과 정확히 일치하지 않으면 미사용으로 본다")
        void treatsAnythingButExactMatchAsUnused(String studyRoomUsage) {
            FeedbackRequestDto dto = minimalRequest();
            dto.setStudyRoomUsage(studyRoomUsage);
            dto.setChallengeMotivation(5);
            dto.setProblemSharingUsefulness(4);
            dto.setStudyRoomNonUsageReason(List.of("친구가 없다"));

            feedbackService.save(dto, "1.1.1.1");

            assertThat(onlySavedFeedback()).satisfies(saved -> {
                assertThat(saved.getChallengeMotivation())
                        .as("문자열 완전 일치로 판정하므로 표기가 조금만 달라도 미사용 처리된다")
                        .isNull();
                assertThat(saved.getProblemSharingUsefulness()).isNull();
                assertThat(saved.getStudyRoomNonUsageReason()).isEqualTo("친구가 없다");
            });
        }

        @ParameterizedTest(name = "자유 입력=\"{0}\"")
        @ValueSource(strings = {"", "   ", "\n\t "})
        @DisplayName("공백뿐인 자유 입력은 null 로 저장한다")
        void storesBlankFreeTextAsNull(String blank) {
            FeedbackRequestDto dto = minimalRequest();
            dto.setPainPoints(blank);
            dto.setDesiredFeatures(blank);

            feedbackService.save(dto, "1.1.1.1");

            assertThat(onlySavedFeedback()).satisfies(saved -> {
                assertThat(saved.getPainPoints()).isNull();
                assertThat(saved.getDesiredFeatures()).isNull();
            });
        }

        @Test
        @DisplayName("자유 입력의 앞뒤 공백은 잘라낸다")
        void trimsFreeText() {
            FeedbackRequestDto dto = minimalRequest();
            dto.setPainPoints("  검색이 느려요  ");

            feedbackService.save(dto, "1.1.1.1");

            assertThat(onlySavedFeedback().getPainPoints()).isEqualTo("검색이 느려요");
        }
    }

    @Nested
    @DisplayName("컬럼 길이 경계")
    class ColumnLengthBoundary {

        @Test
        @DisplayName("usage_purpose 는 500자까지 저장된다")
        void allowsUsagePurposeUpTo500() {
            FeedbackRequestDto dto = minimalRequest();
            dto.setUsagePurpose(List.of("가".repeat(500)));

            assertThatCode(() -> feedbackService.save(dto, "1.1.1.1")).doesNotThrowAnyException();
            assertThat(onlySavedFeedback().getUsagePurpose()).hasSize(500);
        }

        @Test
        @DisplayName("usage_purpose 가 501자면 DB 제약으로 거절된다")
        void rejectsUsagePurposeOver500() {
            FeedbackRequestDto dto = minimalRequest();
            dto.setUsagePurpose(List.of("가".repeat(501)));

            assertThatThrownBy(() -> feedbackService.save(dto, "1.1.1.1"))
                    .as("잘린 채 저장되면 응답 내용이 소리 없이 훼손된다")
                    .isInstanceOf(DataIntegrityViolationException.class);
        }

        @Test
        @DisplayName("study_room_non_usage_reason 은 300자까지 저장된다")
        void allowsStudyRoomNonUsageReasonUpTo300() {
            FeedbackRequestDto dto = minimalRequest();
            dto.setStudyRoomUsage("사용하지 않음");
            dto.setStudyRoomNonUsageReason(List.of("나".repeat(300)));

            assertThatCode(() -> feedbackService.save(dto, "1.1.1.1")).doesNotThrowAnyException();
            assertThat(onlySavedFeedback().getStudyRoomNonUsageReason()).hasSize(300);
        }

        @Test
        @DisplayName("most_used_feature 는 100자까지 저장된다")
        void allowsMostUsedFeatureUpTo100() {
            FeedbackRequestDto dto = minimalRequest();
            dto.setMostUsedFeature("다".repeat(100));

            assertThatCode(() -> feedbackService.save(dto, "1.1.1.1")).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("pain_points 와 desired_features 는 TEXT 라 varchar 한도를 넘겨도 저장된다")
        void allowsLongFreeText() {
            FeedbackRequestDto dto = minimalRequest();
            dto.setPainPoints("라".repeat(3000));
            dto.setDesiredFeatures("마".repeat(3000));

            assertThatCode(() -> feedbackService.save(dto, "1.1.1.1")).doesNotThrowAnyException();
            assertThat(onlySavedFeedback().getPainPoints()).hasSize(3000);
        }

        @Test
        @DisplayName("ip_address 컬럼 한도(50자)를 넘는 값은 잘라서 저장한다")
        void truncatesOverlongIpAddress() {
            String forgedIp = "9".repeat(120);

            assertThatCode(() -> feedbackService.save(minimalRequest(), forgedIp))
                    .as("IP 는 사용자가 X-Forwarded-For 로 조작할 수 있다. 설문 응답이 헤더 때문에 버려지면 안 된다")
                    .doesNotThrowAnyException();

            assertThat(onlySavedFeedback().getIpAddress()).hasSize(50);
        }
    }

    @Nested
    @DisplayName("Discord 알림")
    class DiscordNotification {

        @Test
        @DisplayName("저장에 성공하면 알림을 보낸다")
        void notifiesAfterSave() {
            FeedbackRequestDto dto = minimalRequest();
            dto.setUsageFrequency("매일");

            feedbackService.save(dto, "1.1.1.1");

            verify(discordWebhookNotificationService).sendMessage(anyString(), anyString());
        }

        @Test
        @DisplayName("알림 전송이 실패해도 응답 저장은 되돌아가지 않는다")
        void keepsSavedFeedbackWhenNotificationFails() {
            BDDMockito.willThrow(new RuntimeException("웹훅 장애"))
                    .given(discordWebhookNotificationService).sendMessage(anyString(), anyString());

            assertThatCode(() -> feedbackService.save(minimalRequest(), "1.1.1.1"))
                    .doesNotThrowAnyException();

            assertThat(feedbackRepository.count())
                    .as("알림 실패로 설문 응답이 사라지면 안 된다")
                    .isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("조회")
    class Query {

        @Test
        @DisplayName("제출 시각 내림차순으로 페이지를 돌려준다")
        void returnsPageOrderedBySubmittedAtDesc() {
            UserFeedback oldest = feedbackRepository.save(UserFeedback.builder()
                    .submittedAt(LocalDateTime.now().minusDays(3)).build());
            UserFeedback middle = feedbackRepository.save(UserFeedback.builder()
                    .submittedAt(LocalDateTime.now().minusDays(2)).build());
            UserFeedback newest = feedbackRepository.save(UserFeedback.builder()
                    .submittedAt(LocalDateTime.now()).build());

            Page<FeedbackResponseDto> page = feedbackService.findAll(0, 2);

            assertThat(page.getTotalElements()).isEqualTo(3);
            assertThat(page.getContent()).extracting(FeedbackResponseDto::getId)
                    .containsExactly(newest.getId(), middle.getId());
            assertThat(feedbackService.findAll(1, 2).getContent())
                    .extracting(FeedbackResponseDto::getId)
                    .containsExactly(oldest.getId());
        }

        @Test
        @DisplayName("음수 page 와 0 이하 size 는 유효한 값으로 보정한다")
        void clampsInvalidPageRequest() {
            feedbackRepository.save(UserFeedback.builder().submittedAt(LocalDateTime.now()).build());

            assertThatCode(() -> feedbackService.findAll(-1, 0))
                    .as("PageRequest.of 가 그대로 터지면 관리자 화면 전체가 500 이 된다")
                    .doesNotThrowAnyException();
            assertThat(feedbackService.findAll(-1, 0).getContent()).hasSize(1);
        }

        @Test
        @DisplayName("id 로 단건 조회한다")
        void findsById() {
            UserFeedback saved = feedbackRepository.save(UserFeedback.builder()
                    .npsScore(7)
                    .painPoints("느려요")
                    .submittedAt(LocalDateTime.now())
                    .build());

            FeedbackResponseDto dto = feedbackService.findById(saved.getId());

            assertThat(dto.getId()).isEqualTo(saved.getId());
            assertThat(dto.getNpsScore()).isEqualTo(7);
            assertThat(dto.getPainPoints()).isEqualTo("느려요");
        }

        @Test
        @DisplayName("없는 id 는 FEEDBACK_NOT_FOUND 로 거절한다")
        void rejectsUnknownId() {
            assertThatThrownBy(() -> feedbackService.findById(999_999L))
                    .isInstanceOf(ApplicationException.class)
                    .extracting(thrown -> ((ApplicationException) thrown).getErrorCase())
                    .as("IllegalArgumentException 이면 500 으로 나가면서 에러 알림까지 발송된다")
                    .isEqualTo(FeedbackErrorCase.FEEDBACK_NOT_FOUND);
        }

        @Test
        @DisplayName("응답이 없으면 건수는 0, 평균 NPS 는 null 이다")
        void returnsZeroCountAndNullAverageWhenEmpty() {
            assertThat(feedbackService.count()).isZero();
            assertThat(feedbackService.averageNps())
                    .as("표본이 없을 때 0 을 주면 실제 NPS 0점과 구분되지 않는다")
                    .isNull();
        }

        @Test
        @DisplayName("평균 NPS 는 점수를 남긴 응답만으로 계산한다")
        void averagesOnlyScoredFeedback() {
            feedbackRepository.save(UserFeedback.builder().npsScore(10).submittedAt(LocalDateTime.now()).build());
            feedbackRepository.save(UserFeedback.builder().npsScore(4).submittedAt(LocalDateTime.now()).build());
            feedbackRepository.save(UserFeedback.builder().submittedAt(LocalDateTime.now()).build());

            assertThat(feedbackService.count()).isEqualTo(3);
            assertThat(feedbackService.averageNps()).isEqualTo(7.0);
        }
    }
}
