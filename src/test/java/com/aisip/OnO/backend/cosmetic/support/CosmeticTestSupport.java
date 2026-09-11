package com.aisip.OnO.backend.cosmetic.support;

import com.aisip.OnO.backend.cosmetic.entity.CosmeticSlot;
import com.aisip.OnO.backend.cosmetic.repository.CosmeticItemRepository;
import com.aisip.OnO.backend.cosmetic.repository.UserCosmeticLoadoutRepository;
import com.aisip.OnO.backend.cosmetic.service.CosmeticService;
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
 */
public abstract class CosmeticTestSupport extends IntegrationTestSupport {

    protected static final String HEADBAND_SPROUT = "headband_sprout";
    protected static final String BG_SPRING = "bg_spring";
    protected static final String GLASSES_ROUND = "glasses_round";
    protected static final String SCARF = "scarf";
    protected static final String HAT_BEANIE = "hat_beanie";
    protected static final String BG_STUDY = "bg_study";
    protected static final String BAG_MINI_BACKPACK = "bag_mini_backpack";
    protected static final String OUTFIT_CARDIGAN = "outfit_cardigan";
    protected static final String PROP_STUDY = "prop_study";
    protected static final String GLASSES_SUN = "glasses_sun";
    protected static final String HAT_BUCKET = "hat_bucket";
    protected static final String BG_NIGHT = "bg_night";
    protected static final String HAT_CROWN = "hat_crown";
    protected static final String HAT_GRADUATE = "hat_graduate";
    protected static final String OUTFIT_GRADUATE = "outfit_graduate";
    protected static final String PROP_DIPLOMA = "prop_diploma";
    protected static final String GRADUATE_SET = "graduate";
    /** 2차 콘텐츠라 active = 0 으로 시드된 아이템. 비활성 취급을 확인할 때 쓴다. */
    protected static final String INACTIVE_HAT_BERET = "hat_beret";

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
     * 사용자의 총 학습 레벨을 원하는 값으로 맞춘다.
     *
     * <p>경험치를 쌓아 올리면 레벨 15 를 만드는 데 수천 점이 필요하고, 그 과정에서 미션 적립까지
     * 끌려 들어와 무엇을 재는 테스트인지 흐려진다. 관리자 경로가 쓰는 것과 같은 setter 를 쓴다.
     */
    protected User setLevel(User user, long level) {
        return transactionTemplate.execute(status -> {
            User managed = userRepository.findById(user.getId()).orElseThrow();
            managed.getUserMissionStatus().setTotalStudyLevel(level, 0L);
            return userRepository.save(managed);
        });
    }

    protected User userAtLevel(long level) {
        return setLevel(fixtures.createUser(), level);
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
