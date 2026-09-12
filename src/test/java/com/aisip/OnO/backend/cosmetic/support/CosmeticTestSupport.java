package com.aisip.OnO.backend.cosmetic.support;

import com.aisip.OnO.backend.cosmetic.entity.CosmeticSlot;
import com.aisip.OnO.backend.cosmetic.repository.CosmeticItemRepository;
import com.aisip.OnO.backend.cosmetic.repository.UserCosmeticLoadoutRepository;
import com.aisip.OnO.backend.cosmetic.service.CosmeticService;
import com.aisip.OnO.backend.mission.entity.UserMissionStatus;
import com.aisip.OnO.backend.support.IntegrationTestSupport;
import com.aisip.OnO.backend.user.entity.User;
import com.aisip.OnO.backend.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Map;

/**
 * 꾸미기 도메인 테스트의 공통 베이스.
 *
 * <p>{@code @MockBean} 을 새로 선언하지 않아 스프링 컨텍스트는 다른 도메인 테스트와 그대로 공유된다.
 *
 * <p>해금 기준이 능력치별로 갈리면서 "레벨 N 인 사용자" 라는 말이 더 이상 한 가지 뜻이 아니다.
 * 총 학습 레벨만 올린 사용자({@link #userAtLevel})와 능력치까지 함께 올린 사용자
 * ({@link #userAtAllLevels})를 구분해서 만든다.
 */
public abstract class CosmeticTestSupport extends IntegrationTestSupport {

    // 능력치별로 열리는 것들 — 괄호 안은 (능력치, 레벨)
    /** (PROBLEM_PRACTICE, 2) */
    protected static final String GLASSES_ROUND = "glasses_round";
    /** (PROBLEM_PRACTICE, 4) */
    protected static final String HAT_BEANIE = "hat_beanie";
    /** (PROBLEM_PRACTICE, 8) */
    protected static final String HAT_BUCKET = "hat_bucket";
    /** (PROBLEM_PRACTICE, 15) — 문제 복습의 마지막 아이템 */
    protected static final String HEAD_EARMUFFS_WINTER = "head_earmuffs_winter";
    /** (ATTENDANCE, 2) */
    protected static final String BG_SPRING = "bg_spring";
    /** (ATTENDANCE, 3) — 전경 효과 */
    protected static final String EFFECT_PETALS = "effect_petals";
    /** (ATTENDANCE, 13) — 전경 효과 */
    protected static final String EFFECT_SNOW = "effect_snow";
    /** (ATTENDANCE, 15) */
    protected static final String BG_SPACE = "bg_space";
    /** (ATTENDANCE, 3) — 프로필 프레임. 개구리에 겹치지 않는다 */
    protected static final String FRAME_SPRING = "frame_spring";
    /** (ATTENDANCE, 5) — 프로필 프레임 */
    protected static final String FRAME_SUMMER = "frame_summer";
    /** (ATTENDANCE, 15) — 프로필 프레임 */
    protected static final String FRAME_NIGHT = "frame_night";
    /** (NOTE_WRITE, 2) — 앞으로 메는 가방이라 BAG 이다 */
    protected static final String BAG_MINI_BACKPACK = "bag_mini_backpack";
    /** (NOTE_WRITE, 5) — 등에 메는 가방이라 BACK 이다 */
    protected static final String BACK_BACKPACK_NAVY = "back_backpack_navy";
    /** (NOTE_WRITE, 9) */
    protected static final String BACK_BACKPACK_CANVAS = "back_backpack_canvas";
    /** (NOTE_WRITE, 11) */
    protected static final String BAG_CROSSBODY_SATCHEL = "bag_crossbody_satchel";
    /** (NOTE_WRITE, 12) */
    protected static final String BG_STUDY = "bg_study";
    /** (NOTE_WRITE, 14) — 프로필 프레임 */
    protected static final String FRAME_STUDY = "frame_study";
    /** (NOTE_PRACTICE, 2) */
    protected static final String SCARF = "scarf";
    /** (NOTE_PRACTICE, 5) — 전신 의상 */
    protected static final String OUTFIT_CARDIGAN = "outfit_cardigan";
    /** (NOTE_PRACTICE, 12) */
    protected static final String NECK_MEDAL = "neck_medal";
    /** (NOTE_PRACTICE, 13) — 전신 의상 */
    protected static final String OUTFIT_SCHOOL = "outfit_school";

    // 총 학습 레벨로 열리는 것들 — 괄호 안은 총 학습 레벨
    /** (총 2) */
    protected static final String HEADBAND_SPROUT = "headband_sprout";
    /** (총 3) */
    protected static final String BADGE_LEAF_STAR = "badge_leaf_star";
    /** (총 4) — 프로필 프레임 */
    protected static final String FRAME_LEAF = "frame_leaf";
    /** (총 17) — 프로필 프레임. 총 학습 프레임 중 가장 늦게 열린다 */
    protected static final String FRAME_MASTER = "frame_master";
    /** (총 8) */
    protected static final String PROP_BOUQUET = "prop_bouquet";
    /** (총 16) — 상한이 20 으로 오르면서 생긴 자리 */
    protected static final String PROP_LANTERN = "prop_lantern";
    /** (총 18) */
    protected static final String BADGE_SNOWFLAKE = "badge_snowflake";
    /** (총 19) */
    protected static final String HAT_CROWN = "hat_crown";
    /** (총 20) — 학사 세트 */
    protected static final String HAT_GRADUATE = "hat_graduate";
    /** (총 20) — 학사 세트, 전신 의상 */
    protected static final String OUTFIT_GRADUATE = "outfit_graduate";
    /** (총 20) — 학사 세트 */
    protected static final String PROP_DIPLOMA = "prop_diploma";

