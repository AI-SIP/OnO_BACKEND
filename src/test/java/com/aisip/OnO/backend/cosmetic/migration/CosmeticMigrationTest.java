package com.aisip.OnO.backend.cosmetic.migration;

import com.aisip.OnO.backend.cosmetic.entity.CosmeticItem;
import com.aisip.OnO.backend.cosmetic.entity.CosmeticSlot;
import com.aisip.OnO.backend.cosmetic.support.CosmeticItemSeeder;
import com.aisip.OnO.backend.cosmetic.support.CosmeticTestSupport;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 마이그레이션이 중간에 끊겨도 다시 돌릴 수 있는지.
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
    }

    @Test
    @DisplayName("시드를 세 번 실행해도 카탈로그는 그대로다")
    void seedIsIdempotent() {
        // @BeforeEach 가 이미 한 번 넣었다. 같은 문장을 다시 돌리는 것이 이 테스트의 핵심이다.
        long before = cosmeticItemRepository.count();

        seeder.seed();
        seeder.seed();

        assertThat(cosmeticItemRepository.count()).isEqualTo(before);
    }

    @Test
    @DisplayName("이미 만들어진 스키마에 DDL 을 다시 돌려도 터지지 않는다")
    void rerunningDdlDoesNotFail() {
        // 테스트 DB 에는 두 테이블이 이미 있다. 그 위에 두 번 더 돌려 본다.
        for (int attempt = 0; attempt < 2; attempt++) {
            runMigration(DDL_MIGRATION);
        }

        // 예외가 안 났다는 것만으로는 약하다. 다시 돌린 뒤에도 스키마가 쓸 수 있는 상태인지 확인한다.
        var user = userAtLevel(15);
        cosmeticService.equip(user.getId(), CosmeticSlot.HEAD, HAT_BEANIE);
        assertThat(equippedOf(user.getId())).containsEntry(CosmeticSlot.HEAD, HAT_BEANIE);
    }

    @Test
    @DisplayName("레벨 해금 아이템은 2부터 15까지 빈 레벨 없이 채워져 있다")
    void everyLevelHasAnUnlock() {
        List<Integer> unlockLevels = cosmeticItemRepository.findAllByActiveTrue().stream()
                .map(CosmeticItem::getRequiredLevel)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .sorted()
                .toList();

        assertThat(unlockLevels)
                .as("중간 레벨에 아무것도 안 열리면 그 레벨업만 보상이 없는 것처럼 보인다")
                .containsExactly(2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15);
    }

    @Test
    @DisplayName("모든 이미지 경로가 item_key 와 짝이 맞는다")
    void imageUrlMatchesItemKey() {
        List<String> mismatched = cosmeticItemRepository.findAll().stream()
                .filter(item -> !item.getImageUrl().equals("assets/Cosmetic/" + item.getItemKey() + ".png"))
                .map(CosmeticItem::getItemKey)
                .toList();

        assertThat(mismatched)
                .as("프론트 에셋 파일명은 item_key 로 찾는다. 어긋나면 이미지가 안 나온다")
                .isEmpty();
    }

    @Test
    @DisplayName("이미지 경로는 전부 번들 상대 경로다 - 아직 S3 에 아무것도 없다")
    void imageUrlIsNotRemoteYet() {
        assertThat(cosmeticItemRepository.findAll())
                .extracting(CosmeticItem::getImageUrl)
                .allSatisfy(url -> assertThat(url).doesNotStartWith("http"));
    }

    @Test
    @DisplayName("졸업 세트는 세 슬롯이고 전부 레벨 15 다")
    void graduateSetIsConsistent() {
        List<CosmeticItem> setItems = cosmeticItemRepository.findAllBySetIdAndActiveTrueOrderByIdAsc(GRADUATE_SET);

        assertThat(setItems).extracting(CosmeticItem::getSlot)
                .containsExactlyInAnyOrder(CosmeticSlot.HEAD, CosmeticSlot.OUTFIT, CosmeticSlot.HAND);
        assertThat(setItems).extracting(CosmeticItem::getRequiredLevel)
                .as("세트인데 해금 레벨이 다르면 절반만 가진 상태가 생긴다")
                .containsOnly(15);
    }

    @Test
    @DisplayName("2차 콘텐츠는 비활성이고 해금 레벨이 없다")
    void inactiveItemsHaveNoLevel() {
        List<CosmeticItem> inactive = cosmeticItemRepository.findAll().stream()
                .filter(item -> !item.isActive())
                .toList();

        assertThat(inactive).hasSize(19);
        assertThat(inactive).extracting(CosmeticItem::getRequiredLevel).containsOnlyNulls();
    }

    @Test
    @DisplayName("모든 슬롯 값이 CosmeticSlot 에 있는 이름이다")
    void slotsAreKnown() {
        // 시드에 오타가 있으면 엔티티 매핑에서 IllegalArgumentException 이 난다.
        // 조회가 성공했다는 것 자체가 검증이지만, 슬롯 분포까지 함께 본다.
        assertThat(cosmeticItemRepository.findAll())
                .extracting(CosmeticItem::getSlot)
                .contains(CosmeticSlot.BASE, CosmeticSlot.HEAD, CosmeticSlot.BACKGROUND,
                        CosmeticSlot.OUTFIT, CosmeticSlot.NECK, CosmeticSlot.FACE,
                        CosmeticSlot.HAND, CosmeticSlot.BACK, CosmeticSlot.BADGE);
    }

    /** 마이그레이션의 실행 문장을 순서대로 돌린다. */
    private void runMigration(String path) {
        transactionTemplate.executeWithoutResult(status -> {
            for (String statement : executableStatements(path)) {
                entityManager.createNativeQuery(statement).executeUpdate();
            }
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
