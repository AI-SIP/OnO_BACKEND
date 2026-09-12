package com.aisip.OnO.backend.cosmetic.migration;

import com.aisip.OnO.backend.cosmetic.entity.CosmeticItem;
import com.aisip.OnO.backend.cosmetic.entity.CosmeticSlot;
import com.aisip.OnO.backend.cosmetic.support.CosmeticItemSeeder;
import com.aisip.OnO.backend.cosmetic.support.CosmeticTestSupport;
import com.aisip.OnO.backend.mission.entity.MissionType.AbilityType;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Statement;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 마이그레이션이 중간에 끊겨도 다시 돌릴 수 있는지, 그리고 카탈로그가 해금표와 맞는지.
 *
 * <p>MySQL DDL 은 트랜잭션이 아니다. 테이블은 만들어지고 뒤이은 INSERT 에서 끊기면 Flyway 는 실패로
 * 기록하는데 테이블은 남아, 재기동하면 "table already exists" 로 또 실패한다.
 * {@code flyway repair} 없이는 앱이 뜨지 않고, blue-green 배포 구간이면 그대로 장애다.
 *
 * <p>시드 내용도 여기서 고정한다. 카탈로그가 어긋나면 프론트 에셋 파일명과 맞지 않아
 * 이미지가 통째로 안 나오는데, 이미 깔린 앱이 있으면 그때는 되돌릴 수 없다.
 */
@DisplayName("꾸미기 마이그레이션")
class CosmeticMigrationTest extends CosmeticTestSupport {

    private static final String DDL_MIGRATION = "db/migration/V34__create_cosmetic_system.sql";
    private static final String SEED_MIGRATION = "db/migration/V35__seed_cosmetic_items.sql";
    private static final String ABILITY_DDL_MIGRATION = "db/migration/V36__add_cosmetic_ability_unlock_columns.sql";
    private static final String ABILITY_SEED_MIGRATION = "db/migration/V37__seed_cosmetic_items_by_ability.sql";
    private static final String FRAME_SEED_MIGRATION = "db/migration/V38__seed_profile_frame_cosmetics.sql";

    @Autowired
    private CosmeticItemSeeder seeder;

    @PersistenceContext
    private EntityManager entityManager;

    @Test
    @DisplayName("DDL 파일에는 시드가 섞여 있지 않다")
    void ddlMigrationHasNoSeed() {
        assertThat(statementsOf(DDL_MIGRATION))
                .as("DDL 과 시드가 한 파일에 있으면 시드에서 끊길 때 테이블만 남아 재실행이 막힌다")
                .doesNotContain("INSERT INTO");
        assertThat(statementsOf(ABILITY_DDL_MIGRATION))
                .as("V36 도 같은 이유로 컬럼 추가만 담는다")
                .doesNotContain("INSERT INTO");
    }

    @Test
    @DisplayName("DDL 은 다시 돌려도 안전하게 쓰여 있다")
    void ddlMigrationIsRerunnable() {
        String ddl = statementsOf(DDL_MIGRATION);

        assertThat(ddl).contains("CREATE TABLE IF NOT EXISTS COSMETIC_ITEM");
        assertThat(ddl).contains("CREATE TABLE IF NOT EXISTS USER_COSMETIC_LOADOUT");
        // MySQL 에는 CREATE INDEX IF NOT EXISTS 가 없다. 인덱스를 따로 만들면 그 문장이 재실행 지점이 된다.
        assertThat(ddl).doesNotContain("CREATE INDEX");
    }

    @Test
    @DisplayName("컬럼 추가는 information_schema 로 가드한다 - MySQL 에 ADD COLUMN IF NOT EXISTS 가 없다")
    void abilityDdlGuardsEveryColumn() {
        String ddl = statementsOf(ABILITY_DDL_MIGRATION);

        for (String column : List.of("REQUIRED_ABILITY", "FULL_BODY", "SET_NAME_KO")) {
            assertThat(ddl)
                    .as(column + " 를 가드 없이 추가하면 재실행 때 Duplicate column name 으로 앱이 뜨지 않는다")
                    .contains("COLUMN_NAME = '" + column + "'");
        }
        assertThat(ddl).contains("INFORMATION_SCHEMA.COLUMNS");
    }