    protected static final String GRADUATE_SET = "graduate";
    protected static final String GRADUATE_SET_NAME = "학사 세트";

    /** 전체 시드 개수. 본체(BASE) 는 포함하지 않는다. 능력치별 17 / 10 / 11 / 9 와 총 학습 16 이다. */
    protected static final int SEEDED_ITEM_COUNT = 63;

    /** 장착 가능한 자리 수. 프리셋이 한 자리에 하나씩 채우므로 곧 프리셋 행 수이기도 하다. */
    protected static final int EQUIPPABLE_SLOT_COUNT = 11;

    @Autowired
    private CosmeticItemSeeder cosmeticItemSeeder;

    @Autowired
    protected CosmeticService cosmeticService;

    @Autowired
    protected CosmeticItemRepository cosmeticItemRepository;

    @Autowired
    protected UserCosmeticLoadoutRepository userCosmeticLoadoutRepository;

    @Autowired
    protected UserRepository userRepository;

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    @Autowired
    protected TransactionTemplate transactionTemplate;

    /** DatabaseCleaner 가 매 테스트마다 cosmetic_item 까지 비우므로 여기서 다시 채운다. */
    @BeforeEach
    void seedCosmeticItems() {
        cosmeticItemSeeder.seed();
    }

    /**
     * 사용자의 레벨을 원하는 값으로 맞춘다.
     *
     * <p>경험치를 쌓아 올리면 레벨 20 을 만드는 데 수천 점이 필요하고, 그 과정에서 미션 적립까지
     * 끌려 들어와 무엇을 재는 테스트인지 흐려진다. 관리자 경로가 쓰는 것과 같은 setter 를 쓴다.
     */
    protected User setLevels(User user, long totalStudy,
                             long attendance, long noteWrite, long problemPractice, long notePractice) {
        return transactionTemplate.execute(status -> {
            User managed = userRepository.findById(user.getId()).orElseThrow();
            UserMissionStatus missionStatus = managed.getUserMissionStatus();
            missionStatus.setTotalStudyLevel(totalStudy, 0L);
            missionStatus.setAttendanceLevel(attendance, 0L);
            missionStatus.setNoteWriteLevel(noteWrite, 0L);
            missionStatus.setProblemPracticeLevel(problemPractice, 0L);
            missionStatus.setNotePracticeLevel(notePractice, 0L);
            return userRepository.save(managed);
        });
    }

    /** 총 학습 레벨만 올린다. 능력치 넷은 1 이라 능력치별 아이템은 하나도 열리지 않는다. */
    protected User setLevel(User user, long totalStudyLevel) {
        return setLevels(user, totalStudyLevel, 1L, 1L, 1L, 1L);
    }

    /** 총 학습 레벨만 올린 사용자. */
    protected User userAtLevel(long totalStudyLevel) {
        return setLevel(fixtures.createUser(), totalStudyLevel);
    }

    /** 다섯 레벨을 모두 같은 값으로 올린 사용자. */
    protected User userAtAllLevels(long level) {
        return setLevels(fixtures.createUser(), level, level, level, level, level);
    }

    /**
     * 다 자란 사용자. 총 학습 20, 능력치 넷 모두 15 라 시드된 63 개가 전부 열려 있다.
     *
     * <p>능력치별 아이템의 마지막 해금 레벨이 15, 총 학습 아이템의 마지막이 20 이다.
     */
    protected User fullyGrownUser() {
        return setLevels(fixtures.createUser(), 20L, 15L, 15L, 15L, 15L);
    }

    /** 지금 걸려 있는 것. 서비스 조회 응답에서 꺼낸다. */
    protected Map<CosmeticSlot, String> equippedOf(Long userId) {
        return cosmeticService.getCosmetics(userId).equipped();
    }

    /** 아이템의 충돌 목록을 채운다. 시드는 전부 비어 있으므로 충돌 동작은 이렇게 만들어 확인한다. */
    protected void setConflicts(String itemKey, String conflictsWith) {
        jdbcTemplate.update("UPDATE cosmetic_item SET conflicts_with = ? WHERE item_key = ?",
                conflictsWith, itemKey);
    }

    /**
     * 아이템을 비활성으로 내린다.
     *
     * <p>시드된 55 개는 전부 활성이다. 비활성 취급을 확인하려면 이렇게 직접 내려야 한다.
     * (V35 시절에는 2차 콘텐츠 19 개가 active = 0 이라 그중 하나를 골라 쓰면 됐다.)
     */
    protected void deactivate(String itemKey) {
        jdbcTemplate.update("UPDATE cosmetic_item SET active = 0 WHERE item_key = ?", itemKey);
    }

    /** 장착 행 개수. 프리셋이 행을 만들지 않는지, 첫 변경 때 굳어지는지 확인할 때 쓴다. */
    protected long loadoutRowCount(Long userId) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM user_cosmetic_loadout WHERE user_id = ?", Long.class, userId);
        return count == null ? 0 : count;
    }

    protected String rawItemKeyOf(Long userId, CosmeticSlot slot) {
        return jdbcTemplate.query(
                "SELECT item_key FROM user_cosmetic_loadout WHERE user_id = ? AND slot = ?",
                rs -> rs.next() ? rs.getString(1) : null,
                userId, slot.name());
    }
}
