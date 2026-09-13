package com.aisip.OnO.backend.achievement.dto;

import com.aisip.OnO.backend.achievement.entity.Achievement;
import com.aisip.OnO.backend.achievement.service.AchievementStats;

import java.time.LocalDateTime;

/**
 * 훈장 한 건. 프론트와 맞춘 형태라 필드 이름이 바뀌면 앱이 그대로 깨진다.
 *
 * @param imageUrl    앱 번들 안의 에셋 경로. 치장이 {@code assets/Cosmetic/...} 을 내려주는 것과 같다.
 * @param earned      받았는지. 받은 뒤에는 조건을 다시 계산해 안 맞아도 참이다.
 * @param earnedAt    처음 받은 시각(KST). 못 받았으면 null.
 * @param current     지금까지 온 만큼. 목표치를 넘으면 목표치로 잘린다. 진행도가 없는 훈장이면 null.
 * @param target      받는 데 필요한 만큼. 진행도가 없는 훈장(첫 걸음·불사조)이면 null.
 */
public record AchievementResponseDto(
        String key,
        String nameKo,
        String descriptionKo,
        String imageUrl,
        boolean earned,
        LocalDateTime earnedAt,
        Long current,
        Long target
) {

    public static AchievementResponseDto of(Achievement achievement, AchievementStats stats,
                                            LocalDateTime earnedAt) {
        return new AchievementResponseDto(
                achievement.getKey(),
                achievement.getNameKo(),
                achievement.getDescriptionKo(),
                achievement.getImageUrl(),
                earnedAt != null,
                earnedAt,
                achievement.progressOf(stats).orElse(null),
                achievement.targetValue().orElse(null)
        );
    }
}
