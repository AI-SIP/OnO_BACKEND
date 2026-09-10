package com.aisip.OnO.backend.mission.support;

import com.aisip.OnO.backend.mission.entity.MissionDefinition;
import com.aisip.OnO.backend.mission.entity.MissionProgress;
import com.aisip.OnO.backend.mission.repository.MissionDefinitionRepository;
import com.aisip.OnO.backend.mission.repository.MissionProgressRepository;
import com.aisip.OnO.backend.mission.service.MissionPeriodKey;
import com.aisip.OnO.backend.mission.service.MissionProgressUpdater;
import com.aisip.OnO.backend.mission.service.MissionService;
import com.aisip.OnO.backend.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;

/**
 * 미션 시스템(정의/진행도/보상) 테스트의 공통 베이스.
 *
 * <p>{@code @MockBean} 을 새로 선언하지 않아 스프링 컨텍스트는 다른 도메인 테스트와 그대로 공유된다.
 */
public abstract class MissionSystemTestSupport extends MissionTestSupport {

    public static final String DAILY_ATTEND = "DAILY_ATTEND";
    public static final String DAILY_NOTE_WRITE = "DAILY_NOTE_WRITE";
    public static final String DAILY_REVIEW_3 = "DAILY_REVIEW_3";
    public static final String DAILY_CORRECT_3 = "DAILY_CORRECT_3";
    public static final String DAILY_PRACTICE_SET = "DAILY_PRACTICE_SET";
    public static final String DAILY_MOOD = "DAILY_MOOD";
    public static final String WEEKLY_ATTEND_5 = "WEEKLY_ATTEND_5";
    public static final String WEEKLY_NOTE_10 = "WEEKLY_NOTE_10";
    public static final String WEEKLY_REVIEW_30 = "WEEKLY_REVIEW_30";
    public static final String WEEKLY_SET_3 = "WEEKLY_SET_3";

    @Autowired
    private MissionDefinitionSeeder missionDefinitionSeeder;

    @Autowired
    protected MissionDefinitionRepository missionDefinitionRepository;

    @Autowired
    protected MissionProgressRepository missionProgressRepository;

    @Autowired
    protected MissionService missionService;

    @Autowired
    protected MissionProgressUpdater missionProgressUpdater;

    @Autowired
    protected TransactionTemplate transactionTemplate;

    /** DatabaseCleaner 가 매 테스트마다 mission_definition 까지 비우므로 여기서 다시 채운다. */
    @BeforeEach
    void seedMissionDefinitions() {
        missionDefinitionSeeder.seed();
    }

    protected MissionDefinition definitionOf(String code) {
        return missionDefinitionRepository.findByCode(code).orElseThrow(
                () -> new IllegalStateException("시드에 없는 미션 코드다: " + code));
    }

    protected String periodKeyOf(String code) {
        return MissionPeriodKey.of(definitionOf(code).getCategory(), MissionPeriodKey.today());
    }

    protected MissionProgress progressOf(User user, String code) {
        return progressOf(user.getId(), code);
    }

    protected MissionProgress progressOf(Long userId, String code) {
        MissionDefinition definition = definitionOf(code);
        return missionProgressRepository
                .findByUserIdAndMissionIdAndPeriodKey(userId, definition.getId(), periodKeyOf(code))
                .orElse(null);
    }

    protected int currentOf(Long userId, String code) {
        MissionProgress progress = progressOf(userId, code);
        return progress == null ? 0 : progress.getCurrentValue();
    }

    /** 미션을 목표까지 밀어 올린다. 받기 시나리오의 사전 준비용. */
    protected MissionProgress completeMission(Long userId, String code) {
        MissionDefinition definition = definitionOf(code);
        missionProgressUpdater.increase(userId, definition.getMetric(), definition.getTarget());
        return progressOf(userId, code);
    }

    /**
     * 지난 기간에 완료했지만 받지 않은 진행도를 직접 만든다.
     *
     * <p>기간이 지난 상황은 서비스로 만들 수 없다. 진행도는 언제나 오늘 키로만 쌓이기 때문이다.
     * 어제·지난 주 행은 이렇게 박아 넣어야 재현된다.
     */
    protected Long insertCompletedProgress(Long userId, String code, String periodKey, LocalDateTime completedAt) {
        MissionDefinition definition = definitionOf(code);
        jdbcTemplate.update("""
                INSERT INTO mission_progress
                    (user_id, mission_id, period_key, current_value, target_snapshot, completed_at, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """,
                userId, definition.getId(), periodKey,
                definition.getTarget(), definition.getTarget(),
                completedAt, completedAt, completedAt);

        return jdbcTemplate.queryForObject(
                "SELECT id FROM mission_progress WHERE user_id = ? AND mission_id = ? AND period_key = ?",
                Long.class, userId, definition.getId(), periodKey);
    }

    /**
     * 보상을 받은 진행도를 직접 만든다.
     *
     * <p>받은 시각을 원하는 값으로 두려면 이렇게 박아 넣는 수밖에 없다. 서비스로 받으면 언제나 지금이다.
     */
    protected Long insertClaimedProgress(Long userId, String code, String periodKey, LocalDateTime claimedAt) {
        MissionDefinition definition = definitionOf(code);
        jdbcTemplate.update("""
                INSERT INTO mission_progress
                    (user_id, mission_id, period_key, current_value, target_snapshot,
                     completed_at, claimed_at, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                userId, definition.getId(), periodKey,
                definition.getTarget(), definition.getTarget(),
                claimedAt, claimedAt, claimedAt, claimedAt);

        return jdbcTemplate.queryForObject(
                "SELECT id FROM mission_progress WHERE user_id = ? AND mission_id = ? AND period_key = ?",
                Long.class, userId, definition.getId(), periodKey);
    }

    /** 미션 정의를 비활성화한다. 비활성 미션이 기록에는 남는지 확인할 때 쓴다. */
    protected void deactivateDefinition(String code) {
        jdbcTemplate.update("UPDATE mission_definition SET active = 0 WHERE code = ?", code);
    }

    /** 지난 주 주간 키. */
    protected String lastWeekKey() {
        return MissionPeriodKey.weekly(MissionPeriodKey.today().minusWeeks(1));
    }

    /** 어제 일일 키. */
    protected String yesterdayKey() {
        return MissionPeriodKey.daily(MissionPeriodKey.today().minusDays(1));
    }
}
