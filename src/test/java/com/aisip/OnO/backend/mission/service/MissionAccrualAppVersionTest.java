package com.aisip.OnO.backend.mission.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aisip.OnO.backend.common.web.AppVersionResolver;
import com.aisip.OnO.backend.mission.entity.MissionProgress;
import com.aisip.OnO.backend.mission.entity.MissionType;
import com.aisip.OnO.backend.mission.entity.UserMissionStatus;
import com.aisip.OnO.backend.mission.support.MissionSystemTestSupport;
import com.aisip.OnO.backend.user.entity.User;
import java.lang.reflect.Field;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 앱 버전에 따라 자동 적립을 가르는 동작.
 *
 * <p>적립과 진행도는 서버가 올리지만 <b>받는 것은 앱이 한다.</b> 미션 화면이 없는 구버전 앱은
 * {@code claim} 을 부를 방법이 없어서, 자동 적립을 통째로 끄면 그 사용자는 공부를 해도 XP 가 멈춘다.
 * 그래서 요청이 온 앱 버전을 보고 가른다.
 *
 * <p>이 클래스의 주제는 <b>어느 쪽으로 틀리는가</b>다. 헤더가 없든, 값이 이상하든, HTTP 요청 자체가 없든
 * 전부 구버전으로 떨어져 지금과 같이 적립된다. 덜 주는 것보다 더 주는 것이 낫다.
 *
 * <p>구버전으로 떨어진 요청은 <b>진행도를 올리지 않는다.</b> 적립으로 받은 행동이 미션으로 한 번 더 받을 수
 * 있게 남으면 안 되기 때문이다. 조합 전체는 {@code MissionXpSinglePathTest} 가 본다.
 */
@DisplayName("앱 버전별 자동 적립")
class MissionAccrualAppVersionTest extends MissionSystemTestSupport {

    /** 기준을 만족하는 앱. 프론트가 보내는 형식 그대로다. */
    private static final String MISSION_CAPABLE_HEADER = "4.0.0+70";

    /** 기준에 못 미치는 앱. */
    private static final String LEGACY_HEADER = "3.9.9+69";

    private User user;

    @BeforeEach
    void setUpUser() {
        user = fixtures.createUser();
    }