    @Test
    @DisplayName("이미 컬럼이 있는 스키마에 V36 을 다시 돌려도 터지지 않는다")
    void rerunningAbilityDdlDoesNotFail() {
        // 테스트 DB 에는 세 컬럼이 이미 있다(엔티티에서 Hibernate 가 만든다). 그 위에 두 번 더 돌려 본다.
        for (int attempt = 0; attempt < 2; attempt++) {
            runMigrationOnOneConnection(ABILITY_DDL_MIGRATION);
        }

        // 예외가 안 났다는 것만으로는 약하다. 다시 돌린 뒤에도 카탈로그를 읽을 수 있는지 확인한다.
        assertThat(cosmeticItemRepository.findByItemKey(GLASSES_ROUND))
                .get()
                .extracting(CosmeticItem::getRequiredAbility)
                .isEqualTo(AbilityType.PROBLEM_PRACTICE);
    }

    @Test
    @DisplayName("한 슬롯 한 행을 DB 가 막는다 - 복합 기본키가 실제로 걸려 있다")
    void loadoutHasCompositePrimaryKey() {
        assertThat(statementsOf(DDL_MIGRATION))
                .as("이 기본키가 없으면 upsert 한 문장으로 장착을 처리한다는 전제가 무너진다")
                .contains("PRIMARY KEY (USER_ID, SLOT)");

        List<String> primaryKeyColumns = jdbcTemplate.queryForList("""
                SELECT column_name
                FROM information_schema.statistics
                WHERE table_schema = DATABASE()
                  AND table_name = 'user_cosmetic_loadout'
                  AND index_name = 'PRIMARY'
                ORDER BY seq_in_index
                """, String.class);

        // 순서까지 보지는 않는다. 운영 스키마는 Flyway 가 만들고(ddl-auto 는 세 환경 모두 validate 다)
        // 테스트 스키마만 Hibernate 가 만드는데, Hibernate 는 복합 식별자 컬럼을 이름순으로 늘어놓아
        // 여기서는 (slot, user_id) 가 된다. 운영에 적용되는 순서는 위에서 마이그레이션 원문으로 고정했다.
        assertThat(primaryKeyColumns)
                .as("기본키를 이루는 컬럼 자체가 달라지면 한 슬롯 한 행 보장이 깨진다")
                .containsExactlyInAnyOrder("user_id", "slot");
    }

    @Test
    @DisplayName("시드는 다시 돌려도 안전하게 쓰여 있다")
    void seedMigrationIsRerunnable() {
        assertThat(statementsOf(SEED_MIGRATION)).contains("ON DUPLICATE KEY UPDATE");
        assertThat(statementsOf(ABILITY_SEED_MIGRATION)).contains("ON DUPLICATE KEY UPDATE");
        assertThat(statementsOf(FRAME_SEED_MIGRATION)).contains("ON DUPLICATE KEY UPDATE");
    }

    @Test
    @DisplayName("V38 은 컬럼을 건드리지 않는다 - 시드만 늘어난다")
    void frameSeedHasNoDdl() {
        assertThat(statementsOf(FRAME_SEED_MIGRATION))
                .as("V36 이 이미 적용된 DB 에 얹히는 파일이라 추가할 컬럼이 없다")
                .doesNotContain("ALTER TABLE")
                .doesNotContain("CREATE TABLE");
    }

    @Test
    @DisplayName("V37 은 기존 행을 덮어쓴다 - V35 까지 돈 DB 와 새 DB 가 같은 결과여야 한다")
    void abilitySeedOverwritesExistingRows() {
        String seed = statementsOf(ABILITY_SEED_MIGRATION);

        assertThat(seed)
                .as("덮어쓰지 않으면 이미 V35 가 돈 DB 는 옛 해금 조건을 그대로 들고 있게 된다")
                .contains("REQUIRED_ABILITY = VALUES(REQUIRED_ABILITY)")
                .contains("REQUIRED_LEVEL = VALUES(REQUIRED_LEVEL)")
                .contains("SLOT = VALUES(SLOT)")
                .contains("ACTIVE = VALUES(ACTIVE)");
        assertThat(seed)
                .as("충돌 목록은 운영에서 채우는 값이라 시드가 되돌리면 안 된다")
                .doesNotContain("CONFLICTS_WITH = VALUES(CONFLICTS_WITH)");
    }

