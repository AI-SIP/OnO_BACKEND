package com.aisip.OnO.backend.practicenote.integration;

import com.aisip.OnO.backend.common.emoji.CustomEmojiErrorCase;
import com.aisip.OnO.backend.common.web.AppVersionResolver;
import com.aisip.OnO.backend.folder.entity.Folder;
import com.aisip.OnO.backend.practicenote.dto.PracticeNoteCompleteRequestDto;
import com.aisip.OnO.backend.practicenote.dto.PracticeNoteDeleteRequestDto;
import com.aisip.OnO.backend.practicenote.dto.PracticeNoteRegisterDto;
import com.aisip.OnO.backend.practicenote.dto.PracticeNoteUpdateDto;
import com.aisip.OnO.backend.practicenote.entity.PracticeNote;
import com.aisip.OnO.backend.practicenote.exception.PracticeNoteErrorCase;
import com.aisip.OnO.backend.practicenote.support.PracticeNoteTestSupport;
import com.aisip.OnO.backend.problem.entity.Problem;
import com.aisip.OnO.backend.problem.exception.ProblemErrorCase;
import com.aisip.OnO.backend.user.entity.User;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("복습노트 API")
class PracticeNoteIntegrationTest extends PracticeNoteTestSupport {

    /** 요일을 고르게 강제하는 앱. 프론트가 보내는 형식 그대로다. */
    private static final String NEW_APP_VERSION = "4.0.0+70";

    /** 그 검증이 없는 구버전 앱. */
    private static final String LEGACY_APP_VERSION = "3.6.0+67";

    private Long userId;
    private Long otherUserId;
    private List<Problem> problems;

    @BeforeEach
    void setUpFixtures() {
        User user = fixtures.createUser();
        User otherUser = fixtures.createOtherUser();
        userId = user.getId();
        otherUserId = otherUser.getId();
        Folder folder = fixtures.createRootFolder(userId);
        problems = saveProblems(userId, folder, 4);
        authenticateAs(userId);
    }

    @Nested
    @DisplayName("GET /api/practiceNotes")
    class GetPracticeNotes {

        @Test
        @DisplayName("복습노트 상세를 조회한다")
        void getPracticeDetail() throws Exception {
            PracticeNote practiceNote = savePracticeNote(userId, "복습", problems.subList(0, 2), dailyNotification());

            mockMvc.perform(get("/api/practiceNotes/{practiceId}", practiceNote.getId()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.practiceNoteId").value(practiceNote.getId()))
                    .andExpect(jsonPath("$.data.practiceTitle").value("복습"))
                    .andExpect(jsonPath("$.data.practiceCount").value(0))
                    .andExpect(jsonPath("$.data.problemIdList.length()").value(2))
                    .andExpect(jsonPath("$.data.practiceNotification.repeatType").value("daily"))
                    .andExpect(jsonPath("$.data.practiceNotification.hour").value(9));
        }

        @Test
        @DisplayName("다른 사용자의 복습노트를 조회하면 403")
        void getOtherUserPracticeDetail() throws Exception {
            PracticeNote practiceNote = savePracticeNote(userId, "내 복습", List.of());
            authenticateAs(otherUserId);

            mockMvc.perform(get("/api/practiceNotes/{practiceId}", practiceNote.getId()))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.errorCode")
                            .value(PracticeNoteErrorCase.PRACTICE_NOTE_USER_UNMATCHED.getErrorCode()));
        }

        @Test
        @DisplayName("존재하지 않는 복습노트를 조회하면 404")
        void getMissingPracticeDetail() throws Exception {
            mockMvc.perform(get("/api/practiceNotes/{practiceId}", nonExistentPracticeNoteId()))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.errorCode")
                            .value(PracticeNoteErrorCase.PRACTICE_NOTE_NOT_FOUND.getErrorCode()));
        }

