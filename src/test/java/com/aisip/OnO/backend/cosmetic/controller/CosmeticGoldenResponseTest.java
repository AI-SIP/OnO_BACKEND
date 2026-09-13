package com.aisip.OnO.backend.cosmetic.controller;

import com.aisip.OnO.backend.cosmetic.support.CosmeticTestSupport;
import com.aisip.OnO.backend.user.entity.User;
import com.fasterxml.jackson.core.util.DefaultIndenter;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.core.util.Separators;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 꾸미기 응답의 골든 파일.
 *
 * <p><b>왜 있는가.</b> 프론트는 손으로 적은 더미 JSON 으로 파싱을 테스트하고 서버는 서버대로
 * 자기 응답을 테스트해 왔다. 둘이 같은 JSON 을 한 번도 본 적이 없어서, 응답 모양이 어긋나도
 * 양쪽 테스트는 초록으로 남는다. 그래서 서버가 실제로 뱉는 본문을
 * {@code src/test/resources/cosmetic/golden/} 에 굳혀 두고 프론트가 같은 파일을 읽게 한다.
 * 서버 응답이 바뀌는 순간 여기서 한 번, 프론트 파싱 테스트에서 한 번 깨진다.
 *
 * <p><b>무엇을 굳히는가.</b> {@code CommonResponse} 래퍼를 포함한 <b>응답 본문 전체</b>다.
 * 프론트가 벗겨내야 하는 껍데기까지 계약의 일부라 {@code $.data} 만 잘라 두지 않는다.
 *
 * <p><b>키 순서.</b> 골든이 실행마다 달라지면 계약서 노릇을 못 한다. 지금 {@code equipped} 는
 * {@code EnumMap} 이고 {@code items}·{@code slots} 는 정렬된 리스트라 서버 쪽 순서가 이미
 * 고정돼 있지만, 그건 구현이 우연히 그런 것이지 계약이 아니다({@code CosmeticItemResponseDto}
 * 주석도 "JSON 필드 순서는 계약이 아니다" 라고 못박는다). 그래서 파일로 쓸 때
 * <b>모든 객체의 키를 이름순으로 정렬</b>해 둔다. 배열은 순서가 의미를 가지므로 그대로 둔다.
 *
 * <p><b>비교.</b> 문자열이 아니라 JSON 트리로 비교한다. 들여쓰기나 키 순서 같은 의미 없는 차이로
 * 깨지면 사람이 골든을 믿지 않게 된다. {@code JsonNode#equals} 는 객체의 키 순서를 보지 않고
 * 배열의 순서만 본다.
 *
 * <p><b>갱신하는 법.</b> 일부러 응답을 바꿨다면
 * <pre>./gradlew test --tests "*CosmeticGoldenResponseTest" -Dcosmetic.golden.update=true</pre>
 * 로 다시 쓴다. 바뀐 골든은 반드시 눈으로 diff 를 확인하고 커밋하며, 프론트 레포
 * ({@code OnO_FRONT/test/fixtures/}) 의 같은 파일도 함께 갱신한다. 자세한 내용은
 * {@code src/test/resources/cosmetic/golden/README.md} 에 있다.
 */
@DisplayName("꾸미기 API 골든 응답")
class CosmeticGoldenResponseTest extends CosmeticTestSupport {

    /** 골든 파일이 사는 곳. 테스트 작업 디렉터리는 Gradle 이 프로젝트 루트로 잡아 준다. */
    private static final Path GOLDEN_DIR = Path.of("src", "test", "resources", "cosmetic", "golden");

    /** 이 값이 {@code true} 면 비교 대신 골든을 다시 쓴다. */
    private static final String UPDATE_PROPERTY = "cosmetic.golden.update";

    /** 같은 뜻의 환경 변수. Gradle 은 환경 변수를 테스트 JVM 에 그대로 물려준다. */
    private static final String UPDATE_ENV = "COSMETIC_GOLDEN_UPDATE";

    /**
     * 골든에 실을 대표 아이템.
     *
     * <p>카탈로그 63 개를 통째로 실으면 파일이 수천 줄이 되고, 아이템 하나가 늘 때마다 골든이
     * 흔들려 정작 봐야 할 응답 구조의 변화가 묻힌다. <b>프론트 파싱이 마주칠 모든 모양</b>이
     * 한 번씩 들어가도록 추렸다. 무엇을 왜 남겼는지는 README 의 표에 적어 뒀다.
     */
    private static final List<String> GOLDEN_ITEM_KEYS = List.of(
            BG_SPRING,             // BACKGROUND / 능력치 해금 / 보유
            BG_SPACE,              // BACKGROUND / 능력치 해금 / 잠김
            OUTFIT_CARDIGAN,       // OUTFIT     / fullBody=true / 보유
            OUTFIT_GRADUATE,       // OUTFIT     / fullBody=true + 세트 / 잠김
            BACK_BACKPACK_NAVY,    // BAG        / layerOrder 200 으로 자리 층을 덮어씀
            BAG_MINI_BACKPACK,     // BAG        / layerOrder null (자리 기본값 사용)
            SCARF,                 // NECK
            GLASSES_ROUND,         // FACE
            HAT_BEANIE,            // HEAD       / 능력치 해금
            HEADBAND_SPROUT,       // HEAD       / requiredAbility null (총 학습 해금)
            HAT_GRADUATE,          // HEAD       / 세트 / 잠김
            PROP_DIPLOMA,          // HAND       / 세트 / 잠김
            BADGE_LEAF_STAR,       // BADGE      / requiredAbility null
            EFFECT_PETALS,         // EFFECT
            FRAME_SPRING           // FRAME      / composited=false 인 자리, SVG 에셋
    );

    /** 골든에 실을 차림. HAND 는 잠긴 것뿐이라 비워 둔다. */
    private static final String GOLDEN_LOADOUT_BODY = """
            {"equipped":{\
            "BACKGROUND":"bg_spring",\
            "OUTFIT":"outfit_cardigan",\
            "BAG":"back_backpack_navy",\
            "NECK":"scarf",\
            "FACE":"glasses_round",\
            "HEAD":"hat_beanie",\
            "BADGE":"badge_leaf_star",\
            "EFFECT":"effect_petals",\
            "FRAME":"frame_spring"}}""";

    @Test
    @DisplayName("GET /api/cosmetics - 응답 본문 전체가 골든과 같다")
    void getCosmeticsMatchesGolden() throws Exception {
        User user = goldenUser();
        authenticateAs(user.getId());
        trimCatalogToGoldenItems();

        mockMvc.perform(put("/api/cosmetics/equip-all")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(GOLDEN_LOADOUT_BODY))
                .andExpect(status().isOk());

        MvcResult result = mockMvc.perform(get("/api/cosmetics"))
                .andExpect(status().isOk())
                .andReturn();

        assertMatchesGolden("get-cosmetics.json", bodyOf(result));
    }

    @Test
    @DisplayName("PUT /api/cosmetics/equip-all - 충돌 없는 성공 응답이 골든과 같다")
    void equipAllMatchesGolden() throws Exception {
        User user = goldenUser();
        authenticateAs(user.getId());
        trimCatalogToGoldenItems();

        MvcResult result = mockMvc.perform(put("/api/cosmetics/equip-all")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(GOLDEN_LOADOUT_BODY))
                .andExpect(status().isOk())
                .andReturn();

        assertMatchesGolden("equip-all.json", bodyOf(result));
    }

    /**
     * 충돌로 한 자리가 벗겨진 응답.
     *
     * <p>시드에는 충돌 조합이 하나도 없어서 여기서 직접 만든다. 비니(HEAD, 층 700)와
     * 가디건(OUTFIT, 층 400)을 충돌로 묶고 둘을 함께 보내면, 뒤에 깔리는 가디건이 남고
     * 비니가 벗겨져 {@code unequippedSlots} 에 실린다.
     */
    @Test
    @DisplayName("PUT /api/cosmetics/equip-all - 충돌로 한 자리가 벗겨진 응답이 골든과 같다")
    void equipAllConflictMatchesGolden() throws Exception {
        User user = goldenUser();
        authenticateAs(user.getId());
        trimCatalogToGoldenItems();
        setConflicts(HAT_BEANIE, OUTFIT_CARDIGAN);

        MvcResult result = mockMvc.perform(put("/api/cosmetics/equip-all")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(GOLDEN_LOADOUT_BODY))
                .andExpect(status().isOk())
                .andReturn();

        assertMatchesGolden("equip-all-conflict.json", bodyOf(result));
    }

    // ─────────────────────────── 준비 ───────────────────────────

    /**
     * 골든을 찍을 사용자. 다섯 레벨이 모두 5 라 대표 아이템 중 열린 것과 잠긴 것이 함께 나온다.
     *
     * <p>다 자란 사용자로 찍으면 {@code owned: false} 가 한 건도 없어서, 프론트가 잠긴 아이템을
     * 어떻게 받는지 이 파일로는 알 수 없다.
     */
    private User goldenUser() {
        return setLevels(fixtures.createUser(), 5L, 5L, 5L, 5L, 5L);
    }

    /**
     * 대표 아이템만 남기고 나머지를 비활성으로 내린다. 조회는 {@code active = 1} 만 보므로
     * 이것으로 카탈로그가 추려진다. 개구리 본체({@code BASE}) 행은 {@code baseImageUrl} 이
     * 읽으므로 반드시 남긴다.
     */
    private void trimCatalogToGoldenItems() {
        List<String> keep = new ArrayList<>(GOLDEN_ITEM_KEYS);
        keep.add("BASE");
        String placeholders = String.join(",", Collections.nCopies(keep.size(), "?"));
        jdbcTemplate.update(
                "UPDATE cosmetic_item SET active = 0 WHERE item_key NOT IN (" + placeholders + ")",
                keep.toArray());
    }

    private String bodyOf(MvcResult result) throws Exception {
        return result.getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    // ─────────────────────────── 골든 비교 ───────────────────────────

    /**
     * 실제 응답을 골든 파일과 맞춘다. 다르면 실패한다.
     *
     * <p>{@code -Dcosmetic.golden.update=true} 로 돌리면 비교 대신 골든을 다시 쓴다.
     * 응답을 일부러 바꿨을 때만 쓰는 길이다.
     */
    private void assertMatchesGolden(String fileName, String actualBody) throws Exception {
        JsonNode actual = objectMapper.readTree(actualBody);
        Path goldenFile = GOLDEN_DIR.resolve(fileName);

        if (updateRequested()) {
            Files.createDirectories(GOLDEN_DIR);
            Files.writeString(goldenFile, canonicalJson(actual) + "\n", StandardCharsets.UTF_8);
            return;
        }

        if (!Files.exists(goldenFile)) {
            throw new AssertionError("골든 파일이 없다: " + goldenFile.toAbsolutePath()
                    + "\n-D" + UPDATE_PROPERTY + "=true 로 한 번 돌려 만든 뒤 내용을 확인하고 커밋해라.");
        }

        JsonNode golden = objectMapper.readTree(Files.readString(goldenFile, StandardCharsets.UTF_8));
        // 실패 메시지를 미리 만들어 둔다. AssertJ 의 Supplier 는 검사 예외를 던질 수 없다.
        String failMessage = """
                %s 가 실제 응답과 다르다.

                [골든]
                %s

                [실제]
                %s

                일부러 바꾼 것이면 -D%s=true 로 다시 쓰고, 프론트 레포(OnO_FRONT/test/fixtures/)의
                같은 파일도 함께 갱신해라."""
                .formatted(fileName, canonicalJson(golden), canonicalJson(actual), UPDATE_PROPERTY);
        assertThat(actual)
                .withFailMessage(failMessage)
                .isEqualTo(golden);
    }

    /** 골든을 다시 쓰라는 지시가 있는지. 시스템 프로퍼티와 환경 변수를 모두 본다. */
    private static boolean updateRequested() {
        return Boolean.parseBoolean(System.getProperty(UPDATE_PROPERTY))
                || Boolean.parseBoolean(System.getenv(UPDATE_ENV));
    }

    /**
     * 사람이 읽고 diff 를 뜰 수 있는 형태로 찍는다.
     *
     * <p>객체의 키는 이름순으로 정렬한다. 서버 쪽 순서가 바뀌어도 골든이 흔들리지 않게 하려는 것이다.
     * 배열은 순서 자체가 의미(그리는 층 순서)라 손대지 않는다.
     */
    private String canonicalJson(JsonNode node) throws IOException {
        return objectMapper.writer(prettyPrinter()).writeValueAsString(sortObjectKeys(node));
    }

    private static DefaultPrettyPrinter prettyPrinter() {
        DefaultIndenter indenter = new DefaultIndenter("  ", "\n");
        DefaultPrettyPrinter printer = new DefaultPrettyPrinter()
                .withSeparators(Separators.createDefaultInstance()
                        .withObjectFieldValueSpacing(Separators.Spacing.AFTER));
        printer.indentObjectsWith(indenter);
        printer.indentArraysWith(indenter);
        return printer;
    }

    private static JsonNode sortObjectKeys(JsonNode node) {
        if (node.isObject()) {
            List<String> names = new ArrayList<>();
            node.fieldNames().forEachRemaining(names::add);
            Collections.sort(names);
            ObjectNode sorted = JsonNodeFactory.instance.objectNode();
            names.forEach(name -> sorted.set(name, sortObjectKeys(node.get(name))));
            return sorted;
        }
        if (node.isArray()) {
            ArrayNode sorted = JsonNodeFactory.instance.arrayNode();
            node.forEach(element -> sorted.add(sortObjectKeys(element)));
            return sorted;
        }
        return node.deepCopy();
    }
}