    @Test
    @DisplayName("시드를 세 번 실행해도 카탈로그는 그대로다")
    void seedIsIdempotent() {
        // @BeforeEach 가 이미 한 번 넣었다. 같은 문장을 다시 돌리는 것이 이 테스트의 핵심이다.
        long before = cosmeticItemRepository.count();
        Map<String, Integer> levelsBefore = levelsByKey();

        seeder.seed();
        seeder.seed();

        assertThat(cosmeticItemRepository.count()).isEqualTo(before);
        assertThat(levelsByKey()).isEqualTo(levelsBefore);
    }

    @Test
    @DisplayName("이미 만들어진 스키마에 DDL 을 다시 돌려도 터지지 않는다")
    void rerunningDdlDoesNotFail() {
        // 테스트 DB 에는 두 테이블이 이미 있다. 그 위에 두 번 더 돌려 본다.
        for (int attempt = 0; attempt < 2; attempt++) {
            runMigration(DDL_MIGRATION);
        }

        // 예외가 안 났다는 것만으로는 약하다. 다시 돌린 뒤에도 스키마가 쓸 수 있는 상태인지 확인한다.
        var user = fullyGrownUser();
        cosmeticService.equip(user.getId(), CosmeticSlot.HEAD, HAT_BEANIE);
        assertThat(equippedOf(user.getId())).containsEntry(CosmeticSlot.HEAD, HAT_BEANIE);
    }

    @Test
    @DisplayName("시드는 본체 말고 63개다")
    void seedsEverythingInTheTable() {
        assertThat(cosmeticItemRepository.findAll())
                .filteredOn(item -> !"BASE".equals(item.getItemKey()))
                .as("해금표의 17 + 10 + 11 + 9 + 16 이다")
                .hasSize(SEEDED_ITEM_COUNT);
    }

    @Test
    @DisplayName("프로필 프레임 8종이 전부 들어갔다")
    void everyFrameIsSeeded() {
        assertThat(keysOfSlot(CosmeticSlot.FRAME))
                .containsExactly("frame_autumn", "frame_leaf", "frame_master", "frame_night",
                        "frame_spring", "frame_study", "frame_summer", "frame_winter");
    }

    @Test
    @DisplayName("프레임 해금 자리가 표와 맞는다")
    void frameUnlockPositionsMatchTable() {
        Map<String, String> unlocks = catalog().stream()
                .filter(item -> item.getSlot() == CosmeticSlot.FRAME)
                .collect(Collectors.toMap(CosmeticItem::getItemKey,
                        item -> (item.getRequiredAbility() == null ? "-" : item.getRequiredAbility().name())
                                + " " + item.getRequiredLevel()));

        assertThat(unlocks).containsOnly(
                Map.entry("frame_spring", "ATTENDANCE 3"),
                Map.entry("frame_summer", "ATTENDANCE 5"),
                Map.entry("frame_autumn", "ATTENDANCE 10"),
                Map.entry("frame_winter", "ATTENDANCE 13"),
                Map.entry("frame_night", "ATTENDANCE 15"),
                Map.entry("frame_study", "NOTE_WRITE 14"),
                Map.entry("frame_leaf", "- 4"),
                Map.entry("frame_master", "- 17"));
    }

    @Test
    @DisplayName("능력치별 개수가 해금표와 맞는다")
    void abilityDistributionMatchesTable() {
        Map<AbilityType, Long> byAbility = catalog().stream()
                .filter(item -> item.getRequiredAbility() != null)
                .collect(Collectors.groupingBy(CosmeticItem::getRequiredAbility, Collectors.counting()));

        assertThat(byAbility).containsOnly(
                Map.entry(AbilityType.ATTENDANCE, 17L),
                Map.entry(AbilityType.NOTE_WRITE, 10L),
                Map.entry(AbilityType.PROBLEM_PRACTICE, 11L),
                Map.entry(AbilityType.NOTE_PRACTICE, 9L));

        assertThat(catalog()).filteredOn(item -> item.getRequiredAbility() == null)
                .as("나머지는 총 학습 레벨로 열린다")
                .hasSize(16);
    }