    @AfterEach
    void clearRequestContext() {
        // 싱글턴이 아니라 스레드에 붙는 값이라 안 지우면 다음 테스트가 이 헤더를 그대로 물려받는다.
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    @DisplayName("기준 버전은 설정으로 바꿀 수 있고 기본값은 4.0.0 이다")
    void thresholdIsConfigurable() throws NoSuchFieldException {
        // 코드에 박으면 기준을 되돌릴 때마다 배포해야 한다. 값이 아니라 선언을 본다.
        Field field = LegacyAccrualPolicy.class.getDeclaredField("missionCapableVersion");

        assertThat(field.getAnnotation(Value.class).value())
                .as("프로퍼티 이름과 기본값은 운영에서 기준을 옮길 때 쓰는 계약이다")
                .isEqualTo("${ono.mission.mission-capable-version:4.0.0}");
    }

    @Nested
    @DisplayName("미션을 받을 수 있는 앱이면")
    class MissionCapableApp {

        @BeforeEach
        void sendCapableHeader() {
            requestWithAppVersion(MISSION_CAPABLE_HEADER);
        }

        @Test
        @DisplayName("자동 적립이 들어오지 않는다")
        void doesNotAccrue() {
            missionLogService.registerLoginMission(user.getId());

            assertThat(missionStatus().getTotalStudyPoint())
                    .as("미션 보상으로만 받아야 이중 지급이 없다")
                    .isZero();
        }

        @Test
        @DisplayName("기록과 진행도는 그대로 쌓인다")
        void stillWritesLogAndProgress() {
            missionLogService.registerLoginMission(user.getId());

            assertThat(missionLogRepository.findAllByUserId(user.getId()))
                    .as("관리자 통계가 이 행을 읽는다. 꺼지는 것은 지급뿐이다")
                    .hasSize(1);
            assertThat(currentOf(user.getId(), DAILY_ATTEND)).isEqualTo(1);
        }

        @Test
        @DisplayName("미션 보상은 그대로 들어온다")
        void missionRewardStillGranted() {
            MissionProgress progress = completeMission(user.getId(), DAILY_REVIEW_3);
            assertThat(problemPracticePoints()).isZero();

            missionService.claim(user.getId(), progress.getId());

            assertThat(problemPracticePoints())
                    .as("자동 적립을 끈 자리에서 XP 가 들어오는 유일한 경로다")
                    .isEqualTo(15L);
        }

        @Test
        @DisplayName("기준과 같은 버전도 새 앱이다")
        void thresholdItselfCounts() {
            requestWithAppVersion("4.0.0");

            missionLogService.registerLoginMission(user.getId());

            assertThat(missionStatus().getTotalStudyPoint()).isZero();
        }

        @Test
        @DisplayName("기준보다 높은 버전도 새 앱이다")
        void higherVersionCounts() {
            requestWithAppVersion("4.1.2+103");

            missionLogService.registerLoginMission(user.getId());

            assertThat(missionStatus().getTotalStudyPoint()).isZero();
        }
    }

    @Nested
    @DisplayName("구버전 앱이면")
    class LegacyApp {

        @Test
        @DisplayName("헤더가 낮은 버전이면 지금처럼 적립된다")
        void accruesForLowerVersion() {
            requestWithAppVersion(LEGACY_HEADER);

            missionLogService.registerLoginMission(user.getId());

            assertThat(attendancePoints()).isEqualTo(MissionType.USER_LOGIN.getPoint());
            assertThat(currentOf(user.getId(), DAILY_ATTEND))
                    .as("적립으로 받은 출석이 새 앱에서 미션으로 한 번 더 받을 수 있게 남으면 안 된다")
                    .isZero();
        }

        @Test
        @DisplayName("헤더가 아예 없으면 지금처럼 적립된다")
        void accruesWhenHeaderMissing() {
            // 헤더를 안 보내는 앱이 곧 구버전이다. 프론트는 버전을 못 읽으면 헤더를 붙이지 않는다.
            requestWithAppVersion(null);

            missionLogService.registerLoginMission(user.getId());

            assertThat(attendancePoints()).isEqualTo(MissionType.USER_LOGIN.getPoint());
            assertThat(currentOf(user.getId(), DAILY_ATTEND)).isZero();
        }

        @ParameterizedTest(name = "\"{0}\"")
        @ValueSource(strings = {"abc", "", "   ", "4", "4.0", "4.0.0.1", "v4.0.0", "unknown"})
        @DisplayName("헤더가 이상한 값이어도 예외 없이 구버전으로 떨어진다")
        void accruesForUnparsableHeader(String rawHeader) {
            requestWithAppVersion(rawHeader);

            assertThatCode(() -> missionLogService.registerLoginMission(user.getId()))
                    .doesNotThrowAnyException();
            assertThat(attendancePoints()).isEqualTo(MissionType.USER_LOGIN.getPoint());
        }

        @Test
        @DisplayName("하루 200점 상한도 지금 그대로다")
        void keepsDailyCap() {
            requestWithAppVersion(LEGACY_HEADER);

            for (int i = 1; i <= 14; i++) {
                missionLogService.registerNotePracticeMission(user.getId(), (long) i);
            }

            assertThat(accumulatedNotePracticePoints(user))
                    .as("구버전 기준으로는 이 변경 전과 완전히 같아야 한다")
                    .isEqualTo(185L);
        }
    }

    @Nested
    @DisplayName("HTTP 요청이 없는 곳에서 불리면")
    class OutsideHttpRequest {

        @BeforeEach
        void noRequest() {
            RequestContextHolder.resetRequestAttributes();
        }

        @Test
        @DisplayName("터지지 않고 구버전으로 떨어진다")
        void accruesWithoutThrowing() {
            // 지금 적립을 부르는 경로는 전부 HTTP 요청 안이지만(Quartz 잡·RabbitMQ 소비자 어디에서도
            // 적립을 부르지 않는다), 배치가 하나라도 늘면 여기서 예외가 나 그 경로가 통째로 죽는다.
            assertThatCode(() -> missionLogService.registerLoginMission(user.getId()))
                    .doesNotThrowAnyException();
            assertThat(attendancePoints()).isEqualTo(MissionType.USER_LOGIN.getPoint());
            assertThat(currentOf(user.getId(), DAILY_ATTEND))
                    .as("진행도도 같은 판정을 봐서 적립과 함께 돌지 않는다")
                    .isZero();
        }
    }

    @Nested
    @DisplayName("비상 스위치가 꺼져 있으면")
    class LegacyAccrualFlagDisabled {

        @BeforeEach
        void disableFlag() {
            setLegacyAccrual(false);
        }

        @Test
        @DisplayName("새 앱에서도 적립이 없다")
        void noAccrualForCapableApp() {
            requestWithAppVersion(MISSION_CAPABLE_HEADER);

            missionLogService.registerLoginMission(user.getId());

            assertThat(missionStatus().getTotalStudyPoint()).isZero();
        }

        @Test
        @DisplayName("구버전 앱에서도 적립이 없다")
        void noAccrualForLegacyApp() {
            // 플래그가 버전보다 우선한다. 적립 자체에 문제가 생겼을 때 배포 없이 전부 멈추는 자리다.
            requestWithAppVersion(LEGACY_HEADER);

            missionLogService.registerLoginMission(user.getId());

            assertThat(missionStatus().getTotalStudyPoint()).isZero();
            assertThat(currentOf(user.getId(), DAILY_ATTEND))
                    .as("적립이 전부 꺼지면 진행도는 버전과 무관하게 오른다")
                    .isEqualTo(1);
        }

        @Test
        @DisplayName("헤더가 없어도 적립이 없다")
        void noAccrualWithoutHeader() {
            requestWithAppVersion(null);

            missionLogService.registerLoginMission(user.getId());

            assertThat(missionStatus().getTotalStudyPoint()).isZero();
        }

        @Test
        @DisplayName("미션 보상은 그대로 들어온다")
        void missionRewardUnaffected() {
            requestWithAppVersion(LEGACY_HEADER);
            MissionProgress progress = completeMission(user.getId(), DAILY_REVIEW_3);

            missionService.claim(user.getId(), progress.getId());

            assertThat(problemPracticePoints()).isEqualTo(15L);
        }
    }

    @Nested
    @DisplayName("기준 버전 설정이 잘못돼 있으면")
    class MisconfiguredThreshold {

        @Test
        @DisplayName("아무도 새 앱으로 보지 않는다")
        void treatsEveryoneAsLegacy() {
            // 설정 오타 하나로 전체 XP 유입이 멈추는 것보다 이중 지급이 조금 더 도는 편이 낫다.
            setMissionCapableVersion("사점영");
            requestWithAppVersion(MISSION_CAPABLE_HEADER);

            missionLogService.registerLoginMission(user.getId());

            assertThat(attendancePoints()).isEqualTo(MissionType.USER_LOGIN.getPoint());
            assertThat(currentOf(user.getId(), DAILY_ATTEND)).isZero();
        }

        @Test
        @DisplayName("기준을 올리면 그 아래 버전은 다시 구버전이 된다")
        void raisingThresholdRollsBack() {
            // 미션 화면이 고장 난 버전이 뒤늦게 드러났을 때 배포 없이 되돌리는 길이다.
            setMissionCapableVersion("5.0.0");
            requestWithAppVersion(MISSION_CAPABLE_HEADER);

            missionLogService.registerLoginMission(user.getId());

            assertThat(attendancePoints()).isEqualTo(MissionType.USER_LOGIN.getPoint());
        }
    }

    @Nested
    @DisplayName("실제 HTTP 요청에서는")
    class ThroughMockMvc {

        @Test
        @DisplayName("헤더를 붙여 보내면 자동 적립이 멈춘다")
        void headerReachesServiceThroughFilterChain() throws Exception {
            mockMvc.perform(get("/api/users")
                            .header(AppVersionResolver.APP_VERSION_HEADER, MISSION_CAPABLE_HEADER)
                            .with(asUser(user)))
                    .andExpect(status().isOk());

            assertThat(reload(user).getUserMissionStatus().getTotalStudyPoint())
                    .as("컨트롤러 → 서비스 사이에 버전을 인자로 넘기지 않아도 닿아야 한다")
                    .isZero();
            assertThat(currentOf(user.getId(), DAILY_ATTEND))
                    .as("새 앱이면 진행도가 오른다")
                    .isEqualTo(1);
        }

        @Test
        @DisplayName("헤더 없이 보내면 지금처럼 적립된다")
        void accruesWithoutHeader() {
            assertThatCode(() -> mockMvc.perform(get("/api/users").with(asUser(user)))
                    .andExpect(status().isOk())).doesNotThrowAnyException();

            assertThat(attendancePoints())
                    .as("구버전 앱이 보내는 요청 그대로다")
                    .isEqualTo(MissionType.USER_LOGIN.getPoint());
            assertThat(currentOf(user.getId(), DAILY_ATTEND)).isZero();
        }

        private RequestPostProcessor asUser(User user) {
            // authenticateAs 는 SecurityContextHolder 만 채워 MockMvc 필터 체인까지 가지 않는다.
            return authentication(new UsernamePasswordAuthenticationToken(
                    user.getId(), null, List.of(new SimpleGrantedAuthority("ROLE_MEMBER"))));
        }
    }

    /**
     * 지금 스레드를 "그 헤더를 단 요청을 처리하는 중" 으로 만든다.
     *
     * <p>{@code null} 이면 헤더를 아예 붙이지 않는다. 빈 문자열과는 다른 상황이라 구분해야 한다.
     */
    private void requestWithAppVersion(String appVersion) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        if (appVersion != null) {
            request.addHeader(AppVersionResolver.APP_VERSION_HEADER, appVersion);
        }
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    private UserMissionStatus missionStatus() {
        return reload(user).getUserMissionStatus();
    }

    private long attendancePoints() {
        return accumulatedPoints(missionStatus().getAttendanceLevel(), missionStatus().getAttendancePoint());
    }

    private long problemPracticePoints() {
        return accumulatedPoints(missionStatus().getProblemPracticeLevel(), missionStatus().getProblemPracticePoint());
    }
}