        @Test
        @DisplayName("썸네일 목록에는 본인 복습노트만 담긴다")
        void getAllThumbnails() throws Exception {
            PracticeNote mine = savePracticeNote(userId, "내 복습", problems.subList(0, 1));
            PracticeNote others = savePracticeNote(otherUserId, "남의 복습", List.of());

            mockMvc.perform(get("/api/practiceNotes/thumbnail"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.length()").value(1))
                    .andExpect(jsonPath("$.data[0].practiceNoteId").value(mine.getId()))
                    .andExpect(jsonPath("$.data[?(@.practiceNoteId == " + others.getId() + ")]").isEmpty());
        }

        @Test
        @DisplayName("커서 기반 썸네일 조회는 다음 커서를 준다")
        void getThumbnailsWithCursor() throws Exception {
            PracticeNote first = savePracticeNote(userId, "복습 1", problems.subList(0, 2));
            savePracticeNote(userId, "복습 2", problems.subList(2, 4));

            String response = mockMvc.perform(get("/api/practiceNotes/thumbnail/V2").param("size", "1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.content.length()").value(1))
                    .andExpect(jsonPath("$.data.hasNext").value(true))
                    .andExpect(jsonPath("$.data.nextCursor").value(first.getId()))
                    .andReturn().getResponse().getContentAsString();

            Number nextCursor = JsonPath.read(response, "$.data.nextCursor");

            mockMvc.perform(get("/api/practiceNotes/thumbnail/V2")
                            .param("cursor", String.valueOf(nextCursor.longValue()))
                            .param("size", "1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.content.length()").value(1))
                    .andExpect(jsonPath("$.data.hasNext").value(false));
        }

        @Test
        @DisplayName("전체 상세 목록은 복습노트마다 문제 id 를 담는다")
        void getAllPractices() throws Exception {
            savePracticeNote(userId, "복습 1", problems.subList(0, 2));
            savePracticeNote(userId, "복습 2", problems.subList(2, 4));

            mockMvc.perform(get("/api/practiceNotes/all"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.length()").value(2))
                    .andExpect(jsonPath("$.data[0].problemIdList.length()").value(2))
                    .andExpect(jsonPath("$.data[1].problemIdList.length()").value(2));
        }

        @Test
        @DisplayName("인증 없이 조회하면 401")
        void returns401WithoutAuthentication() throws Exception {
            PracticeNote practiceNote = savePracticeNote(userId, "복습", List.of());
            clearAuthentication();

            mockMvc.perform(get("/api/practiceNotes/{practiceId}", practiceNote.getId()))
                    .andExpect(status().isUnauthorized());
            mockMvc.perform(get("/api/practiceNotes/thumbnail"))
                    .andExpect(status().isUnauthorized());
            mockMvc.perform(get("/api/practiceNotes/all"))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("POST /api/practiceNotes")
    class RegisterPracticeNote {

        @Test
        @DisplayName("복습노트를 만들고 201 과 id 를 준다")
        void registersPracticeNote() throws Exception {
            String body = objectMapper.writeValueAsString(new PracticeNoteRegisterDto(
                    null, "새 복습", problemIdsOf(problems.subList(0, 2)), dailyNotification()));

            String response = mockMvc.perform(post("/api/practiceNotes")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.data").isNumber())
                    .andReturn().getResponse().getContentAsString();

            Number practiceNoteId = JsonPath.read(response, "$.data");
            assertThat(practiceNoteRepository.findById(practiceNoteId.longValue()))
                    .isPresent()
                    .hasValueSatisfying(saved -> assertThat(saved.getTitle()).isEqualTo("새 복습"));
            verify(practiceNotificationScheduler).schedulePracticeNotification(
                    userId, practiceNoteId.longValue(), "새 복습", dailyNotification());
        }

        @Test
        @DisplayName("주간 반복은 고른 요일 그대로 스케줄을 건다")
        void registersWeeklyNotification() throws Exception {
            String body = objectMapper.writeValueAsString(new PracticeNoteRegisterDto(
                    null, "주간 복습", List.of(), weeklyNotification(List.of(1, 3, 5))));

            String response = mockMvc.perform(post("/api/practiceNotes")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isCreated())
                    .andReturn().getResponse().getContentAsString();

            Number practiceNoteId = JsonPath.read(response, "$.data");
            verify(practiceNotificationScheduler).schedulePracticeNotification(
                    userId, practiceNoteId.longValue(), "주간 복습", weeklyNotification(List.of(1, 3, 5)));
        }

        @Test
        @DisplayName("다른 사용자의 문제로는 만들 수 없다")
        void rejectsOtherUserProblem() throws Exception {
            Folder otherUserFolder = fixtures.createRootFolder(otherUserId);
            Problem otherUserProblem = saveProblem(otherUserId, otherUserFolder);
            String body = objectMapper.writeValueAsString(new PracticeNoteRegisterDto(
                    null, "침입 복습", List.of(otherUserProblem.getId()), null));

            mockMvc.perform(post("/api/practiceNotes")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.errorCode").value(ProblemErrorCase.PROBLEM_USER_UNMATCHED.getErrorCode()));

            assertThat(practiceNoteRepository.findAllByUserId(userId)).isEmpty();
        }

        @Test
        @DisplayName("인증 없이 만들면 401")
        void returns401WithoutAuthentication() throws Exception {
            clearAuthentication();
            String body = objectMapper.writeValueAsString(
                    new PracticeNoteRegisterDto(null, "새 복습", List.of(), null));

            mockMvc.perform(post("/api/practiceNotes")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("PATCH /api/practiceNotes/{practiceId}/complete")
    class CompletePracticeNote {

        @Test
        @DisplayName("본문 없이 완료하면 횟수만 올라간다")
        void completeWithoutBody() throws Exception {
            PracticeNote practiceNote = savePracticeNote(userId, "복습", problems.subList(0, 1));

            mockMvc.perform(patch("/api/practiceNotes/{practiceId}/complete", practiceNote.getId()))
                    .andExpect(status().isOk());

            PracticeNote updated = practiceNoteRepository.findById(practiceNote.getId()).orElseThrow();
            assertThat(updated.getPracticeCount()).isEqualTo(1L);
            assertThat(updated.getLastSessionMoodEmojiKey()).isNull();
        }

        @Test
        @DisplayName("완료 소감 이모지를 함께 저장한다")
        void completeWithMoodEmoji() throws Exception {
            PracticeNote practiceNote = savePracticeNote(userId, "복습", List.of());
            String body = objectMapper.writeValueAsString(new PracticeNoteCompleteRequestDto("success_checkmark"));

            mockMvc.perform(patch("/api/practiceNotes/{practiceId}/complete", practiceNote.getId())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isOk());

            mockMvc.perform(get("/api/practiceNotes/{practiceId}", practiceNote.getId()))
                    .andExpect(jsonPath("$.data.lastSessionMoodEmojiKey").value("success_checkmark"))
                    .andExpect(jsonPath("$.data.lastSolvedAt").isNotEmpty());
        }

        @Test
        @DisplayName("지원하지 않는 이모지는 400")
        void rejectsInvalidMoodEmoji() throws Exception {
            PracticeNote practiceNote = savePracticeNote(userId, "복습", List.of());
            String body = objectMapper.writeValueAsString(new PracticeNoteCompleteRequestDto("not_supported"));

            mockMvc.perform(patch("/api/practiceNotes/{practiceId}/complete", practiceNote.getId())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value(CustomEmojiErrorCase.INVALID_EMOJI_KEY.getErrorCode()));

            assertThat(practiceNoteRepository.findById(practiceNote.getId()).orElseThrow().getPracticeCount())
                    .isZero();
        }

        @Test
        @DisplayName("다른 사용자의 복습노트는 완료할 수 없다")
        void rejectsOtherUserPracticeNote() throws Exception {
            PracticeNote practiceNote = savePracticeNote(userId, "내 복습", List.of());
            authenticateAs(otherUserId);

            mockMvc.perform(patch("/api/practiceNotes/{practiceId}/complete", practiceNote.getId()))
                    .andExpect(status().isForbidden());

            assertThat(practiceNoteRepository.findById(practiceNote.getId()).orElseThrow().getPracticeCount())
                    .isZero();
        }

        @Test
        @DisplayName("인증 없이 완료하면 401")
        void returns401WithoutAuthentication() throws Exception {
            PracticeNote practiceNote = savePracticeNote(userId, "복습", List.of());
            clearAuthentication();

            mockMvc.perform(patch("/api/practiceNotes/{practiceId}/complete", practiceNote.getId()))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("PATCH /api/practiceNotes")
    class UpdatePracticeNote {

        @Test
        @DisplayName("제목과 문제 구성을 수정한다")
        void updatesPracticeNote() throws Exception {
            PracticeNote practiceNote = savePracticeNote(userId, "예전 제목", problems.subList(0, 2));
            String body = objectMapper.writeValueAsString(new PracticeNoteUpdateDto(
                    practiceNote.getId(),
                    "새 제목",
                    List.of(problems.get(2).getId()),
                    List.of(problems.get(0).getId()),
                    null));

            mockMvc.perform(patch("/api/practiceNotes")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isOk());

            assertThat(practiceNoteRepository.findById(practiceNote.getId()).orElseThrow().getTitle())
                    .isEqualTo("새 제목");
            assertThat(practiceNoteRepository.findProblemIdListByPracticeNoteId(practiceNote.getId()))
                    .containsExactly(problems.get(1).getId(), problems.get(2).getId());
        }

        @Test
        @DisplayName("문제 리스트를 생략해도 500 이 아니라 정상 처리된다")
        void updatesWithoutProblemLists() throws Exception {
            PracticeNote practiceNote = savePracticeNote(userId, "예전 제목", problems.subList(0, 1));
            String body = """
                    {"practiceNoteId": %d, "practiceTitle": "제목만 변경"}
                    """.formatted(practiceNote.getId());

            mockMvc.perform(patch("/api/practiceNotes")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isOk());

            assertThat(practiceNoteRepository.findById(practiceNote.getId()).orElseThrow().getTitle())
                    .isEqualTo("제목만 변경");
        }

        @Test
        @DisplayName("다른 사용자의 복습노트는 수정할 수 없다")
        void rejectsOtherUserPracticeNote() throws Exception {
            PracticeNote practiceNote = savePracticeNote(userId, "내 복습", List.of());
            authenticateAs(otherUserId);
            String body = objectMapper.writeValueAsString(new PracticeNoteUpdateDto(
                    practiceNote.getId(), "가로채기", List.of(), List.of(), null));

            mockMvc.perform(patch("/api/practiceNotes")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.errorCode")
                            .value(PracticeNoteErrorCase.PRACTICE_NOTE_USER_UNMATCHED.getErrorCode()));

            assertThat(practiceNoteRepository.findById(practiceNote.getId()).orElseThrow().getTitle())
                    .isEqualTo("내 복습");
        }

        @Test
        @DisplayName("인증 없이 수정하면 401")
        void returns401WithoutAuthentication() throws Exception {
            PracticeNote practiceNote = savePracticeNote(userId, "복습", List.of());
            clearAuthentication();
            String body = objectMapper.writeValueAsString(new PracticeNoteUpdateDto(
                    practiceNote.getId(), "새 제목", List.of(), List.of(), null));

            mockMvc.perform(patch("/api/practiceNotes")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("DELETE /api/practiceNotes")
    class DeletePracticeNote {

        @Test
        @DisplayName("선택한 복습노트를 지운다")
        void deletesSelectedPracticeNotes() throws Exception {
            PracticeNote deleted = savePracticeNote(userId, "지울 복습", problems.subList(0, 2));
            PracticeNote kept = savePracticeNote(userId, "남길 복습", problems.subList(2, 4));
            String body = objectMapper.writeValueAsString(
                    new PracticeNoteDeleteRequestDto(List.of(deleted.getId())));

            mockMvc.perform(delete("/api/practiceNotes")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isOk());

            assertThat(practiceNoteRepository.findAllByUserId(userId))
                    .extracting(PracticeNote::getId)
                    .containsExactly(kept.getId());
            assertThat(problemPracticeNoteMappingRepository.findAllByPracticeNoteId(deleted.getId())).isEmpty();
        }

        @Test
        @DisplayName("다른 사용자의 복습노트는 지울 수 없다")
        void rejectsOtherUserPracticeNote() throws Exception {
            PracticeNote practiceNote = savePracticeNote(userId, "내 복습", List.of());
            authenticateAs(otherUserId);
            String body = objectMapper.writeValueAsString(
                    new PracticeNoteDeleteRequestDto(List.of(practiceNote.getId())));

            mockMvc.perform(delete("/api/practiceNotes")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isForbidden());

            assertThat(practiceNoteRepository.findById(practiceNote.getId())).isPresent();
        }

        @Test
        @DisplayName("전체 삭제는 본인 복습노트만 지운다")
        void deletesAllUserPracticeNotes() throws Exception {
            savePracticeNote(userId, "복습 1", problems.subList(0, 1));
            savePracticeNote(userId, "복습 2", problems.subList(1, 2));
            PracticeNote otherUserPractice = savePracticeNote(otherUserId, "남의 복습", List.of());

            mockMvc.perform(delete("/api/practiceNotes/all"))
                    .andExpect(status().isOk());

            assertThat(practiceNoteRepository.findAllByUserId(userId)).isEmpty();
            assertThat(practiceNoteRepository.findAllByUserId(otherUserId))
                    .extracting(PracticeNote::getId)
                    .containsExactly(otherUserPractice.getId());
        }

        @Test
        @DisplayName("인증 없이 삭제하면 401")
        void returns401WithoutAuthentication() throws Exception {
            PracticeNote practiceNote = savePracticeNote(userId, "복습", List.of());
            clearAuthentication();
            String body = objectMapper.writeValueAsString(
                    new PracticeNoteDeleteRequestDto(List.of(practiceNote.getId())));

            mockMvc.perform(delete("/api/practiceNotes")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isUnauthorized());
            mockMvc.perform(delete("/api/practiceNotes/all"))
                    .andExpect(status().isUnauthorized());
        }
    }

    /**
     * 요일 없는 주간 반복 알림을 <b>앱 버전으로 가른다.</b>
     *
     * <p>신버전 앱은 요일을 고르게 강제하므로 이 조합을 만들 수 없다. 구버전 앱에는 그 검증이 없어서
     * 사용자가 "매주" 만 고른 저장이 그대로 올라오고, 예전 서버는 매일 발송으로 받아 줬다. 그래서 운영 DB 에
     * 요일이 빈 주간 알림 행이 이미 있고, 그 복습 세트를 연 구버전 사용자는 제목만 바꿔도 계속 400 을 받는다.
     * 앱을 올리기 전에는 빠져나갈 길이 없다.
     *
     * <p>헤더가 없는 요청은 구버전이다. 스토어에 나간 빌드가 헤더를 보내지 않는다.
     *
     * <p>구버전 요청이 매일 크론으로 변환되는지는 {@code PracticeNotificationSchedulerTest} 가 본다.
     * 여기서는 요청이 통과해 저장되고 스케줄러까지 그 설정 그대로 닿는지를 본다.
     */
    @Nested
    @DisplayName("요일 없는 주간 반복 알림은 앱 버전으로 가른다")
    class WeekDaysRequiredByAppVersion {

        @Test
        @DisplayName("신버전 앱의 등록 요청은 400 이고 아무것도 저장하지 않는다")
        void rejectsRegisterFromNewApp() throws Exception {
            String body = objectMapper.writeValueAsString(new PracticeNoteRegisterDto(
                    null, "요일 없는 주간 복습", List.of(), weeklyNotification(List.of())));

            mockMvc.perform(fromApp(post("/api/practiceNotes"), NEW_APP_VERSION)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode")
                            .value(PracticeNoteErrorCase.PRACTICE_NOTIFICATION_WEEK_DAYS_REQUIRED.getErrorCode()));

            assertThat(practiceNoteRepository.findAllByUserId(userId)).isEmpty();
            verify(practiceNotificationScheduler, never())
                    .schedulePracticeNotification(any(), any(), any(), any());
        }

        @Test
        @DisplayName("구버전 앱의 등록 요청은 예전처럼 받아 준다")
        void acceptsRegisterFromLegacyApp() throws Exception {
            registersWeeklyWithoutWeekDays(LEGACY_APP_VERSION);
        }

        @Test
        @DisplayName("헤더가 없는 등록 요청도 구버전으로 보고 받아 준다")
        void acceptsRegisterWithoutAppVersionHeader() throws Exception {
            registersWeeklyWithoutWeekDays(null);
        }

        @Test
        @DisplayName("신버전 앱의 수정 요청은 400 이고 기존 알림을 건드리지 않는다")
        void rejectsUpdateFromNewApp() throws Exception {
            PracticeNote practiceNote = savePracticeNote(userId, "복습", List.of(), dailyNotification());
            String body = objectMapper.writeValueAsString(new PracticeNoteUpdateDto(
                    practiceNote.getId(), "요일 없는 주간", List.of(), List.of(), weeklyNotification(List.of())));

            mockMvc.perform(fromApp(patch("/api/practiceNotes"), NEW_APP_VERSION)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode")
                            .value(PracticeNoteErrorCase.PRACTICE_NOTIFICATION_WEEK_DAYS_REQUIRED.getErrorCode()));

            PracticeNote unchanged = practiceNoteRepository.findById(practiceNote.getId()).orElseThrow();
            assertThat(unchanged.getTitle()).isEqualTo("복습");
            assertThat(unchanged.getPracticeNotification().getRepeatType()).isEqualTo("daily");
            // Quartz 잡 삭제는 트랜잭션과 함께 롤백되지 않는다. 아예 호출되면 안 된다.
            verify(practiceNotificationScheduler, never()).updateNotification(any(), any(), any(), any());
            verify(practiceNotificationScheduler, never()).deleteNotification(any());
        }

        @Test
        @DisplayName("구버전 앱의 수정 요청은 예전처럼 받아 준다")
        void acceptsUpdateFromLegacyApp() throws Exception {
            updatesWeeklyWithoutWeekDays(LEGACY_APP_VERSION);
        }

        @Test
        @DisplayName("헤더가 없는 수정 요청도 구버전으로 보고 받아 준다")
        void acceptsUpdateWithoutAppVersionHeader() throws Exception {
            updatesWeeklyWithoutWeekDays(null);
        }

        private void registersWeeklyWithoutWeekDays(String appVersion) throws Exception {
            String body = objectMapper.writeValueAsString(new PracticeNoteRegisterDto(
                    null, "요일 없는 주간 복습", List.of(), weeklyNotification(List.of())));

            String response = mockMvc.perform(fromApp(post("/api/practiceNotes"), appVersion)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isCreated())
                    .andReturn().getResponse().getContentAsString();

            Number practiceNoteId = JsonPath.read(response, "$.data");
            assertThat(practiceNoteRepository.findById(practiceNoteId.longValue()))
                    .isPresent()
                    .hasValueSatisfying(saved ->
                            assertThat(saved.getPracticeNotification().getRepeatType()).isEqualTo("weekly"));
            verify(practiceNotificationScheduler).schedulePracticeNotification(
                    userId, practiceNoteId.longValue(), "요일 없는 주간 복습", weeklyNotification(List.of()));
        }

        private void updatesWeeklyWithoutWeekDays(String appVersion) throws Exception {
            PracticeNote practiceNote = savePracticeNote(userId, "복습", List.of(), dailyNotification());
            String body = objectMapper.writeValueAsString(new PracticeNoteUpdateDto(
                    practiceNote.getId(), "요일 없는 주간", List.of(), List.of(), weeklyNotification(List.of())));

            mockMvc.perform(fromApp(patch("/api/practiceNotes"), appVersion)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isOk());

            PracticeNote updated = practiceNoteRepository.findById(practiceNote.getId()).orElseThrow();
            assertThat(updated.getTitle()).isEqualTo("요일 없는 주간");
            assertThat(updated.getPracticeNotification().getRepeatType()).isEqualTo("weekly");
            verify(practiceNotificationScheduler).updateNotification(
                    userId, practiceNote.getId(), "요일 없는 주간", weeklyNotification(List.of()));
        }
    }

    /** {@code null} 이면 헤더를 아예 붙이지 않는다. 헤더를 안 보내는 구버전 앱이다. */
    private MockHttpServletRequestBuilder fromApp(MockHttpServletRequestBuilder request, String appVersion) {
        return appVersion == null
                ? request
                : request.header(AppVersionResolver.APP_VERSION_HEADER, appVersion);
    }
}