    @Test
    @DisplayName("자리와 레이어 순서가 표와 맞는다")
    void slotDistributionMatchesTable() {
        Map<CosmeticSlot, Long> bySlot = catalog().stream()
                .collect(Collectors.groupingBy(CosmeticItem::getSlot, Collectors.counting()));

        assertThat(bySlot).containsOnly(
                Map.entry(CosmeticSlot.BACKGROUND, 9L),
                Map.entry(CosmeticSlot.BACK, 2L),
                Map.entry(CosmeticSlot.OUTFIT, 5L),
                Map.entry(CosmeticSlot.BAG, 3L),
                Map.entry(CosmeticSlot.NECK, 5L),
                Map.entry(CosmeticSlot.FACE, 6L),
                Map.entry(CosmeticSlot.HEAD, 8L),
                Map.entry(CosmeticSlot.HAND, 7L),
                Map.entry(CosmeticSlot.BADGE, 6L),
                Map.entry(CosmeticSlot.EFFECT, 4L),
                Map.entry(CosmeticSlot.FRAME, 8L));

        assertThat(CosmeticSlot.equippableSlots())
                .extracting(CosmeticSlot::getLayerOrder)
                .as("등짐(200)은 본체(300) 뒤, 앞가방(450)은 옷(400) 위, 프레임(1000)이 맨 끝이다")
                .containsExactly(100, 200, 400, 450, 500, 600, 700, 800, 850, 900, 1000);
    }

    @Test
    @DisplayName("프레임만 개구리 합성에서 빠진다")
    void onlyFrameIsNotComposited() {
        assertThat(CosmeticSlot.values())
                .filteredOn(slot -> !slot.isComposited())
                .as("합성에서 빠지는 자리가 늘면 프론트의 그리기 코드가 통째로 흔들린다")
                .containsExactly(CosmeticSlot.FRAME);
    }

    @Test
    @DisplayName("등에 메는 가방과 앞으로 메는 가방이 갈려 있다")
    void backAndBagAreSeparated() {
        assertThat(keysOfSlot(CosmeticSlot.BACK))
                .as("V35 에서 BACK 은 그냥 '가방' 이었다. 뜻이 바뀌었으니 내용도 바뀌어야 한다")
                .containsExactly("back_backpack_canvas", "back_backpack_navy");
        assertThat(keysOfSlot(CosmeticSlot.BAG))
                .containsExactly("bag_crossbody_satchel", "bag_mini_backpack", "bag_waist_pouch");
    }

    @Test
    @DisplayName("능력치 아이템의 해금 레벨은 2 부터 15 사이다")
    void abilityUnlockLevelsAreWithinAbilityRange() {
        assertThat(catalog())
                .filteredOn(item -> item.getRequiredAbility() != null)
                .allSatisfy(item -> assertThat(item.getRequiredLevel())
                        .as(item.getItemKey() + " 의 해금 레벨")
                        .isBetween(2, 15));
    }

    @Test
    @DisplayName("총 학습 아이템의 해금 레벨은 상한 20 을 넘지 않는다")
    void totalUnlockLevelsStayUnderCap() {
        assertThat(catalog())
                .filteredOn(item -> item.getRequiredAbility() == null)
                .allSatisfy(item -> assertThat(item.getRequiredLevel())
                        .as(item.getItemKey() + " 의 해금 레벨. 상한을 넘기면 영영 열리지 않는다")
                        .isBetween(2, 20));
    }

    @Test
    @DisplayName("같은 해금 조건에 같은 자리가 둘 열리지 않는다")
    void noDuplicateSlotAtTheSameUnlock() {
        // 같은 레벨에 두 개가 열리는 것 자체는 막지 않는다. 프레임을 계절 배경과 짝 맞추면서
        // 출석 3 / 5 / 13 / 15 에 두 개씩 열리게 됐고, 그건 의도다.
        // 막아야 하는 것은 "같은 조건에 같은 자리" 다. 그러면 기본 프리셋이 어느 쪽을 고를지가
        // 키 순서라는 우연에 걸린다.
        List<String> keys = catalog().stream()
                .map(item -> (item.getRequiredAbility() == null ? "-" : item.getRequiredAbility().name())
                        + "/" + item.getRequiredLevel() + "/" + item.getSlot())
                .toList();

        assertThat(keys).doesNotHaveDuplicates();
    }

