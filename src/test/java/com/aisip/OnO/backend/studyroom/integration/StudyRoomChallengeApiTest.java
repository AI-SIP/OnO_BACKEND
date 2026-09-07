package com.aisip.OnO.backend.studyroom.integration;

import com.aisip.OnO.backend.studyroom.dto.StudyRoomDtos.ChallengeCreateRequest;
import com.aisip.OnO.backend.studyroom.entity.*;
import com.aisip.OnO.backend.studyroom.support.StudyRoomTestSupport;
import com.aisip.OnO.backend.user.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("스터디룸 챌린지 API")
class StudyRoomChallengeApiTest extends StudyRoomTestSupport {

    private static final LocalDateTime NOW = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);

    @Nested
    @DisplayName("생성")
    class CreateChallenge {

        @Test
        @DisplayName("일반 멤버도 챌린지를 만들 수 있고 초기 상태는 in_progress 다")
        void memberCanCreateChallenge() throws Exception {
            RoomFixture fixture = createRoomWithMemberAndOutsider();
            authenticateAs(fixture.member().getId());

            create(fixture.roomId(), request("주간 문제 10개", "individual", "problem_count",
                    null, null, 10, null, NOW.plusDays(7)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.data.title").value("주간 문제 10개"))
                    .andExpect(jsonPath("$.data.type").value("individual"))
                    .andExpect(jsonPath("$.data.metric").value("problem_count"))
                    .andExpect(jsonPath("$.data.status").value("in_progress"))
                    .andExpect(jsonPath("$.data.targetValue").value(10));

            assertThat(challengeRepository.findAll()).as("저장된 챌린지").hasSize(1);
        }

        @Test
        @DisplayName("startAt 을 생략하면 현재 시각으로 시작한다")
        void omittedStartAtDefaultsToNow() throws Exception {
            RoomFixture fixture = createRoomWithMemberAndOutsider();
            authenticateAs(fixture.host().getId());

            create(fixture.roomId(), request("기본 시작", "individual", "problem_count",
                    null, null, 1, null, NOW.plusDays(3)))
                    .andExpect(status().isCreated());

            assertThat(challengeRepository.findAll().get(0).getStartAt())
                    .as("기본 시작 시각")
                    .isBetween(NOW.minusMinutes(1), LocalDateTime.now().plusMinutes(1));
        }

        @Test
        @DisplayName("제목 앞뒤 공백은 잘려서 저장된다")
        void titleIsTrimmed() throws Exception {
            RoomFixture fixture = createRoomWithMemberAndOutsider();
            authenticateAs(fixture.host().getId());

            create(fixture.roomId(), request("  공백 제목  ", "individual", "problem_count",
                    null, null, 1, null, NOW.plusDays(3)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.data.title").value("공백 제목"));
        }

        @Test
        @DisplayName("제목 40자는 허용되고 41자는 400 으로 거절된다")
        void titleLengthBoundaryIsForty() throws Exception {
            RoomFixture fixture = createRoomWithMemberAndOutsider();
            authenticateAs(fixture.host().getId());

            create(fixture.roomId(), request(repeat('가', 40), "individual", "problem_count",
                    null, null, 1, null, NOW.plusDays(3)))
                    .andExpect(status().isCreated());

            create(fixture.roomId(), request(repeat('가', 41), "individual", "problem_count",
                    null, null, 1, null, NOW.plusDays(3)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value(10016));
        }

        @ParameterizedTest(name = "{0}")
        @CsvSource(delimiter = '|', textBlock = """
                빈 제목         | ''         | individual | problem_count | 1
                공백 제목       | '   '      | individual | problem_count | 1
                목표 0          | 제목       | individual | problem_count | 0
                목표 음수       | 제목       | individual | problem_count | -1
                알 수 없는 타입  | 제목       | unknown    | problem_count | 1
                알 수 없는 지표  | 제목       | individual | unknown       | 1
                """)
        @DisplayName("요청 값이 올바르지 않으면 400 으로 거절된다")
        void invalidRequestIsRejected(String caseName, String title, String type, String metric,
                                      int targetValue) throws Exception {
            RoomFixture fixture = createRoomWithMemberAndOutsider();
            authenticateAs(fixture.host().getId());

            create(fixture.roomId(), request(title, type, metric,
                    null, null, targetValue, null, NOW.plusDays(3)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value(10016));
        }

        @Test
        @DisplayName("목표치가 null 이면 400 이다")
        void nullTargetValueIsRejected() throws Exception {
            RoomFixture fixture = createRoomWithMemberAndOutsider();
            authenticateAs(fixture.host().getId());

            create(fixture.roomId(), request("제목", "individual", "problem_count",
                    null, null, null, null, NOW.plusDays(3)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value(10016));
        }

        @Test
        @DisplayName("타입이 null 이면 400 이다")
        void nullTypeIsRejected() throws Exception {
            RoomFixture fixture = createRoomWithMemberAndOutsider();
            authenticateAs(fixture.host().getId());

            create(fixture.roomId(), request("제목", null, "problem_count",
                    null, null, 1, null, NOW.plusDays(3)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value(10016));
        }

        @Test
        @DisplayName("종료 시각이 없거나 이미 지난 시각이면 400 이다")
        void pastOrMissingEndAtIsRejected() throws Exception {
            RoomFixture fixture = createRoomWithMemberAndOutsider();
            authenticateAs(fixture.host().getId());

            create(fixture.roomId(), request("제목", "individual", "problem_count",
                    null, null, 1, null, null))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value(10016));

            create(fixture.roomId(), request("제목", "individual", "problem_count",
                    null, null, 1, null, NOW.minusDays(1)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value(10016));
        }

        @Test
        @DisplayName("시작 시각이 종료 시각과 같거나 뒤면 400 이다 — 기간 0일·음수 방지")
        void startAtMustBeBeforeEndAt() throws Exception {
            RoomFixture fixture = createRoomWithMemberAndOutsider();
            authenticateAs(fixture.host().getId());
            LocalDateTime endAt = NOW.plusDays(3);

            create(fixture.roomId(), request("0일 챌린지", "individual", "problem_count",
                    null, null, 1, endAt, endAt))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value(10016));

            create(fixture.roomId(), request("음수 기간 챌린지", "individual", "problem_count",
                    null, null, 1, endAt.plusDays(1), endAt))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value(10016));
        }

        @Test
        @DisplayName("period 와 periodDays 를 동시에 주면 400 이다")
        void periodAndPeriodDaysAreExclusive() throws Exception {
            RoomFixture fixture = createRoomWithMemberAndOutsider();
            authenticateAs(fixture.host().getId());

            create(fixture.roomId(), request("둘 다", "individual", "problem_count",
                    "weekly", 3, 1, null, NOW.plusDays(10)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value(10016));
        }

        @Test
        @DisplayName("periodDays 가 0 이하면 400 이다")
        void nonPositivePeriodDaysIsRejected() throws Exception {
            RoomFixture fixture = createRoomWithMemberAndOutsider();
            authenticateAs(fixture.host().getId());

            create(fixture.roomId(), request("0일 주기", "individual", "problem_count",
                    null, 0, 1, null, NOW.plusDays(10)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value(10016));
        }

        @Test
        @DisplayName("period 를 쓰면 periodDays 는 저장되지 않는다")
        void periodClearsPeriodDays() throws Exception {
            RoomFixture fixture = createRoomWithMemberAndOutsider();
            authenticateAs(fixture.host().getId());

            create(fixture.roomId(), request("주간 챌린지", "group", "practice_count",
                    "weekly", null, 5, null, NOW.plusDays(21)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.data.period").value("weekly"))
                    .andExpect(jsonPath("$.data.periodDays").doesNotExist());
        }

        @Test
        @DisplayName("진행 중인 챌린지가 5개면 더 만들 수 없다")
        void inProgressChallengeLimitIsFive() throws Exception {
            RoomFixture fixture = createRoomWithMemberAndOutsider();
            for (int i = 0; i < 5; i++) {
                saveChallenge(fixture.room(), "챌린지" + i, StudyRoomChallengeType.INDIVIDUAL,
                        StudyRoomChallengeMetric.PROBLEM_COUNT, null, null, 1_000,
                        NOW.minusHours(1), NOW.plusDays(7));
            }
            authenticateAs(fixture.host().getId());

            create(fixture.roomId(), request("여섯 번째", "individual", "problem_count",
                    null, null, 1_000, null, NOW.plusDays(7)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.errorCode").value(10010));
        }

        @Test
        @DisplayName("종료된 챌린지는 정원 계산에서 빠진다")
        void expiredChallengesDoNotCountTowardLimit() throws Exception {
            RoomFixture fixture = createRoomWithMemberAndOutsider();
            for (int i = 0; i < 5; i++) {
                saveChallenge(fixture.room(), "지난 챌린지" + i, StudyRoomChallengeType.INDIVIDUAL,
                        StudyRoomChallengeMetric.PROBLEM_COUNT, null, null, 1_000,
                        NOW.minusDays(10), NOW.minusDays(1));
            }
            authenticateAs(fixture.host().getId());

            create(fixture.roomId(), request("새 챌린지", "individual", "problem_count",
                    null, null, 1_000, null, NOW.plusDays(7)))
                    .andExpect(status().isCreated());
        }

        @Test
        @DisplayName("비멤버는 챌린지를 만들 수 없다")
        void nonMemberCannotCreate() throws Exception {
            RoomFixture fixture = createRoomWithMemberAndOutsider();
            authenticateAs(fixture.outsider().getId());

            create(fixture.roomId(), request("몰래 챌린지", "individual", "problem_count",
                    null, null, 1, null, NOW.plusDays(3)))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.errorCode").value(10002));

            assertThat(challengeRepository.findAll()).as("거절된 뒤 저장된 챌린지").isEmpty();
        }

        @Test
        @DisplayName("인증 없이 만들면 401 이다")
        void unauthenticatedIsRejected() throws Exception {
            RoomFixture fixture = createRoomWithMemberAndOutsider();
            clearAuthentication();

            create(fixture.roomId(), request("몰래 챌린지", "individual", "problem_count",
                    null, null, 1, null, NOW.plusDays(3)))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("조회와 상태 전이")
    class ReadChallenges {

        @Test
        @DisplayName("멤버는 챌린지 목록과 멤버별 진행도를 볼 수 있다")
        void memberSeesProgress() throws Exception {
            RoomFixture fixture = createRoomWithMemberAndOutsider();
            saveChallenge(fixture.room(), "문제 2개", StudyRoomChallengeType.INDIVIDUAL,
                    StudyRoomChallengeMetric.PROBLEM_COUNT, null, null, 2,
                    NOW.minusHours(1), NOW.plusDays(7));
            saveProblem(fixture.host().getId());
            saveProblem(fixture.host().getId());
            authenticateAs(fixture.host().getId());

            mockMvc.perform(get("/api/study-room/{roomId}/challenges", fixture.roomId()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.length()").value(1))
                    .andExpect(jsonPath("$.data[0].memberProgress.length()").value(2))
                    .andExpect(jsonPath("$.data[0].memberProgress[?(@.userId == " + fixture.host().getId() + ")].current").value(2))
                    .andExpect(jsonPath("$.data[0].memberProgress[?(@.userId == " + fixture.host().getId() + ")].cleared").value(true))
                    .andExpect(jsonPath("$.data[0].memberProgress[?(@.userId == " + fixture.member().getId() + ")].cleared").value(false));
        }

        @Test
        @DisplayName("아직 시작 전인 챌린지도 in_progress 로 조회된다")
        void notYetStartedChallengeIsInProgress() throws Exception {
            RoomFixture fixture = createRoomWithMemberAndOutsider();
            saveChallenge(fixture.room(), "미래 챌린지", StudyRoomChallengeType.INDIVIDUAL,
                    StudyRoomChallengeMetric.PROBLEM_COUNT, null, null, 5,
                    NOW.plusDays(1), NOW.plusDays(8));
            authenticateAs(fixture.host().getId());

            mockMvc.perform(get("/api/study-room/{roomId}/challenges", fixture.roomId()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data[0].status").value("in_progress"))
                    .andExpect(jsonPath("$.data[0].memberProgress[0].current").value(0));
        }

        @Test
        @DisplayName("종료 시각이 지난 챌린지는 expired 로 바뀌어 저장된다")
        void endedChallengeBecomesExpired() throws Exception {
            RoomFixture fixture = createRoomWithMemberAndOutsider();
            StudyRoomChallenge challenge = saveChallenge(fixture.room(), "지난 챌린지",
                    StudyRoomChallengeType.INDIVIDUAL, StudyRoomChallengeMetric.PROBLEM_COUNT,
                    null, null, 100, NOW.minusDays(10), NOW.minusDays(1));
            authenticateAs(fixture.host().getId());

            mockMvc.perform(get("/api/study-room/{roomId}/challenges", fixture.roomId()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data[0].status").value("expired"));

            assertThat(challengeRepository.findById(challenge.getId()).orElseThrow().getStatus())
                    .as("영속화된 상태")
                    .isEqualTo(StudyRoomChallengeStatus.EXPIRED);
        }

        @Test
        @DisplayName("모든 멤버가 목표를 채운 개인 챌린지는 completed 로 전이하고 완료 시각이 기록된다")
        void individualChallengeCompletesWhenEveryMemberClears() throws Exception {
            RoomFixture fixture = createRoomWithMemberAndOutsider();
            StudyRoomChallenge challenge = saveChallenge(fixture.room(), "각자 1문제",
                    StudyRoomChallengeType.INDIVIDUAL, StudyRoomChallengeMetric.PROBLEM_COUNT,
                    null, null, 1, NOW.minusHours(1), NOW.plusDays(7));
            saveProblem(fixture.host().getId());
            saveProblem(fixture.member().getId());
            authenticateAs(fixture.host().getId());

            mockMvc.perform(get("/api/study-room/{roomId}/challenges", fixture.roomId()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data[0].status").value("completed"));

            StudyRoomChallenge reloaded = challengeRepository.findById(challenge.getId()).orElseThrow();
            assertThat(reloaded.getStatus()).as("영속화된 상태").isEqualTo(StudyRoomChallengeStatus.COMPLETED);
            assertThat(reloaded.getCompletedAt()).as("완료 시각").isNotNull();
        }

        @Test
        @DisplayName("한 명이라도 목표를 못 채우면 개인 챌린지는 in_progress 로 남는다")
        void individualChallengeStaysInProgress() throws Exception {
            RoomFixture fixture = createRoomWithMemberAndOutsider();
            saveChallenge(fixture.room(), "각자 1문제", StudyRoomChallengeType.INDIVIDUAL,
                    StudyRoomChallengeMetric.PROBLEM_COUNT, null, null, 1,
                    NOW.minusHours(1), NOW.plusDays(7));
            saveProblem(fixture.host().getId());
            authenticateAs(fixture.host().getId());

            mockMvc.perform(get("/api/study-room/{roomId}/challenges", fixture.roomId()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data[0].status").value("in_progress"));
        }

        @Test
        @DisplayName("단체 챌린지는 멤버별 진행도 없이 합산 값만 내려준다")
        void groupChallengeReportsSumOnly() throws Exception {
            RoomFixture fixture = createRoomWithMemberAndOutsider();
            saveChallenge(fixture.room(), "다 같이 3문제", StudyRoomChallengeType.GROUP,
                    StudyRoomChallengeMetric.PROBLEM_COUNT, null, null, 3,
                    NOW.minusHours(1), NOW.plusDays(7));
            saveProblem(fixture.host().getId());
            saveProblem(fixture.member().getId());
            authenticateAs(fixture.host().getId());

            mockMvc.perform(get("/api/study-room/{roomId}/challenges", fixture.roomId()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data[0].groupCurrent").value(2))
                    .andExpect(jsonPath("$.data[0].memberProgress").isEmpty())
                    .andExpect(jsonPath("$.data[0].status").value("in_progress"));
        }

        @Test
        @DisplayName("단체 챌린지는 합산이 목표에 닿으면 completed 가 된다")
        void groupChallengeCompletesOnSum() throws Exception {
            RoomFixture fixture = createRoomWithMemberAndOutsider();
            saveChallenge(fixture.room(), "다 같이 2문제", StudyRoomChallengeType.GROUP,
                    StudyRoomChallengeMetric.PROBLEM_COUNT, null, null, 2,
                    NOW.minusHours(1), NOW.plusDays(7));
            saveProblem(fixture.host().getId());
            saveProblem(fixture.member().getId());
            authenticateAs(fixture.host().getId());

            mockMvc.perform(get("/api/study-room/{roomId}/challenges", fixture.roomId()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data[0].groupCurrent").value(2))
                    .andExpect(jsonPath("$.data[0].status").value("completed"));
        }

        @Test
        @DisplayName("주기가 있는 챌린지는 목표를 채워도 조기 완료되지 않는다")
        void periodChallengeNeverCompletesEarly() throws Exception {
            RoomFixture fixture = createRoomWithMemberAndOutsider();
            saveChallenge(fixture.room(), "매일 1문제", StudyRoomChallengeType.INDIVIDUAL,
                    StudyRoomChallengeMetric.PROBLEM_COUNT, StudyRoomChallengePeriod.DAILY, null, 1,
                    NOW.minusHours(1), NOW.plusDays(7));
            saveProblem(fixture.host().getId());
            saveProblem(fixture.member().getId());
            authenticateAs(fixture.host().getId());

            mockMvc.perform(get("/api/study-room/{roomId}/challenges", fixture.roomId()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data[0].status")
                            .value("in_progress"));
        }

        @Test
        @DisplayName("진행 중 챌린지가 종료된 챌린지보다 앞에 온다")
        void inProgressChallengesComeFirst() throws Exception {
            RoomFixture fixture = createRoomWithMemberAndOutsider();
            saveChallenge(fixture.room(), "종료됨", StudyRoomChallengeType.INDIVIDUAL,
                    StudyRoomChallengeMetric.PROBLEM_COUNT, null, null, 100,
                    NOW.minusDays(10), NOW.minusDays(1));
            saveChallenge(fixture.room(), "진행중", StudyRoomChallengeType.INDIVIDUAL,
                    StudyRoomChallengeMetric.PROBLEM_COUNT, null, null, 100,
                    NOW.minusHours(1), NOW.plusDays(7));
            authenticateAs(fixture.host().getId());

            mockMvc.perform(get("/api/study-room/{roomId}/challenges", fixture.roomId()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data[0].title").value("진행중"))
                    .andExpect(jsonPath("$.data[1].title").value("종료됨"));
        }

        @Test
        @DisplayName("다른 방의 챌린지는 목록에 섞이지 않는다")
        void challengesAreScopedToRoom() throws Exception {
            RoomFixture fixture = createRoomWithMemberAndOutsider();
            StudyRoom otherRoom = createRoom(fixture.outsider(), "남의 방");
            saveChallenge(otherRoom, "남의 챌린지", StudyRoomChallengeType.INDIVIDUAL,
                    StudyRoomChallengeMetric.PROBLEM_COUNT, null, null, 1,
                    NOW.minusHours(1), NOW.plusDays(7));
            authenticateAs(fixture.host().getId());

            mockMvc.perform(get("/api/study-room/{roomId}/challenges", fixture.roomId()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data").isEmpty());
        }

        @Test
        @DisplayName("비멤버는 챌린지 목록을 볼 수 없다")
        void nonMemberCannotRead() throws Exception {
            RoomFixture fixture = createRoomWithMemberAndOutsider();
            saveChallenge(fixture.room(), 1);
            authenticateAs(fixture.outsider().getId());

            mockMvc.perform(get("/api/study-room/{roomId}/challenges", fixture.roomId()))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.errorCode").value(10002));
        }

        @Test
        @DisplayName("인증 없이 조회하면 401 이다")
        void unauthenticatedIsRejected() throws Exception {
            RoomFixture fixture = createRoomWithMemberAndOutsider();
            clearAuthentication();

            mockMvc.perform(get("/api/study-room/{roomId}/challenges", fixture.roomId()))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("삭제")
    class DeleteChallenge {

        @Test
        @DisplayName("방장은 챌린지를 삭제할 수 있다")
        void hostCanDelete() throws Exception {
            RoomFixture fixture = createRoomWithMemberAndOutsider();
            StudyRoomChallenge challenge = saveChallenge(fixture.room(), 5);
            authenticateAs(fixture.host().getId());

            mockMvc.perform(delete("/api/study-room/{roomId}/challenges/{challengeId}",
                            fixture.roomId(), challenge.getId()))
                    .andExpect(status().isOk());

            assertThat(challengeRepository.findById(challenge.getId())).as("삭제된 챌린지").isEmpty();
        }

        @Test
        @DisplayName("일반 멤버는 챌린지를 삭제할 수 없다")
        void memberCannotDelete() throws Exception {
            RoomFixture fixture = createRoomWithMemberAndOutsider();
            StudyRoomChallenge challenge = saveChallenge(fixture.room(), 5);
            authenticateAs(fixture.member().getId());

            mockMvc.perform(delete("/api/study-room/{roomId}/challenges/{challengeId}",
                            fixture.roomId(), challenge.getId()))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.errorCode").value(10003));

            assertThat(challengeRepository.findById(challenge.getId())).as("남아 있는 챌린지").isPresent();
        }

        @Test
        @DisplayName("비멤버는 챌린지를 삭제할 수 없다")
        void nonMemberCannotDelete() throws Exception {
            RoomFixture fixture = createRoomWithMemberAndOutsider();
            StudyRoomChallenge challenge = saveChallenge(fixture.room(), 5);
            authenticateAs(fixture.outsider().getId());

            mockMvc.perform(delete("/api/study-room/{roomId}/challenges/{challengeId}",
                            fixture.roomId(), challenge.getId()))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.errorCode").value(10002));
        }

        @Test
        @DisplayName("존재하지 않는 챌린지는 404 다")
        void unknownChallengeIsNotFound() throws Exception {
            RoomFixture fixture = createRoomWithMemberAndOutsider();
            authenticateAs(fixture.host().getId());

            mockMvc.perform(delete("/api/study-room/{roomId}/challenges/{challengeId}",
                            fixture.roomId(), nonExistentChallengeId()))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.errorCode").value(10009));
        }

        @Test
        @DisplayName("다른 방의 챌린지 ID 로는 삭제할 수 없다")
        void challengeFromAnotherRoomIsNotFound() throws Exception {
            RoomFixture fixture = createRoomWithMemberAndOutsider();
            User otherHost = fixtures.createUser("otherHost");
            StudyRoom otherRoom = createRoom(otherHost, "남의 방");
            StudyRoomChallenge otherChallenge = saveChallenge(otherRoom, 5);
            authenticateAs(fixture.host().getId());

            mockMvc.perform(delete("/api/study-room/{roomId}/challenges/{challengeId}",
                            fixture.roomId(), otherChallenge.getId()))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.errorCode").value(10009));

            assertThat(challengeRepository.findById(otherChallenge.getId()))
                    .as("남의 방 챌린지는 그대로 남는다").isPresent();
        }
    }

    private ChallengeCreateRequest request(String title, String type, String metric, String period,
                                           Integer periodDays, Integer targetValue,
                                           LocalDateTime startAt, LocalDateTime endAt) {
        return new ChallengeCreateRequest(title, type, metric, period, periodDays, targetValue, startAt, endAt);
    }

    private ResultActions create(Long roomId, ChallengeCreateRequest request) throws Exception {
        return mockMvc.perform(post("/api/study-room/{roomId}/challenges", roomId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)));
    }
}
