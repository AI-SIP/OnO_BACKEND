package com.aisip.OnO.backend.achievement.entity;

import com.aisip.OnO.backend.achievement.service.AchievementStats;
import lombok.Getter;

import java.util.Optional;
import java.util.function.ToLongFunction;

/**
 * 훈장 열두 개. 훈장표(`OnO_FRONT/docs/훈장/훈장표.md`)가 계약이고 이 enum 이 그 사본이다.
 *
 * <p><b>선언 순서가 곧 응답 순서다.</b> 앱은 서버가 준 순서를 그대로 그리고 다시 정렬하지 않는다.
 * 순서를 바꾸면 화면의 훈장 배치가 바뀐다. 훈장표의 순서와 여기의 순서는 항상 같아야 한다.
 *
 * <p>카탈로그 테이블을 두지 않는다. 치장 아이템과 달리 훈장은 관리자가 늘리는 것이 아니라
 * 에셋과 함께 배포되는 것이라, 테이블을 두면 시드 마이그레이션과 앱 에셋이 따로 놀 여지만 생긴다.
 * 코드에 두면 열두 개가 어긋날 수 없다.
 *
 * <p>조건은 전부 <b>{@code 진행도 >= threshold}</b> 한 가지 꼴로 맞췄다. "틀린 뒤에 맞혔는가" 처럼
 * 참/거짓인 것도 0 또는 1 을 내는 진행도로 바꿔 같은 틀에 넣는다. 조건마다 다른 판정을 쓰면
 * 훈장이 늘 때마다 판정 코드가 따라 늘고, 어떤 훈장이 어떤 규칙을 쓰는지 한눈에 안 보인다.
 *
 * <p>{@code showsProgress} 가 거짓인 둘({@link #FIRST_STEP}, {@link #PHOENIX})은 진행도를 안 내려준다.
 * 0 아니면 1 이라 "0/1" 을 보여 줘 봐야 잠김 여부를 두 번 말하는 것뿐이다. 훈장표가 정한 것이다.
 */
@Getter
public enum Achievement {

    FIRST_STEP("first_step", "첫 걸음", "오답노트를 처음 작성했어요",
            1, false, AchievementStats::problemCount),

    ARCHIVIST("archivist", "기록광", "오답노트를 100개 작성했어요",
            100, true, AchievementStats::problemCount),

    PERSISTENCE("persistence", "집념", "한 문제를 5번 복습했어요",
            5, true, AchievementStats::maxSolveCountOnOneProblem),

    PHOENIX("phoenix", "불사조", "틀렸던 문제를 다시 풀어 맞혔어요",
            1, false, AchievementStats::comebackCount),

    DAWN_CLASS("dawn_class", "새벽반", "새벽 5~8시에 10번 복습했어요",
            10, true, AchievementStats::dawnSolveCount),

    NIGHT_OWL("night_owl", "올빼미", "밤 12~3시에 10번 복습했어요",
            10, true, AchievementStats::nightSolveCount),

    PERFECT_MONTH("perfect_month", "개근", "30일 연속 출석했어요",
            30, true, AchievementStats::longestLoginStreak),

    FLAWLESS("flawless", "무결점", "10번 연속으로 맞혔어요",
            10, true, AchievementStats::longestCorrectStreak),

    ORGANIZER("organizer", "정리의 신", "폴더를 10개 만들었어요",
            10, true, AchievementStats::folderCount),

    REVIEWER("reviewer", "회고왕", "복습 회고를 50번 남겼어요",
            50, true, AchievementStats::reflectionCount),

    COMPANION("companion", "동행", "스터디룸에 처음 참여했어요",
            1, true, AchievementStats::studyRoomCount),

    CHEERLEADER("cheerleader", "응원단장", "응원을 100번 보냈어요",
            100, true, AchievementStats::reactionCount);

    /**
     * 앱 번들 안의 에셋 경로. 치장이 {@code assets/Cosmetic/...} 을 내려주는 것과 같은 방식이다.
     *
     * <p>키에서 만들어 낸다. 열두 줄에 같은 접두사를 되풀이해 적으면 한 줄만 오타가 나도
     * 그 훈장만 이미지가 안 뜨는데, 그건 앱을 켜 보기 전에는 드러나지 않는다.
     */
    private static final String IMAGE_URL_PREFIX = "assets/Medal/";
    private static final String IMAGE_URL_SUFFIX = ".png";

    /** 앱과 DB 가 공유하는 식별자. enum 이름이 아니라 이 값이 계약이다. */
    private final String key;

    private final String nameKo;

    private final String descriptionKo;

    /** 이 값에 닿으면 받는다. 경계는 포함이다. */
    private final int threshold;

    /** 진행도를 내려줄지. 거짓이면 응답의 {@code current}/{@code target} 이 둘 다 null 이다. */
    private final boolean showsProgress;

    private final ToLongFunction<AchievementStats> progress;

    Achievement(String key, String nameKo, String descriptionKo,
                int threshold, boolean showsProgress, ToLongFunction<AchievementStats> progress) {
        this.key = key;
        this.nameKo = nameKo;
        this.descriptionKo = descriptionKo;
        this.threshold = threshold;
        this.showsProgress = showsProgress;
        this.progress = progress;
    }

    public String getImageUrl() {
        return IMAGE_URL_PREFIX + key + IMAGE_URL_SUFFIX;
    }

    /** 지금까지 쌓인 데이터로 이 훈장의 조건을 채웠는지. */
    public boolean isSatisfiedBy(AchievementStats stats) {
        return progress.applyAsLong(stats) >= threshold;
    }

    /**
     * 화면에 보여 줄 진행도. 진행도가 없는 훈장이면 비어 있다.
     *
     * <p><b>목표치를 넘어도 목표치로 잘라서 준다.</b> 오답노트를 187 개 적은 사람에게 "187/100" 을
     * 보여 주면 진행 막대가 계산되지 않고, 이미 받은 훈장에서 숫자가 계속 자라는 것도 이상하다.
     */
    public Optional<Long> progressOf(AchievementStats stats) {
        if (!showsProgress) {
            return Optional.empty();
        }
        return Optional.of(Math.min(progress.applyAsLong(stats), threshold));
    }

    /** 진행도를 내려주는 훈장의 목표치. 진행도가 없으면 비어 있다. */
    public Optional<Long> targetValue() {
        return showsProgress ? Optional.of((long) threshold) : Optional.empty();
    }

    /**
     * 저장된 키를 훈장으로 되돌린다. 모르는 키면 비어 있다.
     *
     * <p>훈장을 뺀 배포를 하면 예전 행의 키가 여기 안 잡힌다. 그때 예외를 던지면 훈장 하나 때문에
     * 훈장 화면 전체가 안 열린다. 모르는 행은 조용히 지나친다.
     */
    public static Optional<Achievement> fromKey(String key) {
        for (Achievement achievement : values()) {
            if (achievement.key.equals(key)) {
                return Optional.of(achievement);
            }
        }
        return Optional.empty();
    }
}