    @Test
    @DisplayName("시드된 아이템은 전부 활성이다")
    void everySeededItemIsActive() {
        assertThat(cosmeticItemRepository.findAll())
                .as("2차 콘텐츠로 내려 두었던 19 개가 전부 해금 레벨을 받았다")
                .allMatch(CosmeticItem::isActive);
    }

    @Test
    @DisplayName("모든 이미지 경로가 item_key 와 짝이 맞는다")
    void imageUrlMatchesItemKey() {
        List<String> mismatched = cosmeticItemRepository.findAll().stream()
                .filter(item -> !item.getImageUrl().equals(expectedImageUrl(item)))
                .map(CosmeticItem::getItemKey)
                .toList();

        assertThat(mismatched)
                .as("프론트 에셋 파일명은 item_key 로 찾는다. 어긋나면 이미지가 안 나온다")
                .isEmpty();
    }

    @Test
    @DisplayName("프레임만 SVG 이고 폴더도 다르다")
    void frameAssetsAreSvg() {
        assertThat(catalog())
                .filteredOn(item -> item.getSlot() == CosmeticSlot.FRAME)
                .as("원형 테두리라 확대해도 깨지면 안 돼서 SVG 다")
                .allSatisfy(item -> assertThat(item.getImageUrl())
                        .startsWith("assets/ProfileFrame/")
                        .endsWith(".svg"));

        assertThat(cosmeticItemRepository.findAll())
                .filteredOn(item -> item.getSlot() != CosmeticSlot.FRAME)
                .as("나머지는 그대로 번들 PNG 다")
                .allSatisfy(item -> assertThat(item.getImageUrl())
                        .startsWith("assets/Cosmetic/")
                        .endsWith(".png"));
    }

    @Test
    @DisplayName("이미지 경로는 전부 번들 상대 경로다 - 아직 S3 에 아무것도 없다")
    void imageUrlIsNotRemoteYet() {
        assertThat(cosmeticItemRepository.findAll())
                .extracting(CosmeticItem::getImageUrl)
                .allSatisfy(url -> assertThat(url).doesNotStartWith("http"));
    }

    @Test
    @DisplayName("전신 의상은 옷 다섯 벌뿐이다")
    void fullBodyIsExactlyTheOutfits() {
        assertThat(cosmeticItemRepository.findAll())
                .filteredOn(CosmeticItem::isFullBody)
                .extracting(CosmeticItem::getItemKey)
                .containsExactlyInAnyOrder("outfit_cardigan", "outfit_hoodie", "outfit_raincoat",
                        "outfit_school", "outfit_graduate");

        assertThat(keysOfSlot(CosmeticSlot.OUTFIT))
                .as("옷 슬롯에 전신이 아닌 것이 생기면 이 단언부터 다시 봐야 한다")
                .hasSize(5);
    }

    @Test
    @DisplayName("학사 세트는 세 슬롯이고 전부 총 학습 20 이며 이름이 같다")
    void graduateSetIsConsistent() {
        List<CosmeticItem> setItems = cosmeticItemRepository.findAllBySetIdAndActiveTrueOrderByIdAsc(GRADUATE_SET);

        assertThat(setItems).extracting(CosmeticItem::getSlot)
                .containsExactlyInAnyOrder(CosmeticSlot.HEAD, CosmeticSlot.OUTFIT, CosmeticSlot.HAND);
        assertThat(setItems).extracting(CosmeticItem::getRequiredLevel)
                .as("세트인데 해금 레벨이 다르면 절반만 가진 상태가 생긴다")
                .containsOnly(20);
        assertThat(setItems).extracting(CosmeticItem::getRequiredAbility)
                .as("세트는 총 학습 레벨로 연다")
                .containsOnlyNulls();
        assertThat(setItems).extracting(CosmeticItem::getSetNameKo)
                .as("세트 이름이 아이템마다 다르면 배너 문구가 흔들린다")
                .containsOnly(GRADUATE_SET_NAME);
    }

