package com.aisip.OnO.backend.mission.support;

import com.aisip.OnO.backend.mission.dto.MissionRegisterDto;
import com.aisip.OnO.backend.mission.entity.MissionLog;
import com.aisip.OnO.backend.mission.entity.MissionType;
import com.aisip.OnO.backend.mission.entity.UserMissionStatus;
import com.aisip.OnO.backend.mission.repository.MissionLogRepository;
import com.aisip.OnO.backend.mission.service.MissionLogService;
import com.aisip.OnO.backend.support.IntegrationTestSupport;
import com.aisip.OnO.backend.user.entity.User;
import com.aisip.OnO.backend.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.AopTestUtils;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * mission 도메인 테스트의 공통 베이스.
 *
 * <p>{@link IntegrationTestSupport} 의 애노테이션 조합을 그대로 물려받아 스프링 컨텍스트를 공유한다.
 * {@code @MockBean} 을 새로 선언하면 컨텍스트가 갈라지므로 추가하지 않는다.
 *
 * <p>미션 중복 방지와 일일 포인트 한도는 모두 "오늘"이라는 창을 기준으로 동작한다.
 * 그 창의 경계를 검증하려면 {@code created_at} 을 직접 밀어 넣어야 하므로 헬퍼를 둔다.
 */
public abstract class MissionTestSupport extends IntegrationTestSupport {

    @Autowired
    protected MissionLogRepository missionLogRepository;

    @Autowired
    protected UserRepository userRepository;

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    @Autowired
    protected MissionLogService missionLogService;

    /** {@code ono.mission.legacy-accrual.enabled} 의 기본값. 설정을 안 건드리면 운영이 이 상태다. */
    protected static final boolean LEGACY_ACCRUAL_DEFAULT = true;

    /**
     * 자동 적립 플래그를 바꾼다.
     *
     * <p>{@code MissionLogService} 는 {@code @Transactional} 프록시라 프록시에 값을 넣으면
     * 정작 로직이 도는 대상 객체의 필드는 그대로다. 대상 객체를 꺼내서 넣어야 한다.
     *
     * <p>싱글턴 빈을 건드리는 것이라 테스트 사이에 값이 새면 엉뚱한 테스트가 깨진다.
     * 앞뒤로 기본값을 되돌려 이 클래스를 상속하지 않는 테스트까지 오염되지 않게 한다.
     */
    protected void setLegacyAccrual(boolean enabled) {
        // 대상 객체를 지역 변수로 받아 둔다. getTargetObject 의 반환형이 제네릭이라 그대로 넘기면
        // 컴파일러가 setField(Class, ...) 오버로드를 골라 실행 시점에 ClassCastException 이 난다.
        MissionLogService target = AopTestUtils.getTargetObject(missionLogService);
        ReflectionTestUtils.setField(target, "legacyAccrualEnabled", enabled);
    }

    @BeforeEach
    void restoreLegacyAccrualBeforeEachTest() {
        setLegacyAccrual(LEGACY_ACCRUAL_DEFAULT);
    }

    @AfterEach
    void restoreLegacyAccrualAfterEachTest() {
        setLegacyAccrual(LEGACY_ACCRUAL_DEFAULT);
    }

    protected MissionLog saveMissionLog(User user, MissionType missionType, Long referenceId) {
        return missionLogRepository.save(MissionLog.from(
                MissionRegisterDto.builder()
                        .userId(user.getId())
                        .missionType(missionType)
                        .referenceId(referenceId)
                        .build(),
                user
        ));
    }

    /**
     * 미션 기록을 특정 시각에 남은 것으로 바꾼다.
     *
     * <p>{@code created_at} 은 JPA Auditing 이 채우므로 애플리케이션에서는 과거 시각을 만들 수 없다.
     * "어제 로그인은 오늘 중복이 아니다" 같은 날짜 경계는 이 방법으로만 검증할 수 있다.
     */
    protected MissionLog saveMissionLogAt(User user, MissionType missionType, Long referenceId, LocalDateTime createdAt) {
        MissionLog missionLog = saveMissionLog(user, missionType, referenceId);
        jdbcTemplate.update("UPDATE mission_log SET created_at = ? WHERE id = ?", createdAt, missionLog.getId());
        return missionLog;
    }

    protected LocalDate today() {
        // MissionLogRepositoryImpl 의 오늘 판정과 같은 기준(JVM 기본 시간대)을 쓴다.
        return LocalDate.now();
    }

    protected User reload(User user) {
        return userRepository.findById(user.getId()).orElseThrow();
    }

    /**
     * 능력치가 지금까지 받은 누적 경험치.
     *
     * <p>레벨 n 까지 오르는 데 드는 총량은 {@code 10 + 20 + ... + 10(n-1) = 5n(n-1)} 이므로,
     * 현재 레벨과 잔여 포인트만으로 누적치를 되돌릴 수 있다. 레벨/포인트를 따로 비교하는 것보다
     * "몇 점을 받았는가"를 그대로 확인할 수 있어 의도가 드러난다.
     */
    protected long accumulatedPoints(Long level, Long point) {
        return 5L * level * (level - 1) + point;
    }

    protected long accumulatedNotePracticePoints(User user) {
        UserMissionStatus status = reload(user).getUserMissionStatus();
        return accumulatedPoints(status.getNotePracticeLevel(), status.getNotePracticePoint());
    }
}
