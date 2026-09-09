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
}