    @Test
    @DisplayName("세트에 속하지 않은 아이템은 세트 이름도 비어 있다")
    void setNameFollowsSetId() {
        assertThat(cosmeticItemRepository.findAll())
                .filteredOn(item -> item.getSetId() == null)
                .extracting(CosmeticItem::getSetNameKo)
                .containsOnlyNulls();
    }

    @Test
    @DisplayName("모든 슬롯 값이 CosmeticSlot 에 있는 이름이다")
    void slotsAreKnown() {
        // 시드에 오타가 있으면 엔티티 매핑에서 IllegalArgumentException 이 난다.
        // 조회가 성공했다는 것 자체가 검증이지만, 슬롯 분포까지 함께 본다.
        assertThat(cosmeticItemRepository.findAll())
                .extracting(CosmeticItem::getSlot)
                .contains(CosmeticSlot.values());
    }

    // ─────────────────────────── 헬퍼 ───────────────────────────

    /** 본체를 뺀 시드 카탈로그. */
    private List<CosmeticItem> catalog() {
        return cosmeticItemRepository.findAll().stream()
                .filter(item -> !"BASE".equals(item.getItemKey()))
                .toList();
    }

    /** 프레임만 폴더와 확장자가 다르다. */
    private String expectedImageUrl(CosmeticItem item) {
        if (item.getSlot() == CosmeticSlot.FRAME) {
            return "assets/ProfileFrame/" + item.getItemKey() + ".svg";
        }
        return "assets/Cosmetic/" + item.getItemKey() + ".png";
    }

    private List<String> keysOfSlot(CosmeticSlot slot) {
        return catalog().stream()
                .filter(item -> item.getSlot() == slot)
                .map(CosmeticItem::getItemKey)
                .sorted()
                .toList();
    }

    private Map<String, Integer> levelsByKey() {
        return catalog().stream()
                .sorted(Comparator.comparing(CosmeticItem::getItemKey))
                .collect(Collectors.toMap(CosmeticItem::getItemKey,
                        item -> Objects.requireNonNull(item.getRequiredLevel()),
                        (left, right) -> left));
    }

    /** 마이그레이션의 실행 문장을 순서대로 돌린다. */
    private void runMigration(String path) {
        transactionTemplate.executeWithoutResult(status -> {
            for (String statement : executableStatements(path)) {
                entityManager.createNativeQuery(statement).executeUpdate();
            }
        });
    }

    /**
     * 커넥션 하나에서 순서대로 돌린다.
     *
     * <p>V36 은 {@code SET @변수} 로 상태를 넘기고 {@code PREPARE}/{@code EXECUTE} 로 DDL 을 실행한다.
     * 사용자 변수는 세션(커넥션) 단위라 문장마다 커넥션이 달라지면 가드가 성립하지 않고,
     * PREPARE 는 prepared statement 프로토콜로는 보낼 수 없어 일반 Statement 가 필요하다.
     */
    private void runMigrationOnOneConnection(String path) {
        List<String> statements = executableStatements(path);
        jdbcTemplate.execute((ConnectionCallback<Void>) connection -> {
            try (Statement statement = connection.createStatement()) {
                for (String sql : statements) {
                    statement.execute(sql);
                }
            }
            return null;
        });
    }

    private List<String> executableStatements(String path) {
        return Arrays.stream(withoutComments(path).split(";"))
                .map(String::trim)
                .filter(statement -> !statement.isEmpty())
                .toList();
    }

    private String withoutComments(String path) {
        return Arrays.stream(read(path).split("\\R"))
                .filter(line -> !line.trim().startsWith("--"))
                .collect(Collectors.joining("\n"));
    }

    /** 주석을 걷어낸 실행 문장만. 주석에 적힌 설명이 단언에 걸리면 안 된다. */
    private String statementsOf(String path) {
        // 공백을 하나로 줄인다. 정렬용으로 넣은 여백 때문에 단언이 깨지면 안 된다.
        return withoutComments(path).toUpperCase(Locale.ROOT).replaceAll("\\s+", " ");
    }

    private String read(String path) {
        try (InputStream inputStream = new ClassPathResource(path).getInputStream()) {
            return StreamUtils.copyToString(inputStream, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("마이그레이션을 읽지 못했다: " + path, e);
        }
    }
}
