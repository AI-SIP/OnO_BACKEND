package com.aisip.OnO.backend.mission.support;

import com.aisip.OnO.backend.mission.dto.MissionRegisterDto;
import com.aisip.OnO.backend.mission.entity.MissionLog;
import com.aisip.OnO.backend.mission.entity.MissionType;
import com.aisip.OnO.backend.mission.entity.UserMissionStatus;
import com.aisip.OnO.backend.mission.repository.MissionLogRepository;
import com.aisip.OnO.backend.support.IntegrationTestSupport;
import com.aisip.OnO.backend.user.entity.User;
import com.aisip.OnO.backend.user.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

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
